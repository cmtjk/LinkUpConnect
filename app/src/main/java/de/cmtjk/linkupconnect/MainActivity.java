package de.cmtjk.linkupconnect;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
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

        SharedPreferences preferences = getSharedPreferences("LinkUpConnect", MODE_PRIVATE);

        fillInputFieldsWith(preferences);
        displayInformationDialog(preferences);

        configureXDripCheckBox();

        SwitchMaterial onOffSwitch = findViewById(R.id.activate);
        configureSwitch(preferences, onOffSwitch);
        updateApplicationStateIfServiceIsAlreadyRunning(onOffSwitch);
        configureResetButton(preferences, onOffSwitch);

        configureLogView();
    }

    private void configureXDripCheckBox() {
        ((CheckBox) findViewById(R.id.forward_to_xdrip)).setOnCheckedChangeListener(((compoundButton, turnedOn) -> {
            if (turnedOn) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("ℹ️ Information")
                        .setMessage("Choose 'Libre2 (patched App)' as source in xDrip.")
                        .setPositiveButton("Ok", (dialog, id) -> {});
                AlertDialog dialog = builder.create();
                dialog.show();
            }
        }));
    }

    private void configureResetButton(SharedPreferences preferences, SwitchMaterial onOffSwitch) {
        Button resetButton = findViewById(R.id.reset);
        resetButton.setOnClickListener(view -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Reset application?");
            builder.setPositiveButton("Yes", (dialog, id) -> {
                onOffSwitch.setChecked(false);
                resetPreferencesAndInputFields(preferences);
                displayInformationDialog(preferences);
            });
            builder.setNegativeButton("No", (dialog, id) -> dialog.cancel());
            AlertDialog dialog = builder.create();
            dialog.show();

        });
    }

    private void resetPreferencesAndInputFields(SharedPreferences preferences) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.clear();
        editor.apply();
        fillInputFieldsWith(preferences);
    }

    private void displayInformationDialog(SharedPreferences preferences) {
        if (!preferences.getBoolean(Preferences.INFORMATION_RED.name(), false)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("ℹ️ Information")
                    .setMessage("Do not rely on values displayed by this app exclusively. Regularly check your glucose meter and enable alarms.");
            builder.setPositiveButton("I understand", (dialog, id) -> {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean(Preferences.INFORMATION_RED.name(), true);
                editor.apply();
            });
            AlertDialog dialog = builder.create();
            dialog.show();
        }
    }

    private void configureLogView() {
        TextView logTextView = findViewById(R.id.log);
        configureDebugCheckBox(logTextView);
        ScrollView logScrollView = findViewById(R.id.logscroll);
        configureLocalBroadcaster(logTextView, logScrollView);
    }

    private void updateApplicationStateIfServiceIsAlreadyRunning(SwitchMaterial sw) {
        if (LinkUpConnectService.isRunning()) {
            disableInputFields();
            sw.setChecked(true);
        }
    }

    private void disableInputFields() {
        findViewById(R.id.email).setEnabled(false);
        findViewById(R.id.password).setEnabled(false);
        findViewById(R.id.url).setEnabled(false);
        findViewById(R.id.intervall).setEnabled(false);
        findViewById(R.id.notification_enabled).setEnabled(false);
        findViewById(R.id.forward_to_xdrip).setEnabled(false);
    }

    private void configureSwitch(SharedPreferences preferences, SwitchMaterial sw) {
        sw.setOnCheckedChangeListener(((compoundButton, turnedOn) -> {
            Intent intent = new Intent(this, LinkUpConnectService.class);
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
        settingsEditor.putString(Preferences.EMAIL.name(), ((TextView) findViewById(R.id.email)).getText().toString());
        settingsEditor.putString(Preferences.PASSWORD.name(), ((TextView) findViewById(R.id.password)).getText().toString());
        settingsEditor.putString(Preferences.URL.name(), ((TextView) findViewById(R.id.url)).getText().toString());
        settingsEditor.putString(Preferences.INTERVAL.name(), ((TextView) findViewById(R.id.intervall)).getText().toString());
        settingsEditor.putBoolean(Preferences.PERMANENT_NOTIFICATION_ENABLED.name(), ((CheckBox) findViewById(R.id.notification_enabled)).isChecked());
        settingsEditor.putBoolean(Preferences.FORWARD_TO_XDRIP.name(), ((CheckBox) findViewById(R.id.forward_to_xdrip)).isChecked());
        settingsEditor.apply();
    }

    private void enableInputFields() {
        findViewById(R.id.email).setEnabled(true);
        findViewById(R.id.password).setEnabled(true);
        findViewById(R.id.url).setEnabled(true);
        findViewById(R.id.intervall).setEnabled(true);
        findViewById(R.id.notification_enabled).setEnabled(true);
        findViewById(R.id.forward_to_xdrip).setEnabled(true);
    }

    private void configureLocalBroadcaster(TextView logTextView, ScrollView logScrollView) {
        LocalBroadcastManager.getInstance(this).registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (debugEnabled() && isLogMessage(intent)) {
                            logTextView.append("[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] ");
                            logTextView.append(intent.getStringExtra("LOG"));
                            logTextView.append("\n");
                            logScrollView.fullScroll(ScrollView.FOCUS_DOWN);
                        }

                        if (isAlert(intent)) {
                            showAlert(intent.getStringExtra("ALERT"));
                        }

                    }
                    private boolean debugEnabled() {
                        return ((CheckBox) findViewById(R.id.debug)).isChecked();
                    }

                    private boolean isLogMessage(Intent intent) {
                        return intent.getStringExtra("LOG") != null && !intent.getStringExtra("LOG").isEmpty();
                    }

                    private boolean isAlert(Intent intent) {
                        return intent.getStringExtra("ALERT") != null && !intent.getStringExtra("ALERT").isEmpty();
                    }
                }, new IntentFilter(LinkUpConnectService.LOCAL_BROADCAST)
        );
    }

    private void showAlert(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("ℹ️ Information")
                .setMessage(message)
                .setPositiveButton("Ok", (dialog, id) -> {});
        AlertDialog dialog = builder.create();
        dialog.show();
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
        ((TextView) findViewById(R.id.email)).setText(preferences.getString(Preferences.EMAIL.name(), ""));
        ((TextView) findViewById(R.id.password)).setText(preferences.getString(Preferences.PASSWORD.name(), ""));
        ((TextView) findViewById(R.id.url)).setText(preferences.getString(Preferences.URL.name(), "api-de.libreview.io"));
        ((TextView) findViewById(R.id.intervall)).setText(preferences.getString(Preferences.INTERVAL.name(), "60"));
        ((CheckBox) findViewById(R.id.debug)).setChecked(preferences.getBoolean(Preferences.DEBUG.name(), false));
        ((CheckBox) findViewById(R.id.notification_enabled)).setChecked(preferences.getBoolean(Preferences.PERMANENT_NOTIFICATION_ENABLED.name(), true));
        ((CheckBox) findViewById(R.id.forward_to_xdrip)).setChecked(preferences.getBoolean(Preferences.FORWARD_TO_XDRIP.name(), false));
    }
}