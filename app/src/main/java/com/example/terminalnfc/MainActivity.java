package com.example.terminalnfc;

import android.app.AlertDialog;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;
import okhttp3.*;

public class MainActivity extends AppCompatActivity implements NfcAdapter.ReaderCallback {

    private OkHttpClient client;
    private WebSocket webSocket;
    private NfcAdapter nfcAdapter;
    private ToneGenerator toneGenerator;

    private TextView tvTerminalStatus, tvAmount, tvInstruction, tvCardInfo;

    private boolean isWaitingForCard = false;
    private String savedCardUid = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        client = new OkHttpClient();
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 100);

        tvTerminalStatus = findViewById(R.id.tvTerminalStatus);
        tvAmount = findViewById(R.id.tvAmount);
        tvInstruction = findViewById(R.id.tvInstruction);
        tvCardInfo = findViewById(R.id.tvCardInfo);

        connectWebSocket();
    }

    private void connectWebSocket() {
        // Konwersja http na ws
        String wsUrl = AppConfig.SERVER_URL.replace("http://", "ws://").replace("/api/terminal", "/ws/terminal");

        Request request = new Request.Builder().url(wsUrl).build();
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                runOnUiThread(() -> {
                    tvInstruction.setText("Połączono z kasą sklepową.");
                    tvTerminalStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"));
                });
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    JSONObject json = new JSONObject(text);
                    String action = json.getString("action");

                    runOnUiThread(() -> {
                        if (action.equals("INIT")) {
                            isWaitingForCard = true;
                            savedCardUid = null;
                            String amount = null;
                            try {
                                amount = json.getString("amount");
                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                            tvTerminalStatus.setText("ZBLIŻ KARTĘ");
                            tvTerminalStatus.setTextColor(android.graphics.Color.parseColor("#FFEB3B"));
                            tvAmount.setText(amount + " PLN");
                            tvInstruction.setText("Oczekuję na zbliżenie karty...");
                            tvCardInfo.setText("---");
                        }
                        else if (action.equals("REQUIRE_PIN")) {
                            showPinDialog();
                        }
                        else if (action.equals("RESULT")) {
                            isWaitingForCard = false;
                            String status = null;
                            try {
                                status = json.getString("status");
                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                            handleFinalResult(status);
                        }
                    });
                } catch (Exception e) { e.printStackTrace(); }
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                runOnUiThread(() -> tvInstruction.setText("Rozłączono."));
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                runOnUiThread(() -> {
                    tvInstruction.setText("Błąd połączenia. Próbuję ponownie...");
                    new Handler(Looper.getMainLooper()).postDelayed(() -> connectWebSocket(), 3000);
                });
            }
        });
    }

    private void handleFinalResult(String status) {
        if ("SUCCESS".equals(status)) {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 400); // PIK!
            tvTerminalStatus.setText("ZAAKCEPTOWANO");
            tvTerminalStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"));
            tvInstruction.setText("Płatność zakończona pomyślnie.");
        } else {
            toneGenerator.startTone(ToneGenerator.TONE_SUP_ERROR, 1000); // BEEP BEEP BEEP!
            tvTerminalStatus.setText("ODRZUCONA");
            tvTerminalStatus.setTextColor(android.graphics.Color.RED);

            if (status.equals("INVALID_PIN")) tvInstruction.setText("BŁĘDNY PIN!");
            else if (status.equals("NO_FUNDS")) tvInstruction.setText("BRAK ŚRODKÓW!");
            else tvInstruction.setText("BŁĄD TRANSAKCJI: " + status);
        }

        // Reset do gotowości po 4 sekundach
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            tvTerminalStatus.setText("TERMINAL GOTOWY");
            tvTerminalStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"));
            tvAmount.setText("0.00 PLN");
            tvInstruction.setText("Oczekuję na żądanie z kasy...");
        }, 4000);
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        if (!isWaitingForCard) return;

        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 100);
        isWaitingForCard = false;

        byte[] idBytes = tag.getId();
        savedCardUid = bytesToHex(idBytes);

        runOnUiThread(() -> {
            tvTerminalStatus.setText("PRZETWARZANIE...");
            tvTerminalStatus.setTextColor(android.graphics.Color.parseColor("#2196F3"));
            tvInstruction.setText("Proszę czekać...");
            tvCardInfo.setText("Karta: " + savedCardUid);
        });

        // Wysyłamy UID do Kasy WPF przez WebSocket!
        sendWebSocketMessage("{\"action\":\"CARD_READ\", \"cardUid\":\"" + savedCardUid + "\"}");
    }

    private void showPinDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setTextAlignment(android.view.View.TEXT_ALIGNMENT_CENTER);
        input.setTextSize(32f);

        new AlertDialog.Builder(this)
                .setTitle("Wymagany PIN")
                .setMessage("Bank wymaga weryfikacji. Wpisz PIN karty:")
                .setView(input)
                .setCancelable(false)
                .setPositiveButton("ZATWIERDŹ", (dialog, which) -> {
                    String pin = input.getText().toString();
                    tvTerminalStatus.setText("PRZETWARZANIE...");
                    sendWebSocketMessage("{\"action\":\"PIN_ENTERED\", \"pin\":\"" + pin + "\"}");
                })
                .show();
    }

    private void sendWebSocketMessage(String json) {
        if (webSocket != null) webSocket.send(json);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfcAdapter != null) nfcAdapter.enableReaderMode(this, this, 15, null);
    }
    @Override
    protected void onPause() {
        super.onPause();
        if (nfcAdapter != null) nfcAdapter.disableReaderMode(this);
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webSocket != null) webSocket.cancel();
        if (toneGenerator != null) toneGenerator.release();
    }

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }
}