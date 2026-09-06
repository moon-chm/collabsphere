package com.example.rohit_project_challlange

object AppConfig {
    /**
     * Production Cloud URL on Render
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
     * Set to TRUE to connect to Render Cloud Production.
     * Set to FALSE to connect to Local Laptop Development server.
     */
    const val USE_PRODUCTION_BACKEND = true

    val BASE_URL: String
        get() = if (USE_PRODUCTION_BACKEND) PRODUCTION_BASE_URL else LOCAL_DEV_BASE_URL
}

