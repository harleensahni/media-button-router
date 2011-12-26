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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;

import com.harleensahni.android.mbr.receivers.MediaButtonReceiver;

/**
 * Settings activity for Media Button Router. This is the activity that the user
 * will launch if they pick our app from their app launcher.
 * 
 * @author Harleen Sahni
 */
public class MediaButtonConfigure extends PreferenceActivity implements OnSharedPreferenceChangeListener {

    private static final int TEXT_TO_SPEECH_CHECK_CODE = 123;
    private SharedPreferences preferences;

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        // Add preferences for hiding apps
        PreferenceCategory visibleAppsCategory = new PreferenceCategory(this);
        visibleAppsCategory.setTitle(R.string.visible_apps_header);
        getPreferenceScreen().addPreference(visibleAppsCategory);
        final List<CheckBoxPreference> showAppCheckBoxPreferences = new ArrayList<CheckBoxPreference>();
        OnPreferenceChangeListener showPreferenceChangeListener = new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                StringBuilder hiddenApps = new StringBuilder();
                boolean first = true;

                for (CheckBoxPreference checkBoxPreference : showAppCheckBoxPreferences) {
                    if ((preference == checkBoxPreference && newValue == Boolean.FALSE)
                            || ((preference != checkBoxPreference && !checkBoxPreference.isChecked()))) {
                        if (first) {
                            first = false;
                        } else {
                            hiddenApps.append(",");
                        }
                        hiddenApps.append(checkBoxPreference.getKey());
                    }
                }

                PreferenceManager.getDefaultSharedPreferences(MediaButtonConfigure.this).edit()
                        .putString(Constants.HIDDEN_APPS_KEY, hiddenApps.toString()).commit();

                return true;
            }
        };

        String hiddenReceiverIdsString = PreferenceManager.getDefaultSharedPreferences(this).getString(
                Constants.HIDDEN_APPS_KEY, "");
        List<String> hiddenIds = Arrays.asList(hiddenReceiverIdsString.split(","));

        List<ResolveInfo> mediaReceivers = Utils.getMediaReceivers(getPackageManager(), false, null);
        for (ResolveInfo mediaReceiver : mediaReceivers) {
            if (MediaButtonReceiver.class.getName().equals(mediaReceiver.activityInfo.name)) {
                continue;
            }
            CheckBoxPreference showReceiverPreference = new CheckBoxPreference(this);
            showReceiverPreference.setTitle(Utils.getAppName(mediaReceiver, getPackageManager()));
            showReceiverPreference.setPersistent(false);
            showReceiverPreference.setKey(mediaReceiver.activityInfo.applicationInfo.sourceDir
                    + mediaReceiver.activityInfo.name);
            showReceiverPreference.setChecked(!hiddenIds.contains(showReceiverPreference.getKey()));
            showReceiverPreference.setOnPreferenceChangeListener(showPreferenceChangeListener);
            visibleAppsCategory.addPreference(showReceiverPreference);
            showAppCheckBoxPreferences.add(showReceiverPreference);
        }

        Eula.show(this);
        Utils.showIntroifNeccessary(this);

        Intent checkIntent = new Intent();
        checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        startActivityForResult(checkIntent, TEXT_TO_SPEECH_CHECK_CODE);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Starts the media monitor service. Most of the time it should be
        // started on boot, but that's not true if the app has just been
        // installed.
        // TODO check if enabled
        // TODO add listener to enable preference to start stop service

        if (Utils.isHandlingThroughSoleReceiver()) {
            if (Utils.isHandlingThroughSoleReceiver() && preferences.getBoolean(Constants.ENABLED_PREF_KEY, true)) {
                Intent intent = new Intent(this, MediaButtonMonitorService.class);
                startService(intent);
            }

        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Utils.isHandlingThroughSoleReceiver()) {
            preferences.registerOnSharedPreferenceChangeListener(this);
        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        if (Utils.isHandlingThroughSoleReceiver()) {
            preferences.unregisterOnSharedPreferenceChangeListener(this);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (Constants.ENABLED_PREF_KEY.equals(key)) {
            Intent intent = new Intent(MediaButtonConfigure.this, MediaButtonMonitorService.class);
            if (sharedPreferences.getBoolean(Constants.ENABLED_PREF_KEY, true)) {
                startService(intent);
            } else {
                stopService(intent);
            }

        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == TEXT_TO_SPEECH_CHECK_CODE) {
            Preference ttsWarningPreference = findPreference("tts_warning");

            if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                ttsWarningPreference.setEnabled(false);
            } else {
                ttsWarningPreference.setEnabled(true);
                ttsWarningPreference.setOnPreferenceClickListener(new OnPreferenceClickListener() {

                    public boolean onPreferenceClick(Preference preference) {
                        Intent installIntent = new Intent();
                        installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                        startActivity(installIntent);
                        return true;
                    }
                });
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }
}