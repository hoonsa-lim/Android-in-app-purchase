package com.anythingsskyblue.android.inapppurchase

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import com.anythingsskyblue.android.inapppurchase.ui.theme.InAppPurchaseTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val purchaseUtils by lazy { PurchaseUtils.getInstance(this@MainActivity) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            InAppPurchaseTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceEvenly,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ){
                        val isPurchased by purchaseUtils.isPurchasedForRemoveAds.collectAsState()

                        Greeting("Android")

                        if (isPurchased.not()){
                            Button(
                                onClick = {
                                    lifecycleScope.launch {
                                        purchaseUtils.onClickBuySubscription(this@MainActivity, "test_subscription")
                                            .onSuccess { Log.d("MainActivity", "onCreate: success") }
                                            .onFailure { Log.d("MainActivity", "onCreate: failure ${it.message}") }
                                    }
                                }
                            ) {
                                Text(text = "구독 결제")
                            }
                        }

                        Text(
                            text = if (isPurchased) "구독!" else "구독 XX",
                            color = if (isPurchased) Color.Green else Color.Red
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        /**
         * 구독 상태가 바뀔 수 있는 상황
         *
         * 구독을 했을 때
         * - 앱에서 구독을 했을 때
         *
         * 구독을 취소 했을 때
         * - 앱 사용 중 만료기간이 지났을 때
         * - 구글 플레이에서 구독 취소하고 만료기간이 지났는데 앱으로 돌아왔을 때
         *
         * [onResume]에서 구독 상태를 확인
         */

        lifecycleScope.launch {
            purchaseUtils.confirmSubscribed()
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    InAppPurchaseTheme {
        Greeting("Android")
    }
}