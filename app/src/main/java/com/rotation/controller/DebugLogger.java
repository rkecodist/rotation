package com.rotation.controller;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DebugLogger {

    private static final String TAG = "RotationDebug";
    private static final String FILE_NAME = "rotation_debug_log.txt";

    public static void log(Context context, String message) {
        try {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
            
            // Get caller stack trace
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            String caller = "";
            if (stackTrace.length >= 4) {
                StackTraceElement element = stackTrace[3];
                caller = element.getClassName().substring(element.getClassName().lastIndexOf('.') + 1) + "." + element.getMethodName() + ":" + element.getLineNumber();
            }

            String formattedMessage = String.format("[%s] [%s] %s", timestamp, caller, message);

            // Log to Logcat
            Log.i(TAG, formattedMessage);

            // Log to File
            if (context != null) {
                File logFile = new File(context.getExternalFilesDir(null), FILE_NAME);
                try (FileOutputStream fos = new FileOutputStream(logFile, true);
                     PrintWriter pw = new PrintWriter(fos)) {
                    pw.println(formattedMessage);
                    pw.flush();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to write to log file", e);
        }
    }
}
