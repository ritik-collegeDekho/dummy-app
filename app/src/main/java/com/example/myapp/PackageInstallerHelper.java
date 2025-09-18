package com.example.myapp;

import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class PackageInstallerHelper {
    private static final String TAG = "PackageInstallerHelper";
    private final Context context;
    private final OkHttpClient client;
    private final DevicePolicyManager dpm;
    private final ComponentName admin;

    public PackageInstallerHelper(Context context) {
        this.context = context;
        this.client = new OkHttpClient();
        this.dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        this.admin = new ComponentName(context, MyDeviceAdminReceiver.class);
    }

    public void installPackage(String packageName, String apkUrl, OnInstallListener listener) {
        Log.i(TAG, "Starting silent installation for: " + packageName);

        // ✅ Allow installs temporarily
        clearInstallRestrictions();

        // Download APK
        File apkFile = new File(context.getCacheDir(), packageName + ".apk");
        downloadApk(apkUrl, apkFile, new DownloadListener() {
            @Override
            public void onDownloadSuccess(File file) {
                performInstallation(packageName, file, listener);
            }

            @Override
            public void onDownloadFailed(String error) {
                Log.e(TAG, "Download failed for: " + packageName + " - " + error);
                listener.onInstallFailed("Download failed: " + error);

                // ❌ If download fails, reapply restrictions immediately
                reapplyInstallRestrictions();
            }
        });
    }

    private void downloadApk(String apkUrl, File apkFile, DownloadListener listener) {
        if (apkFile.exists()) {
            apkFile.delete(); // Ensure fresh download
        }

        Request request = new Request.Builder().url(apkUrl).build();
        new Thread(() -> {
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    listener.onDownloadFailed("HTTP error: " + response.code());
                    return;
                }

                try (InputStream in = response.body().byteStream();
                     FileOutputStream out = new FileOutputStream(apkFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                    out.flush();
                    Log.i(TAG, "Downloaded APK to: " + apkFile.getAbsolutePath());
                    listener.onDownloadSuccess(apkFile);
                }
            } catch (IOException e) {
                Log.e(TAG, "Download error: " + e.getMessage(), e);
                listener.onDownloadFailed("Download error: " + e.getMessage());

                // ❌ Reapply restrictions if download fails
                reapplyInstallRestrictions();
            }
        }).start();
    }

    private void performInstallation(String packageName, File apkFile, OnInstallListener listener) {
        try {
            PackageInstaller packageInstaller = context.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(packageName);

            int sessionId = packageInstaller.createSession(params);
            PackageInstaller.Session session = packageInstaller.openSession(sessionId);

            try (InputStream in = new FileInputStream(apkFile);
                 OutputStream out = session.openWrite(packageName, 0, apkFile.length())) {
                byte[] buffer = new byte[65536];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                session.fsync(out);
            }

            Intent callbackIntent = new Intent(context, PackageInstallerReceiver.class);
            callbackIntent.putExtra("packageName", packageName);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    callbackIntent,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0
            );
            session.commit(pendingIntent.getIntentSender());
            session.close();

            Log.i(TAG, "Installation session committed for: " + packageName);
            listener.onInstallStarted();

            // ✅ Reapply restrictions after install
            reapplyInstallRestrictions();

        } catch (IOException | SecurityException e) {
            Log.e(TAG, "Failed to install package: " + packageName, e);
            listener.onInstallFailed("Installation failed: " + e.getMessage());

            // ❌ Reapply restrictions even if install fails
            reapplyInstallRestrictions();
        }
    }

    // --- Restriction handling ---
    private void clearInstallRestrictions() {
        try {
            dpm.clearUserRestriction(admin, android.os.UserManager.DISALLOW_INSTALL_APPS);
            dpm.clearUserRestriction(admin, android.os.UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES);
            Log.i(TAG, "✅ Cleared install restrictions temporarily");
        } catch (Exception e) {
            Log.w(TAG, "⚠️ Could not clear restrictions: " + e.getMessage(), e);
        }
    }

    private void reapplyInstallRestrictions() {
        try {
            dpm.addUserRestriction(admin, android.os.UserManager.DISALLOW_INSTALL_APPS);
            dpm.addUserRestriction(admin, android.os.UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES);
            Log.i(TAG, "✅ Reapplied install restrictions");
        } catch (Exception e) {
            Log.w(TAG, "⚠️ Could not reapply restrictions: " + e.getMessage(), e);
        }
    }

    public interface OnInstallListener {
        void onInstallStarted();
        void onInstallFailed(String error);
        void onInstallSuccess(String packageName);
    }

    private interface DownloadListener {
        void onDownloadSuccess(File file);
        void onDownloadFailed(String error);
    }
}