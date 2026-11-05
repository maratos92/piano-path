plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
    kotlin("kapt")
}
val supportUrl = providers.environmentVariable("PIANO_SUPPORT_URL").orNull?.trim()?.takeIf { it.isNotBlank() } ?: "https://ko-fi.com/maratbatkaev"
require(supportUrl.isEmpty() || supportUrl.matches(Regex("https://ko-fi[.]com/[A-Za-z0-9_-]+"))) {
    "PIANO_SUPPORT_URL must be a public https://ko-fi.com/profile URL"
}
android {
    namespace = "org.pianopath"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "org.pianopath"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "0.5.0-dev.2"
        // The Actions runner supplies GITHUB_SHA; local builds are intentionally labelled "local".
        val revision = providers.environmentVariable("GITHUB_SHA").orNull
            ?.take(8)?.takeIf { it.matches(Regex("[a-fA-F0-9]{8}")) } ?: "local"
        buildConfigField("String", "BUILD_REVISION", "\"$revision\"")
        buildConfigField("String", "SUPPORT_URL", "\"$supportUrl\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
    kotlinOptions { jvmTarget = "21" }
    signingConfigs {
        // AGP may default to ~/.config/.android; select the restored CI key explicitly.
        providers.environmentVariable("PIANO_DEBUG_KEYSTORE_PATH").orNull?.let { path ->
            getByName("debug") {
                storeFile = file(path)
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        if (System.getenv("PIANO_KEYSTORE") != null) create("release") {
            storeFile = file(System.getenv("PIANO_KEYSTORE"))
            storePassword = System.getenv("PIANO_STORE_PASSWORD")
            keyAlias = System.getenv("PIANO_KEY_ALIAS")
            keyPassword = System.getenv("PIANO_KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (signingConfigs.findByName("release") != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
