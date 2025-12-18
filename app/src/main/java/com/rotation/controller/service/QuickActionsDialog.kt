package com.rotation.controller.service

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rotation.controller.R
import com.rotation.controller.domain.RotationMode

class QuickActionsDialog(context: Context) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.quick_actions_dialog)

        window?.apply {
            // Check if we need overlay permission context or if TileService context is enough
            // TileService dialogs are special.
            // If called from TileService.showDialog, it handles window type usually.
            // But if we construct it manually, we might need TYPE_APPLICATION_OVERLAY if outside activity.
            // However, TileService.showDialog() takes a Dialog and manages it.
            // We should NOT set window type if using showDialog() from TileService.
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawableResource(android.R.color.transparent)
        }

        val recyclerView = findViewById<RecyclerView>(R.id.modes_recycler_view)
        recyclerView.layoutManager = GridLayoutManager(context, 4)
        recyclerView.adapter = ModeAdapter()
    }

    private inner class ModeAdapter : RecyclerView.Adapter<ModeAdapter.ViewHolder>() {
        private val modes = RotationMode.entries

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_mode, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val mode = modes[position]
            holder.bind(mode)
        }

        override fun getItemCount(): Int = modes.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val icon: ImageView = itemView.findViewById(R.id.icon)
            private val label: TextView = itemView.findViewById(R.id.label)

            fun bind(mode: RotationMode) {
                icon.setImageResource(mode.drawableId)
                label.setText(mode.stringId)

                itemView.setOnClickListener {
                    val intent = Intent(context, RotationControlService::class.java).apply {
                        action = RotationControlService.ACTION_CHANGE_MODE
                        putExtra(RotationControlService.INTENT_NEW_MODE, mode.name)
                    }
                    context.startService(intent)

                    val closeOnClick = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                        .getBoolean(context.getString(R.string.close_dialog_on_click_key), true)

                    if (closeOnClick) {
                        dismiss()
                    }
                }
            }
        }
    }
}
