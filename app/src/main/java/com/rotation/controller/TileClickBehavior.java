package com.rotation.controller;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

public enum TileClickBehavior {

    TOGGLE_SERVICE,
    TOGGLE_POWER,
    SHOW_MODES;

    public static TileClickBehavior fromPreferences(Context context) {
        return fromPreferences(context, TOGGLE_SERVICE);
    }

    public static TileClickBehavior fromPreferences(Context context, TileClickBehavior defaultValue) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String name = preferences.getString(context.getString(R.string.tile_click_behavior_key), null);

        if (name == null) {
            return defaultValue;
        }

        try {
            // Handle legacy "TOGGLE_CONTROL" value migration if necessary,
            // or assume user will reset it. For now, strict mapping.
            if ("TOGGLE_CONTROL".equals(name)) return TOGGLE_SERVICE;
            if ("SHOW_MODES_IF_CONTROLLING".equals(name)) return SHOW_MODES;
            if ("ALWAYS_SHOW_MODES".equals(name)) return SHOW_MODES;

            return valueOf(name);
        } catch (IllegalArgumentException __) {
            return defaultValue;
        }
    }

}