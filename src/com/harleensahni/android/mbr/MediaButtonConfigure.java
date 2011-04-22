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

import android.os.Bundle;
import android.preference.PreferenceActivity;

/**
 * Settings activity for Media Button Router. This is the activity that the user
 * will launch if they pick our app from their app launcher.
 * 
 * @author Harleen Sahni
 */
public class MediaButtonConfigure extends PreferenceActivity {

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        Eula.show(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onResume() {
        super.onResume();
        Utils.showIntroifNeccessary(this);
    }
}