package com.anythingsskyblue.android.inapppurchase

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.AcknowledgePurchaseResponseListener
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetailsResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * 1. Google Play와 연결 [connectToGooglePlay]
 * 2. 구독 상품 결제 클릭[onClickBuySubscription]
 * 3. 결제 상품 정보 조회 [getSubscriptionProductDetails]
 * 4. 결제 요청(상품정보 전달) [requestPurchase]
 * 5. 결제 승인 처리 [acknowledgedPurchase]
 */
class PurchaseUtils private constructor(context: Context) {
    private val _isPurchasedForRemoveAds = MutableStateFlow(false)
    val isPurchasedForRemoveAds = _isPurchasedForRemoveAds.asStateFlow()

    private val purchaseListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                acknowledgedPurchase(purchase)
            }
            _isPurchasedForRemoveAds.update { true }

        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            _isPurchasedForRemoveAds.update { true }

        } else {
            _isPurchasedForRemoveAds.update { false }
        }
        Log.d(TAG, "onPurchasesUpdated: result == ${billingResult.responseCode}, ${billingResult.debugMessage}")
    }

    private val purchaseParams = PendingPurchasesParams
        .newBuilder()
        .enableOneTimeProducts()
        .build()

    private val billingClient = BillingClient.newBuilder(context)
        .enablePendingPurchases(purchaseParams)
        .setListener(purchaseListener)
        .build()

    init {
        CoroutineScope(Dispatchers.IO).launch {
            retryIfFailure(3, 500){ connectToGooglePlay() }
        }
    }

    suspend fun onClickBuySubscription(activity: Activity, subscriptionId: String): Result<Unit> {
        val connectResult = connectToGooglePlay()
        if (connectResult.isFailure){
            return Result.failure(Throwable("connection to google play failed."))
        }

        val productDetailResult = getSubscriptionProductDetails(subscriptionId)
        val productDetailsResultCode = productDetailResult.billingResult.responseCode
        if (productDetailsResultCode != BillingClient.BillingResponseCode.OK && productDetailsResultCode != BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED){
            return Result.failure(Throwable("get product details fail == response code is $productDetailsResultCode, message is \"${productDetailResult.billingResult.debugMessage}\""))
        }

        return requestPurchase(activity, productDetailResult)
    }

    /**
     * 구글 플레이와 연결. 가장 먼저 호출 해야함.
     */
    private suspend fun connectToGooglePlay(): Result<Unit>{
        return suspendCancellableCoroutine { continuation ->

            val clientStateListener = object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (continuation.isActive){
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            Log.d(TAG, "onBillingSetupFinished: 연결 완료")
                            continuation.resume(Result.success(Unit))
                        }else{
                            Log.d(TAG, "onBillingSetupFinished: 연결 x")
                            continuation.resume(Result.failure(Throwable()))
                        }
                    }
                }

                override fun onBillingServiceDisconnected() {
                    if (continuation.isActive){
                        Log.d(TAG, "onBillingServiceDisconnected: 연결 x")
                        continuation.resume(Result.failure(Throwable()))
                    }
                }
            }

            billingClient.startConnection(clientStateListener)
        }
    }

    /**
     * 결제 요청
     */
    private suspend fun requestPurchase(activity: Activity, productDetailsResult: ProductDetailsResult): Result<Unit>{
        val productDetails = productDetailsResult.productDetailsList?.firstOrNull()
            ?: return Result.failure(Throwable("productDetailsResult == product details is null"))
        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
            ?: return Result.failure(Throwable("productDetailsResult == offerToken is null"))

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                // retrieve a value for "productDetails" by calling queryProductDetailsAsync()
                .setProductDetails(productDetails)
                // For One-time product, "setOfferToken" method shouldn't be called.
                // For subscriptions, to get an offer token, call ProductDetails.subscriptionOfferDetails()
                // for a list of offers that are available to the user
                .setOfferToken(offerToken)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .setIsOfferPersonalized(true)       //개인화된 가격 정보 표시(유럽)
            .build()

        val billingResult = billingClient.launchBillingFlow(activity, billingFlowParams)

        return if (BillingClient.BillingResponseCode.OK == billingResult.responseCode)
            Result.success(Unit)
        else
            Result.failure(Throwable("result code == ${billingResult.responseCode}, debugMessage == ${billingResult.debugMessage}"))
    }

    /**
     * 구매 가능한 상품(구독, 소모성, 비소모성) 정보를 조회.
     *
     * 현재 코드는 특정 구독상품을 조회(remove_ads)
     */
    private suspend fun getSubscriptionProductDetails(id: String): ProductDetailsResult {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(ProductType.SUBS)
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder()
        params.setProductList(productList)

        val productDetailsResult = withContext(Dispatchers.IO) {
            billingClient.queryProductDetails(params.build())
        }

        return productDetailsResult
    }

    /**
     * 결제 승인 요청
     *
     * - 결제 승인 요청을 기간 내 하지 않으면 결제 취소 처리 됨.
     * - 보안면에서 백엔드에서 할 것을 권장하고 있음.
     */
    private fun acknowledgedPurchase(purchase: Purchase) {
        val listener = AcknowledgePurchaseResponseListener {
            Log.d(TAG, "acknowledgedPurchase: ${it.responseCode}, ${it.debugMessage}")
        }

        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (purchase.isAcknowledged.not()) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams
                    .newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)

                billingClient.acknowledgePurchase(acknowledgePurchaseParams.build(), listener)
            }
        }
    }

    /**
     * onResume 같이 화면 갱신 시 마다 호출하여 구독 상태 확인 필요
     */
    suspend fun confirmSubscribed() {
        val params = QueryPurchasesParams
            .newBuilder()
            .setProductType(ProductType.SUBS)
            .build()

        if (billingClient.isReady.not()){
            connectToGooglePlay()
        }

        return suspendCancellableCoroutine { continuation ->
            billingClient.queryPurchasesAsync(params) { _, purchases ->
                val isPurchased = purchases.isNotEmpty() && purchases.all {
                    Log.d(TAG, "checkSubscribed: ${it.isAcknowledged}, ${it.purchaseState}")
                    it.isAcknowledged || it.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                Log.d(TAG, "checkSubscribed: count == ${purchases.size}, isPurchased == $isPurchased")
                _isPurchasedForRemoveAds.update { isPurchased }

                continuation.resume(Unit)
            }
        }
    }

    companion object {
        @Volatile
        private var instance: PurchaseUtils? = null

        fun getInstance(context: Context) =
            instance ?:   synchronized(PurchaseUtils::class.java) {
                instance ?: PurchaseUtils(context).also {
                    instance = it
                }
            }

        const val TAG = "PurchaseUtils"
    }

    private suspend fun retryIfFailure(
        times: Int,
        delayMillis: Long = 500,
        block: suspend () -> Result<Unit>
    ): Result<Unit> {
        var currentAttempt = 0
        while (true) {
            val result = block()
            if (result.isSuccess) {
                return result
            }
            if (++currentAttempt >= times) {
                return result
            }
            delay(delayMillis)
        }
    }
}