package com.example.terminalnfc;

import android.content.Context;
import android.content.SharedPreferences;

public class AppConfig {
    public static final String PREFS_NAME = "TerminalPrefs";
    public static final String PREF_IP = "server_ip";
    public static final String PREF_PORT = "server_port";

    public static final String DEFAULT_SERVER_IP = "192.168.1.21";
    public static final String DEFAULT_SERVER_PORT = "8080";
    public static final String GITHUB_URL = "https://github.com/Bartlomiej-szkola/terminalNFC";

    // Dynamiczne konstruowanie adresu WebSocket na podstawie pamięci urządzenia
    public static String getWsUrl(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String ip = prefs.getString(PREF_IP, DEFAULT_SERVER_IP);
        String port = prefs.getString(PREF_PORT, DEFAULT_SERVER_PORT);

        return "ws://" + ip + ":" + port + "/ws/terminal";
    }
}