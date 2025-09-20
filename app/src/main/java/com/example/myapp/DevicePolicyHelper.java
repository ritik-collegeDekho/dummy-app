package com.example.myapp;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.UserManager;

public class DevicePolicyHelper {

    private DevicePolicyManager dpm;
    private ComponentName adminComponent;

    public DevicePolicyHelper(Context context) {
        dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(context, MyDeviceAdminReceiver.class);
    }

    // Disable camera
    public void disableCamera() {
        dpm.setCameraDisabled(adminComponent, true);
    }

    // Restrict app installation & uninstallation
    public void restrictAppInstallation() {
        dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_APPS);
        dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_UNINSTALL_APPS);

        // Hide Play Store
        dpm.setApplicationHidden(adminComponent, "com.android.vending", true);
    }

    // Whitelist is **not supported directly on Device Owner** without Managed Google Play
    // So you enforce restrictions by pre-installing apps like WhatsApp + cdsync manually
    public void infoWhitelist() {
        // Just for logging / informational purposes
        // List of apps you should pre-install
        String[] allowedApps = {"com.whatsapp", "com.example.myapp", "com.collegedekho.cdsync"};
        for (String pkg : allowedApps) {
            System.out.println("Allowed app: " + pkg);
        }
    }
}
