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
package com.harleensahni.android.mbr.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.harleensahni.android.mbr.Constants;
import com.harleensahni.android.mbr.MediaButtonMonitorService;
import com.harleensahni.android.mbr.Utils;

public class MediaButtonRouterBootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Utils.isHandlingThroughSoleReceiver()) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            if (preferences.getBoolean(Constants.ENABLED_PREF_KEY, true)) {
                Log.d(Constants.TAG, "Starting media button monitor service through boot listener");
                Intent serviceIntent = new Intent(context, MediaButtonMonitorService.class);
                context.startService(serviceIntent);
            }
        }
    }

}
