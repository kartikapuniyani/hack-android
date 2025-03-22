plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.hiltAndroid)
    alias(libs.plugins.ksp)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

android {
    namespace = "com.example.sensorsride"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.sensorsride"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    //retrofit
    implementation(libs.retrofit)

    // okhttp
    implementation(libs.okhttp)
    implementation(libs.okhttpInterceptor)

    //gson
    implementation(libs.gson)
    implementation(libs.gsonConverter)
    implementation(libs.coroutineAdapter)
    implementation(libs.chucker)
    implementation(libs.play.services.maps)

    ksp(libs.hiltCompiler)

    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.constraintLayoutCompose)
    implementation(libs.viewModelKTX)

    //hilt
    implementation(libs.hiltAndroid)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.firebase.messaging.ktx)
    ksp(libs.hiltCompiler)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.ui.viewbinding)
    implementation(libs.androidx.work.runtime.ktx)

    //play services
    implementation(libs.play.services.maps)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.play.services.location)
    implementation(libs.firebase.auth.ktx)
    //compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.material3)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.navigation.compose)


    implementation(libs.browser)

    implementation(libs.camera.core)
    implementation(libs.androidx.camera.video)
    implementation(libs.camera.view)
    implementation(libs.androidx.camera.video)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.extensions)


    //debug
    debugImplementation(libs.androidx.ui.tooling)
    //test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
    //android test
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    implementation(libs.gson)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.maps.compose)

    implementation(libs.roomKTX)
    implementation(libs.roomRunTime)
    implementation(libs.kotlinx.serialization.json)
    ksp(libs.roomCompiler)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.material)
    implementation ("com.github.PhilJay:MPAndroidChart:v3.1.0")

}