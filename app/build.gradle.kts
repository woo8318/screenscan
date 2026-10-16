plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

/** CI passes the release tag (without "v") as APP_VERSION; local and branch builds are dev builds. */
val appVersion: String = System.getenv("APP_VERSION")?.takeIf { it.isNotBlank() } ?: "0.0.0-dev"

/** Monotonic code so the package installer accepts each newer release as an upgrade. */
fun versionCodeOf(version: String): Int {
    val parts = version.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
    val code = parts.getOrElse(0) { 0 } * 10_000 + parts.getOrElse(1) { 0 } * 100 + parts.getOrElse(2) { 0 }
    return code.coerceAtLeast(1)
}

android {
    namespace = "com.woo.screenscan"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.woo.screenscan"
        minSdk = 26
        targetSdk = 35
        versionCode = versionCodeOf(appVersion)
        versionName = appVersion
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.text.latin)
    implementation(libs.mlkit.text.korean)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.json)
}
