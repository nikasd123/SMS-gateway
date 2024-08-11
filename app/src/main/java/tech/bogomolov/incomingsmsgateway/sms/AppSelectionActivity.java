package tech.bogomolov.incomingsmsgateway.sms;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import tech.bogomolov.incomingsmsgateway.MainActivity;
import tech.bogomolov.incomingsmsgateway.R;


public class AppSelectionActivity extends AppCompatActivity {

    private List<ApplicationInfo> installedApps;
    private PackageManager packageManager;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_selection);

        packageManager = getPackageManager();
        installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);

        sharedPreferences = getSharedPreferences("SelectedAppsPrefs", MODE_PRIVATE);

        Intent intent = new Intent(this, MainActivity.class);
        checkNotificationAccessPermission();

        TextView backBtn = findViewById(R.id.back_button);
        backBtn.setOnClickListener(v -> startActivity(intent));

        ListView listView = findViewById(R.id.app_list_view);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_multiple_choice, getAppNames(installedApps));
        listView.setAdapter(adapter);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        // Загрузка сохраненных ранее выборов
        loadSelectedApps(listView);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            String packageName = installedApps.get(position).packageName;
            toggleAppSelection(packageName);
            Log.d("AAA", "Selected app: " + packageName);
        });
    }

    private List<String> getAppNames(List<ApplicationInfo> apps) {
        List<String> appNames = new ArrayList<>();
        for (ApplicationInfo app : apps) {
            appNames.add((String) packageManager.getApplicationLabel(app));
        }
        return appNames;
    }

    private void toggleAppSelection(String packageName) {
        Set<String> selectedApps = new HashSet<>(sharedPreferences.getStringSet("SelectedApps", new HashSet<>()));
        if (selectedApps.contains(packageName)) {
            selectedApps.remove(packageName);
        } else {
            selectedApps.add(packageName);
        }
        sharedPreferences.edit().putStringSet("SelectedApps", selectedApps).apply();
        Log.d("AAA", "Updated selected apps: " + selectedApps);
    }

    private void loadSelectedApps(ListView listView) {
        Set<String> selectedApps = sharedPreferences.getStringSet("SelectedApps", new HashSet<>());
        for (int i = 0; i < installedApps.size(); i++) {
            if (selectedApps.contains(installedApps.get(i).packageName)) {
                listView.setItemChecked(i, true);
            }
        }
    }

    private static final int REQUEST_CODE_NOTIFICATION_ACCESS = 1001;

    private void checkNotificationAccessPermission() {
        if (!isNotificationAccessGranted()) {
            // Направляем пользователя на экран настроек для предоставления доступа к уведомлениям
            Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
            startActivityForResult(intent, REQUEST_CODE_NOTIFICATION_ACCESS);
        }
    }

    private boolean isNotificationAccessGranted() {
        Set<String> enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(this);
        return enabledListeners.contains(getPackageName());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_NOTIFICATION_ACCESS) {
            if (!isNotificationAccessGranted()) {
                Toast.makeText(this, "Permission is required to access notifications.", Toast.LENGTH_SHORT).show();
                checkNotificationAccessPermission();
            }
        }
    }
}



