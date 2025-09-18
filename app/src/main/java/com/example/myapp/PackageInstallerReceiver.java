package com.example.myapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.util.Log;
import android.widget.Toast;

public class PackageInstallerReceiver extends BroadcastReceiver {
    private static final String TAG = "PkgInstallerReceiver"; // <= 23 chars

    @Override
    public void onReceive(Context context, Intent intent) {
        String packageName = intent.getStringExtra("packageName");
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);

        switch (status) {
            case PackageInstaller.STATUS_SUCCESS:
                Log.i(TAG, "Installation succeeded for: " + packageName);
                Toast.makeText(context, "✅ " + packageName + " installed successfully", Toast.LENGTH_LONG).show();
                break;
            case PackageInstaller.STATUS_PENDING_USER_ACTION:
                Log.w(TAG, "Installation requires user action for: " + packageName);
                Intent userAction = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (userAction != null) {
                    userAction.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(userAction);
                }
                break;
            default:
                Log.e(TAG, "Installation failed for: " + packageName + " - " + message);
                Toast.makeText(context, "❌ Failed to install " + packageName + ": " + message, Toast.LENGTH_LONG).show();
                break;
        }
    }
}