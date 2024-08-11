package tech.bogomolov.incomingsmsgateway;

import android.Manifest;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.multidex.BuildConfig;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;

import tech.bogomolov.incomingsmsgateway.qr.CustomCaptureActivity;

public class MainActivity extends AppCompatActivity {

    private Context context;
    private ListAdapter listAdapter;

    private static final int PERMISSION_CODE = 0;
    private static final int REQUEST_CODE_QR_SCAN = 101;
    private ForwardingConfigDialog currentDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECEIVE_SMS}, PERMISSION_CODE);
        } else {
            showList();
        }

        checkNotificationAccessPermission();
        requestAutoStartPermission();
    }

    private void requestAutoStartPermission(){
        Intent intent = new Intent();
        String manufacturer = android.os.Build.MANUFACTURER;
        if ("xiaomi".equalsIgnoreCase(manufacturer)) {
            intent.setComponent(new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"));
        } else if ("huawei".equalsIgnoreCase(manufacturer)) {
            intent.setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"));
        } else if ("samsung".equalsIgnoreCase(manufacturer)) {
            intent.setComponent(new ComponentName("com.samsung.android.sm", "com.samsung.android.sm.app.dashboard.SmartManagerDashBoardActivity"));
        } else if ("oppo".equalsIgnoreCase(manufacturer)) {
            intent.setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"));
        } else if ("vivo".equalsIgnoreCase(manufacturer)) {
            intent.setComponent(new ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"));
        }

        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        }
    }

    private static final int REQUEST_CODE_NOTIFICATION_ACCESS = 1001;

    private void checkNotificationAccessPermission() {
        if (!isNotificationAccessGranted()) {
            Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
            startActivityForResult(intent, REQUEST_CODE_NOTIFICATION_ACCESS);
        }
    }

    private boolean isNotificationAccessGranted() {
        Set<String> enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(this);
        return enabledListeners.contains(getPackageName());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != PERMISSION_CODE) {
            return;
        }
        for (int i = 0; i < permissions.length; i++) {
            if (!permissions[i].equals(Manifest.permission.RECEIVE_SMS)) {
                continue;
            }

            if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                showList();
            } else {
                showInfo(getResources().getString(R.string.permission_needed));
            }

            return;
        }
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_QR_SCAN) {
            IntentResult result = IntentIntegrator.parseActivityResult(resultCode, data);
            String contents = result.getContents();
            if (contents != null && currentDialog != null) {
                currentDialog.updateFields(contents);
            }
        }
        if (requestCode == REQUEST_CODE_NOTIFICATION_ACCESS) {
            if (!isNotificationAccessGranted()) {
                Toast.makeText(this, "Permission is required to access notifications.", Toast.LENGTH_SHORT).show();
                checkNotificationAccessPermission();
            }
        }
    }

    private void showList() {
        showInfo("");

        context = this;
        ListView listview = findViewById(R.id.listView);

        ArrayList<ForwardingConfig> configs = ForwardingConfig.getAll(context);

        listAdapter = new ListAdapter(configs, context);
        listview.setAdapter(listAdapter);

        FloatingActionButton fab = findViewById(R.id.btn_add);
        fab.setOnClickListener(v -> {
            Log.d("AAA", "fab clicked");
            currentDialog = new ForwardingConfigDialog(this, getLayoutInflater(), listAdapter);
            currentDialog.setOnQrScanListener(() -> {
                Log.d("AAA", "Starting QR scan");
                IntentIntegrator integrator = new IntentIntegrator(this);
                integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
                integrator.setPrompt("Scan a QR code");
                integrator.setCameraId(0);
                integrator.setBeepEnabled(true);
                integrator.setBarcodeImageEnabled(true);
                integrator.setCaptureActivity(CustomCaptureActivity.class);
                integrator.setRequestCode(REQUEST_CODE_QR_SCAN);
                integrator.initiateScan();
            });
            currentDialog.showNew();
        });

        if (!this.isServiceRunning()) {
            this.startService();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.action_bar_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_bar_syslogs) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            View view = getLayoutInflater().inflate(R.layout.syslogs, null);

            String logs = "";
            try {
                String[] command = new String[]{
                        "logcat", "-d", "*:E", "-m", "1000",
                        "|", "grep", "tech.bogomolov.incomingsmsgateway"};
                Process process = Runtime.getRuntime().exec(command);

                BufferedReader bufferedReader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()));

                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    logs += line + "\n";
                }
            } catch (IOException ex) {
                logs = "getLog failed";
            }

            TextView logsTextContainer = view.findViewById(R.id.syslogs_text);
            logsTextContainer.setText(logs);

            TextView version = view.findViewById(R.id.syslogs_version);
            version.setText("v" + BuildConfig.VERSION_NAME);

            builder.setView(view);
            builder.setNegativeButton(R.string.btn_close, null);
            builder.setNeutralButton(R.string.btn_clear, null);

            final AlertDialog dialog = builder.show();
            Objects.requireNonNull(dialog.getWindow())
                    .setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                    .setOnClickListener(view1 -> {
                        String[] command = new String[]{"logcat", "-c"};
                        try {
                            Runtime.getRuntime().exec(command);
                        } catch (IOException e) {
                            Log.e("SmsGateway", "log clear error: " + e);
                        }
                        dialog.cancel();
                    });
        }

        return super.onOptionsItemSelected(item);
    }

    private boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (tech.bogomolov.incomingsmsgateway.SmsReceiverService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void startService() {
        Context appContext = getApplicationContext();
        Intent intent = new Intent(this, SmsReceiverService.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(intent);
        } else {
            appContext.startService(intent);
        }
    }

    private void showInfo(String text) {
        TextView notice = findViewById(R.id.info_notice);
        notice.setText(text);
    }

    private View.OnClickListener showAddDialog() {
        return v -> {
            (new ForwardingConfigDialog(context, getLayoutInflater(), listAdapter)).showNew();
        };
    }
}
