package com.gmail.harleenssahni.mbr.receivers;

import java.util.List;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;

import com.gmail.harleenssahni.mbr.Constants;
import com.gmail.harleenssahni.mbr.MediaButtonList;
import com.gmail.harleenssahni.mbr.MediaButtonListLocked;
import com.gmail.harleenssahni.mbr.Utils;

public class MediaButtonReceiver extends BroadcastReceiver {
    private static final String TAG = "MediaButtonRouter.Receiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!PreferenceManager.getDefaultSharedPreferences(context).getBoolean(Constants.ENABLED_PREF_KEY, true)) {
            return;
        }

        // Sometimes we take too long finish and Android kills
        // us and forwards the intent to another broadcast receiver. If we're
        // forwarding to another receiver,
        // we can take as long as the time it took to determine the right
        // receiver and then forward it two different intents for button down /
        // up. This can cause some weird behavior where we actually do handle
        // the event but get then someone else gets to too.
        // May make this a preference for
        // "aggressive"
        // XXX
        // / abortBroadcast();

        // TODO Handle the case where there is only 0 or 1 media receivers
        // besides ourself by disabling our media receiver
        if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
            Log.i(TAG, "MediaButtonReceiver received media button intent: " + intent);

            KeyEvent keyEvent = (KeyEvent) intent.getExtras().get(Intent.EXTRA_KEY_EVENT);
            int keyCode = keyEvent.getKeyCode();

            // Don't want to capture volume buttons
            if (Utils.isMediaButton(keyCode)) {
                Log.i(TAG, "MediaButtonReceiver handling legitimate media key event: " + keyEvent);

                AudioManager audioManager = ((AudioManager) context.getSystemService(Context.AUDIO_SERVICE));

                if (audioManager.isMusicActive()) {
                    // XXX Need to improve this behavior, somethings doesn't
                    // work. For instance, if you select "Listen" App, and then
                    // hit next,
                    // the built in music app handles it because it has a higher
                    // priority. If we could change priorities on app selection
                    // and have it stick,
                    // would probably be good enough to handle this.
                    // One thing to do would be to add specific classes that
                    // check for each knowhn app if our generic way doesn't work
                    // well for them
                    Log.d(TAG, "MediaButtonReceiver may pass on event because music is already playing: " + keyEvent);

                    // Try to best guess who is playing the music based off of
                    // running foreground services.
                    ActivityManager activityManager = ((ActivityManager) context
                            .getSystemService(Context.ACTIVITY_SERVICE));

                    // XXX Move stuff like receivers to service so we can cache
                    // it. Doing too much stuff here
                    List<RunningServiceInfo> runningServices = activityManager.getRunningServices(Integer.MAX_VALUE);

                    List<ResolveInfo> receivers = Utils.getMediaReceivers(context.getPackageManager());

                    // Remove our app from the list so users can't select it.
                    if (receivers != null) {
                        boolean matched = false;
                        for (ResolveInfo resolveInfo : receivers) {
                            if (MediaButtonReceiver.class.getName().equals(resolveInfo.activityInfo.name)) {
                                continue;
                            }
                            // Find any service that's package matches that of a
                            // receivers.
                            for (RunningServiceInfo runningService : runningServices) {
                                if (runningService.foreground
                                        && runningService.started
                                        && resolveInfo.activityInfo.packageName.equals(runningService.service
                                                .getPackageName())) {
                                    if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
                                        Utils.forwardKeyCodeToComponent(context, new ComponentName(
                                                resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name),
                                                false, keyCode, null);
                                    }
                                    abortBroadcast();
                                    matched = true;
                                    Log.i(TAG,
                                            "MediaButtonReceiver passed on event because music is playing and service in same package as media key receiver was found to be active: "
                                                    + keyEvent);
                                    break;
                                }
                            }
                            if (matched) {
                                break;
                            }

                        }
                        if (!matched) {
                            Log.i(TAG, "No Receivers found playing music.");
                        }
                    }

                    return;
                }

                abortBroadcast();

                if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
                    // Figure out if keyguard is active
                    KeyguardManager manager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
                    boolean locked = manager.inKeyguardRestrictedInputMode();

                    Intent showForwardView = new Intent(Constants.INTENT_ACTION_VIEW_MEDIA_BUTTON_LIST);
                    showForwardView.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    showForwardView.putExtras(intent);
                    showForwardView.setClassName(context, locked ? MediaButtonListLocked.class.getName()
                            : MediaButtonList.class.getName());

                    Log.i(TAG, "MediaButtonReceiver starting selector activity for keyevent: " + keyEvent);

                    if (locked) {

                        // XXX See if this actually makes a difference, might
                        // not be needed if we move more things to onCreate?
                        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                        // acquire temp wake lock
                        WakeLock wakeLock = powerManager.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP
                                | PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, TAG);
                        wakeLock.setReferenceCounted(false);

                        // Our app better display within 3 seconds or we have
                        // bigger issues.
                        wakeLock.acquire(3000);

                    }
                    context.startActivity(showForwardView);

                }
            }

        }
    }
}
