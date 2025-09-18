package com.example.myapp

import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat

class MyNotificationListenerService : NotificationListenerService() {
    companion object {
        private const val TAG = "MyNotificationListener"
        @JvmStatic
        fun isNotificationAccessEnabled(context: Context): Boolean {
            return try {
                val pkgName = context.packageName
                val enabledListeners = Settings.Secure.getString(
                    context.contentResolver,
                    "enabled_notification_listeners"
                )
                val isEnabled = enabledListeners?.contains(pkgName) == true
                Log.d(TAG, "Notification access enabled: $isEnabled for $pkgName")
                isEnabled
            } catch (e: Exception) {
                Log.e(TAG, "Error checking notification access: ${e.message}", e)
                false
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        try {
            sbn ?: return
            val packageName = sbn.packageName
            val notification = sbn.notification
            val title = notification.extras.getString("android.title", "No Title")
            val text = notification.extras.getCharSequence("android.text", "")?.toString() ?: ""
            val timestamp = sbn.postTime
            val isSensitive = notification.extras.getBoolean("android.sensitive", false)

            Log.d(
                TAG,
                "Notification posted: $packageName - $title - $text (Sensitive: $isSensitive, Time: $timestamp)"
            )

            // TODO: Re-enable Room database storage if needed
            /*
            val notificationEntity = NotificationEntity(
                packageName = packageName,
                title = title,
                text = text,
                timestamp = timestamp,
                isSensitive = isSensitive
            )
            coroutineScope.launch {
                db.notificationDao().insert(notificationEntity)
            }
            */
        } catch (e: Exception) {
            Log.e(TAG, "Error processing notification: ${e.message}", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        Log.d(TAG, "Notification removed: ${sbn.packageName}")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Notification listener disconnected")
    }
}