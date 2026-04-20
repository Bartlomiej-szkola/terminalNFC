package com.example.terminalnfc;

public class AppConfig {
    public static final String SERVER_IP = "172.31.114.133";
    public static final String SERVER_URL = "http://" + SERVER_IP + ":8080/api/terminal";
    public static final int WEBHOOK_PORT = 9090;
    
    // Ustaw na true, jeśli testujesz na emulatorze (wymaga komendy adb forward)
    // Ustaw na false, jeśli używasz fizycznego telefonu w tej samej sieci Wi-Fi co kasa.
    public static final boolean IS_EMULATOR = true;
}
