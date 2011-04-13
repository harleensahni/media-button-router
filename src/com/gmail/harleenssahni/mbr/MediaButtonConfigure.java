package com.gmail.harleenssahni.mbr;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class MediaButtonConfigure extends PreferenceActivity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Utils.showIntroifNeccessary(this);
    }
}