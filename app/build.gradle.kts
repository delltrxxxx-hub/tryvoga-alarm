plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "ua.tryvoga.alarm"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "ua.tryvoga.alarm"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "1.4"
        // Репозиторій, з якого застосунок бере оновлення (GitHub Releases).
        buildConfigField("String", "UPDATE_REPO", "\"delltrxxxx-hub/tryvoga-alarm\"")
    }

    // Ключ підпису зберігається ПОЗА репозиторієм: ~/.gradle/gradle.properties (TRYVOGA_*).
    val storeFilePath = providers.gradleProperty("TRYVOGA_STORE_FILE").orNull
    signingConfigs {
        if (storeFilePath != null) create("release") {
            storeFile = file(storeFilePath)
            storePassword = providers.gradleProperty("TRYVOGA_STORE_PASSWORD").get()
            keyAlias = providers.gradleProperty("TRYVOGA_KEY_ALIAS").get()
            keyPassword = providers.gradleProperty("TRYVOGA_KEY_PASSWORD").get()
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
