package com.gmail.harleenssahni.mbr;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

import com.gmail.harleenssahni.mbr.*;

public class MediaButtonConfigure extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        Button button = (Button) findViewById(R.id.viewMediaButton);
        button.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Constants.INTENT_ACTION_VIEW_MEDIA_BUTTON_LIST);
                startActivity(intent);
            }
        });
    }
}