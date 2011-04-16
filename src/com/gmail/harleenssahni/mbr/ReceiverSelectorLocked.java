package com.gmail.harleenssahni.mbr;

import android.view.Menu;

/**
 * Allows us to display on lock screen.
 * 
 * @author harleenssahni@gmail.com
 */
public class ReceiverSelectorLocked extends ReceiverSelector {
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // No context menu, we're showing above the lock screen. Can't get to
        // our preferences screen anyway.
        return false;
    }
}
