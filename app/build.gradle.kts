plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0"

    // ✅ Apply Google Services plugin so Firebase can initialize with google-services.json
    id("com.google.gms.google-services")

    id("kotlin-parcelize") // Add this line
}

android {
    namespace = "com.example.structurescan"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.structurescan"
        minSdk = 24
        targetSdk = 36
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

        // Add Compose compiler feature flags
        freeCompilerArgs += listOf(
            "-P=plugin:androidx.compose.compiler.plugins.kotlin:featureFlag=IntrinsicRemember=true",
            "-P=plugin:androidx.compose.compiler.plugins.kotlin:featureFlag=OptimizeNonSkippingGroups=true",
            "-P=plugin:androidx.compose.compiler.plugins.kotlin:featureFlag=StrongSkipping=true"
        )
    }
    buildFeatures {
        viewBinding = true
        mlModelBinding = true
    }
}

dependencies {
    // ------------------------------
    // ✅ Firebase Setup
    // ------------------------------

    // Firebase BOM (keeps all Firebase libs on same version automatically)
    implementation(platform("com.google.firebase:firebase-bom:34.3.0"))

    // ✅ Google Sign-In (needed for Firebase Google Auth)
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // Core Firebase services
    implementation("com.google.firebase:firebase-analytics")     // Analytics
    implementation("com.google.firebase:firebase-auth")          // Authentication (Login, Register, Forgot Password)
    implementation("com.google.firebase:firebase-firestore")     // Cloud Firestore (Database)
    implementation("com.google.firebase:firebase-storage")       // Firebase Storage (Upload photos, scan results, etc.)

    // TensorFlow Lite runtime (main required dependency)
    implementation("org.tensorflow:tensorflow-lite:2.13.0")
    // TensorFlow Lite Support Library (for TensorBuffer, preprocessing, etc.)
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    // TensorFlow Lite GPU Delegate (if you want GPU acceleration, optional)
    implementation("org.tensorflow:tensorflow-lite-gpu:2.13.0")
    // If your generated model class (ModelUnquant) needs the metadata library
    implementation("org.tensorflow:tensorflow-lite-metadata:0.4.4")


    // ------------------------------
    // Existing project dependencies
    // ------------------------------

    // Core AndroidX + Material
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // Image loading
    implementation("io.coil-kt:coil-compose:2.4.0")

    // Jetpack Compose BOM (keeps versions consistent)
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))

    // Compose UI + Preview
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation(libs.tensorflow.lite.support)
    implementation(libs.tensorflow.lite.metadata)
    implementation(libs.tensorflow.lite.gpu)
    implementation(libs.androidx.material3)
    debugImplementation("androidx.compose.ui:ui-tooling")

    // CameraX (for scanning feature)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.lifecycle)

    // Material 3 (modern UI components)
    implementation("androidx.compose.material3:material3")

    // Icons (e.g., visibility toggle in password fields)
    implementation("androidx.compose.material:material-icons-extended")

    // Foundation (Row, Column, Spacer, etc.)
    implementation("androidx.compose.foundation:foundation")

    // Activity Compose integration
    implementation("androidx.activity:activity-compose")

    // UI Tests
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Unit + Instrumentation Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}