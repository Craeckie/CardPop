/*
 * Copyright (C) 2026 FloFla Dev
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.cardpop.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast

/**
 * Launches the Pleco Chinese dictionary app via its dedicated launcher activity.
 *
 * Uses ACTION_SEND to com.pleco.chinesesystem/.PlecoDictLauncherActivity, which is the
 * same entry point Pleco registers for the system text-selection share menu.
 *
 * Requires a `<queries><package .../></queries>` entry for `com.pleco.chinesesystem`
 * in AndroidManifest.xml so PackageManager queries succeed on Android 11+.
 */
object PlecoLauncher {

    private const val TAG = "PlecoLauncher"
    private const val PLECO_PACKAGE = "com.pleco.chinesesystem"
    private const val PLECO_LAUNCHER_CLASS = "com.pleco.chinesesystem.PlecoDictLauncherActivity"

    fun isAvailable(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(PLECO_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun lookup(context: Context, word: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, word)
            setClassName(PLECO_PACKAGE, PLECO_LAUNCHER_CLASS)
            // Required when starting an Activity from a non-Activity context (overlay service).
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        Log.d(TAG, "Launching Pleco with word='$word'")
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Pleco launcher activity not found", e)
            Toast.makeText(context, "Pleco is not installed", Toast.LENGTH_SHORT).show()
        }
    }
}
