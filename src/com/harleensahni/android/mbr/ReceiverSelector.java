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

import static com.harleensahni.android.mbr.Constants.TAG;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.harleensahni.android.mbr.receivers.MediaButtonReceiver;

/**
 * Allows the user to choose which media receiver will handle a media button
 * press. Can be navigated via touch screen or media button keys. Provides voice
 * feedback.
 * 
 * @author Harleen Sahni
 */
public class ReceiverSelector extends ListActivity implements OnInitListener, AudioManager.OnAudioFocusChangeListener {

    private class SweepBroadcastReceiver extends BroadcastReceiver {
        String name;

        public SweepBroadcastReceiver(String name) {
            this.name = name;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Media Button Selector: After running broadcast receiver " + name + "have resultcode: "
                    + getResultCode() + " result Data: " + getResultData());
        }
    }

    /**
     * Key used to store and retrieve last selected receiver.
     */
    private static final String SELECTION_KEY = "btButtonSelection";

    /**
     * Key used to store and retrieve last selected receiver that actually was
     * forwarded a media button by the user.
     */
    private static final String SELECTION_ACTED_KEY = "btButtonSelectionActed";

    /**
     * Number of seconds to wait before timing out and just cancelling.
     */
    private int timeoutTime;

    /**
     * The media button event that {@link MediaButtonReceiver} captured, and
     * that we will be forwarding to a music player's {@code BroadcastReceiver}
     * on selection.
     */
    private KeyEvent trappedKeyEvent;

    /**
     * The {@code BroadcastReceiver}'s registered in the system for *
     * {@link Intent.ACTION_MEDIA_BUTTON}.
     */
    private List<ResolveInfo> receivers;

    /** The intent filter for registering our local {@code BroadcastReceiver}. */
    private IntentFilter uiIntentFilter;

    /** The text to speech engine used to announce navigation to the user. */
    private TextToSpeech textToSpeech;

    /**
     * The receiver currently selected by bluetooth next/prev navigation. We
     * track this ourselves because there isn't persisted selection w/ touch
     * screen interfaces.
     */
    private int btButtonSelection;

    /**
     * Whether we've done the start up announcement to the user using the text
     * to speech. Tracked so we don't repeat ourselves on orientation change.
     */
    private boolean announced;

    /**
     * The power manager used to wake the device with a wake lock so that we can
     * handle input. Allows us to have a regular activity life cycle when media
     * buttons are pressed when and the screen is off.
     */
    private PowerManager powerManager;

    /**
     * Used to wake up screen so that we can navigate our UI and select an app
     * to handle media button presses when display is off.
     */
    private WakeLock wakeLock;

    /**
     * Whether we've requested audio focus.
     */
    private boolean audioFocus;

    /**
     * ScheduledExecutorService used to time out and close activity if the user
     * doesn't make a selection within certain amount of time. Resets on user
     * interaction.
     */
    private ScheduledExecutorService timeoutExecutor;

    /**
     * ScheduledFuture of timeout.
     */
    private ScheduledFuture<?> timeoutScheduledFuture;

    /** The cancel button. */
    private View cancelButton;

    private ImageView mediaImage;

    /** The header */
    private TextView header;

    /** The intro dialog. May be null if no dialog is being shown. */
    private AlertDialog introDialog;

    private boolean eulaAcceptedAlready;

    /**
     * Local broadcast receiver that allows us to handle media button events for
     * navigation inside the activity.
     */
    private BroadcastReceiver uiMediaReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {

            if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
                KeyEvent navigationKeyEvent = (KeyEvent) intent.getExtras().get(Intent.EXTRA_KEY_EVENT);
                int keyCode = navigationKeyEvent.getKeyCode();
                if (Utils.isMediaButton(keyCode)) {
                    Log.i(TAG, "Media Button Selector: UI is directly handling key: " + navigationKeyEvent);
                    if (navigationKeyEvent.getAction() == KeyEvent.ACTION_UP) {
                        switch (keyCode) {
                        case KeyEvent.KEYCODE_MEDIA_NEXT:
                            moveSelection(1);
                            break;
                        case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                            moveSelection(-1);
                            break;
                        case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                            select();
                            break;
                        case KeyEvent.KEYCODE_MEDIA_STOP:
                            // just cancel
                            finish();
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

    /** Used to figure out if music is playing and handle audio focus. */
    private AudioManager audioManager;

    /** Preferences. */
    private SharedPreferences preferences;

    /**
     * {@inheritDoc}
     */
    @Override
    public void onInit(int status) {
        // text to speech initialized
        // XXX This is where we announce to the user what we're handling. It's
        // not clear that this will always get called. I don't know how else to
        // query if the text to speech is started though.

        // Only announce if we haven't before
        if (!announced && trappedKeyEvent != null) {
            requestAudioFocus();

            String actionText = "";
            switch (Utils.getAdjustedKeyCode(trappedKeyEvent)) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                // This is just play even though the keycode is both play/pause,
                // the app shouldn't handle
                // pause if music is already playing, it should go to whoever is
                // playing the music.
                actionText = getString(audioManager.isMusicActive() ? R.string.pause_play_speak_text
                        : R.string.play_speak_text);
                break;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                actionText = getString(R.string.next_speak_text);
                break;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                actionText = getString(R.string.previous_speak_text);
                break;
            case KeyEvent.KEYCODE_MEDIA_STOP:
                actionText = getString(R.string.stop_speak_text);
                break;

            }
            String textToSpeak = null;
            if (btButtonSelection >= 0 && btButtonSelection < receivers.size()) {
                textToSpeak = String.format(getString(R.string.application_announce_speak_text), actionText,
                        Utils.getAppName(receivers.get(btButtonSelection), getPackageManager()));
            } else {
                textToSpeak = String.format(getString(R.string.announce_speak_text), actionText);
            }
            textToSpeech.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null);
            announced = true;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "Media Button Selector: On Create Called");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        setContentView(R.layout.media_button_list);

        // Show eula
        eulaAcceptedAlready = Eula.show(this);

        uiIntentFilter = new IntentFilter(Intent.ACTION_MEDIA_BUTTON);
        uiIntentFilter.setPriority(Integer.MAX_VALUE);

        // TODO Handle text engine not installed, etc. Documented on android
        // developer guide
        textToSpeech = new TextToSpeech(this, this);

        audioManager = (AudioManager) this.getSystemService(AUDIO_SERVICE);
        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        // XXX can't use integer array, argh:
        // http://code.google.com/p/android/issues/detail?id=2096
        timeoutTime = Integer.valueOf(preferences.getString(Constants.TIMEOUT_KEY, "5"));

        btButtonSelection = preferences.getInt(SELECTION_KEY, -1);

        receivers = Utils.getMediaReceivers(getPackageManager(), true, getApplicationContext());

        Boolean lastAnnounced = (Boolean) getLastNonConfigurationInstance();
        if (lastAnnounced != null) {
            announced = lastAnnounced;
        }

        // Remove our app's receiver from the list so users can't select it.
        // NOTE: Our local receiver isn't registered at this point so we don't
        // have to remove it.
        if (receivers != null) {
            for (int i = 0; i < receivers.size(); i++) {
                if (MediaButtonReceiver.class.getName().equals(receivers.get(i).activityInfo.name)) {
                    receivers.remove(i);
                    break;
                }
            }
        }
        // TODO MAYBE sort receivers by MRU so user doesn't have to skip as many
        // apps,
        // right now apps are sorted by priority (not set by the user, set by
        // the app authors.. )
        setListAdapter(new BaseAdapter() {

            @Override
            public int getCount() {
                return receivers.size();
            }

            @Override
            public Object getItem(int position) {
                return receivers.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {

                View view = convertView;
                if (view == null) {
                    LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = vi.inflate(R.layout.media_receiver_view, null);
                }

                ResolveInfo resolveInfo = receivers.get(position);

                ImageView imageView = (ImageView) view.findViewById(R.id.receiverAppImage);
                imageView.setImageDrawable(resolveInfo.loadIcon(getPackageManager()));

                TextView textView = (TextView) view.findViewById(R.id.receiverAppName);
                textView.setText(Utils.getAppName(resolveInfo, getPackageManager()));
                return view;

            }
        });
        header = (TextView) findViewById(R.id.dialogHeader);
        cancelButton = findViewById(R.id.cancelButton);
        cancelButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                finish();
            }
        });
        mediaImage = (ImageView) findViewById(R.id.mediaImage);

        Log.i(TAG, "Media Button Selector: created.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        textToSpeech.shutdown();

        Log.d(TAG, "Media Button Selector: destroyed.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        btButtonSelection = position;
        forwardToMediaReceiver(position);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "Media Button Selector: onPause");
        Log.d(TAG, "Media Button Selector: unegistered UI receiver");
        unregisterReceiver(uiMediaReceiver);
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
        textToSpeech.stop();
        timeoutExecutor.shutdownNow();
        audioManager.abandonAudioFocus(this);
        preferences.edit().putInt(SELECTION_KEY, btButtonSelection).commit();
    }

    @Override
    protected void onStart() {

        super.onStart();
        Log.d(TAG, "Media Button Selector: On Start called");

        // TODO Originally thought most work should happen onResume and onPause.
        // I don't know if the onResume part is
        // right since you can't actually ever get back to this view, single
        // instance, and not shown in recents. Maybe it's possible if ANOTHER
        // dialog opens in front of ours?
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "Media Button Selector: onResume");

        // Check to see if intro has been displayed before
        if (introDialog == null || !introDialog.isShowing()) {
            introDialog = Utils.showIntroifNeccessary(this);
        }
        requestAudioFocus();
        // TODO Clean this up, figure out which things need to be set on the
        // list view and which don't.
        if (getIntent().getExtras() != null && getIntent().getExtras().get(Intent.EXTRA_KEY_EVENT) != null) {
            trappedKeyEvent = (KeyEvent) getIntent().getExtras().get(Intent.EXTRA_KEY_EVENT);

            Log.i(TAG, "Media Button Selector: handling event: " + trappedKeyEvent + " from intent:" + getIntent());

            getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            getListView().setClickable(true);
            getListView().setFocusable(true);
            getListView().setFocusableInTouchMode(true);

            String action = "";
            switch (trappedKeyEvent.getKeyCode()) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                action = getString(audioManager.isMusicActive() ? R.string.pausePlay : R.string.play);
                break;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                action = getString(R.string.next);
                break;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                action = getString(R.string.prev);
                break;
            case KeyEvent.KEYCODE_MEDIA_STOP:
                action = getString(R.string.stop);
                break;
            }
            header.setText(String.format(getString(R.string.dialog_header_with_action), action));
            if (btButtonSelection >= 0 && btButtonSelection < receivers.size()) {
                // scroll to last selected item
                getListView().setSelection(btButtonSelection);
            }
        } else {
            Log.i(TAG, "Media Button Selector: launched without key event, started with intent: " + getIntent());

            trappedKeyEvent = null;
            getListView().setClickable(false);
            getListView().setChoiceMode(ListView.CHOICE_MODE_NONE);
            getListView().setFocusable(false);
            getListView().setFocusableInTouchMode(false);

        }
        Log.d(TAG, "Media Button Selector: Registered UI receiver");
        registerReceiver(uiMediaReceiver, uiIntentFilter);

        // power on device's screen so we can interact with it, otherwise on
        // pause gets called immediately.
        // alternative would be to change all of the selection logic to happen
        // in a service?? don't know if that process life cycle would fit well
        // -- look into
        // added On after release so screen stays on a little longer instead of
        // immediately to try and stop resume pause cycle that sometimes
        // happens.
        wakeLock = powerManager.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.SCREEN_DIM_WAKE_LOCK
                | PowerManager.ON_AFTER_RELEASE, TAG);
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire();
        timeoutExecutor = Executors.newSingleThreadScheduledExecutor();
        if (introDialog == null && eulaAcceptedAlready) {
            // Don't time out in the middle of showing the dialog, that's rude.
            // We could reset timeout here, but this is the first time the user
            // is seeing the selection screen, so just let it stay till they
            // dismiss.
            resetTimeout();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object onRetainNonConfigurationInstance() {
        return announced;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // We're not supposed to show a menu since we show as a dialog,
        // according to google's ui guidelines. No other sane place to put this,
        // except maybe
        // a small configure button in the dialog header, but don't want users
        // to hit it by accident when selecting music app.
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.selector_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_settings) {
            startActivity(new Intent(this, MediaButtonConfigure.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Resets the timeout before the application is automatically dismissed.
     */
    private void resetTimeout() {
        if (timeoutScheduledFuture != null) {
            timeoutScheduledFuture.cancel(false);
        }

        timeoutScheduledFuture = timeoutExecutor.schedule(new Runnable() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        onTimeout();
                    }
                });

            }
        }, timeoutTime, TimeUnit.SECONDS);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onUserInteraction() {
        super.onUserInteraction();

        // Reset timeout before we finish
        if (!timeoutExecutor.isShutdown()) {
            resetTimeout();
        }
    }

    /**
     * Forwards the {@code #trappedKeyEvent} to the receiver at specified
     * position.
     * 
     * @param position
     *            The index of the receiver to select. Must be in bounds.
     */
    private void forwardToMediaReceiver(int position) {
        ResolveInfo resolveInfo = receivers.get(position);
        if (resolveInfo != null) {
            if (trappedKeyEvent != null) {

                ComponentName selectedReceiver = new ComponentName(resolveInfo.activityInfo.packageName,
                        resolveInfo.activityInfo.name);
                Utils.forwardKeyCodeToComponent(this, selectedReceiver, true,
                        Utils.getAdjustedKeyCode(trappedKeyEvent),
                        new SweepBroadcastReceiver(selectedReceiver.toString()));
                // save the last acted on app in case we have no idea who is
                // playing music so we can make a guess
                preferences.edit().putString(SELECTION_ACTED_KEY, resolveInfo.activityInfo.name).commit();
                finish();
            }
        }
    }

    /**
     * Moves selection by the amount specified in the list. If we're already at
     * the last item and we're moving forward, wraps to the first item. If we're
     * already at the first item, and we're moving backwards, wraps to the last
     * item.
     * 
     * @param amount
     *            The amount to move, may be positive or negative.
     */
    private void moveSelection(int amount) {

        resetTimeout();
        btButtonSelection += amount;

        if (btButtonSelection >= receivers.size()) {
            // wrap
            btButtonSelection = 0;
        } else if (btButtonSelection < 0) {
            // wrap
            btButtonSelection = receivers.size() - 1;
        }

        // May not highlight, but will scroll to item
        getListView().setSelection(btButtonSelection);

        textToSpeech.speak(Utils.getAppName(receivers.get(btButtonSelection), getPackageManager()),
                TextToSpeech.QUEUE_FLUSH, null);

    }

    /**
     * Select the currently selected receiver.
     */
    private void select() {
        if (btButtonSelection == -1) {
            finish();
        } else {
            forwardToMediaReceiver(btButtonSelection);
        }

    }

    /**
     * Takes appropriate action to notify user and dismiss activity on timeout.
     */
    private void onTimeout() {
        Log.d(TAG, "Media Button Selector: Timed out waiting for user interaction, finishing activity");
        final MediaPlayer timeoutPlayer = MediaPlayer.create(this, R.raw.dismiss);
        timeoutPlayer.start();
        // not having an on error listener results in on completion listener
        // being called anyway
        timeoutPlayer.setOnCompletionListener(new OnCompletionListener() {

            public void onCompletion(MediaPlayer mp) {
                timeoutPlayer.release();
            }
        });

        // If the user has set their preference not to confirm actions, we'll
        // just forward automatically to whoever was last selected. If no one is
        // selected, it just acts like finish anyway.
        if (preferences.getBoolean(Constants.CONFIRM_ACTION_PREF_KEY, true)) {
            finish();
        } else {
            select();
        }
    }

    /**
     * Requests audio focus if necessary.
     */
    private void requestAudioFocus() {
        if (!audioFocus) {
            audioFocus = audioManager.requestAudioFocus(this, AudioManager.STREAM_NOTIFICATION,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        }
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        // TODO Auto-generated method stub

    }
}
