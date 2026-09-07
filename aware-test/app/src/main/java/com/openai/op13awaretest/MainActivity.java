package com.openai.op13awaretest;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.net.wifi.aware.AttachCallback;
import android.net.wifi.aware.DiscoverySessionCallback;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.PublishDiscoverySession;
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.aware.WifiAwareSession;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final String TAG = "OP13AwareTest";
    private static final int REQ_NEARBY_WIFI = 1001;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private TextView status;
    private WifiAwareManager awareManager;
    private WifiAwareSession awareSession;
    private PublishDiscoverySession publishSession;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);

        Button run = new Button(this);
        run.setText("Run Wi-Fi Aware attach + publish test");
        run.setOnClickListener(v -> startTest());
        root.addView(run, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button stop = new Button(this);
        stop.setText("Stop / reset");
        stop.setOnClickListener(v -> {
            closeSessions();
            append("STOPPED. Ready to test again.");
        });
        root.addView(stop, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        status = new TextView(this);
        status.setTextIsSelectable(true);
        status.setTextSize(16f);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(status);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);

        awareManager = (WifiAwareManager) getSystemService(WIFI_AWARE_SERVICE);
        boolean feature = getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE);
        append("OP13 Aware Test v1");
        append("FEATURE_WIFI_AWARE = " + feature);
        append("WifiAwareManager = " + (awareManager != null ? "present" : "null"));
        if (awareManager != null) {
            append("WifiAwareManager.isAvailable() = " + awareManager.isAvailable());
        }
        append("Press RUN. Keep this app open while the publish session is active.");
    }

    private void startTest() {
        closeSessions();
        append("--- NEW TEST ---");

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)) {
            append("FAIL: android.hardware.wifi.aware feature is missing.");
            return;
        }

        awareManager = (WifiAwareManager) getSystemService(WIFI_AWARE_SERVICE);
        if (awareManager == null) {
            append("FAIL: WifiAwareManager is null.");
            return;
        }

        append("isAvailable = " + awareManager.isAvailable());
        if (!awareManager.isAvailable()) {
            append("FAIL: Wi-Fi Aware is currently unavailable.");
            return;
        }

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES)
                != PackageManager.PERMISSION_GRANTED) {
            append("Requesting NEARBY_WIFI_DEVICES permission...");
            requestPermissions(new String[]{Manifest.permission.NEARBY_WIFI_DEVICES}, REQ_NEARBY_WIFI);
            return;
        }

        doAttach();
    }

    private void doAttach() {
        append("Calling WifiAwareManager.attach()...");
        Log.i(TAG, "ATTACH_REQUEST");
        try {
            awareManager.attach(new AttachCallback() {
                @Override
                public void onAttached(WifiAwareSession session) {
                    awareSession = session;
                    append("ATTACH OK ✅");
                    Log.i(TAG, "ATTACH_OK");
                    doPublish(session);
                }

                @Override
                public void onAttachFailed() {
                    append("ATTACH FAILED ❌");
                    Log.e(TAG, "ATTACH_FAILED");
                }
            }, mainHandler);
        } catch (Throwable t) {
            append("ATTACH EXCEPTION ❌: " + t);
            Log.e(TAG, "ATTACH_EXCEPTION", t);
        }
    }

    private void doPublish(WifiAwareSession session) {
        append("Calling publish(serviceName=op13_mosey_probe)...");
        Log.i(TAG, "PUBLISH_REQUEST");

        try {
            PublishConfig config = new PublishConfig.Builder()
                    .setServiceName("op13_mosey_probe")
                    .setServiceSpecificInfo("OP13-AWARE-TEST".getBytes(StandardCharsets.UTF_8))
                    .setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED)
                    .setTerminateNotificationEnabled(true)
                    .build();

            session.publish(config, new DiscoverySessionCallback() {
                @Override
                public void onPublishStarted(PublishDiscoverySession session) {
                    publishSession = session;
                    append("PUBLISH OK ✅");
                    append("Wi-Fi Aware attach + real publish session is ACTIVE.");
                    append("Leave this screen open while collecting dumpsys/logcat.");
                    Log.i(TAG, "PUBLISH_OK");
                }

                @Override
                public void onSessionConfigFailed() {
                    append("PUBLISH CONFIG FAILED ❌");
                    Log.e(TAG, "PUBLISH_CONFIG_FAILED");
                }

                @Override
                public void onSessionTerminated() {
                    append("PUBLISH SESSION TERMINATED");
                    Log.w(TAG, "PUBLISH_SESSION_TERMINATED");
                    publishSession = null;
                }
            }, mainHandler);
        } catch (Throwable t) {
            append("PUBLISH EXCEPTION ❌: " + t);
            Log.e(TAG, "PUBLISH_EXCEPTION", t);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NEARBY_WIFI) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                append("NEARBY_WIFI_DEVICES granted ✅");
                doAttach();
            } else {
                append("FAIL: NEARBY_WIFI_DEVICES permission denied.");
            }
        }
    }

    private void closeSessions() {
        if (publishSession != null) {
            try { publishSession.close(); } catch (Throwable ignored) {}
            publishSession = null;
        }
        if (awareSession != null) {
            try { awareSession.close(); } catch (Throwable ignored) {}
            awareSession = null;
        }
    }

    private void append(String line) {
        Log.i(TAG, line);
        runOnUiThread(() -> {
            if (status != null) {
                status.append(line + "\n");
            }
        });
    }

    @Override
    protected void onDestroy() {
        closeSessions();
        super.onDestroy();
    }
}
