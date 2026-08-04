plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

/**
 * OPTIONAL adapter — pulls the proprietary StarXpand SDK (com.starmicronics:stario10).
 * Kept OUT of the SDK-free core (:universal-printer-search) per CLAUDE.md rule 6; a
 * consumer includes this module only if they need Star discovery. It plugs into the
 * core via the PrinterDiscoverer interface, no core changes required.
 */
android {
    namespace = "com.universalprintersearch.adapter.star"
    compileSdk = 34

    defaultConfig {
        minSdk = 26 // StarXpand SDK floor
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(project(":universal-printer-search"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.stario10)
}
