package com.example.rohit_project_challlange

object AppConfig {
    /**
     * Production Cloud URL on Render (update once deployed to Render)
     * e.g., "https://collabsphere-server.onrender.com"
     */
    const val PRODUCTION_BASE_URL = "https://collabsphere-server-qtke.onrender.com"

    /**
     * Local Development Server IP:
     * - "127.0.0.1" / "localhost" if using ADB reverse port forwarding (adb reverse tcp:8080 tcp:8080)
     * - "10.0.2.2" if using Android Studio Emulator
     * - "192.168.x.x" if using physical device over local Wi-Fi without ADB reverse
     */
    const val LOCAL_DEV_IP = "127.0.0.1"
    const val LOCAL_DEV_PORT = "8080"
    const val LOCAL_DEV_BASE_URL = "http://$LOCAL_DEV_IP:$LOCAL_DEV_PORT"

    /**
     * Driven by the build type (see app/build.gradle.kts) instead of a hand-edited constant, so a
     * debug build can never accidentally ship pointed at production, or a release build at localhost.
     */
    val USE_PRODUCTION_BACKEND = BuildConfig.USE_PRODUCTION_BACKEND

    val BASE_URL: String
        get() = if (USE_PRODUCTION_BACKEND) PRODUCTION_BASE_URL else LOCAL_DEV_BASE_URL
}

