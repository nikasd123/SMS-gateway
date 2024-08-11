package tech.bogomolov.incomingsmsgateway;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import tech.bogomolov.incomingsmsgateway.sms.MyNotificationListenerService;

public class BootCompletedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent argIntent) {
        Log.d("AAA", "BootCompletedReceiver triggered");

        if (Intent.ACTION_BOOT_COMPLETED.equals(argIntent.getAction())) {
            Intent serviceIntent = new Intent(context, MyNotificationListenerService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }
}
