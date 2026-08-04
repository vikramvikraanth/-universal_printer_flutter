plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

group = "com.universalprinter"
version = "1.0.0"

android {
    namespace = "com.universalprinter"
    compileSdk = 34

    defaultConfig {
        minSdk = 26 // StarXpand SDK floor (bundled)
        consumerProguardFiles("consumer-rules.pro")
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

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // ESC/POS engine (TCP + USB, text + image) — wrapped by our Kotlin API.
    implementation(libs.dantsu.escpos)
    // Vendor built-in printers (bundled, single SDK).
    implementation(libs.stario10)       // Star (StarXpand)
    implementation(libs.sunmi.printer)  // Sunmi inner printer
    implementation(libs.imin.printer)   // iMin v1 + v2
    // Barcode/QR bitmap generation for the HTML receipt path (pure-Java).
    implementation(libs.zxing.core)
    // URL image download + offline (disk) caching for receipt logos / hosted QR images.
    implementation(libs.glide)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            afterEvaluate { from(components["release"]) }
            artifactId = "universal-printer"
            pom {
                name.set("Universal Printer")
                description.set(
                    "Kotlin/coroutine Android printing SDK: ESC/POS (TCP/USB), Star, Sunmi, iMin; " +
                        "full-receipt image + formatted text; per-printer print queue.",
                )
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
            }
        }
    }
}
