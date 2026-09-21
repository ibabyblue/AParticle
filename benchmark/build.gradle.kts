plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ibabyblue.aparticle.benchmark"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.ibabyblue.aparticle.benchmark"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        release {
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    // Compile the actual library sources, including internal rendering seams, into a
    // non-debuggable standalone harness. No copied implementation or test double.
    sourceSets.getByName("main").java.srcDir("../aparticle/src/main/kotlin")
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
