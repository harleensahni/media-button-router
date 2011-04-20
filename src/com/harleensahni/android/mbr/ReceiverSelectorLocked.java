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

import android.view.Menu;

/**
 * Allows us to display on lock screen.
 * 
 * @author Harleen Sahni
 */
public class ReceiverSelectorLocked extends ReceiverSelector {
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // No context menu, we're showing above the lock screen. Can't get to
        // our preferences screen anyway.
        return false;
    }
}
