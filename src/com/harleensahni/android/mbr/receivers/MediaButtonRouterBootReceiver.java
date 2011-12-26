package com.harleensahni.android.mbr.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.harleensahni.android.mbr.Constants;
import com.harleensahni.android.mbr.MediaButtonMonitorService;

public class MediaButtonRouterBootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (android.os.Build.VERSION.SDK_INT >= 14) {

            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            if (preferences.getBoolean(Constants.ENABLED_PREF_KEY, true)) {
                Intent serviceIntent = new Intent(context, MediaButtonMonitorService.class);
                context.startService(serviceIntent);
            }
        }
    }

}
