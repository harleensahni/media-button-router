/*
 * Copyright 2011 Harleen Sahni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.harleensahni.android.mbr;

import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.view.KeyEvent;

public final class Utils {

    private static final String TAG = "MediaButtonRouter";

    /**
     * Prevent instantiation.
     */
    private Utils() {
        // Intentionally blank
    }

    /**
     * Whether the keyCode represents a media button that we handle.
     * 
     * @param keyCode
     * @return
     */
    public static boolean isMediaButton(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD || keyCode == KeyEvent.KEYCODE_MEDIA_NEXT
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS
                || keyCode == KeyEvent.KEYCODE_MEDIA_REWIND || keyCode == KeyEvent.KEYCODE_MEDIA_STOP;
    }

    /**
     * Forwards {@code keyCode} to receiver specified as two key events, one for
     * up and one for down. Optionally launches the application for the
     * receiver.
     * 
     * @param context
     * @param selectedReceiver
     * @param launch
     * @param keyCode
     * @param cleanUpReceiver
     */
    public static void forwardKeyCodeToComponent(Context context, ComponentName selectedReceiver, boolean launch,
            int keyCode, BroadcastReceiver cleanUpReceiver) {

        Intent mediaButtonDownIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        KeyEvent downKe = new KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN,
                keyCode, 0);
        mediaButtonDownIntent.putExtra(Intent.EXTRA_KEY_EVENT, downKe);

        Intent mediaButtonUpIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        KeyEvent upKe = new KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), KeyEvent.ACTION_UP,
                keyCode, 0);
        mediaButtonUpIntent.putExtra(Intent.EXTRA_KEY_EVENT, upKe);

        mediaButtonDownIntent.setComponent(selectedReceiver);
        mediaButtonUpIntent.setComponent(selectedReceiver);

        Log.i(TAG, "Forwarding Down and Up intent events to " + selectedReceiver + " Down Intent: "
                + mediaButtonDownIntent + " Down key:" + downKe + " Up Intent: " + mediaButtonUpIntent + " Up key:"
                + upKe);
        // We start the selected application because some apps broadcast
        // receivers won't do anything with the intents unless the
        // application is open. (This this is only if the app isn't
        // playing music and you want it to play music now)
        // XXX Is this true? recheck..
        if (launch) {
            context.startActivity(context.getPackageManager().getLaunchIntentForPackage(
                    selectedReceiver.getPackageName()));
        }

        context.sendOrderedBroadcast(mediaButtonDownIntent, null, cleanUpReceiver, null, Activity.RESULT_OK, null, null);
        context.sendOrderedBroadcast(mediaButtonUpIntent, null, cleanUpReceiver, null, Activity.RESULT_OK, null, null);

    }

    public static List<ResolveInfo> getMediaReceivers(PackageManager packageManager) {
        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        return packageManager.queryBroadcastReceivers(mediaButtonIntent, PackageManager.GET_INTENT_FILTERS
                | PackageManager.GET_RESOLVED_FILTER);
    }

    public static AlertDialog showIntroifNeccessary(Context context) {
        final SharedPreferences preferenceManager = PreferenceManager.getDefaultSharedPreferences(context);
        if (!preferenceManager.getBoolean(Constants.INTRO_SHOWN_KEY, false)) {
            // TextView textview = new TextView(context);
            // textview.setText(context.getText(R.string.intro_text));
            Spanned s = Html.fromHtml(context.getString(R.string.intro_text));
            Builder alertDialog = new AlertDialog.Builder(context).setTitle("Introduction").setMessage(s);
            alertDialog.setOnCancelListener(new OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    // preferenceManager.edit().putBoolean(Constants.INTRO_SHOWN_KEY,
                    // true);
                    Log.d(TAG, "Intro cancelled. will show again.");
                }
            });
            alertDialog.setNegativeButton("Close", new DialogInterface.OnClickListener() {

                public void onClick(DialogInterface dialog, int which) {

                    preferenceManager.edit().putBoolean(Constants.INTRO_SHOWN_KEY, true).commit();

                    Log.d(TAG, "Intro closed. Will not show again.");
                }
            });
            return alertDialog.show();
        }
        return null;
    }
}
