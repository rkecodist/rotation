package com.rotation.controller;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.RemoteViews;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import java.util.Set;

import lombok.Data;
import lombok.Getter;

public class RotationService extends Service {

    public static final String PACKAGE = "com.rotation.controller";
    public static final String TAG = RotationService.class.getSimpleName();

    public static final String CONTROLS_CHANNEL_ID = "Controls";
    public static final String SERVICE_CHANNEL_ID = "Service";
    public static final String WARNING_CHANNEL_ID = "Warning";
    public static final int NOTIFICATION_ID = 1;
    public static final int PRESETS_NOTIFICATION_ID = 2;

    public static final String ACTION_START = "START";
    public static final String ACTION_CONFIGURATION_CHANGED = "CONFIGURATION_CHANGED";
    public static final String ACTION_ORIENTATION_CHANGED = "ORIENTATION_CHANGED";
    public static final String ACTION_PRESETS_UPDATE = "PRESETS_UPDATE";
    public static final String ACTION_PRESETS_RESTORE = "PRESETS_RESTORE";

    public static final String ACTION_REFRESH_NOTIFICATION = "REFRESH";
    public static final int ACTION_REFRESH_NOTIFICATION_REQUEST_CODE = 10;

    public static final String ACTION_CHANGE_GUARD = "CHANGE_GUARD";
    public static final int ACTION_CHANGE_GUARD_REQUEST_CODE = 20;

    public static final String ACTION_CHANGE_MODE = "CHANGE_MODE";
    public static final int ACTION_CHANGE_MODE_REQUEST_CODE_BASE = 30;
    public static final String INTENT_NEW_MODE = "NEW_MODE";

    public static final String ACTION_REFRESH_MODE = "REFRESH_MODE";
    public static final int ACTION_REFRESH_MODE_REQUEST_CODE = 40;

    public static final String ACTION_TOGGLE_POWER = "TOGGLE_POWER";
    public static final int ACTION_TOGGLE_POWER_REQUEST_CODE = 50;

    public static final String ACTION_EXIT_SERVICE = "EXIT_SERVICE";
    public static final int ACTION_EXIT_SERVICE_REQUEST_CODE = 60;

    public static final String TINT_METHOD = "setColorFilter";

    public static final String ACTION_NOTIFY_CREATED = "com.rotation.controller.SERVICE_CREATED";
    public static final String ACTION_NOTIFY_DESTROYED = "com.rotation.controller.SERVICE_DESTROYED";
    public static final String ACTION_NOTIFY_UPDATED = "com.rotation.controller.SERVICE_UPDATED";

    private final Runnable mBroadcastToggleGuardIntent = new Runnable() {
        @Override
        public void run() {
            currentlyRefreshing = false;
            Log.i(TAG, String.format("new new guard=%s", guard));
            afterStartCommand();
        }
    };

    private final Runnable mTriggerAutoLock = new Runnable() {
        @Override
        public void run() {
            Log.i(TAG, "triggering auto lock");
            triggerAutoLock();
        }
    };

    private final IBinder binder = new LocalBinder();

    private @Getter boolean guard = true;
    private @Getter RotationMode activeMode = RotationMode.AUTO;
    private @Getter RotationMode previousActiveMode = null;
    private @Getter boolean currentlyRefreshing = false;
    private @Getter boolean isPowerOn = true;

    private @Getter AutoLockSettings autoLock = new AutoLockSettings();
    private @Getter int lastDisplayRotationValue = -1;

    private View mView;

    private Handler mHandler;
    private UnlockBroadcastReceiver mUnlockBroadcastReceiver;
    private OrientationBroadcastReceiver mOrientationReceiver;
    private ChargeBroadcastReceiver mChargeReceiver;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate");
        DebugLogger.log(this, "Service onCreate called. PowerOn: " + isPowerOn);

        createNotificationChannel(CONTROLS_CHANNEL_ID, R.string.controls_notification_channel_name);
        createNotificationChannel(SERVICE_CHANNEL_ID, R.string.service_notification_channel_name);
        createNotificationChannel(WARNING_CHANNEL_ID, R.string.warning_notification_channel_name);
        loadFromPreferences();

        if (isPowerOn) {
            saveSystemState();
        }

        mHandler = new Handler(Looper.getMainLooper());

        mUnlockBroadcastReceiver = new UnlockBroadcastReceiver();
        registerReceiver(mUnlockBroadcastReceiver, new IntentFilter(Intent.ACTION_USER_PRESENT));

