package com.example.myapp;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityManager;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class WhatsAppAccessibilityService extends AccessibilityService {
    private static final String TAG = "WhatsAppRawLog";
    private static final String WHATSAPP_PACKAGE = "com.whatsapp";
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());
    private int eventCounter = 0;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "==========================================");
        Log.i(TAG, "🟢 WHATSAPP ACCESSIBILITY SERVICE STARTED");
        Log.i(TAG, "==========================================");

        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            Log.i(TAG, "Service Configuration:");
            Log.i(TAG, "  Target Packages: " + java.util.Arrays.toString(info.packageNames));
            Log.i(TAG, "  Event Types Mask: " + info.eventTypes);
            Log.i(TAG, "  Feedback Type: " + info.feedbackType);
            Log.i(TAG, "  Can Retrieve Content: " + ((info.flags & AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS) != 0));
        }
        Log.i(TAG, "🎯 Ready to capture WhatsApp events...");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Only process WhatsApp events
        if (!WHATSAPP_PACKAGE.equals(event.getPackageName())) {
            return;
        }

        eventCounter++;
        String timestamp = timeFormat.format(new Date(event.getEventTime()));

        Log.i(TAG, "");
        Log.i(TAG, "🔴 EVENT #" + eventCounter + " [" + timestamp + "]");
        Log.i(TAG, "┌─ TYPE: " + getEventTypeName(event.getEventType()) + " (" + event.getEventType() + ")");
        Log.i(TAG, "├─ PACKAGE: " + event.getPackageName());
        Log.i(TAG, "├─ CLASS: " + event.getClassName());

        // Log content description if present
        if (event.getContentDescription() != null) {
            Log.i(TAG, "├─ CONTENT_DESC: '" + event.getContentDescription() + "'");
        }

        // Log all text content from event
        List<CharSequence> texts = event.getText();
        if (texts != null && !texts.isEmpty()) {
            Log.i(TAG, "├─ EVENT_TEXTS (" + texts.size() + "):");
            for (int i = 0; i < texts.size(); i++) {
                if (texts.get(i) != null && texts.get(i).length() > 0) {
                    Log.i(TAG, "│  └─ [" + i + "]: '" + texts.get(i) + "'");
                }
            }
        }

        // Log before/after text for text change events
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            if (event.getBeforeText() != null) {
                Log.i(TAG, "├─ BEFORE_TEXT: '" + event.getBeforeText() + "'");
            }
            Log.i(TAG, "├─ TEXT_CHANGE: From[" + event.getFromIndex() + "] To[" + event.getToIndex() + "] Added[" + event.getAddedCount() + "] Removed[" + event.getRemovedCount() + "]");
        }

        // Log additional event properties for relevant events
        if (event.getItemCount() > 0) {
            Log.i(TAG, "├─ ITEMS: Count=" + event.getItemCount() + " CurrentIndex=" + event.getCurrentItemIndex());
        }

        // Process source node
        AccessibilityNodeInfo source = event.getSource();
        if (source != null) {
            logSourceNode(source);
            logImportantNodes(source);
            source.recycle();
        } else {
            Log.i(TAG, "├─ SOURCE: NULL");
        }

        Log.i(TAG, "└─ END EVENT #" + eventCounter);
    }

    private void logSourceNode(AccessibilityNodeInfo node) {
        Log.i(TAG, "├─ SOURCE_NODE:");
        Log.i(TAG, "│  ├─ Text: '" + node.getText() + "'");
        Log.i(TAG, "│  ├─ ContentDesc: '" + node.getContentDescription() + "'");
        Log.i(TAG, "│  ├─ Class: " + node.getClassName());
        Log.i(TAG, "│  ├─ ViewID: " + node.getViewIdResourceName());
        Log.i(TAG, "│  ├─ Children: " + node.getChildCount());
        Log.i(TAG, "│  ├─ Clickable: " + node.isClickable());
        Log.i(TAG, "│  ├─ Enabled: " + node.isEnabled());
        Log.i(TAG, "│  └─ Bounds: " + getBounds(node));
    }

    private void logImportantNodes(AccessibilityNodeInfo root) {
        Log.i(TAG, "├─ NODE_TREE:");
        traverseAndLogImportantNodes(root, "│  ", 0);
    }

    private void traverseAndLogImportantNodes(AccessibilityNodeInfo node, String prefix, int depth) {
        if (node == null || depth > 4) return; // Limit depth to avoid overwhelming logs

        // Only log nodes with interesting content
        boolean hasText = node.getText() != null && node.getText().length() > 0;
        boolean hasContentDesc = node.getContentDescription() != null && node.getContentDescription().length() > 0;
        boolean hasViewId = node.getViewIdResourceName() != null;
        boolean isInteractive = node.isClickable() || node.isFocusable() || node.isCheckable();

        if (hasText || hasContentDesc || hasViewId || isInteractive) {
            String nodeInfo = String.format("%s├─ [%d] %s",
                    prefix, depth, node.getClassName());

            if (hasText) nodeInfo += " Text:'" + node.getText() + "'";
            if (hasContentDesc) nodeInfo += " Desc:'" + node.getContentDescription() + "'";
            if (hasViewId) nodeInfo += " ID:" + getShortViewId(node.getViewIdResourceName());
            if (isInteractive) nodeInfo += " [INTERACTIVE]";

            Log.i(TAG, nodeInfo);
        }

        // Traverse children
        int childCount = node.getChildCount();
        for (int i = 0; i < Math.min(childCount, 8); i++) { // Limit children to avoid spam
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                traverseAndLogImportantNodes(child, prefix + "│  ", depth + 1);
                child.recycle();
            }
        }
    }

    private String getBounds(AccessibilityNodeInfo node) {
        android.graphics.Rect bounds = new android.graphics.Rect();
        node.getBoundsInScreen(bounds);
        return String.format("[%d,%d-%d,%d]", bounds.left, bounds.top, bounds.right, bounds.bottom);
    }

    private String getShortViewId(String fullViewId) {
        if (fullViewId == null) return "null";
        int lastSlash = fullViewId.lastIndexOf('/');
        return lastSlash >= 0 ? fullViewId.substring(lastSlash + 1) : fullViewId;
    }

    private String getEventTypeName(int eventType) {
        switch (eventType) {
            case AccessibilityEvent.TYPE_VIEW_CLICKED: return "VIEW_CLICKED";
            case AccessibilityEvent.TYPE_VIEW_LONG_CLICKED: return "VIEW_LONG_CLICKED";
            case AccessibilityEvent.TYPE_VIEW_SELECTED: return "VIEW_SELECTED";
            case AccessibilityEvent.TYPE_VIEW_FOCUSED: return "VIEW_FOCUSED";
            case AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED: return "VIEW_TEXT_CHANGED";
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED: return "WINDOW_STATE_CHANGED";
            case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED: return "NOTIFICATION_STATE_CHANGED";
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED: return "WINDOW_CONTENT_CHANGED";
            case AccessibilityEvent.TYPE_VIEW_SCROLLED: return "VIEW_SCROLLED";
            case AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED: return "VIEW_TEXT_SELECTION_CHANGED";
            case AccessibilityEvent.TYPE_ANNOUNCEMENT: return "ANNOUNCEMENT";
            case AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED: return "VIEW_ACCESSIBILITY_FOCUSED";
            case AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED: return "VIEW_ACCESSIBILITY_FOCUS_CLEARED";
            case AccessibilityEvent.TYPE_WINDOWS_CHANGED: return "WINDOWS_CHANGED";
            case AccessibilityEvent.TYPE_TOUCH_INTERACTION_START: return "TOUCH_INTERACTION_START";
            case AccessibilityEvent.TYPE_TOUCH_INTERACTION_END: return "TOUCH_INTERACTION_END";
            default: return "UNKNOWN_" + eventType;
        }
    }

    @Override
    public void onInterrupt() {
        Log.i(TAG, "");
        Log.i(TAG, "🔴 WHATSAPP ACCESSIBILITY SERVICE INTERRUPTED");
        Log.i(TAG, "Total events captured: " + eventCounter);
    }

    public static boolean isAccessibilityServiceEnabled(Context context) {
        AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) {
            Log.e("AccessibilityCheck", "AccessibilityManager is null");
            return false;
        }

        String serviceId = context.getPackageName() + "/" + WhatsAppAccessibilityService.class.getName();
        Log.d("AccessibilityCheck", "Checking for service: " + serviceId);

        List<AccessibilityServiceInfo> enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        Log.d("AccessibilityCheck", "Found " + enabledServices.size() + " enabled services");

        for (AccessibilityServiceInfo service : enabledServices) {
            Log.d("AccessibilityCheck", "  Enabled: " + service.getId());
            if (serviceId.equals(service.getId())) {
                Log.i("AccessibilityCheck", "✅ Service is ENABLED");
                return true;
            }
        }

        Log.w("AccessibilityCheck", "❌ Service NOT enabled");
        return false;
    }
}