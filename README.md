## In App Purchase example

### Limit
- Currently, only cover subscription products.

### Step
1. Create subscription products from Google Play Console
2. Modify the applicationId.
```kotlin
//app/build.gradle.kts
android {
    ...
    compileSdk = 34

    defaultConfig {
        applicationId = "your applicationId"
        ...
    }
}
```
3. Add in-app-purchase module.
[![](https://jitpack.io/v/hoonsa-lim/Android-in-app-purchase.svg)](https://jitpack.io/#hoonsa-lim/Android-in-app-purchase)
```kotlin
//app/build.gradle.kts
dependencies {
    ...
    implementation("com.github.hoonsa-lim:Android-in-app-purchase:1.0.1")
}
```
4. Modify to the ID of the subscription product created in No.1.
```kotlin
//MainActivity.kt
Button(
    onClick = {
        lifecycleScope.launch {
            purchaseUtils.onClickBuySubscription(this@MainActivity, "your subscription product id")
        }
    }
) {
    Text(text = "구독 결제")
}
```

### ETC
- If it doesn't work, delete the cache of the Google Play app and try again.
