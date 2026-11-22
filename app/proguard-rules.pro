# No custom shrinking rules are required for the debug build.
# Keep platform listener/service entry points when enabling release minification.
-keep class app.novapill.android.media.MediaNotificationListener { *; }
-keep class app.novapill.android.overlay.IslandOverlayService { *; }
