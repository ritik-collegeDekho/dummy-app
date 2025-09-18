package com.example.myapp;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.UserManager;
import android.util.Log;
import android.widget.Toast;
import android.os.UserManager;
import java.util.Arrays;
import java.util.List;

public class SimplifiedDevicePolicyHelper {
    private static final String TAG = "SimplifiedPolicy";

    private DevicePolicyManager dpm;
    private ComponentName adminComponent;
    private Context context;

    // List of approved apps with APK URLs
    private static final String[][] APPROVED_APPS = {
            {"com.whatsapp", "WhatsApp Messenger", "https://43cf9e92e311.ngrok-free.app/whatsapp.apk"},
            {"com.microsoft.teams", "Microsoft Teams", "https://your-server.com/apks/com.microsoft.teams.apk"}
    };

    // App stores to hide
    private static final List<String> HIDDEN_APP_STORES = Arrays.asList(
            "com.android.vending",
            "com.xiaomi.mipicks",
            "com.sec.android.app.samsungapps",
            "com.amazon.venezia",
            "com.huawei.appmarket"
    );

    public SimplifiedDevicePolicyHelper(Context context) {
        this.context = context;
        dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(context, MyDeviceAdminReceiver.class);
    }

    public List<String> getHiddenAppStores() {
        return HIDDEN_APP_STORES;
    }

    public boolean isDeviceOwner() {
        return dpm.isDeviceOwnerApp(context.getPackageName());
    }

    public void applyDefaultRestrictions() {
        if (!isDeviceOwner()) {
            Log.e(TAG, "❌ Not device owner - cannot configure restrictions");
            Toast.makeText(context, "❌ Device Owner permission required", Toast.LENGTH_LONG).show();
            return;
        }

        Log.i(TAG, "🔧 Applying default restrictions");

        try {
            // Clear any existing restrictions
            clearOldRestrictions();

            // Hide all app stores
            hideAppStores();

            // Prevent APK sideloading
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES);
            Log.i(TAG, "✅ Unknown sources blocked");

            // Block all app installations
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_APPS);
            Log.i(TAG, "✅ All app installations blocked");

            // Disable camera
            dpm.setCameraDisabled(adminComponent, true);
            Log.i(TAG, "✅ Camera disabled");

