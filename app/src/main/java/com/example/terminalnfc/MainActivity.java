package com.example.terminalnfc;

import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.io.IOException;

public class MainActivity extends AppCompatActivity implements NfcAdapter.ReaderCallback {

    // IP TWOJEGO SERWERA SKLEPU (PORT 8080)
    private final String TERMINAL_API = AppConfig.SERVER_URL;

    private OkHttpClient client;
    private NfcAdapter nfcAdapter;
    private Handler pollingHandler = new Handler(Looper.getMainLooper());
    private Runnable pollingRunnable;

    private TextView tvTerminalStatus, tvAmount, tvInstruction, tvCardInfo;

    // Zmienna, by terminal nie czytał kart, gdy kasa tego nie wymaga
    private boolean isWaitingForCard = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        client = new OkHttpClient();
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        tvTerminalStatus = findViewById(R.id.tvTerminalStatus);
        tvAmount = findViewById(R.id.tvAmount);
        tvInstruction = findViewById(R.id.tvInstruction);
        tvCardInfo = findViewById(R.id.tvCardInfo);

        if (nfcAdapter == null) {
            Toast.makeText(this, "To urządzenie nie obsługuje NFC!", Toast.LENGTH_LONG).show();
            tvInstruction.setText("Brak modułu NFC");
        }

        startPollingStoreServer();
    }

    // --- CYKLICZNE ODPYTYWANIE SERWERA SKLEPU ---
    private void startPollingStoreServer() {
        pollingRunnable = new Runnable() {
            @Override
            public void run() {
                checkTerminalStatus();
                pollingHandler.postDelayed(this, 1500); // Odpytuj co 1.5 sekundy
            }
        };
        pollingHandler.post(pollingRunnable);
    }

    private void checkTerminalStatus() {
        Request request = new Request.Builder().url(TERMINAL_API + "/status").build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {}

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String json = response.body().string();
                        JSONObject state = new JSONObject(json);

                        String status = state.getString("status");
                        String amount = state.getString("amount");

                        runOnUiThread(() -> updateUiBasedOnStatus(status, amount));
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }
        });
    }

    private void updateUiBasedOnStatus(String status, String amount) {
        if ("WAITING_FOR_CARD".equals(status)) {
            isWaitingForCard = true;
            tvTerminalStatus.setText("ZBLIŻ KARTĘ");
            tvTerminalStatus.setTextColor(android.graphics.Color.parseColor("#FFEB3B")); // Żółty
            tvAmount.setText(amount + " PLN");
            tvInstruction.setText("Przyłącz kartę do tyłu telefonu...");
        } else if ("CARD_READ".equals(status)) {
            isWaitingForCard = false;
            tvTerminalStatus.setText("PRZETWARZANIE");
            tvTerminalStatus.setTextColor(android.graphics.Color.parseColor("#2196F3")); // Niebieski
            tvInstruction.setText("Proszę czekać na odpowiedź z kasy...");
        } else {
            isWaitingForCard = false;
            tvTerminalStatus.setText("TERMINAL GOTOWY");
            tvTerminalStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50")); // Zielony
            tvAmount.setText("0.00 PLN");
            tvInstruction.setText("Oczekuję na żądanie z kasy...");
            tvCardInfo.setText("ID Karty: ---");
        }
    }

    // --- OBSŁUGA NFC ---
    @Override
    protected void onResume() {
        super.onResume();
        if (nfcAdapter != null) {
            // Włącza nasłuchiwanie na KAŻDY rodzaj tagu NFC
            int flags = NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B | NfcAdapter.FLAG_READER_NFC_F | NfcAdapter.FLAG_READER_NFC_V;
            nfcAdapter.enableReaderMode(this, this, flags, null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcAdapter != null) {
            nfcAdapter.disableReaderMode(this);
        }
    }

    // TA METODA ODPALA SIĘ, GDY ZBLIŻYSZ KARTĘ DO TELEFONU
    @Override
    public void onTagDiscovered(Tag tag) {
        if (!isWaitingForCard) return; // Ignorujemy kartę, jeśli kasa nie kazała płacić

        // Odczytujemy ID sprzętowe (UID) karty
        byte[] idBytes = tag.getId();
        String cardUid = bytesToHex(idBytes);

        runOnUiThread(() -> {
            tvCardInfo.setText("Odczytano kartę: " + cardUid);
            tvInstruction.setText("Wysyłanie do kasy...");
        });

        // Wysyłamy ID karty do serwera sklepu
        sendCardToStore(cardUid);
    }

    private void sendCardToStore(String cardUid) {
        String url = TERMINAL_API + "/cardRead?cardUid=" + cardUid;
        Request request = new Request.Builder().url(url).post(RequestBody.create(new byte[0])).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Błąd wysyłania na serwer", Toast.LENGTH_SHORT).show());
            }
            @Override
            public void onResponse(Call call, Response response) {}
        });
    }

    // Metoda pomocnicza: Konwersja bajtów na czytelny kod HEX (np. 04A1B2C3)
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