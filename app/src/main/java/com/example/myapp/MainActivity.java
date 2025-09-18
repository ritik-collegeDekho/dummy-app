package com.example.myapp;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.os.UserManager;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private SimplifiedDevicePolicyHelper policyHelper;
    private DevicePolicyManager dpm;
    private ComponentName admin;
    private TextView statusText;
    private LinearLayout buttonLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "🚀 MainActivity starting");

        dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        admin = new ComponentName(this, MyDeviceAdminReceiver.class);
        policyHelper = new SimplifiedDevicePolicyHelper(this);

        policyHelper.applyDefaultRestrictions();
        setContentView(createMainLayout());

        boolean hasAccess = MyNotificationListenerService.isNotificationAccessEnabled(this);
        if (!hasAccess) {
            statusText.setOnClickListener(v -> {
                Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
                startActivity(intent);
            });
        }

        updateStatusDisplay();
        policyHelper.logCurrentRestrictions();
    }

    private View createMainLayout() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(40, 40, 40, 40);

        TextView titleText = new TextView(this);
        titleText.setText("📱 Device Management Console");
        titleText.setTextSize(20);
        titleText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        titleText.setPadding(0, 0, 0, 30);
        mainLayout.addView(titleText);

        statusText = new TextView(this);
        statusText.setId(android.R.id.text1);
        statusText.setText("Initializing...");
        statusText.setTextSize(14);
        statusText.setPadding(20, 20, 20, 20);
        statusText.setBackgroundColor(0xFFF5F5F5);
        mainLayout.addView(statusText);

        buttonLayout = new LinearLayout(this);
        buttonLayout.setOrientation(LinearLayout.VERTICAL);
        buttonLayout.setPadding(0, 30, 0, 0);
        createAppInstallationButtons();
        mainLayout.addView(buttonLayout);
        scrollView.addView(mainLayout);

        return scrollView;
    }

    private void createAppInstallationButtons() {
        addSectionHeader("📱 Approved App Installation");

        PackageInstallerHelper.OnInstallListener installListener = new PackageInstallerHelper.OnInstallListener() {
            @Override
            public void onInstallStarted() {
                updateStatusDisplay();
            }

            @Override
            public void onInstallFailed(String error) {
                updateStatusDisplay();
            }

            @Override
            public void onInstallSuccess(String packageName) {
                updateStatusDisplay();
            }
        };

        Button whatsappButton = createButton("💬 Install WhatsApp", v -> {
            Log.i(TAG, "WhatsApp install button clicked");
            if (policyHelper.isAppInstalled("com.whatsapp")) {
                Toast.makeText(this, "✅ WhatsApp is already installed", Toast.LENGTH_SHORT).show();
                return;
            }
            policyHelper.installWhatsApp(installListener);
        });
        buttonLayout.addView(whatsappButton);

        Button teamsButton = createButton("💼 Install Microsoft Teams", v -> {
            installSpecificApp("com.microsoft.teams", "Microsoft Teams", installListener);
        });
        buttonLayout.addView(teamsButton);

        Button showApprovedButton = createButton("📋 Show All Approved Apps", v -> {
            showApprovedAppsList();
        });
        buttonLayout.addView(showApprovedButton);
    }

    private void addSectionHeader(String title) {
        TextView header = new TextView(this);
        header.setText(title);
        header.setTextSize(16);
        header.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        header.setPadding(0, 20, 0, 10);
        header.setBackgroundColor(0xFFE3F2FD);
        buttonLayout.addView(header);
    }

    private Button createButton(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setOnClickListener(listener);
        button.setPadding(20, 15, 20, 15);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 5, 0, 5);
        button.setLayoutParams(params);
        return button;
    }

    private void installSpecificApp(String packageName, String appName, PackageInstallerHelper.OnInstallListener listener) {
        if (policyHelper.isAppInstalled(packageName)) {
            Toast.makeText(this, "✅ " + appName + " is already installed", Toast.LENGTH_SHORT).show();
            return;
        }
        String apkUrl = null;
        for (String[] app : policyHelper.getApprovedApps()) {
            if (app[0].equals(packageName)) {
                apkUrl = app[2];
                break;
            }
        }
        if (apkUrl != null) {
            policyHelper.installApprovedApp(packageName, appName, apkUrl, listener);
        } else {
            Toast.makeText(this, "❌ No APK URL for " + appName, Toast.LENGTH_LONG).show();
        }
    }

    private void showApprovedAppsList() {
        StringBuilder appList = new StringBuilder();
        appList.append("📱 APPROVED APPS LIST\n\n");
        String[][] approvedApps = policyHelper.getApprovedApps();
        for (int i = 0; i < approvedApps.length; i++) {
            String packageName = approvedApps[i][0];
            String appName = approvedApps[i][1];
            boolean installed = policyHelper.isAppInstalled(packageName);
            appList.append((i + 1)).append(". ").append(appName);
            appList.append(installed ? " ✅ INSTALLED" : " ⬜ NOT INSTALLED").append("\n");
        }
        appList.append("\nTap individual install buttons to install specific apps.");
        statusText.setText(appList.toString());
        Toast.makeText(this, "📋 Approved apps list updated above", Toast.LENGTH_LONG).show();
    }

    private void updateStatusDisplay() {
        if (statusText == null) return;
        StringBuilder status = new StringBuilder();
        status.append("📱 DEVICE MANAGEMENT STATUS\n\n");

        boolean isDeviceOwner = policyHelper.isDeviceOwner();
        status.append("Device Owner: ").append(isDeviceOwner ? "✅ YES" : "❌ NO").append("\n");
        if (!isDeviceOwner) {
            status.append("\n⚠️ WARNING: Device Owner permission required!\n");
            status.append("Run: adb shell dpm set-device-owner com.example.myapp/.MyDeviceAdminReceiver\n\n");
        }

        boolean hasAccess = MyNotificationListenerService.isNotificationAccessEnabled(this);
        status.append("Notification Access: ").append(hasAccess ? "✅ Granted" : "⚠ Not Enabled (Tap to grant)").append("\n");

        status.append("Camera: ").append(dpm.getCameraDisabled(admin) ? "❌ Disabled" : "✅ Enabled").append("\n");

        for (String store : policyHelper.getHiddenAppStores()) {
            boolean hidden = dpm.isApplicationHidden(admin, store);
            status.append(store).append(": ").append(hidden ? "❌ Hidden" : "✅ Visible").append("\n");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Bundle restrictions = dpm.getUserRestrictions(admin);
            status.append("App Installations: ").append(restrictions.getBoolean(UserManager.DISALLOW_INSTALL_APPS, false) ? "❌ Blocked" : "✅ Allowed").append("\n");
            status.append("Unknown Sources: ").append(restrictions.getBoolean(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES, false) ? "❌ Blocked" : "✅ Allowed").append("\n");
        } else {
            status.append("App Installations: Not checkable (API < 24)\n");
            status.append("Unknown Sources: Not checkable (API < 24)\n");
        }

        status.append("App Package: ").append(getPackageName()).append("\n");
        status.append("Policy Helper: ").append(policyHelper != null ? "✅ Ready" : "❌ Error").append("\n\n");

        status.append("📱 KEY APP STATUS:\n");
        status.append("WhatsApp: ").append(policyHelper.isAppInstalled("com.whatsapp") ? "✅ Installed" : "⬜ Not Installed").append("\n");
        status.append("Microsoft Teams: ").append(policyHelper.isAppInstalled("com.microsoft.teams") ? "✅ Installed" : "⬜ Not Installed").append("\n\n");

        status.append("🎯 Restrictions are applied by default. Contact system admin to modify.");

        statusText.setText(status.toString());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatusDisplay();
    }
}