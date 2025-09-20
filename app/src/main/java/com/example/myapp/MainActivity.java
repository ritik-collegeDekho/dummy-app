package com.example.myapp;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private SimplifiedDevicePolicyHelper policyHelper;
    private DevicePolicyManager dpm;
    private ComponentName admin;
    private TextView statusText;
    private LinearLayout buttonLayout;
    private Button whatsappButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "🚀 MainActivity starting");

        dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        admin = new ComponentName(this, MyDeviceAdminReceiver.class);
        policyHelper = new SimplifiedDevicePolicyHelper(this);

        policyHelper.applyDefaultRestrictions();
        setContentView(createMainLayout());

        boolean hasNotificationAccess = MyNotificationListenerService.isNotificationAccessEnabled(this);
        boolean hasAccessibilityAccess = WhatsAppAccessibilityService.isAccessibilityServiceEnabled(this);
        if (!hasNotificationAccess || !hasAccessibilityAccess) {
            statusText.setOnClickListener(v -> {
                if (!hasNotificationAccess) {
                    Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
                    startActivity(intent);
                } else if (!hasAccessibilityAccess) {
                    Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    startActivity(intent);
                }
            });
        }

        updateStatusDisplay();
        policyHelper.logCurrentRestrictions();
    }

    private View createMainLayout() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.white));
        ViewCompat.setAccessibilityHeading(scrollView, true);

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(dpToPx(16), dpToPx(24), dpToPx(16), dpToPx(24));
        mainLayout.setBackgroundColor(ContextCompat.getColor(this, android.R.color.white));

        CardView titleCard = new CardView(this);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, 0, 0, dpToPx(16));
        titleCard.setLayoutParams(titleParams);
        titleCard.setCardElevation(dpToPx(4));
        titleCard.setRadius(dpToPx(12));
        titleCard.setCardBackgroundColor(ContextCompat.getColor(this, android.R.color.white));

        TextView titleText = new TextView(this);
        titleText.setText("📱 Device Management Console");
        titleText.setTextSize(22);
        titleText.setTypeface(null, android.graphics.Typeface.BOLD);
        titleText.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        titleText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        titleText.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
        ViewCompat.setAccessibilityHeading(titleText, true);
        titleCard.addView(titleText);
        mainLayout.addView(titleCard);

        CardView statusCard = new CardView(this);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.setMargins(0, 0, 0, dpToPx(16));
        statusCard.setLayoutParams(statusParams);
        statusCard.setCardElevation(dpToPx(4));
        statusCard.setRadius(dpToPx(12));
        statusCard.setCardBackgroundColor(ContextCompat.getColor(this, android.R.color.white));

        statusText = new TextView(this);
        statusText.setId(android.R.id.text1);
        statusText.setText("Initializing...");
        statusText.setTextSize(16);
        statusText.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        statusText.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
        statusText.setContentDescription("Device management status");
        statusCard.addView(statusText);
        mainLayout.addView(statusCard);

        buttonLayout = new LinearLayout(this);
        buttonLayout.setOrientation(LinearLayout.VERTICAL);
        buttonLayout.setPadding(0, dpToPx(16), 0, 0);
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
                runOnUiThread(() -> {
                    whatsappButton.setText("Installing WhatsApp...");
                    whatsappButton.setEnabled(false);
                    updateStatusDisplay();
                });
            }

            @Override
            public void onInstallFailed(String error) {
                runOnUiThread(() -> {
                    whatsappButton.setText("💬 Install WhatsApp");
                    whatsappButton.setEnabled(true);
                    updateStatusDisplay();
                });
            }

            @Override
            public void onInstallSuccess(String packageName) {
                runOnUiThread(() -> {
                    whatsappButton.setText("💬 WhatsApp Installed");
                    whatsappButton.setEnabled(false);
                    updateStatusDisplay();
                });
            }
        };

        whatsappButton = createButton("💬 Install WhatsApp", v -> {
            Log.i(TAG, "WhatsApp install button clicked");
            if (policyHelper.isAppInstalled("com.whatsapp")) {
                Toast.makeText(this, "✅ WhatsApp is already installed", Toast.LENGTH_SHORT).show();
                return;
            }
            whatsappButton.setText("Installing WhatsApp...");
            whatsappButton.setEnabled(false);
            policyHelper.installWhatsApp(installListener);
        });

        if (policyHelper.isAppInstalled("com.whatsapp")) {
            whatsappButton.setText("💬 WhatsApp Installed");
            whatsappButton.setEnabled(false);
        }

        ViewCompat.setAccessibilityLiveRegion(whatsappButton, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE);
        buttonLayout.addView(whatsappButton);
    }

    private void addSectionHeader(String title) {
        CardView headerCard = new CardView(this);
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        headerParams.setMargins(0, dpToPx(8), 0, dpToPx(8));
        headerCard.setLayoutParams(headerParams);
        headerCard.setCardElevation(dpToPx(2));
        headerCard.setRadius(dpToPx(8));
        headerCard.setCardBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_blue_light));

        TextView header = new TextView(this);
        header.setText(title);
        header.setTextSize(18);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        header.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        header.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));
        ViewCompat.setAccessibilityHeading(header, true);
        headerCard.addView(header);
        buttonLayout.addView(headerCard);
    }

    private Button createButton(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setOnClickListener(listener);
        button.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));
        button.setTextSize(16);
        button.setBackgroundResource(android.R.drawable.btn_default);
        button.setTransformationMethod(null); // Disable all-caps
        button.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        button.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.holo_green_dark));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        button.setLayoutParams(params);
        button.setContentDescription(text);

        button.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                    break;
            }
            return false;
        });

        return button;
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

        boolean hasNotificationAccess = MyNotificationListenerService.isNotificationAccessEnabled(this);
        status.append("Notification Access: ").append(hasNotificationAccess ? "✅ Granted" : "⚠ Not Enabled").append("\n");

        boolean hasAccessibilityAccess = WhatsAppAccessibilityService.isAccessibilityServiceEnabled(this);
        status.append("Accessibility Access: ").append(hasAccessibilityAccess ? "✅ Granted" : "⚠ Not Enabled").append("\n");

        if (!hasNotificationAccess || !hasAccessibilityAccess) {
            status.append("⚠ Tap here to enable required permissions\n");
        }

        status.append("Camera: ").append(dpm.getCameraDisabled(admin) ? "❌ Disabled" : "✅ Enabled").append("\n");

        status.append("App Package: ").append(getPackageName()).append("\n");
        status.append("Policy Helper: ").append(policyHelper != null ? "✅ Ready" : "❌ Error").append("\n\n");

        status.append("📱 KEY APP STATUS:\n");
        status.append("WhatsApp: ").append(policyHelper.isAppInstalled("com.whatsapp") ? "✅ Installed" : "⬜ Not Installed").append("\n\n");

        status.append("🎯 Managed apps can be installed using the button below.");

        statusText.setText(status.toString());
        statusText.setContentDescription(status.toString());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatusDisplay();
        if (policyHelper.isAppInstalled("com.whatsapp")) {
            whatsappButton.setText("💬 WhatsApp Installed");
            whatsappButton.setEnabled(false);
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}