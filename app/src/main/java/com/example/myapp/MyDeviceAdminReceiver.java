package com.example.myapp;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.app.admin.DevicePolicyManager;
import android.os.PersistableBundle;
import android.util.Log;

public class MyDeviceAdminReceiver extends DeviceAdminReceiver {
    private static final String TAG = "MyDeviceAdminReceiver";

    @Override
    public void onProfileProvisioningComplete(Context context, Intent intent) {
        Log.i(TAG, "Provisioning complete called");
        PersistableBundle extras = intent.getParcelableExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE);
        if (extras != null) {
            String enrollmentUrl = extras.getString("enrollment_url");
            String deviceId = extras.getString("device_id");
            Log.i(TAG, "Received enrollment_url=" + enrollmentUrl + " device_id=" + deviceId);
            // TODO: start a service or background job to call your MDM server (enrollmentUrl) and finish enrollment.
        }
    }

    @Override
    public void onEnabled(Context context, Intent intent) {
        Log.i(TAG, "Device admin enabled");
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        Log.i(TAG, "Device admin disabled");
    }
}