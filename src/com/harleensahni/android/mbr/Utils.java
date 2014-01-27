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

import java.util.Arrays;
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

/**
 * Utility class.
 * 
 * @author Harleen Sahni
 */
public final class Utils {

    private static final String TAG = "MediaButtonRouter";
//    private static final String GOOGLE_MUSIC_RECEIVER = "com.google.blahdfdf";

    public static final int KEYCODE_MEDIA_PLAY = 126;
    public static final int KEYCODE_MEDIA_PAUSE = 127;
    public static final int ICS_API_LEVEL = 14;

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
                || keyCode == KeyEvent.KEYCODE_MEDIA_REWIND || keyCode == KeyEvent.KEYCODE_MEDIA_STOP
                || keyCode == KEYCODE_MEDIA_PLAY || keyCode == KEYCODE_MEDIA_PAUSE 
                || keyCode == KeyEvent.KEYCODE_HEADSETHOOK;
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

        /* COMMENTED OUT FOR MARKET RELEASE Log.i(TAG, "Forwarding Down and Up intent events to " + selectedReceiver + " Down Intent: "
                + mediaButtonDownIntent + " Down key:" + downKe + " Up Intent: " + mediaButtonUpIntent + " Up key:"
                + upKe); */
        // We start the selected application because some apps broadcast
        // receivers won't do anything with the intents unless the
        // application is open. (This this is only if the app isn't
        // playing music and you want it to play music now)
        // XXX Is that true? recheck..
        // Another reason to launch the app is that if the app does
        // AudioManager#registerMediaButtonEventReceiver
        // on load, and we are unable to tell when this app is playing music,
        // android's default behavior should be correct.
        if (launch) {
            Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(
                    selectedReceiver.getPackageName());
            if (launchIntent != null) {
                context.startActivity(launchIntent);
            }
        }

        context.sendOrderedBroadcast(mediaButtonDownIntent, null, cleanUpReceiver, null, Activity.RESULT_OK, null, null);
        context.sendOrderedBroadcast(mediaButtonUpIntent, null, cleanUpReceiver, null, Activity.RESULT_OK, null, null);

    }

    /**
     * Gets the list of available media receivers, optionally filtering out ones
     * the user has indicated should be hidden in preferences.
     * 
     * @param packageManager
     *            The {@code PackageManager} used to retrieve media button
     *            receivers.
     * 
     * @param filterHidden
     *            Whether user-hidden media receivers should be shown.
     * @return The list of {@code ResolveInfo} for different media button
     *         receivers.
     */
    public static List<ResolveInfo> getMediaReceivers(PackageManager packageManager, boolean filterHidden,
            Context context) {
        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);

        List<ResolveInfo> mediaReceivers = packageManager.queryBroadcastReceivers(mediaButtonIntent,
                PackageManager.GET_INTENT_FILTERS | PackageManager.GET_RESOLVED_FILTER);
        if (filterHidden) {

            String hiddenReceiverIdsString = PreferenceManager.getDefaultSharedPreferences(context).getString(
                    Constants.HIDDEN_APPS_KEY, "");
            List<String> hiddenIds = Arrays.asList(hiddenReceiverIdsString.split(","));

            for (int i = mediaReceivers.size() - 1; i >= 0; i--) {
                ResolveInfo mediaReceiverResolveInfo = mediaReceivers.get(i);
              
                if (hiddenIds.contains(getMediaReceiverUniqueID(mediaReceiverResolveInfo, packageManager))) {
                    mediaReceivers.remove(i);
                }
            }
        }

        return mediaReceivers;
    }
    
    public static String getMediaReceiverUniqueID(ResolveInfo resolveInfo, PackageManager packageManager) {
        String receiverId = resolveInfo.activityInfo.name;
        // Don't think the following is an issue anymore with the latest versions of google play, if it is i'll add back
//        if (GOOGLE_MUSIC_RECEIVER.contains(receiverId)) {
//        // i have to be more exact than just application name because
//        // the two versions (old and new) of google music
//        // have the same classnames for their intent receivers. I need
//        // to know where their apks live to be able to differentiate.
//            receiverId = resolveInfo.activityInfo.applicationInfo.sourceDir + receiverId;
//        }
        return receiverId;
    }

    /**
     * Returns the name of the application of the broadcast receiver specified
     * by {@code resolveInfo}.
     * 
     * @param resolveInfo
     *            The receiver.
     * @return The name of the application.
     */
    public static String getAppName(ResolveInfo resolveInfo, PackageManager packageManager) {
        return resolveInfo.activityInfo.applicationInfo.loadLabel(packageManager).toString();
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

    public static int getAdjustedKeyCode(KeyEvent keyEvent) {
        int keyCode = keyEvent.getKeyCode();
        if (keyCode == KEYCODE_MEDIA_PLAY || keyCode == KEYCODE_MEDIA_PAUSE) {
            keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
        }
        return keyCode;
    }

    /**
     * Whether we have to go through AudioManager's register media button
     * receiver where this is only a single media button receiver. See ticket
     * #10.
     * 
     * @return
     */
    public static boolean isHandlingThroughSoleReceiver() {

        return android.os.Build.VERSION.SDK_INT >= ICS_API_LEVEL;
    }
}
