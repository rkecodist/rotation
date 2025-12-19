package com.rotation.controller;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import java.util.Set;

public class QuickActionsDialog extends Dialog implements View.OnClickListener, ServiceConnection {

    private final Listener mListener = new Listener();

    private RotationService mService;
    private boolean isBound = false;
    private boolean isRegistered = false;

    public QuickActionsDialog(@NonNull Context context) {
        super(new ContextThemeWrapper(context, R.style.AppTheme_QuickActionsDialog));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
// ... existing code ...
        updateViews(false, null);
    }
// ... existing onClick ...
    @Override
    protected void onStart() {
        super.onStart();

        final Context context = getContext();

        Intent intent = new Intent(context, RotationService.class);
        isBound = context.bindService(intent, this, Context.BIND_AUTO_CREATE);

        IntentFilter filter = new IntentFilter(RotationService.ACTION_NOTIFY_UPDATED);
        ContextCompat.registerReceiver(context, mListener, filter, ContextCompat.RECEIVER_EXPORTED);
        isRegistered = true;
    }

    @Override
    protected void onStop() {
        super.onStop();

        final Context context = getContext();

        if (isBound) {
            try {
                context.unbindService(this);
            } catch (IllegalArgumentException e) {
                DebugLogger.log(context, "Error unbinding service: " + e.getMessage());
            }
            isBound = false;
        }
        mService = null;

        if (isRegistered) {
            try {
                context.unregisterReceiver(mListener);
            } catch (IllegalArgumentException e) {
                DebugLogger.log(context, "Error unregistering receiver: " + e.getMessage());
            }
            isRegistered = false;
        }
    }

    @Override
    public void onServiceConnected(ComponentName className, IBinder service) {
        if (!isBound) {
            return;
        }

        RotationService.LocalBinder binder = (RotationService.LocalBinder) service;
        mService = binder.getService();

        RotationMode activeMode = mService.getActiveMode();
        boolean guard = mService.isGuardEnabledOrForced();

        updateViews(guard, activeMode);
    }

    @Override
    public void onServiceDisconnected(ComponentName className) {
        mService = null;
    }

    public void updateViews(boolean guard, RotationMode activeMode) {
        final Context context = getContext();

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        Set<String> enabledButtons = preferences.getStringSet(context.getString(R.string.buttons_key), null);
        for (RotationMode mode : RotationMode.values()) {
            ImageView view = findViewById(mode.viewId());

            if (enabledButtons != null && !enabledButtons.contains(mode.name())) {
                view.setVisibility(View.GONE);
            } else {
                view.setVisibility(View.VISIBLE);
            }

            setActiveColor(context, view, mode == activeMode);
        }

        ImageView guardView = findViewById(R.id.guard);
        if (enabledButtons != null && !enabledButtons.contains("GUARD")) {
            guardView.setVisibility(View.GONE);
        } else {
            guardView.setVisibility(View.VISIBLE);
        }
        setActiveColor(context, guardView, guard);

        boolean isPowerOn = preferences.getBoolean(context.getString(R.string.power_on_key), true);
        ImageView toggleServiceView = findViewById(R.id.toggle_service);
        setActiveColor(context, toggleServiceView, isPowerOn);
    }

    private void setActiveColor(Context context, ImageView view, boolean active) {
        if (active) {
            view.setColorFilter(context.getColor(R.color.active));
        } else {
            view.setColorFilter(context.getColor(R.color.inactive));
        }
    }

    public boolean shouldCloseOnClick() {
        final Context context = getContext();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        return preferences.getBoolean(context.getString(R.string.close_dialog_on_click_key), true);
    }

    public class Listener extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }

            String action = intent.getAction();
            if (!RotationService.ACTION_NOTIFY_UPDATED.equals(action)) {
                return;
            }

            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

            boolean guard = preferences.getBoolean(context.getString(R.string.guard_key), false);
            RotationMode activeMode = RotationMode.fromPreferences(context);

            updateViews(guard, activeMode);
        }
    }

}