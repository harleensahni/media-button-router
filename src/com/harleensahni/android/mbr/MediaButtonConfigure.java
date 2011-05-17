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

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.speech.tts.TextToSpeech;

/**
 * Settings activity for Media Button Router. This is the activity that the user
 * will launch if they pick our app from their app launcher.
 * 
 * @author Harleen Sahni
 */
public class MediaButtonConfigure extends PreferenceActivity {

    private static final int TEXT_TO_SPEECH_CHECK_CODE = 123;

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        Eula.show(this);
        Utils.showIntroifNeccessary(this);

        Intent checkIntent = new Intent();
        checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        startActivityForResult(checkIntent, TEXT_TO_SPEECH_CHECK_CODE);

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