package com.example.myapp;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class WhatsAppAccessibilityService extends AccessibilityService {
    private static final String TAG = "WhatsAppStructuredLog";
    private static final String WHATSAPP_PACKAGE = "com.whatsapp";
    private static final Pattern TIME_PATTERN = Pattern.compile("\\d{1,2}:\\d{2}\\s*[ap]m");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\+\\d{1,3} \\d+");
    private SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private int eventCounter = 0;
    private String currentChatId = null;
    private boolean isGroupChat = false;

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
            Log.i(TAG, "  Service ID: " + info.getId());
        }
        Log.i(TAG, "🎯 Ready to capture WhatsApp events...");
        Log.d(TAG, "Checking service status on start: " + isAccessibilityServiceEnabled(this));
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!WHATSAPP_PACKAGE.equals(event.getPackageName())) {
            return;
        }

        eventCounter++;
        String timestamp = timeFormat.format(new Date(event.getEventTime()));
        Log.v(TAG, "Event #" + eventCounter + " [" + timestamp + "] Type: " + getEventTypeName(event.getEventType()));

        // Handle chat open
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                event.getClassName().equals("com.whatsapp.Conversation")) {
            AccessibilityNodeInfo source = event.getSource();
            if (source == null) {
                Log.w(TAG, "Source node is null for WINDOW_STATE_CHANGED");
                return;
            }
            currentChatId = findChatId(source);
            isGroupChat = detectGroupChat(source);
            Log.i(TAG, "Chat opened: " + (isGroupChat ? "Group" : "Private") + " - " + (currentChatId != null ? currentChatId : "Unknown"));
            source.recycle();
        }

        // Process content changes
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            AccessibilityNodeInfo source = event.getSource();
            if (source == null) {
                Log.w(TAG, "Source node is null for event type: " + getEventTypeName(event.getEventType()));
                return;
            }
            processChatContent(source);
            source.recycle();
        }
    }

    private String findChatId(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/conversation_contact_name");
        if (!nodes.isEmpty()) {
            CharSequence name = nodes.get(0).getText();
            nodes.get(0).recycle();
            return name != null ? name.toString() : null;
        }

        nodes = findNodesByClassName(root, "android.widget.TextView");
        for (AccessibilityNodeInfo node : nodes) {
            CharSequence text = node.getText();
            if (text != null) {
                String textStr = text.toString();
                if (textStr.contains(",") && PHONE_PATTERN.matcher(textStr).find()) {
                    node.recycle();
                    return textStr;
                } else if (textStr.startsWith("+") || textStr.matches(".*admissions.*")) {
                    String result = textStr;
                    node.recycle();
                    return result;
                }
            }
            node.recycle();
        }
        return null;
    }

    private boolean detectGroupChat(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> buttons = root.findAccessibilityNodeInfosByText("GROUP INFO");
        if (!buttons.isEmpty()) {
            buttons.get(0).recycle();
            return true;
        }
        List<AccessibilityNodeInfo> nodes = findNodesByClassName(root, "android.widget.TextView");
        for (AccessibilityNodeInfo node : nodes) {
            CharSequence text = node.getText();
            if (text != null && text.toString().contains(",") && PHONE_PATTERN.matcher(text.toString()).find()) {
                node.recycle();
                return true;
            }
            node.recycle();
        }
        return false;
    }

    private List<AccessibilityNodeInfo> findNodesByClassName(AccessibilityNodeInfo root, String className) {
        List<AccessibilityNodeInfo> result = new ArrayList<>();
        if (root == null) return result;

        if (className.equals(root.getClassName())) {
            result.add(root);
        }

        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            if (child != null) {
                result.addAll(findNodesByClassName(child, className));
            }
        }
        return result;
    }

    private void processChatContent(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> listViews = findNodesByClassName(root, "android.widget.ListView");
        if (listViews.isEmpty()) {
            Log.w(TAG, "No ListView found in node tree");
            return;
        }

        AccessibilityNodeInfo listView = listViews.get(0);
        JSONArray messages = new JSONArray();

        int childCount = listView.getChildCount();
        Log.v(TAG, "Processing ListView with " + childCount + " children");
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = listView.getChild(i);
            if (child == null) continue;

            JSONObject item = parseNode(child);
            if (item != null) {
                messages.put(item);
            }
            child.recycle();
        }

        listView.recycle();
        for (AccessibilityNodeInfo node : listViews) {
            node.recycle();
        }

        if (messages.length() > 0) {
            logStructuredData(messages);
        } else {
            Log.w(TAG, "No messages parsed from ListView");
        }
    }

    private JSONObject parseNode(AccessibilityNodeInfo node) {
        String className = node.getClassName().toString();
        JSONObject result = new JSONObject();

        try {
            result.put("chat_id", currentChatId != null ? currentChatId : "Unknown");
            result.put("is_group", isGroupChat);
            result.put("timestamp", timeFormat.format(new Date()));

            if (className.equals("android.widget.TextView")) {
                CharSequence text = node.getText();
                if (text == null) return null;
                String textStr = text.toString();

                if (textStr.equals("Today") || textStr.equals("Yesterday") || textStr.contains("end-to-end encrypted")) {
                    return null;
                }
                if (textStr.contains("unread messages")) {
                    result.put("unread_count", textStr.replaceAll("\\D+", ""));
                    return result;
                }
                if (TIME_PATTERN.matcher(textStr).matches()) {
                    return null;
                }

                result.put("message", textStr);
                result.put("is_sent", false);
                return result;

            } else if (className.equals("android.widget.Button") || className.equals("android.view.ViewGroup")) {
                CharSequence text = node.getText();
                if (text != null) {
                    String textStr = text.toString();
                    if (textStr.contains("added you") || textStr.contains("changed the group name") || textStr.contains("call")) {
                        result.put(isGroupChat ? "system_message" : "call_info", textStr);
                        return result;
                    }
                }

            } else if (className.equals("android.widget.ImageView") && node.getContentDescription() != null) {
                String desc = node.getContentDescription().toString();
                if (desc.equals("Delivered") || desc.equals("Read") || desc.equals("Sent")) {
                    return null;
                }
            }

            int childCount = node.getChildCount();
            StringBuilder messageText = new StringBuilder();
            String timestamp = null;
            String status = null;
            boolean isSent = false;
            String sender = null;

            for (int i = 0; i < childCount; i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child == null) continue;

                String childClass = child.getClassName().toString();
                CharSequence childText = child.getText();
                CharSequence childDesc = child.getContentDescription();

                if (childClass.equals("android.widget.TextView") && childText != null) {
                    String textStr = childText.toString();
                    if (TIME_PATTERN.matcher(textStr).matches()) {
                        timestamp = textStr;
                    } else if (textStr.startsWith("~ ") || textStr.startsWith("+") || childDesc != null && childDesc.toString().contains("Maybe")) {
                        sender = textStr;
                    } else if (!textStr.contains("end-to-end encrypted") && !textStr.contains("members") && !textStr.contains("added you") && !textStr.contains("changed the group")) {
                        messageText.append(textStr).append(" ");
                    } else if (textStr.contains("members") || textStr.contains("Group created")) {
                        result.put("group_info", textStr);
                    }
                } else if (childClass.equals("android.widget.ImageView") && childDesc != null) {
                    String desc = childDesc.toString();
                    if (desc.equals("Delivered") || desc.equals("Read") || desc.equals("Sent")) {
                        isSent = true;
                        status = desc;
                    }
                } else if (childClass.equals("android.widget.Button") && childText != null) {
                    String btnText = childText.toString();
                    if (btnText.equals("GROUP INFO") || btnText.equals("ADD MEMBERS")) {
                        isGroupChat = true;
                    }
                }

                child.recycle();
            }

            if (messageText.length() > 0 || result.has("group_info")) {
                if (messageText.length() > 0) {
                    result.put("message", messageText.toString().trim());
                    result.put("is_sent", isSent);
                    if (timestamp != null) result.put("message_timestamp", timestamp);
                    if (status != null) result.put("status", status);
                    if (sender != null) result.put("sender", sender);
                }
                return result;
            }

        } catch (Exception e) {
            Log.e(TAG, "JSON error: " + e.getMessage());
        }
        return null;
    }

    private void logStructuredData(JSONArray messages) {
        try {
            JSONObject logEntry = new JSONObject();
            logEntry.put("event_id", eventCounter);
            logEntry.put("timestamp", timeFormat.format(new Date()));
            logEntry.put("chat_id", currentChatId != null ? currentChatId : "Unknown");
            logEntry.put("is_group", isGroupChat);
            logEntry.put("items", messages);
            Log.i(TAG, "Structured Log: " + logEntry.toString(2));
        } catch (Exception e) {
            Log.e(TAG, "JSON formatting error: " + e.getMessage());
        }
    }

    private String getEventTypeName(int eventType) {
        switch (eventType) {
            case AccessibilityEvent.TYPE_VIEW_CLICKED: return "VIEW_CLICKED";
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED: return "WINDOW_STATE_CHANGED";
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED: return "WINDOW_CONTENT_CHANGED";
            case AccessibilityEvent.TYPE_VIEW_SCROLLED: return "VIEW_SCROLLED";
            default: return "UNKNOWN_" + eventType;
        }
    }

    @Override
    public void onInterrupt() {
        Log.i(TAG, "🔴 WHATSAPP ACCESSIBILITY SERVICE INTERRUPTED");
        Log.i(TAG, "Total events captured: " + eventCounter);
    }

    public static boolean isAccessibilityServiceEnabled(Context context) {
        AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) {
            Log.e(TAG, "AccessibilityManager is null");
            return false;
        }

        String serviceId = context.getPackageName() + "/" + WhatsAppAccessibilityService.class.getCanonicalName();
        Log.v(TAG, "Checking service: " + serviceId);
        List<AccessibilityServiceInfo> enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        Log.v(TAG, "Enabled services count: " + enabledServices.size());

        for (AccessibilityServiceInfo service : enabledServices) {
            Log.v(TAG, "Enabled service: " + service.getId());
            if (serviceId.equals(service.getId())) {
                Log.i(TAG, "✅ Service is ENABLED");
                return true;
            }
        }

        Log.w(TAG, "❌ Service NOT enabled");
        return false;
    }
}