package com.gmail.harleenssahni.mbr;

import java.util.List;

import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.gmail.harleenssahni.mbr.receivers.MediaButtonReceiver;

public class MediaButtonList extends ListActivity implements OnInitListener {
    private static final String TAG = "com.gmail.harleenssahni.mbr.selector";
    private AudioManager audioManager;
    private KeyEvent trappedKeyEvent;
    private List<ResolveInfo> receivers;
    private ComponentName mediaButtonComponentName;
    private IntentFilter uiIntentFilter;
    private TextToSpeech textToSpeech;
    private int btButtonSelection;
    private PowerManager powerManager;

    /**
     * Used to wake up screen so that we can navigate our UI and select an app
     * to handle media button presses when display is off.
     */
    private WakeLock wakeLock;

    private BroadcastReceiver uiMediaReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {

            if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
                KeyEvent navigationKeyEvent = (KeyEvent) intent.getExtras().get(Intent.EXTRA_KEY_EVENT);
                int keyCode = navigationKeyEvent.getKeyCode();
                if (Utils.isMediaButton(keyCode)) {
                    Log.i(TAG, "Media Button Selector UI is directly handling key: " + navigationKeyEvent);
                    if (navigationKeyEvent.getAction() == KeyEvent.ACTION_UP) {
                        switch (keyCode) {
                        case KeyEvent.KEYCODE_MEDIA_NEXT:
                            moveSelectionForward();
                            break;
                        case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                            moveSelectionBack();
                            break;
                        case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                            select();
                            break;
                        default:
                            break;
                        }
                    }
                    abortBroadcast();
                }

            }

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        this.setContentView(R.layout.media_button_list);
        uiIntentFilter = new IntentFilter(Intent.ACTION_MEDIA_BUTTON);
        uiIntentFilter.setPriority(Integer.MAX_VALUE);
        textToSpeech = new TextToSpeech(this, this);

