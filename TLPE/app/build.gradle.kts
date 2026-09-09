plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "poc.sithi.tlpe"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "poc.sithi.tlpe"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("teststore") {
            storeFile = file("teststore.jks")
            storePassword = "teststore"
            keyAlias = "teststore"
            keyPassword = "teststore"
        }
    }
    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("teststore")
        }
        release {
            signingConfig = signingConfigs.getByName("teststore")
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
    buildFeatures {
        viewBinding = true
    }
    androidResources {
        noCompress.add("apk")
    }
    flavorDimensions += "appType"
    productFlavors {
        create("poc") {
            dimension = "appType"
        }
        create("system") {
            dimension = "appType"
        }
    }
}
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}