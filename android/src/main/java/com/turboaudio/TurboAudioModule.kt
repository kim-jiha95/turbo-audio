package com.turboaudio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.MediaMetadataCompat

class TurboAudioModule(private val reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

   private var player: ExoPlayer? = null
   private var playerNotificationManager: PlayerNotificationManager? = null
   private var mediaSession: MediaSessionCompat? = null
   private var currentTrackInfo: ReadableMap? = null
   private var pendingPlaybackPromise: Promise? = null
   private val mainHandler = Handler(Looper.getMainLooper())
   private val NOTIFICATION_ID = 1
   private val CHANNEL_ID = "turbo_audio_channel"
   
   companion object {
       private const val NOTIFICATION_CHANNEL_NAME = "TurboAudio Player"
       private const val NOTIFICATION_CHANNEL_DESCRIPTION = "Shows audio playback controls"
   }

   override fun getName(): String {
       return "TurboAudio"
   }

    override fun canOverrideExistingModule(): Boolean {
        return true
    }

   private fun initializePlayer() {
       try {
           if (player == null) {
               val context = reactContext.applicationContext
               player = ExoPlayer.Builder(context).build().apply {
                   playWhenReady = false
                   
                   addListener(object : Player.Listener {
                       override fun onPlaybackStateChanged(state: Int) {
                           when (state) {
                               Player.STATE_READY -> {
                                   sendEvent("onPlaybackReady", null)
                                   updatePlaybackState()
                                   
                                   pendingPlaybackPromise?.let { promise ->
                                       try {
                                           val duration = if (duration == C.TIME_UNSET) 0.0 else duration / 1000.0
                                           val result = Arguments.createMap().apply {
                                               putDouble("duration", duration)
                                               putBoolean("success", true)
                                           }
                                           promise.resolve(result)
                                       } catch (e: Exception) {
                                           promise.reject("playback_error", "Failed to process ready state: ${e.message}")
                                       }
                                       pendingPlaybackPromise = null
                                   }
                               }
                               Player.STATE_ENDED -> {
                                   sendEvent("onPlaybackEnded", null)
                                   updatePlaybackState()
                               }
                               Player.STATE_BUFFERING -> {
                                   updatePlaybackState()
                               }
                               Player.STATE_IDLE -> {
                                   updatePlaybackState()
                               }
                           }
                       }

                       override fun onPlayerError(error: PlaybackException) {
                           sendEvent("onPlaybackError", Arguments.createMap().apply {
                               putString("error", error.message)
                           })
                           pendingPlaybackPromise?.let { promise ->
                               promise.reject("playback_error", error.message)
                               pendingPlaybackPromise = null
                           }
                       }

                       override fun onIsPlayingChanged(isPlaying: Boolean) {
                           updatePlaybackState()
                       }
                   })
               }
               
               sendEvent("onInitSuccess", null)
               setupMediaSessionAndNotification()
           }
       } catch (e: Exception) {
           val errorMessage = "Failed to initialize ExoPlayer: ${e.message}\n${e.stackTrace.joinToString("\n")}"
           sendEvent("onInitError", Arguments.createMap().apply {
               putString("error", errorMessage)
           })
           throw Exception(errorMessage)
       }
   }

   private fun setupMediaSessionAndNotification() {
       try {
           setupMediaSession()
           setupNotificationManager()
       } catch (e: Exception) {
           sendEvent("onSetupWarning", Arguments.createMap().apply {
               putString("warning", "Media session or notification setup failed: ${e.message}")
           })
       }
   }

   private fun setupMediaSession() {
       mediaSession = MediaSessionCompat(reactContext, "TurboAudioSession").apply {
           setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
           
           setCallback(object : MediaSessionCompat.Callback() {
               override fun onPlay() {
                   player?.play()
               }

               override fun onPause() {
                   player?.pause()
               }

               override fun onStop() {
                   player?.stop()
               }

               override fun onSeekTo(pos: Long) {
                   player?.seekTo(pos)
               }

               override fun onSkipToNext() {
                   // Todo: Next track implementation
               }

               override fun onSkipToPrevious() {
                   // Todo : Previous track implementation
               }
           })

           isActive = true
           updatePlaybackState()
       }
   }

   private fun updatePlaybackState() {
       val state = when {
           player?.isPlaying == true -> PlaybackStateCompat.STATE_PLAYING
           player?.playbackState == Player.STATE_BUFFERING -> PlaybackStateCompat.STATE_BUFFERING
           player?.playbackState == Player.STATE_ENDED -> PlaybackStateCompat.STATE_STOPPED
           player?.playbackState == Player.STATE_READY -> PlaybackStateCompat.STATE_PAUSED
           else -> PlaybackStateCompat.STATE_NONE
       }

       val position = player?.currentPosition ?: 0L
       val playbackSpeed = player?.playbackParameters?.speed ?: 1f

       mediaSession?.setPlaybackState(
           PlaybackStateCompat.Builder()
               .setState(state, position, playbackSpeed)
               .setActions(
                   PlaybackStateCompat.ACTION_PLAY or
                   PlaybackStateCompat.ACTION_PAUSE or
                   PlaybackStateCompat.ACTION_PLAY_PAUSE or
                   PlaybackStateCompat.ACTION_STOP or
                   PlaybackStateCompat.ACTION_SEEK_TO or
                   PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                   PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
               )
               .build()
       )

       updateMetadata(currentTrackInfo)
       playerNotificationManager?.invalidate()
   }

   private fun updateMetadata(trackInfo: ReadableMap?) {
       if (trackInfo == null) return

       val metadataBuilder = MediaMetadataCompat.Builder()
       metadataBuilder
           .putString(MediaMetadataCompat.METADATA_KEY_TITLE, trackInfo.getString("title"))
           .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, trackInfo.getString("artist"))
           .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, player?.duration ?: 0)
           .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, trackInfo.getString("title"))
           .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, trackInfo.getString("artist"))
       
       mediaSession?.setMetadata(metadataBuilder.build())
   }

   private fun setupNotificationManager() {
    createNotificationChannel()

    playerNotificationManager = PlayerNotificationManager.Builder(
        reactContext,
        NOTIFICATION_ID,
        CHANNEL_ID
    )
        .setMediaDescriptionAdapter(object : PlayerNotificationManager.MediaDescriptionAdapter {
            override fun getCurrentContentTitle(player: Player): CharSequence {
                return currentTrackInfo?.getString("title") ?: "Unknown Title"
            }

            override fun getCurrentContentText(player: Player): CharSequence {
                return currentTrackInfo?.getString("artist") ?: "Unknown Artist"
            }

            override fun getCurrentLargeIcon(
                player: Player,
                callback: PlayerNotificationManager.BitmapCallback
            ): Bitmap? {
                return null
            }

            override fun createCurrentContentIntent(player: Player): PendingIntent? {
                val intent = reactContext.packageManager.getLaunchIntentForPackage(
                    reactContext.packageName
                )?.apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                return PendingIntent.getActivity(
                    reactContext,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }
        })
        .setChannelNameResourceId(android.R.string.ok)
        .setChannelDescriptionResourceId(android.R.string.ok)
        .setNotificationListener(object : PlayerNotificationManager.NotificationListener {
            override fun onNotificationPosted(
                notificationId: Int,
                notification: Notification,
                ongoing: Boolean
            ) {
                notification.flags = notification.flags or Notification.FLAG_ONGOING_EVENT
                notification.flags = notification.flags or Notification.FLAG_NO_CLEAR

                if (ongoing) {
                    val serviceIntent = Intent(reactContext, AudioService::class.java).apply {
                        putExtra("notification", notification)
                    }
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        reactContext.startForegroundService(serviceIntent)
                    } else {
                        reactContext.startService(serviceIntent)
                    }
                }
            }

            override fun onNotificationCancelled(
                notificationId: Int,
                dismissedByUser: Boolean
            ) {
                player?.stop()
                mediaSession?.isActive = false
                reactContext.stopService(Intent(reactContext, AudioService::class.java))
            }
        })
        .setSmallIconResourceId(android.R.drawable.ic_media_play)
        .build()

    mediaSession?.sessionToken?.let { token ->
        playerNotificationManager?.setMediaSessionToken(token)
    }

    playerNotificationManager?.apply {
        setUseNextActionInCompactView(true)
        setUsePreviousActionInCompactView(true)
        setUsePlayPauseActions(true)
        setUseStopAction(true)
        setPriority(NotificationCompat.PRIORITY_MAX)
        setPlayer(player)
        }
    }

   private fun createNotificationChannel() {
       if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
           val channel = NotificationChannel(
               CHANNEL_ID,
               NOTIFICATION_CHANNEL_NAME,
               NotificationManager.IMPORTANCE_HIGH
           ).apply {
               description = NOTIFICATION_CHANNEL_DESCRIPTION
               setShowBadge(true)
               lockscreenVisibility = Notification.VISIBILITY_PUBLIC
               enableLights(true)
               enableVibration(true)
               setAllowBubbles(true)
           }
           
           val notificationManager = reactContext.getSystemService(
               Context.NOTIFICATION_SERVICE
           ) as NotificationManager
           
           notificationManager.createNotificationChannel(channel)
       }
   }

   @ReactMethod
   fun loadAndPlayAudio(uri: String, trackInfo: ReadableMap, promise: Promise) {
       mainHandler.post {
           try {
               if (player == null) {
                   initializePlayer()
               }
               
               player?.let { exoPlayer ->
                   exoPlayer.stop()
                   exoPlayer.clearMediaItems()
                   
                   currentTrackInfo = trackInfo
                   pendingPlaybackPromise = promise

                   val mediaItem = MediaItem.fromUri(Uri.parse(uri))
                   exoPlayer.setMediaItem(mediaItem)
                   exoPlayer.playWhenReady = true
                   exoPlayer.prepare()
                   updateMetadata(trackInfo)
               } ?: throw Exception("Player initialization failed")
               
           } catch (e: Exception) {
               pendingPlaybackPromise = null
               promise.reject("load_error", "Failed to load audio: ${e.message}")
           }
       }
   }

    @ReactMethod
    fun getStatus(promise: Promise) {
        mainHandler.post {
            try {
                if (player == null) {
                    // Player가 없는 경우 기본 상태 반환
                    val defaultStatus = Arguments.createMap().apply {
                        putDouble("duration", 0.0)
                        putDouble("position", 0.0)
                        putBoolean("isPlaying", false)
                        putBoolean("isInitialized", false)
                    }
                    promise.resolve(defaultStatus)
                    return@post
                }

                player?.let {
                    val duration = if (it.duration == C.TIME_UNSET) 0.0 else it.duration / 1000.0
                    val position = if (it.currentPosition == C.TIME_UNSET) 0.0 else it.currentPosition / 1000.0
                    
                    val result = Arguments.createMap().apply {
                        putDouble("duration", duration)
                        putDouble("position", position)
                        putBoolean("isPlaying", it.isPlaying)
                        putBoolean("isInitialized", true)
                        putInt("playbackState", it.playbackState)
                    }
                    promise.resolve(result)
                }
            } catch (e: Exception) {
                promise.reject("status_error", "Failed to get status: ${e.message}")
            }
        }
    }

    @ReactMethod
    fun pauseAudio(promise: Promise) {
        mainHandler.post {
            try {
                player?.let {
                    it.pause()
                    promise.resolve(true)
                } ?: promise.reject("no_player", "ExoPlayer not initialized2")
            } catch (e: Exception) {
                promise.reject("pause_error", "Failed to pause: ${e.message}")
            }
        }
    }

    @ReactMethod
    fun resumeAudio(promise: Promise) {
        mainHandler.post {
            try {
                player?.let {
                    it.play()
                    promise.resolve(true)
                } ?: promise.reject("no_player", "ExoPlayer not initialized3")
            } catch (e: Exception) {
                promise.reject("resume_error", "Failed to resume: ${e.message}")
            }
        }
    }

    @ReactMethod
    fun stopAudio(promise: Promise) {
        mainHandler.post {
            try {
                player?.let {
                    it.stop()
                    it.seekTo(0)
                    promise.resolve(true)
                } ?: promise.reject("no_player", "ExoPlayer not initialized4")
            } catch (e: Exception) {
                promise.reject("stop_error", "Failed to stop: ${e.message}")
            }
        }
    }

    @ReactMethod
    fun seekAudio(position: Double, promise: Promise) {
        mainHandler.post {
            try {
                player?.let {
                    val positionMs = (position * 1000).toLong()
                    it.seekTo(positionMs)
                    promise.resolve(true)
                } ?: promise.reject("no_player", "ExoPlayer not initialized5")
            } catch (e: Exception) {
                promise.reject("seek_error", "Failed to seek: ${e.message}")
            }
        }
    }

    private fun sendEvent(eventName: String, params: WritableMap?) {
        reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(eventName, params)
    }

    override fun onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy()
        mainHandler.post {
            playerNotificationManager?.setPlayer(null)
            mediaSession?.release()
            player?.release()
            player = null
            mediaSession = null
        }
    }
}