        mOrientationReceiver = new OrientationBroadcastReceiver();
        registerReceiver(mOrientationReceiver, new IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED));

        mChargeReceiver = new ChargeBroadcastReceiver();
        IntentFilter chargeFilter = new IntentFilter();
        chargeFilter.addAction(Intent.ACTION_POWER_CONNECTED);
        chargeFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        registerReceiver(mChargeReceiver, chargeFilter);

        setupAutoLock();

        sendBroadcast(new Intent(ACTION_NOTIFY_CREATED));
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy");

        if (mView != null) {
            getWindowManager().removeView(mView);
            mView = null;
        }

        if (mUnlockBroadcastReceiver != null) {
            unregisterReceiver(mUnlockBroadcastReceiver);
            mUnlockBroadcastReceiver = null;
        }

        if (mOrientationReceiver != null) {
            unregisterReceiver(mOrientationReceiver);
            mOrientationReceiver = null;
        }

        if (mChargeReceiver != null) {
            unregisterReceiver(mChargeReceiver);
            mChargeReceiver = null;
        }

        sendBroadcast(new Intent(ACTION_NOTIFY_DESTROYED));

        DebugLogger.log(this, "Service onDestroy called. Service stopping.");

        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putBoolean(getString(R.string.service_enabled_key), false)
                .apply();

        getNotificationManager().cancel(NOTIFICATION_ID);

        mHandler.removeCallbacks(mBroadcastToggleGuardIntent);
        mHandler.removeCallbacks(mTriggerAutoLock);

        restoreSystemState();

        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            DebugLogger.log(this, "onStartCommand: intent is null");
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        DebugLogger.log(this, String.format("onStartCommand: action=%s, flags=%d, startId=%d", action, flags, startId));

        if (action == null) {
            return START_NOT_STICKY;
        }

        switch (action) {
            // ... (keep existing cases but ensure logging is present)
            case ACTION_START: {
                DebugLogger.log(this, "Processing ACTION_START");
                Notification notification = createNotification(isNotificationShown());
                // ...
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
                } else {
                    startForeground(NOTIFICATION_ID, notification);
                }
                break;
            }
            
            case ACTION_TOGGLE_POWER: {
                boolean wasEnabled = isPowerOn;
                isPowerOn = !isPowerOn;
                DebugLogger.log(this, String.format("Processing ACTION_TOGGLE_POWER. Old=%s, New=%s", wasEnabled, isPowerOn));

                PreferenceManager.getDefaultSharedPreferences(this)
                        .edit()
                        .putBoolean(getString(R.string.power_on_key), isPowerOn)
                        .apply();

                if (isPowerOn && !wasEnabled) {
                    saveSystemState();
                } else if (!isPowerOn && wasEnabled) {
                    restoreSystemState();
                }
                break;
            }
            
            // ... other cases
        }

        // ...
        
        afterStartCommand();
        return START_STICKY;
    }

    private void afterStartCommand() {
        DebugLogger.log(this, String.format("afterStartCommand: guard=%s, mode=%s, powerOn=%s", guard, activeMode, isPowerOn));
        applyMode();

        // ... notification logic ...
        NotificationManager notificationManager = getNotificationManager();
        if (isNotificationShown()) {
            notificationManager.notify(NOTIFICATION_ID, createNotification(true));
        } else {
            notificationManager.cancel(NOTIFICATION_ID);
        }

        DebugLogger.log(this, "Sending ACTION_NOTIFY_UPDATED broadcast");
        sendBroadcast(new Intent(ACTION_NOTIFY_UPDATED));

        // ...
    }

    private void setupAutoLock() {
        mHandler.removeCallbacks(mTriggerAutoLock);

        boolean isEnabled = autoLock.isEnabled() && !currentlyRefreshing;
        if (!isEnabled) {
            Log.d(TAG, String.format("setupAutoLock not enabled - autoLockWait=%s currentlyRefreshing=%s", autoLock.isEnabled(), currentlyRefreshing));
            return;
        }

        if (!RotationMode.AUTO.equals(activeMode)) {
            Log.d(TAG, String.format("setupAutoLock cancelled - activeMode=%s", activeMode));
            return;
        }

        lastDisplayRotationValue = getCurrentDisplayRotation();
        Log.d(TAG, String.format("setupAutoLock - lastDisplayRotationValue=%s", lastDisplayRotationValue));

        mHandler.postDelayed(mTriggerAutoLock, autoLock.getWaitSeconds() * 1000L);
    }

    private void triggerAutoLock() {
        if (lastDisplayRotationValue == -1) {
            lastDisplayRotationValue = getCurrentDisplayRotation();
        }

        RotationMode newMode = RotationMode.fromPreferences(this, R.string.auto_lock_mode_key, RotationMode.AUTO);
        if (newMode == RotationMode.AUTO) {
            newMode = RotationMode.fromRotationValue(lastDisplayRotationValue);
        } else if (!autoLock.isForce()) {
            RotationMode currentMode = RotationMode.fromRotationValue(getCurrentDisplayRotation());

            if (!isCompatible(newMode, currentMode)) {
                return;
            }
        }

        Toast.makeText(this, getString(R.string.auto_lock_trigger, getString(newMode.stringId())), Toast.LENGTH_SHORT).show();

        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putString(getString(R.string.mode_key), newMode.name())
                .apply();

        notifyConfigurationChanged(this);
    }

    private boolean isCompatible(RotationMode toMode, RotationMode currentMode) {
        if (toMode.equals(currentMode)) {
            return true;
        }

        if (RotationMode.LANDSCAPE_SENSOR.equals(toMode)) {
            return RotationMode.LANDSCAPE.equals(currentMode) || RotationMode.LANDSCAPE_REVERSE.equals(currentMode);
        }

        if (RotationMode.PORTRAIT_SENSOR.equals(toMode)) {
            return RotationMode.PORTRAIT.equals(currentMode) || RotationMode.PORTRAIT_REVERSE.equals(currentMode);
        }

        return false;
    }

    private Notification createNotification(boolean showNotification) {
        String channelId = showNotification
                ? CONTROLS_CHANNEL_ID
                : SERVICE_CHANNEL_ID;

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(getApplicationContext(), channelId)
                .setSmallIcon(R.drawable.mode_auto)
                .setOngoing(true)
                .setSilent(true)
                .setShowWhen(false)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET);

        if (showNotification) {
            RemoteViews layout = new RemoteViews(getPackageName(), R.layout.notification);
            layout.setOnClickPendingIntent(R.id.guard, newGuardPendingIntent());
            layout.setOnClickPendingIntent(R.id.toggle_service, newToggleServicePendingIntent());
            layout.setOnClickPendingIntent(R.id.exit_service, newExitServicePendingIntent());
            layout.setOnClickPendingIntent(R.id.refresh, newRefreshPendingIntent());

            for (RotationMode mode : RotationMode.values()) {
                // Log.i(TAG, String.format("attach intent - mode=%s viewId=%d", mode, mode.viewId()));
                layout.setOnClickPendingIntent(mode.viewId(), newModePendingIntent(mode));
            }

            notificationBuilder
                    .setCustomContentView(layout)
                    .setCustomBigContentView(layout)
                    .setDeleteIntent(newRefreshPendingIntent());

            notificationBuilder
                    .setSubText(null);

            updateViews(layout);
        } else {
            notificationBuilder
                    .setSubText(getString(R.string.notification_discard_me_title));

            notificationBuilder
                    .setCustomContentView(null)
                    .setCustomBigContentView(null)
                    .setDeleteIntent(null);
        }

        Log.i(TAG, String.format("prepared notification - showNotification=%s", showNotification));

        return notificationBuilder.build();
    }

    private boolean isNotificationShown() {
        return PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(getString(R.string.show_notification_key), true);
    }

    private Notification createPresetsNotification() {
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                PRESETS_NOTIFICATION_ID /* should use a dedicated code */,
                SettingsFragment.newOpenAccessibilityServiceSettingsIntent(),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(getApplicationContext(), WARNING_CHANNEL_ID)
                .setSmallIcon(R.drawable.mode_auto)
                .setSilent(true)
                .setContentText(getString(R.string.notification_accessibility_not_enabled_subtitle))
                .setSubText(getString(R.string.notification_discard_me_title))
                .setContentIntent(pendingIntent);

        Log.i(TAG, "prepared presets notification");

        return notificationBuilder.build();
    }

    private void loadFromPreferences() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        guard = preferences.getBoolean(getString(R.string.guard_key), true);
        isPowerOn = preferences.getBoolean(getString(R.string.power_on_key), true);
        activeMode = RotationMode.fromPreferences(this);

        autoLock.load(preferences);
    }

    public boolean isGuardEnabledOrForced() {
        return (guard || activeMode.doesRequireGuard()) && !currentlyRefreshing;
    }

    public boolean isUsingPresets() {
        return previousActiveMode != null;
    }

    private void updateViews(RemoteViews layout) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        Set<String> enabledButtons = preferences.getStringSet(getString(R.string.buttons_key), null);
        for (RotationMode mode : RotationMode.values()) {
            if (enabledButtons != null && !enabledButtons.contains(mode.name())) {
                layout.setViewVisibility(mode.viewId(), View.GONE);
            } else {
                layout.setViewVisibility(mode.viewId(), View.VISIBLE);
            }

            layout.setInt(mode.viewId(), TINT_METHOD, getColor(R.color.inactive));
        }

        if (enabledButtons != null && !enabledButtons.contains("POWER")) {
            layout.setViewVisibility(R.id.toggle_service, View.GONE);
        } else {
            layout.setViewVisibility(R.id.toggle_service, View.VISIBLE);
        }

        if (enabledButtons != null && !enabledButtons.contains("EXIT")) {
            layout.setViewVisibility(R.id.exit_service, View.GONE);
        } else {
            layout.setViewVisibility(R.id.exit_service, View.VISIBLE);
        }

        if (enabledButtons != null && !enabledButtons.contains("GUARD")) {
            layout.setViewVisibility(R.id.guard, View.GONE);
        } else {
            layout.setViewVisibility(R.id.guard, View.VISIBLE);
        }

        if (enabledButtons != null && !enabledButtons.contains("REFRESH")) {
            layout.setViewVisibility(R.id.refresh, View.GONE);
        } else {
            layout.setViewVisibility(R.id.refresh, View.VISIBLE);
        }

        if (isPowerOn) {
            layout.setInt(activeMode.viewId(), TINT_METHOD, getColor(R.color.active));
            layout.setInt(R.id.toggle_service, TINT_METHOD, getColor(R.color.active));
            layout.setInt(R.id.exit_service, TINT_METHOD, getColor(R.color.inactive));
            layout.setInt(R.id.refresh, TINT_METHOD, getColor(R.color.inactive));

            if (isGuardEnabledOrForced()) {
                layout.setInt(R.id.guard, TINT_METHOD, getColor(R.color.active));
            } else {
                layout.setInt(R.id.guard, TINT_METHOD, getColor(R.color.inactive));
            }
        } else {
            layout.setInt(R.id.toggle_service, TINT_METHOD, getColor(R.color.inactive));
            layout.setInt(R.id.guard, TINT_METHOD, getColor(R.color.inactive));
            layout.setInt(R.id.exit_service, TINT_METHOD, getColor(R.color.inactive));
            layout.setInt(R.id.refresh, TINT_METHOD, getColor(R.color.inactive));
        }
    }

    private void applyMode() {
        ContentResolver contentResolver = getContentResolver();

        if (!isPowerOn) {
            if (mView != null) {
                getWindowManager().removeView(mView);
                mView = null;
            }
            // Logic handled by restoreSystemState called in ACTION_TOGGLE_POWER
            return;
        }

        if (isGuardEnabledOrForced()) {
            WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(
                    0,
                    0,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    Gravity.TOP
            );
            layoutParams.screenOrientation = activeMode.orientationValue();

            if (mView == null) {
                mView = new View(getApplicationContext());
                getWindowManager().addView(mView, layoutParams);
            } else {
                getWindowManager().updateViewLayout(mView, layoutParams);
            }

            Settings.System.putInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1);
        } else {
            if (mView != null) {
                getWindowManager().removeView(mView);
                mView = null;
            }

            if (activeMode.shouldUseAccelerometerRotation()) {
                Settings.System.putInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1);
            } else {
                Settings.System.putInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0);
                Settings.System.putInt(contentResolver, Settings.System.USER_ROTATION, activeMode.rotationValue());
            }
        }
    }

    private void createNotificationChannel(String id, @StringRes int name) {
        NotificationChannel notificationChannel = new NotificationChannel(id, getString(name), NotificationManager.IMPORTANCE_DEFAULT);
        notificationChannel.setSound(null, null);
        notificationChannel.setShowBadge(false);
        notificationChannel.enableVibration(false);
        notificationChannel.enableLights(false);
        notificationChannel.setLockscreenVisibility(NotificationCompat.VISIBILITY_SECRET);

        getNotificationManager().createNotificationChannel(notificationChannel);
    }

    private PendingIntent newRefreshPendingIntent() {
        Intent intent = new Intent(getApplicationContext(), RotationService.class);
        intent.setAction(ACTION_REFRESH_NOTIFICATION);

        return PendingIntent.getService(
                this,
                ACTION_REFRESH_NOTIFICATION_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private PendingIntent newRefreshModePendingIntent() {
        Intent intent = newRefreshModeIntent(this);

        return PendingIntent.getService(
                this,
                ACTION_REFRESH_NOTIFICATION_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private PendingIntent newToggleServicePendingIntent() {
        Intent intent = new Intent(getApplicationContext(), RotationService.class);
        intent.setAction(ACTION_TOGGLE_POWER);

        return PendingIntent.getService(
                this,
                ACTION_TOGGLE_POWER_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private PendingIntent newExitServicePendingIntent() {
        Intent intent = new Intent(getApplicationContext(), RotationService.class);
        intent.setAction(ACTION_EXIT_SERVICE);

        return PendingIntent.getService(
                this,
                ACTION_EXIT_SERVICE_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private PendingIntent newGuardPendingIntent() {
        Intent intent = newToggleGuardIntent(this);

        return PendingIntent.getService(
                this,
                ACTION_CHANGE_GUARD_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private PendingIntent newModePendingIntent(RotationMode mode) {
        Intent intent = newChangeModeIntent(this, mode);

        return PendingIntent.getService(
                this,
                ACTION_CHANGE_MODE_REQUEST_CODE_BASE + mode.ordinal(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private int getCurrentDisplayRotation() {
        return getWindowManager().getDefaultDisplay().getRotation();
    }

    public boolean hasRotationValueChanged() {
        int rotationValue = getCurrentDisplayRotation();

        return lastDisplayRotationValue != rotationValue;
    }

    private boolean isCharging() {
        Intent intent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent == null) return false;
        int plugged = intent.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, -1);
        return plugged == android.os.BatteryManager.BATTERY_PLUGGED_AC
                || plugged == android.os.BatteryManager.BATTERY_PLUGGED_USB
                || plugged == android.os.BatteryManager.BATTERY_PLUGGED_WIRELESS;
    }

    public static Intent newToggleGuardIntent(Context context) {
        Intent intent = new Intent(context.getApplicationContext(), RotationService.class);
        intent.setAction(ACTION_CHANGE_GUARD);

        return intent;
    }

    public static Intent newChangeModeIntent(Context context, RotationMode mode) {
        Intent intent = new Intent(context.getApplicationContext(), RotationService.class);
        intent.setAction(ACTION_CHANGE_MODE);
        intent.putExtra(INTENT_NEW_MODE, mode.name());

        return intent;
    }

    public static Intent newRefreshModeIntent(Context context) {
        Intent intent = new Intent(context.getApplicationContext(), RotationService.class);
        intent.setAction(ACTION_REFRESH_MODE);

        return intent;
    }

    public static Intent newTogglePowerIntent(Context context) {
        Intent intent = new Intent(context.getApplicationContext(), RotationService.class);
        intent.setAction(ACTION_TOGGLE_POWER);

        return intent;
    }

    private NotificationManager getNotificationManager() {
        return (NotificationManager) getApplicationContext().getSystemService(NOTIFICATION_SERVICE);
    }

    private WindowManager getWindowManager() {
        return (WindowManager) getApplicationContext().getSystemService(WINDOW_SERVICE);
    }

    public static void start(Context context) {
        RotationSharedPreferences.from(context).setServiceEnabled(true);

        Intent intent = new Intent(context.getApplicationContext(), RotationService.class);
        intent.setAction(ACTION_START);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
        
        DebugLogger.log(context, "Service Start Requested");
    }

    public static void notifyConfigurationChanged(Context context) {
        notifyConfigurationChanged(context, false);
    }

    public static void notifyConfigurationChanged(Context context, boolean forceStart) {
        if (!forceStart && !isRunning(context)) {
            return;
        }

        Intent intent = new Intent(context.getApplicationContext(), RotationService.class);
        intent.setAction(ACTION_CONFIGURATION_CHANGED);

        context.startService(intent);
    }

    public static void notifyOrientationChanged(Context context) {
        if (!isRunning(context)) {
            return;
        }

        Intent intent = new Intent(context.getApplicationContext(), RotationService.class);
        intent.setAction(ACTION_ORIENTATION_CHANGED);

        context.startService(intent);
    }

    public static void notifyPresetsUpdate(Context context, RotationMode newMode) {
        if (!isRunning(context)) {
            return;
        }

        Intent intent = new Intent(context.getApplicationContext(), RotationService.class);
        intent.setAction(ACTION_PRESETS_UPDATE);
        intent.putExtra(INTENT_NEW_MODE, newMode.name());

        context.startService(intent);
    }

    public static void notifyPresetsRestore(Context context) {
        if (!isRunning(context)) {
            return;
        }

        Intent intent = new Intent(context.getApplicationContext(), RotationService.class);
        intent.setAction(ACTION_PRESETS_RESTORE);

        context.startService(intent);
    }

    public static void stop(Context context) {
        RotationSharedPreferences.from(context).setServiceEnabled(false);

        Intent intent = new Intent(context, RotationService.class);

        context.stopService(intent);
        DebugLogger.log(context, "Service Stop Requested");
    }

    public static boolean isRunning(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);

        for (ActivityManager.RunningServiceInfo serviceInfo : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (RotationService.class.getName().equals(serviceInfo.service.getClassName())) {
                return true;
            }
        }

        return false;
    }

    private void saveSystemState() {
        try {
            int accelRotation = Settings.System.getInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION);
            int userRotation = Settings.System.getInt(getContentResolver(), Settings.System.USER_ROTATION, 0);

            PreferenceManager.getDefaultSharedPreferences(this)
                    .edit()
                    .putInt(getString(R.string.saved_accel_rotation_key), accelRotation)
                    .putInt(getString(R.string.saved_user_rotation_key), userRotation)
                    .apply();

            Log.i(TAG, String.format("Saved system state: accel=%d, user=%d", accelRotation, userRotation));
        } catch (Settings.SettingNotFoundException e) {
            Log.e(TAG, "Failed to save system state", e);
        }
    }

    private void restoreSystemState() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int accelRotation = prefs.getInt(getString(R.string.saved_accel_rotation_key), -1);
        int userRotation = prefs.getInt(getString(R.string.saved_user_rotation_key), -1);

        if (accelRotation != -1) {
            Settings.System.putInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, accelRotation);
        }
        if (userRotation != -1) {
            Settings.System.putInt(getContentResolver(), Settings.System.USER_ROTATION, userRotation);
        }

        Log.i(TAG, String.format("Restored system state: accel=%d, user=%d", accelRotation, userRotation));
    }

    public class LocalBinder extends Binder {

        public RotationService getService() {
            return RotationService.this;
        }

    }

    @Data
    public class AutoLockSettings {

        private boolean enabled;
        private int waitSeconds;
        private boolean force;

        public void load(SharedPreferences preferences) {
            this.waitSeconds = Integer.parseInt(preferences.getString(getString(R.string.auto_lock_key), "0"));
            this.enabled = this.waitSeconds != 0;
            this.force = preferences.getBoolean(getString(R.string.auto_lock_force_key), false);
        }

    }

    public class ChargeBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !isPowerOn) {
                return;
            }

            String action = intent.getAction();
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

            if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
                String modeStr = preferences.getString(getString(R.string.smart_charge_connect_mode_key), SmartChargeDialogFragment.VALUE_NONE);
                if (SmartChargeDialogFragment.VALUE_NONE.equals(modeStr)) {
                    return;
                }

                Log.i(TAG, "Charger connected, applying " + modeStr);
                // Save current activeMode before overriding for charge mode
                preferences.edit()
                        .putString(getString(R.string.saved_original_mode_for_charge_key), activeMode.name())
                        .apply();

                activeMode = RotationMode.valueOf(modeStr);
                afterStartCommand();
            } else if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
                String modeStr = preferences.getString(getString(R.string.smart_charge_disconnect_mode_key), SmartChargeDialogFragment.VALUE_LAST_USED);
                if (SmartChargeDialogFragment.VALUE_NONE.equals(modeStr)) {
                    return;
                }

                Log.i(TAG, "Charger disconnected, applying " + modeStr);
                if (SmartChargeDialogFragment.VALUE_LAST_USED.equals(modeStr)) {
                    // Restore original activeMode
                    String originalModeName = preferences.getString(getString(R.string.saved_original_mode_for_charge_key), RotationMode.AUTO.name());
                    activeMode = RotationMode.valueOf(originalModeName);
                } else {
                    activeMode = RotationMode.valueOf(modeStr);
                }

                afterStartCommand();
            }
        }
    }

}