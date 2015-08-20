# media-button-router
_Nicer Android media button handling by allowing the user to choose the receiver._

*NOTE*: _As my commute has changed, I no longer use this app. I am not actively developing this app. All bug fixes and improvements must come from your contributions._

In android, app writers decide what priority they get when receiving media buttons (pause/play, previous, next, etc). This can be frustrating to users if they want to use a different app to handle these button presses on their Bluetooth devices. 

This app attempts to alleviate these pains by providing hands-free selection of the app you want to use to handle media buttons through text-to-speech and use of the previous, next, and play buttons for selection.

NOTE: It is likely that there are some music apps that will never work well with this app. [Status of Various Apps](https://github.com/harleensahni/media-button-router/wiki/Music-App-Status)

Available now on the Android Market: https://market.android.com/details?id=com.harleensahni.android.mbr

## How this app works
On older versions of Android this application works by setting itself as the highest priority receiver for button presses. On later versions of Android, this no longer worked. For later versions, this app tracks when the broadcast receiver changes, and then sets itself back as the broadcast receiver.
