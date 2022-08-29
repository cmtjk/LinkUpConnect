package de.cmtjk.lluclient;

import static android.app.PendingIntent.FLAG_IMMUTABLE;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class LLUClientService extends Service {

    public static final String LOCAL_BROADCAST = LLUClientService.class.getName() + "LocationBroadcast";
    private static boolean SERVICE_IS_RUNNING = false;
    private final Timer timer = new Timer();
    private final String channelId = "LLUClientForeGroundChannelId";
    private final NotificationChannel serviceChannel = new NotificationChannel(channelId, "LLU Client Forground Notification Channel", NotificationManager.IMPORTANCE_HIGH);

    public static boolean isRunning() {
        return SERVICE_IS_RUNNING;
    }

    @Override
    public void onDestroy() {
        timer.cancel();
        stopSelf();
        SERVICE_IS_RUNNING = false;
        sendToActivitiesLogView("Service stopped");
    }

    private void sendToActivitiesLogView(String message) {
        Intent intent = new Intent(LOCAL_BROADCAST);
        intent.putExtra("LOG", message);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        SERVICE_IS_RUNNING = true;
        sendToActivitiesLogView("Service started");

        SharedPreferences settings = getSharedPreferences("LLUClient", MODE_PRIVATE);
        int intervalInSeconds = Integer.parseInt(settings.getString(Properties.INTERVAL.name(), "60"));

        RequestQueue queue = Volley.newRequestQueue(getApplicationContext());
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {

                long tokenExpiryDate = settings.getLong(Properties.TOKEN_VALIDITY.name(), Long.MIN_VALUE);
                if (tokenExpiryDate < (System.currentTimeMillis() / 1000)) {
                    sendToActivitiesLogView("No valid Token found. Logging in...");
                    JsonObjectRequest requestChain = createLoginRequest(queue, settings);
                    queue.add(requestChain);
                } else {
                    sendToActivitiesLogView("Token still valid. Fetching connections...");
                    String connectionId = settings.getString(Properties.CONNECTION_ID.name(), "");
                    if (connectionId.isEmpty()) {
                        sendToActivitiesLogView("No valid ConnectionID found. Getting connections...");
                        JsonObjectRequest requestChain = createConnectionRequest(queue, settings);
                        queue.add(requestChain);
                    } else {
                        sendToActivitiesLogView("Using ConnectionID: " + connectionId);
                        JsonObjectRequest graphRequest = createGraphRequest(settings);
                        queue.add(graphRequest);
                    }
                }
            }
        }, 0, intervalInSeconds * 1000);

        return START_NOT_STICKY;
    }

    @NonNull
    private JsonObjectRequest createGraphRequest(SharedPreferences settings) {
        String url = settings.getString(Properties.URL.name(), "");
        String token = settings.getString(Properties.TOKEN.name(), "");
        String connectionId = settings.getString(Properties.CONNECTION_ID.name(), "");
        JsonObjectRequest glucoseDataRequest = new JsonObjectRequest(
                Request.Method.GET,
                "https://" + url + "/llu/connections/" + connectionId + "/graph",
                new JSONObject(),
                response -> {
                    int bloodGlucoseValue = 0;
                    int trendArrow = 0;
                    String timestamp = "";
                    try {
                        bloodGlucoseValue = response.getJSONObject("data").getJSONObject("connection").getJSONObject("glucoseMeasurement").getInt("ValueInMgPerDl");
                        trendArrow = response.getJSONObject("data").getJSONObject("connection").getJSONObject("glucoseMeasurement").getInt("TrendArrow");
                        timestamp = response.getJSONObject("data").getJSONObject("connection").getJSONObject("glucoseMeasurement").getString("Timestamp");
                        sendToActivitiesLogView(response.getJSONObject("data").getJSONObject("connection").getJSONObject("glucoseMeasurement").toString(2));
                    } catch (JSONException e) {
                        sendToActivitiesLogView("Getting measurement unsuccessfully: " + e.getMessage());
                    }

                    String formattedBloodGlucoseValue = formatBloodClucoseString(bloodGlucoseValue, trendArrow);
                    String formattedTimeStamp = formatTimeStampString(timestamp);

                    sendNotification(formattedBloodGlucoseValue, formattedTimeStamp);


                },
                this::sendErrorToActivitiesLogView

        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> header = getDefaultHeaders();
                header.put("authorization", "Bearer " + token);
                return header;
            }
        };
        return glucoseDataRequest;
    }

    private void sendErrorToActivitiesLogView(VolleyError error) {
        Intent messageIntent = new Intent(LOCAL_BROADCAST);
        messageIntent.putExtra("LOG", error.getMessage());
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(messageIntent);
    }

    private void sendNotification(String formattedBloodGlucoseValue, String formattedTimeStamp) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(serviceChannel);

        Intent intentMainLanding = new Intent(getApplicationContext(), MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, intentMainLanding, FLAG_IMMUTABLE);
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(getApplicationContext(), channelId)
                .setSmallIcon(R.drawable.baseline_opacity_light_24dp)
                .setContentTitle(formattedBloodGlucoseValue)
                .setContentText(formattedTimeStamp)
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true);
        Notification notification = notificationBuilder.build();

        // watch
        manager.notify(1, notification);
        // phone
        startForeground(1, notification);
    }

    @NonNull
    private String formatTimeStampString(String timestamp) {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("M/d/y h:m:s a", Locale.US);
        LocalDateTime measurementDateTime = LocalDateTime.parse(timestamp, dateTimeFormatter);
        LocalDateTime currentDateTime = LocalDateTime.now();
        DateTimeFormatter notificationDateTimeFormatter = DateTimeFormatter.ofPattern("hh:mm 'Uhr,' MM.dd.yyyy");
        String notificationDateTime = measurementDateTime.format(notificationDateTimeFormatter);
        String icon = "";
        if (currentDateTime.minusMinutes(5).isAfter(measurementDateTime)) {
            icon = "❗ ";
        }
        Duration duration = Duration.between(measurementDateTime, currentDateTime);
        String formattedTimeStamp = String.format(Locale.GERMANY, "%s⏳ %sm ago (%s)", icon, duration.toMinutes(), notificationDateTime);
        return formattedTimeStamp;
    }

    @NonNull
    private String formatBloodClucoseString(int bloodGlucoseValue, int trendArrow) {
        String arrow = "";
        switch (trendArrow) {
            case 1:
                arrow = "⬆";
                break;
            case 2:
                arrow = "↗";
                break;
            case 3:
                arrow = "➡";
                break;
            case 4:
                arrow = "↘";
                break;
            case 5:
                arrow = "⬇";
                break;
        }
        return String.format(Locale.GERMANY, "%s %d mg/dl", arrow, bloodGlucoseValue);
    }

    @NonNull
    private Map<String, String> getDefaultHeaders() {
        Map<String, String> header = new HashMap<>();
        header.put("product", "llu.ios");
        header.put("User-Agent", "FreeStyle LibreLink Up Uploader");
        header.put("Content-Type", "application/json");
        header.put("version", "4.1.1");
        header.put("Accept-Encoding", "gzip, deflate, br");
        header.put("Connection", "keep-alive");
        header.put("Pragma", "no-cache");
        header.put("Cache-Control", "no-cache");
        return header;
    }

    @NonNull
    private JsonObjectRequest createConnectionRequest(RequestQueue queue, SharedPreferences settings) {
        String url = settings.getString(Properties.URL.name(), "");
        String token = settings.getString(Properties.TOKEN.name(), "");
        JsonObjectRequest connectionRequest = new JsonObjectRequest(
                Request.Method.GET,
                "https://" + url + "/llu/connections",
                new JSONObject(),
                response -> {

                    JSONArray connectionData = null;
                    try {
                        connectionData = response.getJSONArray("data");
                        // todo: multiple connection ids not supported
                        String connectionId = connectionData.getJSONObject(0).getString("patientId");
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(Properties.CONNECTION_ID.name(), connectionId);
                        editor.apply();
                        JsonObjectRequest graphRequest = createGraphRequest(settings);
                        queue.add(graphRequest);
                    } catch (JSONException e) {
                        sendToActivitiesLogView("Getting connections unsuccessfully: " + e.getMessage());
                    }

                },
                this::sendErrorToActivitiesLogView
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> defaultHeaders = getDefaultHeaders();
                defaultHeaders.put("authorization", "Bearer " + token);
                return defaultHeaders;
            }
        };
        return connectionRequest;
    }

    @NonNull
    private JsonObjectRequest createLoginRequest(RequestQueue queue, SharedPreferences settings) {

        String email = settings.getString(Properties.EMAIL.name(), "");
        String password = settings.getString(Properties.PASSWORD.name(), "");
        String url = settings.getString(Properties.URL.name(), "");

        JSONObject jsonRequest = null;
        try {
            jsonRequest = new JSONObject().put("email", email).put("password", password);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        JsonObjectRequest loginRequest = new JsonObjectRequest(
                Request.Method.POST,
                "https://" + url + "/llu/auth/login",
                jsonRequest,
                response -> {
                    try {
                        JSONObject data = response.getJSONObject("data");
                        JSONObject authTicket = data.getJSONObject("authTicket");
                        String token = authTicket.getString("token");
                        long expiryDate = authTicket.getLong("expires");
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(Properties.TOKEN.name(), token);
                        editor.putLong(Properties.TOKEN_VALIDITY.name(), expiryDate);
                        editor.apply();
                        JsonObjectRequest connectionRequest = createConnectionRequest(queue, settings);
                        queue.add(connectionRequest);
                    } catch (JSONException e) {
                        sendToActivitiesLogView("Login unsuccessfully: " + e.getMessage());
                    }
                },
                this::sendErrorToActivitiesLogView
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return getDefaultHeaders();
            }
        };
        return loginRequest;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
