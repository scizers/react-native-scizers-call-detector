package com.scizerscalldetector;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.media.AudioManager;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import com.facebook.react.bridge.Promise;
import android.content.Intent;
import android.net.Uri;

import com.facebook.react.bridge.Arguments;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import android.database.Cursor;
import android.provider.CallLog;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import java.util.HashMap;
import java.util.Map;

public class CallDetectionManagerModule extends ReactContextBaseJavaModule implements Application.ActivityLifecycleCallbacks, CallDetectionPhoneStateListener.PhoneCallStateUpdate {

    private static final String TAG = "CallDetectionManager";
    private static final String EVENT_NAME = "PhoneCallStateUpdateAndroid";

    private boolean wasAppInOffHook = false;
    private boolean wasAppInRinging = false;

    private final ReactApplicationContext reactContext;
    private TelephonyManager telephonyManager;
    private AudioManager audioManager;
    private CallDetectionPhoneStateListener callDetectionPhoneStateListener;
    private Activity activity = null;

    @RequiresApi(api = android.os.Build.VERSION_CODES.S)
    private CallStateListener callStateListener;

    public CallDetectionManagerModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @Override
    public String getName() {
        return "CallDetectionModule";
    }

    private void sendEvent(String callState, String phoneNumber) {
        if (reactContext.hasActiveCatalystInstance()) {
            reactContext
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                    .emit(EVENT_NAME, callState);
        }
    }

    @ReactMethod
    public void getCallLogs(double minTimestamp, double maxTimestamp, Promise promise) {
        if (ContextCompat.checkSelfPermission(reactContext, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            promise.reject("permission_denied", "READ_CALL_LOG permission not granted");
            return;
        }

        WritableArray callLogsArray = Arguments.createArray();

        try {
            Uri callLogUri = CallLog.Calls.CONTENT_URI;
            String[] projection = { CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.TYPE, CallLog.Calls.CACHED_NAME };

            String selection = null;

            if (minTimestamp > 0 || maxTimestamp > 0) {
                StringBuilder where = new StringBuilder();
                if (minTimestamp > 0) where.append(CallLog.Calls.DATE + " >= " + (long) minTimestamp);
                if (maxTimestamp > 0) {
                    if (where.length() > 0) where.append(" AND ");
                    where.append(CallLog.Calls.DATE + " <= " + (long) maxTimestamp);
                }
                selection = where.toString();
            }

            Cursor cursor = reactContext.getContentResolver().query(callLogUri, projection, selection, null, CallLog.Calls.DATE + " DESC");

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    WritableMap callLog = Arguments.createMap();

                    callLog.putString("number", cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)));
                    callLog.putDouble("timestamp", cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)));
                    callLog.putInt("duration", cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)));
                    callLog.putInt("type", cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)));
                    String name = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME));
                    callLog.putString("name", name != null ? name : "");

                    callLogsArray.pushMap(callLog);
                }
                cursor.close();
            }

            promise.resolve(callLogsArray);
        } catch (Exception e) {
            Log.e(TAG, "Error reading call logs", e);
            promise.reject("call_log_error", "Failed to read call logs", e);
        }
    }

    @ReactMethod
    public void startListener(Promise promise) {
        if (activity == null) {
            activity = getCurrentActivity();
            if (activity != null) {
                activity.getApplication().registerActivityLifecycleCallbacks(this);
            }
        }

        telephonyManager = (TelephonyManager) reactContext.getSystemService(Context.TELEPHONY_SERVICE);
        callDetectionPhoneStateListener = new CallDetectionPhoneStateListener(this);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (reactContext.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                callStateListener = new CallStateListener();
                telephonyManager.registerTelephonyCallback(ContextCompat.getMainExecutor(reactContext), callStateListener);
                promise.resolve("success");
            } else {
                promise.resolve("error");
                Log.e(TAG, "Permission READ_PHONE_STATE is not granted.");
            }
        } else {
            telephonyManager.listen(callDetectionPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
            promise.resolve("success");
        }
    }

    @ReactMethod
    public void stopListener() {
        if (telephonyManager != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && callStateListener != null) {
                telephonyManager.unregisterTelephonyCallback(callStateListener);
            } else if (callDetectionPhoneStateListener != null) {
                telephonyManager.listen(callDetectionPhoneStateListener, PhoneStateListener.LISTEN_NONE);
            }
        }
        callDetectionPhoneStateListener = null;
        telephonyManager = null;
    }

    @ReactMethod
    public void checkPhoneState(Callback callback) {
        audioManager = (AudioManager) reactContext.getSystemService(Context.AUDIO_SERVICE);
        boolean isInCall = audioManager.getMode() == AudioManager.MODE_IN_CALL;
        callback.invoke(isInCall);
    }

    @ReactMethod
    public void makePhoneCall(String phoneNumber, Promise promise) {
        try {
            if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
                promise.reject("invalid_number", "Phone number is invalid");
                return;
            }

            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel:" + phoneNumber));
            callIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (callIntent.resolveActivity(getReactApplicationContext().getPackageManager()) != null) {
                getReactApplicationContext().startActivity(callIntent);
                promise.resolve(true);
            } else {
                promise.reject("cannot_open_url", "No dialer app available to handle call");
            }
        } catch (SecurityException e) {
            promise.reject("permission_denied", "Missing CALL_PHONE permission", e);
        } catch (Exception e) {
            promise.reject("call_failed", "Failed to make the phone call", e);
        }
    }

    @Override
    public Map<String, Object> getConstants() {
        Map<String, Object> constants = new HashMap<>();
        constants.put("Incoming", "Incoming");
        constants.put("Offhook", "Offhook");
        constants.put("Disconnected", "Disconnected");
        constants.put("Missed", "Missed");
        return constants;
    }

    @Override
    public void phoneCallStateUpdated(int state, String phoneNumber) {
        switch (state) {
            case TelephonyManager.CALL_STATE_IDLE:
                if (wasAppInOffHook) {
                    sendEvent("Disconnected", phoneNumber);
                } else if (wasAppInRinging) {
                    sendEvent("Missed", phoneNumber);
                }
                wasAppInRinging = false;
                wasAppInOffHook = false;
                break;

            case TelephonyManager.CALL_STATE_OFFHOOK:
                wasAppInOffHook = true;
                sendEvent("Offhook", phoneNumber);
                break;

            case TelephonyManager.CALL_STATE_RINGING:
                wasAppInRinging = true;
                sendEvent("Incoming", phoneNumber);
                break;
        }
    }

    @RequiresApi(api = android.os.Build.VERSION_CODES.S)
    private class CallStateListener extends TelephonyCallback implements TelephonyCallback.CallStateListener {
        @Override
        public void onCallStateChanged(int state) {
            phoneCallStateUpdated(state, null);
        }
    }

    @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityResumed(Activity activity) {}
    @Override public void onActivityPaused(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
    @Override public void onActivityDestroyed(Activity activity) {}
}
