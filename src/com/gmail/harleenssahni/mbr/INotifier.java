/**
 * 
 */
package com.gmail.harleenssahni.mbr;

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
