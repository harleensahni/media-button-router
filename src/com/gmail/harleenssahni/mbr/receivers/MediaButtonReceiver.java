package com.gmail.harleenssahni.mbr.receivers;

import java.util.List;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.util.Log;
import android.view.KeyEvent;

import com.gmail.harleenssahni.mbr.Constants;
import com.gmail.harleenssahni.mbr.Utils;

public class MediaButtonReceiver extends BroadcastReceiver {
    private static final String TAG = "com.gmail.harleenssahni.mbr.receiver";

    @Override
    public void onReceive(Context context, Intent intent) {

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
                    Log.d(TAG, "MediaButtonReceiver may pass on event because music is already playing: " + keyEvent);

                    // Try to best guess who is playing the music based off of
                    // running foreground services.
                    ActivityManager activityManager = ((ActivityManager) context
                            .getSystemService(Context.ACTIVITY_SERVICE));

                    // XXX Move stuff like receivers to service so we can cache
                    // it. Doing too much stuff here
                    List<RunningServiceInfo> runningServices = activityManager.getRunningServices(Integer.MAX_VALUE);

                    Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
                    List<ResolveInfo> receivers = context.getPackageManager().queryBroadcastReceivers(
                            mediaButtonIntent, PackageManager.GET_INTENT_FILTERS | PackageManager.GET_RESOLVED_FILTER);

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
                    }

                    return;
                }

                abortBroadcast();

                if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
                    Intent showForwardView = new Intent(Constants.INTENT_ACTION_VIEW_MEDIA_BUTTON_LIST);
                    showForwardView.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    showForwardView.putExtras(intent);
                    Log.i(TAG, "MediaButtonReceiver starting selector activity for keyevent: " + keyEvent);

                    context.startActivity(showForwardView);

                }
            }

        }
    }
}
