package com.example.terminalnfc;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        TextInputEditText etIp = findViewById(R.id.etSettingsIp);
        TextInputEditText etPort = findViewById(R.id.etSettingsPort);
        Button btnSave = findViewById(R.id.btnSaveSettings);
        Button btnReset = findViewById(R.id.btnResetDefaults);
        TextView tvAppVersion = findViewById(R.id.tvAppVersion);
        Button btnGithub = findViewById(R.id.btnGithub);
        Button btnLogs = findViewById(R.id.btnLogs);

        // 1. Ładowanie aktualnych ustawień
        SharedPreferences prefs = getSharedPreferences(AppConfig.PREFS_NAME, MODE_PRIVATE);
        etIp.setText(prefs.getString(AppConfig.PREF_IP, AppConfig.DEFAULT_SERVER_IP));
        etPort.setText(prefs.getString(AppConfig.PREF_PORT, AppConfig.DEFAULT_SERVER_PORT));

        // 2. Automatyczne pobieranie wersji aplikacji
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String version = pInfo.versionName;
            tvAppVersion.setText("Wersja aplikacji: " + version);
        } catch (PackageManager.NameNotFoundException e) {
            tvAppVersion.setText("Wersja: Nieznana");
        }

        // 3. Zapis ustawień i powrót
        btnSave.setOnClickListener(v -> {
            prefs.edit()
                    .putString(AppConfig.PREF_IP, etIp.getText().toString().trim())
                    .putString(AppConfig.PREF_PORT, etPort.getText().toString().trim())
                    .apply();

            Toast.makeText(this, "Zapisano ustawienia", Toast.LENGTH_SHORT).show();
            finish(); // Zamyka ekran i wraca do MainActivity
        });

        btnReset.setOnClickListener(v -> {
            // Wpisuje w pola domyślne wartości
            etIp.setText(AppConfig.DEFAULT_SERVER_IP);
            etPort.setText(AppConfig.DEFAULT_SERVER_PORT);

            // Zapisuje je od razu w pamięci
            prefs.edit()
                    .putString(AppConfig.PREF_IP, AppConfig.DEFAULT_SERVER_IP)
                    .putString(AppConfig.PREF_PORT, AppConfig.DEFAULT_SERVER_PORT)
                    .apply();

            Toast.makeText(this, "Przywrócono domyślne ustawienia", Toast.LENGTH_SHORT).show();
            finish(); // Zamyka ekran ustawień i wraca, by MainActivity mogło się zresetować
        });

        // 4. Link do GitHuba
        btnGithub.setOnClickListener(v -> {
            String githubUrl = AppConfig.GITHUB_URL;
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl));
            startActivity(browserIntent);
        });

        // 5. Guzik logów (na razie wyświetla powiadomienie, możesz to rozwinąć w przyszłości)
        btnLogs.setOnClickListener(v -> {
            Toast.makeText(this, "Funkcja logów wkrótce...", Toast.LENGTH_SHORT).show();
        });
    }
}