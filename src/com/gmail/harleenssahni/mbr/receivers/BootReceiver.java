package com.gmail.harleenssahni.mbr.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // ((AudioManager) context.getSystemService(Context.AUDIO_SERVICE))
        // .registerMediaButtonEventReceiver(new ComponentName(context,
        // MediaButtonReceiver.class));
    }

}
