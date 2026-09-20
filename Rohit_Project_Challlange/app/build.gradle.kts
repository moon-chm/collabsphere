plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("androidx.room") version "2.7.2"
    id("com.google.devtools.ksp") version "2.0.0-1.0.22"
    kotlin("plugin.serialization") version "2.2.10"
}

android {
    namespace = "com.example.rohit_project_challlange"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.rohit_project_challlange"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("boolean", "USE_PRODUCTION_BACKEND", "true")
        }
        debug {
            buildConfigField("boolean", "USE_PRODUCTION_BACKEND", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    val room_ver = "2.7.2"
    val datastore = "1.1.2"
    val glance_ver = "1.1.1"

    // ── Jetpack Glance (home screen widgets with Compose syntax) ──
    implementation("androidx.glance:glance-appwidget:$glance_ver")
    implementation("androidx.glance:glance-material3:$glance_ver")
    val ktor_version = "3.1.0"
    val work_version = "2.10.2"

    implementation("androidx.datastore:datastore-preferences:$datastore")

    ksp("androidx.room:room-compiler:$room_ver")
    implementation("androidx.room:room-runtime:$room_ver")
    implementation("androidx.room:room-ktx:$room_ver")

    implementation("androidx.work:work-runtime-ktx:$work_version")

    implementation("io.ktor:ktor-client-core:${ktor_version}")
    implementation("io.ktor:ktor-client-android:${ktor_version}")
    implementation("io.ktor:ktor-client-okhttp:${ktor_version}")
    implementation("io.ktor:ktor-client-websockets:${ktor_version}")
    implementation("io.ktor:ktor-client-content-negotiation:${ktor_version}")
    implementation("io.ktor:ktor-serialization-kotlinx-json:${ktor_version}")
    implementation("io.ktor:ktor-client-logging:${ktor_version}")

    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.compose.material:material-icons-extended")

    // ── Coil (avatar images: uploaded JPG/PNG + server-generated default SVG) ──
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-svg:2.7.0")

    // ── uCrop (in-app circular photo cropper before avatar upload) ──
    implementation("com.github.yalantis:ucrop:2.2.8-native")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-process:2.6.1")

    implementation("at.favre.lib:bcrypt:0.10.2")

    implementation("io.insert-koin:koin-android:3.5.6")
    implementation("io.insert-koin:koin-androidx-compose:3.5.6")
    implementation("io.insert-koin:koin-androidx-workmanager:3.5.6")

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}