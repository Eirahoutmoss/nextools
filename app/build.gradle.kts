import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// İmza bilgileri depoya girmez: proje kökündeki keystore.properties dosyasından okunur.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.eirahoutmoss.nextools"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.eirahoutmoss.nextools"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "1.0.0"

        // Güncelleme denetiminin baktığı GitHub deposu (Nex standardı).
        buildConfigField("String", "UPDATE_REPO_OWNER", "\"Eirahoutmoss\"")
        buildConfigField("String", "UPDATE_REPO_NAME", "\"nextools\"")
    }

    androidResources {
        // Android 13+ "Uygulama dili" ayarı için dil listesi otomatik üretilir (res/resources.properties).
        generateLocaleConfig = true
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreProps.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            // BouncyCastle (bcprov / bcpkix / bcutil) aynı imza dosyalarını taşır.
            excludes += setOf("META-INF/versions/9/OSGI-INF/MANIFEST.MF", "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
        }
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    // Kamera araçları (Renk Ölçer, Büyüteç) ve QR okuma (Google kod tarayıcı; kamera izni istemez)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.play.services.code.scanner)
    // Kimlik Kartı (ICAO 9303) ve UWB
    implementation(libs.jmrtd)
    implementation(libs.scuba.sc.android)
    implementation(libs.bcprov)
    implementation(libs.bcpkix)
    implementation(libs.jp2.android)
    implementation(libs.androidx.core.uwb)
    implementation(libs.guava)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
