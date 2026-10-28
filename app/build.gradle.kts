plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.tensei.ytxtube"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.tensei.ytxtube"
        minSdk = 24
        targetSdk = 36
        versionCode = 160
        versionName = "1.6.0"
    }

    signingConfigs {
        getByName("debug") {
            // v1 matters on some Android 7/8 Samsung installers; v2 keeps modern
            // devices happy. Signing with both makes the same APK installable on
            // the widest range of devices.
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {

            isMinifyEnabled = true
            isShrinkResources = true


            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17

        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures { compose = true; buildConfig = true }



    androidResources {
        localeFilters += listOf("en")
    }


    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        jniLibs {
            // Extract native libraries on install. Uncompressed/embedded .so files
            // are the usual cause of "problem parsing the package" on older Samsung
            // Android 7/8 firmware.
            useLegacyPackaging = true
        }
        resources {
            excludes += listOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/*.kotlin_module",
                "META-INF/DEPENDENCIES",

                "META-INF/com/android/build/**",
                "META-INF/version-control-info.textproto",
                "META-INF/*.version",


                "google/**",
                "kotlin/**",

                "okhttp3/internal/publicsuffix/publicsuffixes.gz",

                "org/mozilla/javascript/resources/Messages_*.properties",

                "DebugProbesKt.bin",
                "META-INF/**/LICENSE.txt",
                "META-INF/**/LICENSE",
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    val media3 = "1.10.1"

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")

    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")


    implementation("io.github.kyant0:backdrop:2.0.1")
    implementation("io.github.kyant0:shapes:1.2.1")

    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-dash:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")
    implementation("androidx.media3:media3-database:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("androidx.media3:media3-ui-compose:$media3")

    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.google.guava:guava:33.3.1-android")


    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
