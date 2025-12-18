# Rotation Controller (Fork Improvements)

This fork significantly enhances the original Rotation Control app with new features, better UX, and optimizations.

## 🚀 Key Features & Changes

### ⚡ Smart Service Toggle
*   **New "Power" Button:** Replaced the old "Refresh" button in the notification with a dedicated **Service Toggle**.
*   **Pause Logic:** Toggling the button "Pauses" the rotation enforcement without killing the service (keeping the notification accessible).
*   **State Restoration:** When paused or stopped, the app now **automatically restores** your system's previous rotation settings (e.g., Auto-Rotate or Portrait), eliminating the "stuck rotation" bug.

### 🔋 Advanced Smart Charge
*   **Configurable Modes:** New "Smart Charge" settings dialog allows you to choose specific behaviors:
    *   **On Connect:** Automatically switch to a specific mode (e.g., Reverse Portrait) or do nothing.
    *   **On Disconnect:** Restore the "Last Used" mode, or switch to a specific mode.
*   **Intelligent Memory:** If you manually change the rotation mode *while* charging, the app remembers this new preference and keeps it even after you unplug (updating the "Last Used" state dynamically).

### 🎨 Modern UI & Branding
*   **Rebranded:** App name changed to **Rotation Controller** (`com.rotation.controller`).
*   **AMOLED Theme:** Updated the dark theme to use a **Pitch Black** background instead of grey.
*   **New Accent Color:** Replaced the old violet theme with a modern **Blue (#5F97F4)** for active states, toggles, and notification icons.
*   **Clean Look:** Status bar and Toolbar are now pitch black to match the background.

### 🛠️ Optimization & Cleanup
*   **Massive Size Reduction:** Removed unused Jetpack Compose dependencies, reducing APK size from **~24MB to ~3MB**.
*   **Minification Enabled:** Enabled ProGuard/R8 resource shrinking for release builds.
*   **UX Cleanup:** Removed the persistent "Enable Accessibility Service" notification prompt for optional features.

## 📦 Technical Details
*   **Package Name:** `com.rotation.controller`
*   **Build System:** Gradle (Kotlin DSL)
*   **Min SDK:** 29 (Android 10)
*   **Target SDK:** 34 (Android 14)
