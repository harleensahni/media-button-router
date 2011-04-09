package com.gmail.harleenssahni.mbr;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;

public final class Utils {

    private static final String TAG = "MediaButtonRouter.Utils";

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
}
