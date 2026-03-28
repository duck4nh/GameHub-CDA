plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.gamehub"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.gamehub"
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
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    
    // WorkManager
    implementation(libs.androidx.work.runtime)

    // 1. Firebase (Cloud: Quản lý Đăng nhập & Lưu hồ sơ/Bạn bè)
    implementation(platform("com.google.firebase:firebase-bom:34.11.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")

    // 2. Room Database (Local: Lưu cache danh sách bạn bè)
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    annotationProcessor("androidx.room:room-compiler:$room_version")

    // 3. Glide (Tải ảnh đại diện từ DiceBear API như thiết kế)
    implementation("com.github.bumptech.glide:glide:4.16.0")
    // Thêm dòng này để sửa lỗi ListenableFuture
    implementation("com.google.guava:guava:31.1-android")

    // Đảm bảo bạn cũng đã có WorkManager (vì bạn đang dùng SyncWorker)
    implementation("androidx.work:work-runtime:2.8.1")
}
