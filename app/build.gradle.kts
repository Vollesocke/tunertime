import java.io.FileInputStream
import java.util.Properties
val keyprops=Properties().apply {
    load(FileInputStream(rootProject.file("keystore.properties")))
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.galexander.tunertime"
    compileSdk {
        version = release(36)
    }
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/lib")
        }
    }
    signingConfigs {
        create("release") {
            storeFile = file(keyprops["storeFile"] as String)
            storePassword = keyprops["storePassword"] as String
            keyAlias = keyprops["keyAlias"] as String
            keyPassword = keyprops["keyPassword"] as String
        }
    }

    defaultConfig {
        applicationId = "org.galexander.tunertime"
        minSdk = 21
        targetSdk = 36
        versionCode = 28
        versionName = "18.8-organ"
        manifestPlaceholders["appLabel"] = "TunerTime Organ"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    // UI layout tests (app/src/androidTest) run against their own build,
    // which installs next to the Play Store and dev apps on a real phone
    // and leaves their settings alone: ./gradlew connectedUitestAndroidTest
    testBuildType = "uitest"

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        // a release build that installs next to the Play Store app,
        // with its own settings and presets: ./gradlew assembleDev
        create("dev") {
            initWith(getByName("release"))
            applicationIdSuffix = ".dev"
            manifestPlaceholders["appLabel"] = "TunerTime Organ"
        }
        // the same, as Onno installs it on his phone (from his own build,
        // signed with his keystore.properties): ./gradlew assembleLocal
        create("local") {
            initWith(getByName("release"))
            applicationIdSuffix = ".local"
            manifestPlaceholders["appLabel"] = "TunerTime Organ"
        }
        create("uitest") {
            initWith(getByName("release"))
            applicationIdSuffix = ".uitest"
            manifestPlaceholders["appLabel"] = "TunerTime UI test"
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
    implementation(libs.androidx.core.ktx)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.accessibility.test.framework)
    androidTestImplementation(libs.guava)
    // plain JVM tests (app/src/test): ./gradlew testDevUnitTest
    testImplementation(libs.junit)
/*
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
*/
}
