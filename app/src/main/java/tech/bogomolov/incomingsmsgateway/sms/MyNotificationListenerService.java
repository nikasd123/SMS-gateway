package tech.bogomolov.incomingsmsgateway.sms;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import org.json.JSONObject;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MyNotificationListenerService extends NotificationListenerService {

    private static final String WEBHOOK_URL = "https://webhook-test.com/ee871622c402ac7a9bb4bfe888835ebf";
    private OkHttpClient client;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("AAA", "NotificationListenerService created");
        client = new OkHttpClient();
        startForegroundService();
    }

    private void startForegroundService() {
        String channelId = "MyNotificationListenerServiceChannel";
        String channelName = "Notification Listener Service Channel";
        NotificationChannel channel = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            channel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT);
        }

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("Notification Listener Service")
                .setContentText("Service is running in the background")
                .build();

        startForeground(1, notification);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        // Обрабатываем уведомление
        String packageName = sbn.getPackageName();
        Notification notification = sbn.getNotification();
        Bundle extras = notification.extras;

        CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT); // Текст уведомления

        long receivedStamp = new Date().getTime(); // Время получения уведомления

        // Формируем JSON payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("from", packageName);
        payload.put("text", text != null ? text.toString() : "");
        payload.put("receivedStamp", receivedStamp);
        payload.put("sim", ""); // Сим карта оставляем пустым
        payload.put("token", "testToken"); // Тестовый токен

        // Отправляем payload на вебхук, актуализируем sentStamp перед отправкой
        sendToWebhook(payload);
    }

    private void sendToWebhook(Map<String, Object> payload) {
        MediaType JSON = MediaType.get("application/json; charset=utf-8");

        // Обновляем значение sentStamp на текущее время перед отправкой
        payload.put("sentStamp", System.currentTimeMillis());

        SharedPreferences preferences = getSharedPreferences("MyPreferences", Context.MODE_PRIVATE);
        String webhookUrl = preferences.getString("webhook_url", "");
        Log.d("AAA", "Webhook URL is: " + webhookUrl);

        if (webhookUrl.isEmpty()){
            return;
        }

        JSONObject json = new JSONObject(payload);
        RequestBody body = RequestBody.create(json.toString(), JSON);

        Request request = new Request.Builder()
                .url(webhookUrl) // Используем URL из SharedPreferences
                .post(body)
                .build();

        new Thread(() -> {
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
                assert response.body() != null;
                Log.d("AAA", "Webhook response: " + response.body().string());
            } catch (IOException e) {
                e.printStackTrace();
                Log.e("AAA", "Error sending webhook", e);
            }
        }).start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopForeground(true);
        }

        Intent broadcastIntent = new Intent(this, ServiceRestartReceiver.class);
        sendBroadcast(broadcastIntent);
    }
}


