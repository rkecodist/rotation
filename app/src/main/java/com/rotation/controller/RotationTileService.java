package com.rotation.controller;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.IBinder;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

public class RotationTileService extends TileService implements ServiceConnection {

    public static final String TAG = RotationTileService.class.getSimpleName();

    private Listener mListener;
    private RotationService mService;

    @Override
    public void onStartListening() {
        super.onStartListening();

        Log.i(TAG, "onStartListening");

        if (mListener == null) {
            mListener = new Listener();

            IntentFilter filter = new IntentFilter();
            filter.addAction(RotationService.ACTION_NOTIFY_CREATED);
            filter.addAction(RotationService.ACTION_NOTIFY_UPDATED);
            filter.addAction(RotationService.ACTION_NOTIFY_DESTROYED);

            ContextCompat.registerReceiver(this, mListener, filter, ContextCompat.RECEIVER_EXPORTED);
        }

        if (mService == null) {
            Intent intent = new Intent(this, RotationService.class);
            bindService(intent, this, Context.BIND_AUTO_CREATE);
        }

        updateTile(RotationService.isRunning(this));
    }

    @Override
    public void onStopListening() {
        super.onStopListening();

        Log.i(TAG, "onStopListening");

        if (mListener != null) {
            unregisterReceiver(mListener);
            mListener = null;
        }

        if (mService != null) {
            unbindService(this);
            mService = null;
        }
    }

    @Override
    public void onServiceConnected(ComponentName className, IBinder service) {
        RotationService.LocalBinder binder = (RotationService.LocalBinder) service;
        mService = binder.getService();

        updateTileUsingService();
    }

    @Override
    public void onServiceDisconnected(ComponentName className) {
        mService = null;
    }

    @Override
    public void onClick() {
        super.onClick();

        Log.i(TAG, "onClick");

        // Single Source of Truth 1: Master Service Switch
        boolean isServiceRunning = RotationService.isRunning(this);

        // If Service is OFF, ANY click starts it.
        if (!isServiceRunning) {
            setTileUnavailable();
            RotationService.start(this);
            return;
        }

        // If Service is ON, check behavior
        TileClickBehavior tileClickBehavior = TileClickBehavior.fromPreferences(this);

        switch (tileClickBehavior) {
            case TOGGLE_SERVICE: {
                // Master Switch OFF
                setTileUnavailable();
                RotationService.stop(this);
                break;
            }

            case TOGGLE_POWER: {
                // Logic Switch Toggle
                startService(RotationService.newTogglePowerIntent(this));
                break;
            }

            case SHOW_MODES: {
                showDialog(new QuickActionsDialog(this));
                break;
            }
        }
    }

    public void setTileUnavailable() {
        Tile tile = getQsTile();
        tile.setState(Tile.STATE_UNAVAILABLE);
        tile.updateTile();
    }

    public void updateTile(boolean running) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        RotationMode activeMode = RotationMode.fromPreferences(this);
        boolean guard = preferences.getBoolean(getString(R.string.guard_key), true);
        boolean presets = false;
        boolean powerOn = preferences.getBoolean(getString(R.string.power_on_key), true);

