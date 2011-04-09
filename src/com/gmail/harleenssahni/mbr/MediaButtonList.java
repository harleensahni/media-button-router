package com.gmail.harleenssahni.mbr;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.gmail.harleenssahni.mbr.receivers.MediaButtonReceiver;

/**
 * Allows the user to choose which media receiver will handle a media button
 * press. Can be navigated via touch screen or media button keys. Provides voice
 * feedback.
 * 
 * @author harleenssahni@gmail.com
 */
public class MediaButtonList extends ListActivity implements OnInitListener {

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

    /** Our tag for logging purposes and identification purposes. */
    private static final String TAG = "MediaButtonRouter.Selector";

    /**
     * Key used to store and retrieve last selected receiver.
     */
    private static final String SELECTION_KEY = "btButtonSelection";

    /**
     * Number of seconds to wait before timing out and just cancelling.
     */
    private static final long TIMEOUT_TIME = 7;

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
                    Log.i(TAG, "Media Button Selector UI is directly handling key: " + navigationKeyEvent);
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

    private View cancelButton;

    private View disableButton;

    private ImageView mediaImage;

    private TextView header;

    /**
     * {@inheritDoc}
     */
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
            String textToSpeak = null;
            if (btButtonSelection > 0 && btButtonSelection < receivers.size()) {
                textToSpeak = "Select app to use for " + actionText + ", currently "
                        + getAppName(receivers.get(btButtonSelection));
            } else {
                textToSpeak = "Select app to use for " + actionText;
            }
            textToSpeech.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "On Create Called");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        setContentView(R.layout.media_button_list);

        uiIntentFilter = new IntentFilter(Intent.ACTION_MEDIA_BUTTON);
        uiIntentFilter.setPriority(Integer.MAX_VALUE);

        // TODO Handle text engine not installed, etc. Documented on android
        // developer guide
        textToSpeech = new TextToSpeech(this, this);

        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);

        // btButtonSelection = savedInstanceState != null ?
        // savedInstanceState.getInt(SELECTION_KEY, -1) : -1;
        btButtonSelection = getPreferences(MODE_PRIVATE).getInt(SELECTION_KEY, -1);

        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        receivers = getPackageManager().queryBroadcastReceivers(mediaButtonIntent,
                PackageManager.GET_INTENT_FILTERS | PackageManager.GET_RESOLVED_FILTER);

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
                return 0;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {

                View v = convertView;
                if (v == null) {
                    LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    v = vi.inflate(R.layout.media_receiver_view, null);
                }

                ResolveInfo resolveInfo = receivers.get(position);

                ImageView iv = (ImageView) v.findViewById(R.id.receiverAppImage);
                iv.setImageDrawable(resolveInfo.loadIcon(getPackageManager()));

                TextView textView = (TextView) v.findViewById(R.id.receiverAppName);
                textView.setText(getAppName(resolveInfo));
                return v;

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
        disableButton = findViewById(R.id.disableButton);
        mediaImage = (ImageView) findViewById(R.id.mediaImage);

        Log.i(TAG, "Media button selector created.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        textToSpeech.shutdown();

        Log.d(TAG, "Media button selector destroyed.");
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
        Log.d(TAG, "onPause");
        Log.d(TAG, "unegistered UI receiver");
        unregisterReceiver(uiMediaReceiver);
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
        textToSpeech.stop();
        timeoutExecutor.shutdownNow();
        getPreferences(MODE_PRIVATE).edit().putInt(SELECTION_KEY, btButtonSelection).commit();
    }

    @Override
    protected void onStart() {

        super.onStart();
        Log.d(TAG, "On Start called");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
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

            switch (trappedKeyEvent.getKeyCode()) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                header.setText("Android Media Router: Play");
                break;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                header.setText("Android Media Router: Next");
                break;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                header.setText("Android Media Router: Previous");
                break;
            case KeyEvent.KEYCODE_MEDIA_STOP:
                header.setText("Android Media Router: Stop");
                break;
            }

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

        // FIXME The wakelock is too late here. We end up doing multiple
        // resume/pause cycles (at least three) before the screen turns on and
        // our app is stable (not flickering). What to do?
        wakeLock.acquire();
        timeoutExecutor = Executors.newSingleThreadScheduledExecutor();
        resetTimeout();
    }

    private void resetTimeout() {
        if (timeoutScheduledFuture != null) {
            timeoutScheduledFuture.cancel(false);
        }

        // TODO Clean this up
        timeoutScheduledFuture = timeoutExecutor.schedule(new Runnable() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        Log.d(TAG, "Timed out waiting for user interaction, finishing activity");
                        finish();
                    }
                });

            }
        }, TIMEOUT_TIME, TimeUnit.SECONDS);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onUserInteraction() {
        super.onUserInteraction();

        // Reset timeout to finish
        resetTimeout();
    }

    //
    // /**
    // * {@inheritDoc}
    // */
    // @Override
    // protected void onSaveInstanceState(Bundle outState) {
    // super.onSaveInstanceState(outState);
    // outState.putInt(SELECTION_KEY, btButtonSelection);
    // Log.d(TAG, "Saving selection state, selected is " + btButtonSelection);
    // }

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
                Utils.forwardKeyCodeToComponent(this, selectedReceiver, true, trappedKeyEvent.getKeyCode(),
                        new SweepBroadcastReceiver(selectedReceiver.toString()));

                finish();

            }
        }
    }

    /**
     * Returns the name of the application of the broadcast receiver specified
     * by {@code resolveInfo}.
     * 
     * @param resolveInfo
     *            The receiver.
     * @return The name of the application.
     */
    private String getAppName(ResolveInfo resolveInfo) {
        return resolveInfo.activityInfo.applicationInfo.loadLabel(getPackageManager()).toString();
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
        // todo scroll to item, highlight it
        textToSpeech.speak(getAppName(receivers.get(btButtonSelection)), TextToSpeech.QUEUE_FLUSH, null);

    }

    /**
     * Select the currently selected receiver.
     */
    private void select() {
        // TODO if there is no selection, we should either forward to whoever
        // would have handled if we didn't exist, or to mru

        if (btButtonSelection == -1) {
            finish();
        } else {
            forwardToMediaReceiver(btButtonSelection);
        }

    }

}
