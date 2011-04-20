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

/**
 * Announces to the user the triggering media action and selections as they
 * navigate the activity's UI.
 * 
 * @author harleenssahni@gmail.com
 * 
 */
public interface INotifier {

    /**
     * Announces to the user the current action, and the selection, if any.
     * 
     * @param mediaKeyCode
     *            The KeyCode of the media action.
     * @param selection
     *            The selected application, may be null.
     */
    void announceOnLoad(int mediaKeyCode, String selection);

    void announceTimeout(int mediaKeyCode);

    void announceDismiss();

    void announceSelectionChange(String selection);

    void announceFowarding(String selection);

    void destroy();

}
