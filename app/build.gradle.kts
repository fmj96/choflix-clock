import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.choflix.clock"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.choflix.clock"
        // 서랍 속 공기계가 대상이라 낮게 잡는다. 8.0 부터 포그라운드 서비스·알림 채널이 갖춰진다.
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "0.5.1"
    }

    signingConfigs {
        // 서명 키는 저장소 밖(local.properties)에서 받는다. 없으면 서명 없이 빌드된다.
        create("release") {
            val props = Properties()
            val f = rootProject.file("local.properties")
            if (f.exists()) f.inputStream().use { props.load(it) }
            val store = props.getProperty("storeFile")
            if (store != null) {
                storeFile = file(store)
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // 아이콘 묶음(material-icons-extended)이 통째로 들어가 10MB 가 넘는다 — 쓰는 것만 남긴다.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)

    implementation(libs.kotlinx.coroutines)
}
