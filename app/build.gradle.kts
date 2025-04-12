plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

configurations.all {
    resolutionStrategy {
        // Force single version of kotlin-gradle-plugin-api
        eachDependency {
            if (requested.group == "org.jetbrains.kotlin" &&
                requested.name == "kotlin-gradle-plugin-api") {
                useVersion("2.1.20")
            }
        }
    }
}

android {
    namespace = "me.thuanc177.kotsune"
    compileSdk = 35

    defaultConfig {
        applicationId = "me.thuanc177.kotsune"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // Ensure signing config is applied
            signingConfig = signingConfigs.getByName("debug") // Or your custom signing config
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Custom APK naming
            applicationVariants.all {
                val variant = this
                variant.outputs.all {
                    (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                        "kotsune-${variant.versionCode}.apk"
                }
            }
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
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.androidx.navigation.dynamic.features.fragment)
    implementation(libs.androidx.material3.window.size.class1)
    implementation(libs.gson)
    androidTestImplementation(libs.androidx.navigation.testing)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)
    implementation(libs.apollo.runtime)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}