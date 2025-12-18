package com.rotation.controller;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.List;

public class SmartChargeDialogFragment extends DialogFragment {

    public static final String TAG = SmartChargeDialogFragment.class.getSimpleName();

    public static final String VALUE_NONE = "NONE";
    public static final String VALUE_LAST_USED = "LAST_USED";

    private Spinner mConnectSpinner;
    private Spinner mDisconnectSpinner;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_smart_charge, null);

        mConnectSpinner = view.findViewById(R.id.spinner_connect_mode);
        mDisconnectSpinner = view.findViewById(R.id.spinner_disconnect_mode);

        setupSpinners(context);

        return new AlertDialog.Builder(context)
                .setTitle(R.string.smart_charge_title)
                .setView(view)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> savePreferences())
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }

    private void setupSpinners(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String savedConnect = prefs.getString(context.getString(R.string.smart_charge_connect_mode_key), VALUE_NONE);
        String savedDisconnect = prefs.getString(context.getString(R.string.smart_charge_disconnect_mode_key), VALUE_LAST_USED);

        // Setup Connect Adapter
        List<ModeItem> connectItems = new ArrayList<>();
        connectItems.add(new ModeItem(VALUE_NONE, context.getString(R.string.mode_none)));
        for (RotationMode mode : RotationMode.values()) {
            connectItems.add(new ModeItem(mode.name(), context.getString(mode.stringId())));
        }
        ArrayAdapter<ModeItem> connectAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, connectItems);
        mConnectSpinner.setAdapter(connectAdapter);
        setSpinnerSelection(mConnectSpinner, connectItems, savedConnect);

        // Setup Disconnect Adapter
        List<ModeItem> disconnectItems = new ArrayList<>();
        disconnectItems.add(new ModeItem(VALUE_NONE, context.getString(R.string.mode_none)));
        disconnectItems.add(new ModeItem(VALUE_LAST_USED, context.getString(R.string.mode_last_used)));
        for (RotationMode mode : RotationMode.values()) {
            disconnectItems.add(new ModeItem(mode.name(), context.getString(mode.stringId())));
        }
        ArrayAdapter<ModeItem> disconnectAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, disconnectItems);
        mDisconnectSpinner.setAdapter(disconnectAdapter);
        setSpinnerSelection(mDisconnectSpinner, disconnectItems, savedDisconnect);
    }

    private void setSpinnerSelection(Spinner spinner, List<ModeItem> items, String value) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).value.equals(value)) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    private void savePreferences() {
        Context context = getContext();
        if (context == null) return;

        ModeItem selectedConnect = (ModeItem) mConnectSpinner.getSelectedItem();
        ModeItem selectedDisconnect = (ModeItem) mDisconnectSpinner.getSelectedItem();

        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(getString(R.string.smart_charge_connect_mode_key), selectedConnect.value)
                .putString(getString(R.string.smart_charge_disconnect_mode_key), selectedDisconnect.value)
                .apply();
    }

    private static class ModeItem {
        final String value;
        final String label;

        ModeItem(String value, String label) {
            this.value = value;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}