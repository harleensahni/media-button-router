# Introduction #

Media Button Router has varying levels of support for different music apps. There are two issues that have to be worked around:
  1. Android will let you know that music is being played, but not which app is playing the music.
  1. Media Button Intent handling is done through ordered broadcasts, and each app sets it's own priority for receiving.

The music apps that will work best are ones that have a started foreground service when they are playing music and do not have any gaps from when they handle a media button press and when Android considers music to be playing (AudioManager.isMusicActive). Apps that don't declare their media button receivers won't work because we won't know about them -- unless we hardcode for them; likewise, apps that declare their priority as high  us won't work because we won't be able to handle the intent before them.

Note: Some apps may need to have a "headset" preference set in their preferences.

# The Apps #

Apps that work well:
  * PowerAMP
  * MixZing
  * ³(Cubed)
  * Default Android music app
  * Pandora (if you hit a media button while pandora is loading a song, you will get our selection dialog again since we don't know music is playing)
  * Amazon MP3 (version 2.0.4+, previous don't declare media button receiver in manifest so we don't know about them)

Apps that work mostly, but don't use a started foreground service to play music. We don't know it's them that's playing the music. Can fall back to default android behavior, which mostly works or go a conservative route and ask the user again. :
  * Google Listen
  * Rhapsody

Apps that won't work:
  * Winamp (same button priority as us)
  * DoubleTwist (user reported)