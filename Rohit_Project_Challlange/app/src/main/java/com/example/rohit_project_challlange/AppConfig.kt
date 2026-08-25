package com.example.rohit_project_challlange

object AppConfig {
    /**
     * Set this to:
     * - "10.0.2.2" if you are running the app on the Android Emulator
     * - "192.168.83.101" (your current laptop Wi-Fi IP) if you are running the app on a physical device
     */
    const val SERVER_IP = "192.168.83.101"
    const val SERVER_PORT = "8080"

    const val BASE_URL = "http://$SERVER_IP:$SERVER_PORT"
}
