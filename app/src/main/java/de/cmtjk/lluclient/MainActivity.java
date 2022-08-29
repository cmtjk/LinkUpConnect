package de.cmtjk.lluclient;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SharedPreferences preferences = getSharedPreferences("LLUClient", MODE_PRIVATE);
        fillInputFieldsWith(preferences);

        SwitchMaterial onOffSwitch = findViewById(R.id.activate);
        configureSwitch(preferences, onOffSwitch);
        updateApplicationStateIfServiceIsAlreadyRunning(onOffSwitch);

        configureLogView();
    }

    private void configureLogView() {
        TextView logTextView = findViewById(R.id.log);
        configureDebugCheckBox(logTextView);
        ScrollView logScrollView = findViewById(R.id.logscroll);
        configureLocalBroadcaster(logTextView, logScrollView);
    }

    private void updateApplicationStateIfServiceIsAlreadyRunning(SwitchMaterial sw) {
        if (LLUClientService.isRunning()) {
            disableInputFields();
            sw.setChecked(true);
        }
    }

    private void disableInputFields() {
        findViewById(R.id.email).setEnabled(false);
        findViewById(R.id.password).setEnabled(false);
        findViewById(R.id.url).setEnabled(false);
        findViewById(R.id.intervall).setEnabled(false);
    }

    private void configureSwitch(SharedPreferences preferences, SwitchMaterial sw) {
        sw.setOnCheckedChangeListener(((compoundButton, turnedOn) -> {
            Intent intent = new Intent(this, LLUClientService.class);
            if (turnedOn) {
                disableInputFields();
                savePreferences(preferences);
                startService(intent);
            } else {
                stopService(intent);
                enableInputFields();
            }
        }));
    }

    private void savePreferences(SharedPreferences preferences) {
        SharedPreferences.Editor settingsEditor = preferences.edit();
        settingsEditor.putString(Properties.EMAIL.name(), ((TextView) findViewById(R.id.email)).getText().toString());
        settingsEditor.putString(Properties.PASSWORD.name(), ((TextView) findViewById(R.id.password)).getText().toString());
        settingsEditor.putString(Properties.URL.name(), ((TextView) findViewById(R.id.url)).getText().toString());
        settingsEditor.putString(Properties.INTERVAL.name(), ((TextView) findViewById(R.id.intervall)).getText().toString());
        settingsEditor.putBoolean(Properties.DEBUG.name(), ((CheckBox) findViewById(R.id.debug)).isChecked());
        settingsEditor.apply();
    }

    private void enableInputFields() {
        findViewById(R.id.email).setEnabled(true);
        findViewById(R.id.password).setEnabled(true);
        findViewById(R.id.url).setEnabled(true);
        findViewById(R.id.intervall).setEnabled(true);
    }

    private void configureLocalBroadcaster(TextView logTextView, ScrollView logScrollView) {
        LocalBroadcastManager.getInstance(this).registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (debugEnabled()) {
                            logTextView.append("[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] ");
                            logTextView.append(intent.getStringExtra("LOG"));
                            logTextView.append("\n");
                            logScrollView.fullScroll(ScrollView.FOCUS_DOWN);
                        }
                    }

                    private boolean debugEnabled() {
                        return ((CheckBox) findViewById(R.id.debug)).isChecked();
                    }
                }, new IntentFilter(LLUClientService.LOCAL_BROADCAST)
        );
    }

    private void configureDebugCheckBox(TextView logTextView) {
        CheckBox debugCheckBox = findViewById(R.id.debug);
        debugCheckBox.setOnCheckedChangeListener((compoundButton, checked) -> {
            if (!checked) {
                logTextView.setText("");
            }
        });
    }

    private void fillInputFieldsWith(SharedPreferences preferences) {
        ((TextView) findViewById(R.id.email)).setText(preferences.getString(Properties.EMAIL.name(), ""));
        ((TextView) findViewById(R.id.password)).setText(preferences.getString(Properties.PASSWORD.name(), ""));
        ((TextView) findViewById(R.id.url)).setText(preferences.getString(Properties.URL.name(), "api-de.libreview.io"));
        ((TextView) findViewById(R.id.intervall)).setText(preferences.getString(Properties.INTERVAL.name(), "60"));
        ((CheckBox) findViewById(R.id.debug)).setChecked(preferences.getBoolean(Properties.DEBUG.name(), false));
    }
}