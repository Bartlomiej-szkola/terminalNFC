package com.example.terminalnfc;

public class AppConfig {
    public static final String SERVER_IP = "192.168.1.21";
    public static final String SERVER_URL = "http://" + SERVER_IP + ":8080/api/terminal";

    // Adres WebSocket (zauważ "ws://" zamiast "http://")
    public static final String WS_URL = "ws://" + SERVER_IP + ":8080/ws/terminal";
}