        audioManager = ((AudioManager) getSystemService(Context.AUDIO_SERVICE));
        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);

        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        receivers = getPackageManager().queryBroadcastReceivers(mediaButtonIntent,
                PackageManager.GET_INTENT_FILTERS | PackageManager.GET_RESOLVED_FILTER);

        // Remove our app from the list so users can't select it.
        if (receivers != null) {
            for (int i = 0; i < receivers.size(); i++) {
                if (MediaButtonReceiver.class.getName().equals(receivers.get(i).activityInfo.name)) {
                    receivers.remove(i);
                    break;
                }
            }
        }
        // TODO sort receivers by MRU so user doesn't have to skip as many apps,
        // right now apps are sorted by priority (not set by the user, set by
        // the app authors.. )

        setListAdapter(new BaseAdapter() {

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {

                TextView textView = new TextView(MediaButtonList.this);
                ResolveInfo resolveInfo = receivers.get(position);
                // TODO where should I re-write priorities for other app's

                textView.setText(resolveInfo.activityInfo.applicationInfo.loadLabel(getPackageManager()));

                return textView;
            }

            @Override
            public long getItemId(int position) {
                return 0;
            }

            @Override
            public Object getItem(int position) {
                return receivers.get(position);
            }

            @Override
            public int getCount() {
                return receivers.size();
            }
        });
        Log.i(TAG, "Media button selector created.");
    }

    @Override
    protected void onResume() {
        super.onResume();

        // TODO Add timeout so this activity finishes after x seconds of no user
        // interaction without
        // forwarding selection.

        // TODO Clean this up, figure out which things need to be set on the
        // list view and which don't.
        if (getIntent().getExtras() != null && getIntent().getExtras().get(Intent.EXTRA_KEY_EVENT) != null) {
            trappedKeyEvent = (KeyEvent) getIntent().getExtras().get(Intent.EXTRA_KEY_EVENT);

            Log.i(TAG, "Media button selector handling event: " + trappedKeyEvent + " from intent:" + getIntent());

            getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            getListView().setClickable(true);
            getListView().setFocusable(true);
            getListView().setFocusableInTouchMode(true);

        } else {
            Log.i(TAG, "Media button selector launched without key event, started with intent: " + getIntent());

            trappedKeyEvent = null;
            getListView().setClickable(false);
            getListView().setChoiceMode(ListView.CHOICE_MODE_NONE);
            getListView().setFocusable(false);
            getListView().setFocusableInTouchMode(false);

        }
        Log.d(TAG, "Registered UI receiver");
        registerReceiver(uiMediaReceiver, uiIntentFilter);

        btButtonSelection = -1;

        // power on device's screen so we can interact with it, otherwise on
        // pause gets called immediately.
        // alternative would be to change all of the selection logic to happen
        // in a service?? don't know if that process life cycle would fit well
        // -- look into
        wakeLock = powerManager
                .newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.SCREEN_DIM_WAKE_LOCK, TAG);
        wakeLock.setReferenceCounted(false); // We may release before the
                                             // timeout
        wakeLock.acquire(5 * 1000);
    }

    // @Override
    // protected void onPostResume() {
    // super.onPostResume();
    // try{
    // if (getListView().getSelectedItemPosition() ==
    // AdapterView.INVALID_POSITION) {
    // moveSelectionForward();
    // }
    // }
    // catch (ArrayIndexOutOfBoundsException ae){
    // Log.e(TAG, "Array out of bounds error on resume");
    // }
    // }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "unegistered UI receiver");
        unregisterReceiver(uiMediaReceiver);
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        textToSpeech.shutdown();

        Log.i(TAG, "Media button selector destroyed.");
    }

    /**
     * Moves selection forward in the list. If we're already at the last item,
     * wraps to the first item.
     */
    private void moveSelectionForward() {
        // FIXME handle no media receivers somewhere

        // try 1
        // int position = -1;
        // if (AdapterView.INVALID_POSITION != getListView()
        // .getSelectedItemPosition()) {
        // position = getListView().getSelectedItemPosition();
        // }
        //
        // position++;
        //
        // // wrap if past last item
        // if (position >= getListView().getCount()) {
        // position = 0;
        // }

        // try 2
        // if (getListView().getSelectedItemPosition() ==
        // getListView().getCount() - 1) {
        // getListView().setSelection(0);
        // } else {
        // getListView().onKeyDown(KeyEvent.KEYCODE_DPAD_DOWN,
        // new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN));
        // }

        // textToSpeech.speak(receivers.get(getListView().getSelectedItemPosition()).activityInfo.applicationInfo
        // .loadLabel(getPackageManager()).toString(), TextToSpeech.QUEUE_FLUSH,
        // null);

        btButtonSelection++;

        if (btButtonSelection >= receivers.size()) {
            // wrap
            btButtonSelection = 0;
        }
        // todo scroll to item, highlight it
        textToSpeech.speak(receivers.get(btButtonSelection).activityInfo.applicationInfo.loadLabel(getPackageManager())
                .toString(), TextToSpeech.QUEUE_FLUSH, null);

    }

    /**
     * Moves selection back in the list. If we're already at the first item,
     * wraps to the last item.
     */
    private void moveSelectionBack() {
        // FIXME handle no media receivers somewhere
        // try 1
        // int position = 0;
        // if (AdapterView.INVALID_POSITION != getListView()
        // .getSelectedItemPosition()) {
        // position = getListView().getSelectedItemPosition();
        // }
        // position--;
        //
        // // wrap if past last item
        // if (position < 0) {
        // position = getListView().getCount() - 1;
        // }
        // FIXME this doesn't make it wrap. is there a way to do it?
        // try 2
        // if (getListView().getSelectedItemPosition() == 0) {
        // getListView().setSelection(getListView().getCount() - 1);
        // } else {
        // getListView().onKeyDown(KeyEvent.KEYCODE_DPAD_UP,
        // new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP));
        // }
        // textToSpeech.speak(receivers.get(getListView().getSelectedItemPosition()).activityInfo.applicationInfo
        // .loadLabel(getPackageManager()).toString(), TextToSpeech.QUEUE_FLUSH,
        // null);

        btButtonSelection--;
        if (btButtonSelection < 0) {
            // wrap
            btButtonSelection = receivers.size() - 1;
        }
        textToSpeech.speak(receivers.get(btButtonSelection).activityInfo.applicationInfo.loadLabel(getPackageManager())
                .toString(), TextToSpeech.QUEUE_FLUSH, null);

    }

    public void select() {
        // TODO if there is no selection, we should either forward to whoever
        // would have handled if we didn't exist, or to mru
        // forwardToMediaReceiver(getListView().getSelectedItemPosition() !=
        // AdapterView.INVALID_POSITION ? getListView()
        // .getSelectedItemPosition() : 0);

        if (btButtonSelection == -1) {
            finish();
        } else {
            forwardToMediaReceiver(btButtonSelection);
        }

    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        forwardToMediaReceiver(position);
    }

    private void forwardToMediaReceiver(int position) {
        ResolveInfo resolveInfo = receivers.get(position);
        if (resolveInfo != null) {
            if (trappedKeyEvent != null) {
                // We will send two intents, one for button down, and one for
                // button up.
                ComponentName selectedReceiver = new ComponentName(resolveInfo.activityInfo.packageName,
                        resolveInfo.activityInfo.name);
                Utils.forwardKeyCodeToComponent(this, selectedReceiver, true, trappedKeyEvent.getKeyCode(),
                        new SweepBroadcastReceiver(selectedReceiver.toString()));

                finish();

            }
        }
    }

    @Override
    public void onInit(int status) {
        // text to speech initialized
        // XXX This is where we announce to the user what we're handling. It's
        // not clear that this will always get called. I don't know how else to
        // query if the text to speech is started though.

        if (trappedKeyEvent != null) {
            String actionText = "";
            switch (trappedKeyEvent.getKeyCode()) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                // This is just play even though the keycode is both play/pause,
                // the app shouldn't handle
                // pause if music is already playing, it should go to whoever is
                // playing the music.
                actionText = "Playing";
                break;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                actionText = "Going to Next";
                break;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                actionText = "Going to Previous";
                break;

            }
            textToSpeech.speak("Select app to use for " + actionText, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    private class SweepBroadcastReceiver extends BroadcastReceiver {
        String name;

        public SweepBroadcastReceiver(String name) {
            this.name = name;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "After running broadcast receiver " + name + "have resultcode: " + getResultCode()
                    + " result Data: " + getResultData());
        }
    }

}
