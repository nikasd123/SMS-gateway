package tech.bogomolov.incomingsmsgateway;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;

public class ForwardingConfigDialog {

    static final public String BROADCAST_KEY = "TEST_RESULT";
    private static final int REQUEST_CODE_QR_SCAN = 101;

    private final Context context;
    private final LayoutInflater layoutInflater;
    private final ListAdapter listAdapter;
    private AlertDialog dialog;

    public ForwardingConfigDialog(Context context, LayoutInflater layoutInflater, ListAdapter listAdapter) {
        this.context = context;
        this.layoutInflater = layoutInflater;
        this.listAdapter = listAdapter;

        IntentFilter filter = new IntentFilter(BROADCAST_KEY);
        BroadcastReceiver testResult = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String result = intent.getStringExtra(BROADCAST_KEY);
                Toast.makeText(context.getApplicationContext(), result, Toast.LENGTH_LONG).show();
            }
        };
        context.registerReceiver(testResult, filter);
    }

    public interface OnQrScanListener {
        void onQrScan();
    }

    private OnQrScanListener qrScanListener;

    public void setOnQrScanListener(OnQrScanListener listener) {
        this.qrScanListener = listener;
    }

    public void showNew() {
        Log.d("AAA", "showNew called");
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View view = layoutInflater.inflate(R.layout.dialog_config_edit_form, null);

        ImageView scanQrImageView = view.findViewById(R.id.scanQr);
        scanQrImageView.setOnClickListener(v -> {
            Log.d("AAA", "scanQrImageView clicked");
            if (qrScanListener != null) {
                qrScanListener.onQrScan();
            }
        });

        final EditText templateInput = view.findViewById(R.id.input_json_template);
        templateInput.setText(ForwardingConfig.getDefaultJsonTemplate());

        final EditText headersInput = view.findViewById(R.id.input_json_headers);
        headersInput.setText(ForwardingConfig.getDefaultJsonHeaders());

        final EditText retriesNumInput = view.findViewById(R.id.input_number_retries);
        retriesNumInput.setText(String.valueOf(ForwardingConfig.getDefaultRetriesNumber()));

        final CheckBox chunkedModeCheckbox = view.findViewById(R.id.input_chunked_mode);
        chunkedModeCheckbox.setChecked(true);

        prepareSimSelector(context, view, 0);

        builder.setView(view);
        builder.setPositiveButton(R.string.btn_add, null);
        builder.setNegativeButton(R.string.btn_cancel, null);
        builder.setNeutralButton(R.string.btn_test, null);

        dialog = builder.show();
        Objects.requireNonNull(dialog.getWindow())
                .setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        EditText urlInput = view.findViewById(R.id.input_url);

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view1 -> {
                    ForwardingConfig config = populateConfig(view, context, new ForwardingConfig(context));
                    if (config == null) {
                        return;
                    }

                    String webhookUrl = urlInput.getText().toString();
                    String jsonTemplate = templateInput.getText().toString();

                    // Парсим JSON-шаблон, чтобы извлечь только токен
                    String token = extractTokenFromJson(jsonTemplate);

                    SharedPreferences preferences = context.getSharedPreferences("MyPreferences", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putString("webhook_url", webhookUrl);
                    editor.putString("token", token); // Сохраняем только токен
                    editor.apply();

                    Log.d("AAA", "DIALOG Webhook URL is: " + webhookUrl);
                    Log.d("AAA", "DIALOG Token is: " + token);

                    config.save();
                    listAdapter.add(config);
                    dialog.dismiss();
                });


        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                .setOnClickListener(view1 -> {
                    ForwardingConfig config = populateConfig(view, context, new ForwardingConfig(context));
                    testConfig(config);
                });
    }

    public void updateFields(String jsonString) {
        try {
            Log.d("AAA", "updateFields: " + jsonString);
            JSONObject jsonObject = new JSONObject(jsonString);
            String sender = jsonObject.optString("sender", "");
            String webhookUrl = jsonObject.optString("webhookUrl", "");
            JSONObject payloadObject = jsonObject.optJSONObject("payload");

            if (payloadObject != null) {
                String from = payloadObject.optString("from", "");
                String text = payloadObject.optString("text", "");
                String receivedStamp = payloadObject.optString("receivedStamp", "%receivedStamp%");
                String sentStamp = payloadObject.optString("sentStamp", "%sentStamp%");
                String sim = payloadObject.optString("sim", "");
                String token = payloadObject.optString("token", "");

                // Создаем строку JSON вручную, убирая кавычки вокруг receivedStamp и sentStamp
                String formattedPayload = String.format(
                        "{\"from\":\"%s\",\"text\":\"%s\",\"sentStamp\":%s,\"receivedStamp\":%s,\"sim\":\"%s\",\"token\":\"%s\"}",
                        from, text, sentStamp, receivedStamp, sim, token
                );

                if (dialog != null && dialog.isShowing()) {
                    View view = dialog.findViewById(R.id.dialog_config_edit_form);
                    if (view != null) {
                        EditText phoneInput = view.findViewById(R.id.input_phone);
                        EditText urlInput = view.findViewById(R.id.input_url);
                        EditText templateInput = view.findViewById(R.id.input_json_template);

                        phoneInput.setText(sender);
                        urlInput.setText(webhookUrl);
                        templateInput.setText(formattedPayload); // Используем отформатированный JSON
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(context, "Error parsing JSON", Toast.LENGTH_LONG).show();
        }
    }

    public void showEdit(ForwardingConfig config) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View view = layoutInflater.inflate(R.layout.dialog_config_edit_form, null);

        final EditText phoneInput = view.findViewById(R.id.input_phone);
        phoneInput.setText(config.getSender());

        final EditText urlInput = view.findViewById(R.id.input_url);
        urlInput.setText(config.getUrl());

        prepareSimSelector(context, view, config.getSimSlot());

        final EditText templateInput = view.findViewById(R.id.input_json_template);
        templateInput.setText(config.getTemplate());

        final EditText headersInput = view.findViewById(R.id.input_json_headers);
        headersInput.setText(config.getHeaders());

        final EditText retriesNumInput = view.findViewById(R.id.input_number_retries);
        retriesNumInput.setText(String.valueOf(config.getRetriesNumber()));

        final CheckBox ignoreSslCheckbox = view.findViewById(R.id.input_ignore_ssl);
        ignoreSslCheckbox.setChecked(config.getIgnoreSsl());

        final CheckBox chunkedModeCheckbox = view.findViewById(R.id.input_chunked_mode);
        chunkedModeCheckbox.setChecked(config.getChunkedMode());

        builder.setView(view);
        builder.setPositiveButton(R.string.btn_save, null);
        builder.setNegativeButton(R.string.btn_cancel, null);
        builder.setNeutralButton(R.string.btn_test, null);

        final AlertDialog dialog = builder.show();
        Objects.requireNonNull(dialog.getWindow())
                .setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view1 -> {
                    ForwardingConfig configUpdated = populateConfig(view, context, config);
                    if (configUpdated == null) {
                        return;
                    }

                    String webhookUrl = urlInput.getText().toString();
                    String jsonTemplate = templateInput.getText().toString();

                    // Парсим JSON-шаблон, чтобы извлечь только токен
                    String token = extractTokenFromJson(jsonTemplate);

                    SharedPreferences preferences = context.getSharedPreferences("MyPreferences", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putString("webhook_url", webhookUrl);
                    editor.putString("token", token); // Сохраняем только токен
                    editor.apply();

                    Log.d("AAA", "DIALOG Webhook URL is: " + webhookUrl);
                    Log.d("AAA", "DIALOG Token is: " + token);

                    configUpdated.save();
                    listAdapter.notifyDataSetChanged();
                    dialog.dismiss();
                });


        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                .setOnClickListener(view1 -> {
                    ForwardingConfig configUpdated = populateConfig(view, context, config);
                    testConfig(configUpdated);
                });
    }

    private String extractTokenFromJson(String jsonTemplate) {
        try {
            JSONObject jsonObject = new JSONObject(jsonTemplate);
            return jsonObject.optString("token", "");
        } catch (JSONException e) {
            e.printStackTrace();
            return "testToken";
        }
    }

    public ForwardingConfig populateConfig(View view, Context context, ForwardingConfig config) {
        final EditText senderInput = view.findViewById(R.id.input_phone);
        String sender = senderInput.getText().toString();
        if (TextUtils.isEmpty(sender)) {
            senderInput.setError(context.getString(R.string.error_empty_sender));
            return null;
        }

        final EditText urlInput = view.findViewById(R.id.input_url);
        String url = urlInput.getText().toString();
        if (TextUtils.isEmpty(url)) {
            urlInput.setError(context.getString(R.string.error_empty_url));
            return null;
        }
        try {
            new URL(url);
        } catch (MalformedURLException e) {
            urlInput.setError(context.getString(R.string.error_wrong_url));
            return null;
        }

        Spinner simSlotSelector = (Spinner) view.findViewById(R.id.input_sim_slot);
        int simSlot = (int) simSlotSelector.getSelectedItemId();
        config.setSimSlot(simSlot);

        final EditText templateInput = view.findViewById(R.id.input_json_template);
        String template = templateInput.getText().toString();
        try {
            new JSONObject(template);
        } catch (JSONException e) {
            templateInput.setError(context.getString(R.string.error_wrong_json));
            return null;
        }

        final EditText headersInput = view.findViewById(R.id.input_json_headers);
        String headers = headersInput.getText().toString();
        try {
            new JSONObject(headers);
        } catch (JSONException e) {
            headersInput.setError(context.getString(R.string.error_wrong_json));
            return null;
        }

        final EditText retriesNumInput = view.findViewById(R.id.input_number_retries);
        int retriesNum = Integer.parseInt(retriesNumInput.getText().toString());
        if (retriesNum < 0) {
            retriesNumInput.setError(context.getString(R.string.error_wrong_retries_number));
            return null;
        }

        final CheckBox ignoreSslCheckbox = view.findViewById(R.id.input_ignore_ssl);
        boolean ignoreSsl = ignoreSslCheckbox.isChecked();

        final CheckBox chunkedModeCheckbox = view.findViewById(R.id.input_chunked_mode);
        boolean chunkedMode = chunkedModeCheckbox.isChecked();

        config.setSender(sender);
        config.setUrl(url);
        config.setTemplate(template);
        config.setHeaders(headers);
        config.setRetriesNumber(retriesNum);
        config.setIgnoreSsl(ignoreSsl);
        config.setChunkedMode(chunkedMode);

        return config;
    }

    private void prepareSimSelector(Context context, View view, int selected) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
            SubscriptionManager subscriptionManager =
                    (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            int simSlots = subscriptionManager.getActiveSubscriptionInfoCountMax();
            if (simSlots > 1) {
                View label = view.findViewById(R.id.input_sim_slot_label);
                label.setVisibility(View.VISIBLE);

                Spinner simSlotSelector = (Spinner) view.findViewById(R.id.input_sim_slot);
                simSlotSelector.setVisibility(View.VISIBLE);

                String[] items = new String[simSlots + 1];
                items[0] = "any";
                for (int i = 1; i <= simSlots; i++) {
                    items[i] = "sim" + i;
                }
                ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                        android.R.layout.simple_spinner_item, items);
                simSlotSelector.setAdapter(adapter);

                if (selected > simSlots || selected < 0) {
                    selected = 0;
                }

                simSlotSelector.setSelection(selected);
            }
        }
    }

    private void testConfig(ForwardingConfig config) {
        if (config == null) {
            return;
        }

        Thread thread = new Thread(() -> {
            String payload = config.prepareMessage(
                    "123456789", "test message", "sim1", System.currentTimeMillis());
            Request request = new Request(config.getUrl(), payload);
            request.setJsonHeaders(config.getHeaders());
            request.setIgnoreSsl(config.getIgnoreSsl());
            request.setUseChunkedMode(config.getChunkedMode());

            String result = request.execute();
            if (!Objects.equals(result, Request.RESULT_SUCCESS)) {
                result = Request.RESULT_ERROR;
            }

            Intent in = new Intent(BROADCAST_KEY);
            in.putExtra(BROADCAST_KEY, result);
            context.sendBroadcast(in);
        });
        thread.start();
    }
}
