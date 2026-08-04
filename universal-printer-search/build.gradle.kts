plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

group = "com.universalprintersearch"
version = "1.0.0"

android {
    namespace = "com.universalprintersearch"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
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

    // Expose a single publishable "release" variant (with a sources jar for consumers).
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    testOptions {
        unitTests {
            // android.util.Log etc. return defaults instead of throwing in JVM unit tests.
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}

publishing {
    publications {
        // Coordinates: com.universalprintersearch:universal-printer-search:1.0.0
        // Publish locally with `./gradlew :universal-printer-search:publishToMavenLocal`.
        // JitPack builds this automatically from a git tag; add a maven { url = … } repository
        // below to push to a private/remote Maven.
        register<MavenPublication>("release") {
            afterEvaluate { from(components["release"]) }
            artifactId = "universal-printer-search"

            pom {
                name.set("Universal Printer Search")
                description.set(
                    "SDK-free Android library that discovers receipt/label printers on the LAN " +
                        "(Epson ENPC, Sunmi mDNS, Zebra UDP, Star web, SNMP brands) and over USB.",
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