        updateTile(running, powerOn, activeMode, guard, presets);
    }

    public void updateTileUsingService() {
        RotationMode activeMode = mService.getActiveMode();
        boolean guard = mService.isGuardEnabledOrForced();
        boolean presets = mService.isUsingPresets();
        boolean powerOn = mService.isPowerOn();

        updateTile(true, powerOn, activeMode, guard, presets);
    }

    public void updateTile(boolean running, boolean powerOn, RotationMode activeMode, boolean guard, boolean presets) {
        Tile tile = getQsTile();
        TileClickBehavior behavior = TileClickBehavior.fromPreferences(this);

        // Condition 1: Service OFF -> Tile Inactive, "Service Off"
        if (!running) {
            tile.setState(Tile.STATE_INACTIVE);
            tile.setLabel(getString(R.string.tile_title));
            tile.setSubtitle(getString(R.string.tile_service_off));
            tile.setIcon(Icon.createWithResource(this, R.drawable.mode_auto)); // Default/Inactive icon
            tile.updateTile();
            return;
        }

        // Condition 2: Service ON
        String suffix = "";
        if (guard) suffix += " " + getString(R.string.tile_with_guard);
        if (presets) suffix += " " + getString(R.string.tile_with_presets);

        if (behavior == TileClickBehavior.TOGGLE_SERVICE) {
            // Behavior: Toggle Service
            // Service is running, so it's Active
            tile.setState(Tile.STATE_ACTIVE);
            tile.setLabel(getString(R.string.tile_title));
            tile.setSubtitle(getString(R.string.tile_service_on));
            tile.setIcon(getIconWith(activeMode, guard, presets));

        } else if (behavior == TileClickBehavior.TOGGLE_POWER) {
            // Behavior: Toggle Power
            if (powerOn) {
                tile.setState(Tile.STATE_ACTIVE);
                tile.setLabel(getString(R.string.tile_title));
                tile.setSubtitle(getString(R.string.tile_power_on));
                tile.setIcon(getIconWith(activeMode, guard, presets));
            } else {
                tile.setState(Tile.STATE_INACTIVE);
                tile.setLabel(getString(R.string.tile_title));
                tile.setSubtitle(getString(R.string.tile_power_off));
                tile.setIcon(Icon.createWithResource(this, R.drawable.mode_auto)); // Or specific off icon
            }

        } else { // SHOW_MODES
            // Behavior: Show Modes
            // Always Active if Service is Running (Power state affects internal logic, but Mode is what we show)
            // Ideally, if Power is OFF, maybe we show "Paused"? But requirement says "Active: $Mode"
            // Let's stick to the mode display, maybe indicate paused in subtitle if needed.
            // Requirement: "Active: $Mode" (no active/inactive text)

            tile.setState(Tile.STATE_ACTIVE);
            tile.setLabel(getString(R.string.tile_title));
            
            // If Power is OFF, we might want to indicate it, but the requirement was specific about "$Mode"
            // "Active: $Mode" was the old behavior. New requirement: "(subtitle: $Mode)"
            // Let's refine based on "Power Off" state?
            // "system wide state for service off and on... if service is OFF... INACTIVE (Service Off)" - Handled.
            // "then after turning on it will follow their behaviour"

            if (!powerOn) {
                // If logic is paused, showing the Mode might be misleading if it's not applying.
                // However, the user might want to know what mode *will* apply.
                // Let's append (Paused) or similar if power is off? 
                // Or just show "Power Off" if that overrides everything?
                // Returning to requirement 3: "Active: $Mode" (Subtitle: $Mode). 
                
                // Let's respect the "System Wide State" rule first.
                // If Power is Off (Logic paused), effectively the Tile isn't doing anything to rotation.
                // But the Behavior is SHOW_MODES.
                
                // Let's display the Mode, maybe with a visual cue?
                // For now, simple:
                tile.setSubtitle(getString(activeMode.stringId()) + suffix);
            } else {
                 tile.setSubtitle(getString(activeMode.stringId()) + suffix);
            }
            
            tile.setIcon(getIconWith(activeMode, guard, presets));
        }

        tile.updateTile();
        Log.d(TAG, String.format("updated tile - running=%s power=%s activeMode=%s", running, powerOn, activeMode));
    }

    public class Listener extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }

            String action = intent.getAction();
            if (action == null) {
                return;
            }

            Log.d(TAG, String.format("received intent - action=%s", action));

            switch (action) {
                case RotationService.ACTION_NOTIFY_CREATED: {
                    updateTile(true);
                    break;
                }

                case RotationService.ACTION_NOTIFY_UPDATED: {
                    if (mService != null) {
                        updateTileUsingService();
                    }

                    break;
                }

                case RotationService.ACTION_NOTIFY_DESTROYED: {
                    updateTile(false);
                    break;
                }
            }
        }
    }

    public Icon getIconWith(RotationMode mode, boolean guard, boolean presets) {
        if (!guard && !presets) {
            return Icon.createWithResource(this, mode.drawableId());
        }

        Bitmap mainBitmap = getBitmapFromDrawable(getDrawable(mode.drawableId()));
        Canvas canvas = new Canvas(mainBitmap);

        if (guard) {
            Bitmap bitmap = getBitmapFromDrawable(getDrawable(R.drawable.guard));
            Bitmap scaledBitmap = scaledBitmap(bitmap, 0.4f);

            int left = mainBitmap.getWidth() - scaledBitmap.getWidth();
            int top = mainBitmap.getHeight() - scaledBitmap.getHeight();

            {
                float centerX = left + (scaledBitmap.getWidth() / 2f);
                float centerY = top + (scaledBitmap.getHeight() / 2f);
                float radius = (scaledBitmap.getWidth() / 2f) * 1.05f;

                Paint paint = new Paint();
                paint.setBlendMode(BlendMode.CLEAR);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(centerX, centerY, radius, paint);
            }

            canvas.drawBitmap(scaledBitmap, left, top, null);
        }

        if (presets) {
            Bitmap bitmap = getBitmapFromDrawable(getDrawable(R.drawable.icon_smart_toy));
            Bitmap scaledBitmap = scaledBitmap(bitmap, 0.4f);

            int left = 0;
            int top = 0;

            {
                float centerX = left + (scaledBitmap.getWidth() / 2f);
                float centerY = top + (scaledBitmap.getHeight() / 2f);
                float radius = (scaledBitmap.getWidth() / 2f) * 1.05f;

                Paint paint = new Paint();
                paint.setBlendMode(BlendMode.CLEAR);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(centerX, centerY, radius, paint);
            }

            canvas.drawBitmap(scaledBitmap, left, top, null);
        }

        return Icon.createWithBitmap(mainBitmap);
    }

    private static Bitmap scaledBitmap(Bitmap original, float scale) {
        int width = (int) (original.getWidth() * scale);
        int height = (int) (original.getHeight() * scale);

        return Bitmap.createScaledBitmap(original, width, height, true);
    }

    private static Bitmap getBitmapFromDrawable(Drawable drawable) {
        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }

}