            Log.i(TAG, "✅ Default restrictions applied successfully");
            Toast.makeText(context, "✅ Default restrictions applied", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Log.e(TAG, "❌ Error applying default restrictions", e);
            Toast.makeText(context, "❌ Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void hideAppStores() {
        for (String packageName : HIDDEN_APP_STORES) {
            try {
                dpm.setApplicationHidden(adminComponent, packageName, true);
                Log.i(TAG, "✅ Hidden app store: " + packageName);
            } catch (Exception e) {
                Log.w(TAG, "⚠ Could not hide " + packageName + ": " + e.getMessage());
            }
        }
    }

    private void clearOldRestrictions() {
        try {
            dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_APPS);
            dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES);
            dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_UNINSTALL_APPS);
            dpm.setCameraDisabled(adminComponent, false);
            for (String packageName : HIDDEN_APP_STORES) {
                try {
                    dpm.setApplicationHidden(adminComponent, packageName, false);
                } catch (Exception e) {
                    Log.w(TAG, "⚠ Could not show " + packageName, e);
                }
            }
            Log.i(TAG, "🔄 Cleared old restrictions");
        } catch (Exception e) {
            Log.w(TAG, "Note: Some old restrictions might not have existed", e);
        }
    }

    public void installApprovedApp(String packageName, String appName, String apkUrl, PackageInstallerHelper.OnInstallListener listener) {
        if (!isDeviceOwner()) {
            Log.e(TAG, "❌ Not device owner - cannot install apps");
            Toast.makeText(context, "❌ Device Owner permission required", Toast.LENGTH_LONG).show();
            listener.onInstallFailed("Device Owner permission required");
            return;
        }

        if (!isAppApproved(packageName)) {
            Log.w(TAG, "❌ App not in approved list: " + packageName);
            Toast.makeText(context, "❌ " + appName + " is not approved for installation", Toast.LENGTH_LONG).show();
            listener.onInstallFailed(appName + " is not approved");
            return;
        }

        // Start silent installation
        PackageInstallerHelper installer = new PackageInstallerHelper(context);
        installer.installPackage(packageName, apkUrl, new PackageInstallerHelper.OnInstallListener() {
            @Override
            public void onInstallStarted() {
                Log.i(TAG, "Installation started for: " + appName);
                Toast.makeText(context, "Installing " + appName + "...", Toast.LENGTH_SHORT).show();
                listener.onInstallStarted();
            }

            @Override
            public void onInstallFailed(String error) {
                Log.e(TAG, "Installation failed for: " + appName + " - " + error);
                Toast.makeText(context, "❌ Failed to install " + appName + ": " + error, Toast.LENGTH_LONG).show();
                listener.onInstallFailed(error);
            }

            @Override
            public void onInstallSuccess(String packageName) {
                Log.i(TAG, "Installation succeeded for: " + appName);
                Toast.makeText(context, "✅ " + appName + " installed successfully", Toast.LENGTH_LONG).show();
                listener.onInstallSuccess(packageName);
            }
        });
    }

    public void installWhatsApp(PackageInstallerHelper.OnInstallListener listener) {
        installApprovedApp("com.whatsapp", "WhatsApp", getApkUrl("com.whatsapp"), listener);
    }

    public boolean isAppApproved(String packageName) {
        for (String[] app : APPROVED_APPS) {
            if (app[0].equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    private String getApkUrl(String packageName) {
        for (String[] app : APPROVED_APPS) {
            if (app[0].equals(packageName)) {
                return app[2]; // APK URL
            }
        }
        return null;
    }

    public String[][] getApprovedApps() {
        return APPROVED_APPS;
    }

    public boolean isAppInstalled(String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public void logCurrentRestrictions() {
        Log.i(TAG, "=== 📊 CURRENT DEVICE RESTRICTIONS ===");
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Bundle restrictions = dpm.getUserRestrictions(adminComponent);
                Log.i(TAG, "DISALLOW_INSTALL_APPS: " + (restrictions.getBoolean(UserManager.DISALLOW_INSTALL_APPS, false) ? "✅ BLOCKED" : "❌ ALLOWED"));
                Log.i(TAG, "DISALLOW_INSTALL_UNKNOWN_SOURCES: " + (restrictions.getBoolean(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES, false) ? "✅ BLOCKED" : "❌ ALLOWED"));
                Log.i(TAG, "DISALLOW_UNINSTALL_APPS: " + (restrictions.getBoolean(UserManager.DISALLOW_UNINSTALL_APPS, false) ? "✅ BLOCKED" : "❌ ALLOWED"));
            } else {
                Log.i(TAG, "DISALLOW_INSTALL_APPS: Not checkable (API < 24)");
                Log.i(TAG, "DISALLOW_INSTALL_UNKNOWN_SOURCES: Not checkable (API < 24)");
                Log.i(TAG, "DISALLOW_UNINSTALL_APPS: Not checkable (API < 24)");
            }

            for (String store : HIDDEN_APP_STORES) {
                boolean hidden = dpm.isApplicationHidden(adminComponent, store);
                Log.i(TAG, store + " Hidden: " + (hidden ? "✅ YES" : "❌ NO"));
            }

            boolean cameraDisabled = dpm.getCameraDisabled(adminComponent);
            Log.i(TAG, "Camera Disabled: " + (cameraDisabled ? "✅ YES" : "❌ NO"));
            Log.i(TAG, "Device Owner Status: " + (isDeviceOwner() ? "✅ YES" : "❌ NO"));
            Log.i(TAG, "=== END RESTRICTIONS LOG ===");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error checking restrictions", e);
        }
    }

    public void disableAllRestrictions() {
        if (!isDeviceOwner()) return;

        Log.w(TAG, "⚠️ EMERGENCY: Disabling all restrictions");

        try {
            dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_APPS);
            dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES);
            dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_UNINSTALL_APPS);
            dpm.setCameraDisabled(adminComponent, false);
            for (String packageName : HIDDEN_APP_STORES) {
                try {
                    dpm.setApplicationHidden(adminComponent, packageName, false);
                } catch (Exception e) {
                    Log.w(TAG, "⚠ Could not show " + packageName, e);
                }
            }

            Log.w(TAG, "⚠️ All restrictions disabled - device is now unrestricted");
            Toast.makeText(context, "⚠️ All restrictions disabled", Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Log.e(TAG, "❌ Error disabling restrictions", e);
        }
    }

    public void disableCamera() {
        if (isDeviceOwner()) {
            dpm.setCameraDisabled(adminComponent, true);
            Log.i(TAG, "📷 Camera disabled");
            Toast.makeText(context, "📷 Camera disabled", Toast.LENGTH_SHORT).show();
        }
    }

    public void enableCamera() {
        if (isDeviceOwner()) {
            dpm.setCameraDisabled(adminComponent, false);
            Log.i(TAG, "📷 Camera enabled");
            Toast.makeText(context, "📷 Camera enabled", Toast.LENGTH_SHORT).show();
        }
    }
}