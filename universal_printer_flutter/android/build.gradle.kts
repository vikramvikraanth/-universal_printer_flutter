group = "com.universalprinter.universal_printer_flutter"
version = "1.0-SNAPSHOT"

buildscript {
    val kotlinVersion = "2.2.20"
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:8.11.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        // DantSu ESC/POS + iMin printer libraries (mirrors the SDK repo's settings.gradle.kts).
        maven { url = uri("https://jitpack.io") }
    }
}

plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    namespace = "com.universalprinter.universal_printer_flutter"

    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
        getByName("test") {
            java.srcDirs("src/test/kotlin")
        }
    }

    defaultConfig {
        // StarXpand SDK floor (bundled in :universal-printer) — forced on consumers.
        minSdk = 26
    }

    testOptions {
        unitTests {
            // The moved SDK unit tests touch android.graphics/os stubs; return defaults instead of throwing.
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // ESC/POS engine (TCP + USB, text + image) — wrapped by the moved Kotlin API.
    implementation("com.github.DantSu:ESCPOS-ThermalPrinter-Android:3.3.0")
    // Vendor built-in printers.
    implementation("com.starmicronics:stario10:1.12.0")   // Star (StarXpand)
    implementation("com.sunmi:printerlibrary:1.0.19")      // Sunmi inner printer
    implementation("com.github.iminsoftware:IminPrinterLibrary:V2.0.0.18") // iMin v1 + v2
    // Barcode/QR bitmap generation for the HTML receipt path.
    implementation("com.google.zxing:core:3.5.3")
    // URL image download + offline (disk) caching.
    implementation("com.github.bumptech.glide:glide:4.16.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
