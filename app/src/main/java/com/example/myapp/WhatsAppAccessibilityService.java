package com.example.myapp;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityManager;
import java.util.List;

public class WhatsAppAccessibilityService extends AccessibilityService {
    private static final String TAG = "WhatsAppRawLog";
    private static final String WHATSAPP_PACKAGE = "com.whatsapp";

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "========================================");
        Log.i(TAG, "WhatsApp Accessibility Service CONNECTED");
        Log.i(TAG, "========================================");

        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            Log.i(TAG, "Service Info:");
            Log.i(TAG, "- Package Names: " + java.util.Arrays.toString(info.packageNames));
            Log.i(TAG, "- Event Types: " + info.eventTypes);
            Log.i(TAG, "- Feedback Type: " + info.feedbackType);
            Log.i(TAG, "- Flags: " + info.flags);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Log EVERYTHING without any filtering
        Log.i(TAG, "================== RAW EVENT ==================");
        Log.i(TAG, "Package: " + event.getPackageName());
        Log.i(TAG, "Event Type: " + event.getEventType() + " (" + getEventTypeName(event.getEventType()) + ")");
        Log.i(TAG, "Event Time: " + event.getEventTime());
        Log.i(TAG, "Class Name: " + event.getClassName());
        Log.i(TAG, "Content Description: " + event.getContentDescription());

        // Log all text in the event
        List<CharSequence> texts = event.getText();
        if (texts != null && !texts.isEmpty()) {
            Log.i(TAG, "Event Texts (" + texts.size() + " items):");
            for (int i = 0; i < texts.size(); i++) {
                Log.i(TAG, "  Text[" + i + "]: '" + texts.get(i) + "'");
            }
        } else {
            Log.i(TAG, "Event Texts: NONE");
        }

        // Log additional event properties
        Log.i(TAG, "Before Text: " + event.getBeforeText());
        Log.i(TAG, "Item Count: " + event.getItemCount());
        Log.i(TAG, "Current Item Index: " + event.getCurrentItemIndex());
        Log.i(TAG, "From Index: " + event.getFromIndex());
        Log.i(TAG, "To Index: " + event.getToIndex());
        Log.i(TAG, "Added Count: " + event.getAddedCount());
        Log.i(TAG, "Removed Count: " + event.getRemovedCount());
        Log.i(TAG, "Window ID: " + event.getWindowId());

        // Log source node information if available
        AccessibilityNodeInfo source = event.getSource();
        if (source != null) {
            logNodeInfo(source, "SOURCE");
            logNodeHierarchy(source, 0, "ROOT");
            source.recycle();
        } else {
            Log.i(TAG, "Source Node: NULL");
        }

        Log.i(TAG, "===============================================");
    }

    private void logNodeInfo(AccessibilityNodeInfo node, String label) {
        if (node == null) {
            Log.i(TAG, label + " Node: NULL");
            return;
        }

        Log.i(TAG, label + " Node Info:");
        Log.i(TAG, "  Text: '" + node.getText() + "'");
        Log.i(TAG, "  Content Description: '" + node.getContentDescription() + "'");
        Log.i(TAG, "  Class Name: " + node.getClassName());
        Log.i(TAG, "  Package Name: " + node.getPackageName());
        Log.i(TAG, "  View ID: " + node.getViewIdResourceName());
        Log.i(TAG, "  Child Count: " + node.getChildCount());
        Log.i(TAG, "  Clickable: " + node.isClickable());
        Log.i(TAG, "  Focusable: " + node.isFocusable());
        Log.i(TAG, "  Enabled: " + node.isEnabled());
        Log.i(TAG, "  Selected: " + node.isSelected());
        Log.i(TAG, "  Checkable: " + node.isCheckable());
        Log.i(TAG, "  Checked: " + node.isChecked());
    }

    private void logNodeHierarchy(AccessibilityNodeInfo node, int depth, String prefix) {
        if (node == null || depth > 3) { // Limit depth to avoid spam
            return;
        }

        String indent = "  ".repeat(depth);
        Log.i(TAG, indent + prefix + " - Class: " + node.getClassName() +
                ", Text: '" + node.getText() + "'" +
                ", ContentDesc: '" + node.getContentDescription() + "'");

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount && i < 5; i++) { // Limit to first 5 children
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                logNodeHierarchy(child, depth + 1, "CHILD[" + i + "]");
                child.recycle();
            }
        }
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
            case AccessibilityEvent.TYPE_VIEW_HOVER_ENTER: return "VIEW_HOVER_ENTER";
            case AccessibilityEvent.TYPE_VIEW_HOVER_EXIT: return "VIEW_HOVER_EXIT";
            case AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START: return "TOUCH_EXPLORATION_GESTURE_START";
            case AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END: return "TOUCH_EXPLORATION_GESTURE_END";
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED: return "WINDOW_CONTENT_CHANGED";
            case AccessibilityEvent.TYPE_VIEW_SCROLLED: return "VIEW_SCROLLED";
            case AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED: return "VIEW_TEXT_SELECTION_CHANGED";
            case AccessibilityEvent.TYPE_ANNOUNCEMENT: return "ANNOUNCEMENT";
            case AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED: return "VIEW_ACCESSIBILITY_FOCUSED";
            case AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED: return "VIEW_ACCESSIBILITY_FOCUS_CLEARED";
            case AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY: return "VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY";
            case AccessibilityEvent.TYPE_GESTURE_DETECTION_START: return "GESTURE_DETECTION_START";
            case AccessibilityEvent.TYPE_GESTURE_DETECTION_END: return "GESTURE_DETECTION_END";
            case AccessibilityEvent.TYPE_TOUCH_INTERACTION_START: return "TOUCH_INTERACTION_START";
            case AccessibilityEvent.TYPE_TOUCH_INTERACTION_END: return "TOUCH_INTERACTION_END";
            case AccessibilityEvent.TYPE_WINDOWS_CHANGED: return "WINDOWS_CHANGED";
            default: return "UNKNOWN(" + eventType + ")";
        }
    }

    @Override
    public void onInterrupt() {
        Log.i(TAG, "========================================");
        Log.i(TAG, "Accessibility Service INTERRUPTED");
        Log.i(TAG, "========================================");
    }

    public static boolean isAccessibilityServiceEnabled(Context context) {
        AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) {
            Log.e("AccessibilityCheck", "AccessibilityManager is null");
            return false;
        }

        String serviceId = context.getPackageName() + "/" + WhatsAppAccessibilityService.class.getName();
        Log.d("AccessibilityCheck", "Looking for service ID: " + serviceId);

        // List all enabled services for debugging
        List<AccessibilityServiceInfo> enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        Log.d("AccessibilityCheck", "Found " + enabledServices.size() + " enabled accessibility services:");
        for (AccessibilityServiceInfo service : enabledServices) {
            Log.d("AccessibilityCheck", "  - " + service.getId());
            if (serviceId.equals(service.getId())) {
                Log.i("AccessibilityCheck", "SUCCESS: Found our service in enabled list!");
                return true;
            }
        }

        Log.w("AccessibilityCheck", "Our service NOT found in enabled list");
        return false;
    }
}