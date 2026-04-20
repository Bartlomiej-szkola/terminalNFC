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

import org.json.JSONObject;

import okhttp3.*;
import java.io.IOException;

public class MainActivity extends AppCompatActivity implements NfcAdapter.ReaderCallback {

    private final String TERMINAL_API = AppConfig.SERVER_URL;

    private OkHttpClient client;
    private NfcAdapter nfcAdapter;
    private Handler pollingHandler = new Handler(Looper.getMainLooper());
    private Runnable pollingRunnable;

    private TextView tvTerminalStatus, tvAmount, tvInstruction, tvCardInfo;

    private boolean isWaitingForCard = false;
    private double currentAmountDouble = 0.0;

    // Generator profesjonalnych dźwięków systemowych
    private ToneGenerator toneGenerator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        client = new OkHttpClient();
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        // Inicjalizacja dźwięków na 100% głośności systemowej
        toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 100);

        tvTerminalStatus = findViewById(R.id.tvTerminalStatus);
        tvAmount = findViewById(R.id.tvAmount);
        tvInstruction = findViewById(R.id.tvInstruction);
        tvCardInfo = findViewById(R.id.tvCardInfo);

        startPollingStoreServer();
    }

    private void startPollingStoreServer() {
        pollingRunnable = new Runnable() {
            @Override
            public void run() {
                checkTerminalStatus();
                pollingHandler.postDelayed(this, 1000); // Odpytuj co 1 sek
            }
        };
        pollingHandler.post(pollingRunnable);
    }

    private void checkTerminalStatus() {
        Request request = new Request.Builder().url(TERMINAL_API + "/status").build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String json = response.body().string();
                        JSONObject state = new JSONObject(json);

                        String status = state.getString("status");
                        String amount = state.getString("amount");
                        currentAmountDouble = Double.parseDouble(amount);

                        runOnUiThread(() -> updateUiBasedOnStatus(status, amount));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void updateUiBasedOnStatus(String status, String amount) {
        if ("WAITING_FOR_CARD".equals(status)) {
            isWaitingForCard = true;
            tvTerminalStatus.setText("ZBLIŻ KARTĘ");
            tvTerminalStatus.setTextColor(android.graphics.Color.parseColor("#FFEB3B"));
            tvAmount.setText(amount + " PLN");
            tvInstruction.setText("Oczekuję na zbliżenie karty...");
        } else if ("CARD_READ".equals(status)) {
            isWaitingForCard = false;
            tvTerminalStatus.setText("PRZETWARZANIE...");
            tvTerminalStatus.setTextColor(android.graphics.Color.parseColor("#2196F3"));
            tvInstruction.setText("Proszę nie odkładać telefonu.");
        } else if ("SUCCESS".equals(status)) {
            // SUKCES! Gra krótki dźwięk
            if (!tvTerminalStatus.getText().toString().equals("ZAAKCEPTOWANO")) {
                toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 400); // PIK!
            }
            tvTerminalStatus.setText("ZAAKCEPTOWANO");
            tvTerminalStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"));
            tvInstruction.setText("Płatność zakończona pomyślnie.");
        } else if (status.startsWith("REJECTED")) {
            // BŁĄD! Gra potrójny ostrzegawczy dźwięk
            if (!tvTerminalStatus.getText().toString().startsWith("ODRZUCONA")) {
                toneGenerator.startTone(ToneGenerator.TONE_SUP_ERROR, 1000); // BEEP BEEP BEEP!
            }
            tvTerminalStatus.setText("ODRZUCONA");
            tvTerminalStatus.setTextColor(android.graphics.Color.RED);

            if (status.equals("REJECTED_PIN")) tvInstruction.setText("BŁĘDNY PIN!");
            else if (status.equals("REJECTED_FUNDS")) tvInstruction.setText("BRAK ŚRODKÓW!");
            else tvInstruction.setText("KARTA ZABLOKOWANA LUB BŁĄD!");
        } else {
            isWaitingForCard = false;
            tvTerminalStatus.setText("TERMINAL GOTOWY");
            tvTerminalStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"));
            tvAmount.setText("0.00 PLN");
            tvInstruction.setText("Oczekuję na kasę...");
            tvCardInfo.setText("---");
        }
    }

    // --- CZYTANIE KARTY (NFC) ---
    @Override
    public void onTagDiscovered(Tag tag) {
        if (!isWaitingForCard) return;

        // Odtwarzamy szybki "pik" na znak odczytania karty (jak w Biedronce)
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 100);

        byte[] idBytes = tag.getId();
        String cardUid = bytesToHex(idBytes);
        isWaitingForCard = false; // Blokujemy ponowne czytanie

        runOnUiThread(() -> {
            tvCardInfo.setText("Karta: " + cardUid);
            // Jeśli kwota > 100 PLN, wymagamy PINu
            if (currentAmountDouble > 100.00) {
                showPinDialog(cardUid);
            } else {
                sendCardToStore(cardUid, null);
            }
        });
    }

    private void showPinDialog(String cardUid) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setTextAlignment(android.view.View.TEXT_ALIGNMENT_CENTER);
        input.setTextSize(32f);

        new AlertDialog.Builder(this)
                .setTitle("Wymagany PIN")
                .setMessage("Kwota powyżej 100 PLN. Wpisz PIN karty:")
                .setView(input)
                .setCancelable(false)
                .setPositiveButton("ZATWIERDŹ", (dialog, which) -> {
                    String pin = input.getText().toString();
                    sendCardToStore(cardUid, pin);
                })
                .show();
    }

    private void sendCardToStore(String cardUid, String pin) {
        String url = TERMINAL_API + "/cardRead?cardUid=" + cardUid;
        if (pin != null && !pin.isEmpty()) url += "&pin=" + pin;

        Request request = new Request.Builder().url(url).post(RequestBody.create(null, new byte[0])).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
            }

            @Override
            public void onResponse(Call call, Response response) {
            }
        });
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
        pollingHandler.removeCallbacks(pollingRunnable);
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