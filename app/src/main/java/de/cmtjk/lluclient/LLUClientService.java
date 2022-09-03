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
    private static final int PERMANENT_NOTIFICATION_ID = 1;
    private final Timer timer = new Timer();
    private final String channelId = "LLUClientForeGroundChannelId";
    private final NotificationChannel notificationChannel = new NotificationChannel(channelId, "LLU Client Foreground Notification Channel", NotificationManager.IMPORTANCE_HIGH);
    private NotificationManager manager;
    private NotificationCompat.Builder notificationBuilder;

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
        setUpNotification();

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
                    sendToActivitiesLogView("No valid token found. Logging in...");
                    JsonObjectRequest requestChain = createLoginRequestChain(queue, settings);
                    queue.add(requestChain);
                } else {
                    sendToActivitiesLogView("Token still valid");
                    String connectionId = settings.getString(Properties.CONNECTION_ID.name(), "");
                    if (connectionId.isEmpty()) {
                        sendToActivitiesLogView("No valid connection ID found. Getting connections...");
                        JsonObjectRequest requestChain = createConnectionRequestChain(queue, settings);
                        queue.add(requestChain);
                    } else {
                        String shortenedConnectionId = obfuscateConnectionId(connectionId);
                        sendToActivitiesLogView("Using stored connection ID: " + shortenedConnectionId);
                        JsonObjectRequest graphRequest = createGraphRequest(settings);
                        queue.add(graphRequest);
                    }
                }
            }

            private String obfuscateConnectionId(String connectionId) {
                String[] connectionIdParts = connectionId.split("-");
                if (connectionIdParts.length > 1) {
                    return connectionIdParts[0] + "-XXXX-XXXX-XXXX-XXXXXXXXXXXX";
                }
                return "XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX";
            }
        }, 0, intervalInSeconds * 1000L);

        return START_NOT_STICKY;
    }

    private void setUpNotification() {
        manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(notificationChannel);

        notificationBuilder = new NotificationCompat.Builder(getApplicationContext(), channelId);
        notificationBuilder.setSmallIcon(R.drawable.blood_sugar_icon)
                .setShowWhen(false);
        Intent intentMainLanding = new Intent(getApplicationContext(), MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, intentMainLanding, FLAG_IMMUTABLE);
        notificationBuilder.setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true);
    }

    @NonNull
    private JsonObjectRequest createGraphRequest(SharedPreferences settings) {
        String url = settings.getString(Properties.URL.name(), "");
        String token = settings.getString(Properties.TOKEN.name(), "");
        String connectionId = settings.getString(Properties.CONNECTION_ID.name(), "");
        return new JsonObjectRequest(
                Request.Method.GET,
                "https://" + url + "/llu/connections/" + connectionId + "/graph",
                new JSONObject(),
                this::handleGraphResponse,
                this::sendErrorToActivitiesLogViewAndNotification

        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> header = getDefaultHeaders();
                header.put("authorization", "Bearer " + token);
                return header;
            }
        };
    }

    private void handleGraphResponse(JSONObject response) {
        try {
            JSONObject glucoseMeasurement = response.getJSONObject("data").getJSONObject("connection").getJSONObject("glucoseMeasurement");
            sendToActivitiesLogView(glucoseMeasurement.toString(2));

            int bloodGlucoseValue = glucoseMeasurement.getInt("ValueInMgPerDl");
            int trendArrow = glucoseMeasurement.getInt("TrendArrow");
            String timestamp = glucoseMeasurement.getString("Timestamp");

            String formattedBloodGlucoseValue = formatBloodGlucoseString(bloodGlucoseValue, trendArrow);
            String formattedTimeStamp = formatTimeStampString(timestamp);

            sendNotification(formattedBloodGlucoseValue, formattedTimeStamp);

        } catch (JSONException e) {
            sendToActivitiesLogView("Getting measurement failed: " + e.getMessage());
        }
    }

    private void sendErrorToActivitiesLogViewAndNotification(VolleyError error) {
        sendToActivitiesLogView(error);
        displayConnectionErrorInNotification();
    }

    private void sendToActivitiesLogView(VolleyError error) {
        Intent messageIntent = new Intent(LOCAL_BROADCAST);
        String errorType = error.toString();
        String message = error.getMessage();
        String formattedErrorMessage = String.format("Volley Error%nClass: %s%nMessage: %s", errorType, message);
        messageIntent.putExtra("LOG", formattedErrorMessage);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(messageIntent);
    }

    private void displayConnectionErrorInNotification() {
        Notification notification = notificationBuilder.setContentTitle("🚫 Connection error")
                .setContentText("Check your internet connection and debug log.")
                .build();
        manager.notify(PERMANENT_NOTIFICATION_ID, notification);
    }

    private void sendNotification(String formattedBloodGlucoseValue, String formattedTimeStamp) {

        Notification notification = notificationBuilder.setContentTitle(formattedBloodGlucoseValue)
                .setContentText(formattedTimeStamp)
                .build();

        // watch
        manager.notify(PERMANENT_NOTIFICATION_ID, notification);
        // phone
        startForeground(PERMANENT_NOTIFICATION_ID, notification);
    }

    @NonNull
    private String formatTimeStampString(String timestamp) {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("M/d/y h:m:s a", Locale.US);
        LocalDateTime measurementDateTime = LocalDateTime.parse(timestamp, dateTimeFormatter);
        LocalDateTime currentDateTime = LocalDateTime.now();
        DateTimeFormatter notificationDateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm',' MM.dd.yyyy");
        String notificationDateTime = measurementDateTime.format(notificationDateTimeFormatter);
        String icon = "";
        if (currentDateTime.minusMinutes(5).isAfter(measurementDateTime)) {
            icon = "❗";
        }
        Duration duration = Duration.between(measurementDateTime, currentDateTime);
        return String.format(Locale.GERMANY, "%s⏳ %sm ago (%s)", icon, duration.toMinutes(), notificationDateTime);
    }

    @NonNull
    private String formatBloodGlucoseString(int bloodGlucoseValue, int trendArrow) {
        String arrow = "";
        switch (trendArrow) {
            case 1:
                arrow = "⬇";
                break;
            case 2:
                arrow = "↘";
                break;
            case 3:
                arrow = "➡";
                break;
            case 4:
                arrow = "↗";
                break;
            case 5:
                arrow = "⬆";
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
    private JsonObjectRequest createConnectionRequestChain(RequestQueue queue, SharedPreferences preferences) {
        String url = preferences.getString(Properties.URL.name(), "");
        String token = preferences.getString(Properties.TOKEN.name(), "");
        return new JsonObjectRequest(
                Request.Method.GET,
                "https://" + url + "/llu/connections",
                new JSONObject(),
                response -> handleConnectionResponse(response, queue, preferences),
                this::sendErrorToActivitiesLogViewAndNotification
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> defaultHeaders = getDefaultHeaders();
                defaultHeaders.put("authorization", "Bearer " + token);
                return defaultHeaders;
            }
        };
    }

    private void handleConnectionResponse(JSONObject response, RequestQueue queue, SharedPreferences preferences) {
        try {
            JSONArray connectionData = response.getJSONArray("data");
            // todo: multiple connection ids not supported
            String connectionId = connectionData.getJSONObject(0).getString("patientId");
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString(Properties.CONNECTION_ID.name(), connectionId);
            editor.apply();
            JsonObjectRequest graphRequest = createGraphRequest(preferences);
            queue.add(graphRequest);
        } catch (JSONException e) {
            sendToActivitiesLogView("Getting connections failed: " + e.getMessage());
        }
    }

    @NonNull
    private JsonObjectRequest createLoginRequestChain(RequestQueue queue, SharedPreferences preferences) {

        String email = preferences.getString(Properties.EMAIL.name(), "");
        String password = preferences.getString(Properties.PASSWORD.name(), "");
        String url = preferences.getString(Properties.URL.name(), "");

        JSONObject jsonRequest = null;
        try {
            jsonRequest = new JSONObject().put("email", email).put("password", password);
        } catch (JSONException e) {
            sendToActivitiesLogView("Creating login request failed: " + e.getMessage());
        }
        return new JsonObjectRequest(
                Request.Method.POST,
                "https://" + url + "/llu/auth/login",
                jsonRequest,
                response -> handleLoginResponse(response, queue, preferences),
                this::sendErrorToActivitiesLogViewAndNotification
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return getDefaultHeaders();
            }
        };
    }

    private void handleLoginResponse(JSONObject response, RequestQueue queue, SharedPreferences preferences) {
        try {
            JSONObject data = response.getJSONObject("data");
            JSONObject authTicket = data.getJSONObject("authTicket");
            String token = authTicket.getString("token");
            long expiryDate = authTicket.getLong("expires");
            saveToken(preferences, token, expiryDate);
            JsonObjectRequest connectionRequest = createConnectionRequestChain(queue, preferences);
            queue.add(connectionRequest);
        } catch (JSONException e) {
            sendToActivitiesLogView("Login failed: " + response);
        }
    }

    private void saveToken(SharedPreferences preferences, String token, long expiryDate) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(Properties.TOKEN.name(), token);
        editor.putLong(Properties.TOKEN_VALIDITY.name(), expiryDate);
        editor.apply();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
