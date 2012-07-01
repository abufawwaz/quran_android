/* 
 * This code is based on the RandomMusicPlayer example from
 * the Android Open Source Project samples.  It has been modified
 * for use in Quran Android.
 *   
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.quran.labs.androidquran.service;

import java.io.IOException;
import java.io.Serializable;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.service.util.AudioFocusHelper;
import com.quran.labs.androidquran.service.util.AudioFocusable;
import com.quran.labs.androidquran.service.util.AudioIntentReceiver;
import com.quran.labs.androidquran.service.util.AudioRequest;
import com.quran.labs.androidquran.service.util.MediaButtonHelper;
import com.quran.labs.androidquran.ui.PagerActivity;

/**
 * Service that handles media playback. This is the Service through which we
 * perform all the media handling in our application. It waits for Intents
 * (which come from our main activity, {@link PagerActivity}, which signal
 * the service to perform specific operations: Play, Pause, Rewind, Skip, etc.
 */
public class AudioService extends Service implements OnCompletionListener,
        OnPreparedListener, OnErrorListener, AudioFocusable {

   // The tag we put on debug messages
   final static String TAG = "AudioService";

   // These are the Intent actions that we are prepared to handle. Notice that
   // the fact these constants exist in our class is a mere convenience: what
   // really defines the actions our service can handle are the <action> tags
   // in the <intent-filters> tag for our service in AndroidManifest.xml.
   public static final String ACTION_PLAYBACK =
         "com.quran.labs.androidquran.action.PLAYBACK";
   public static final String ACTION_PLAY =
           "com.quran.labs.androidquran.action.PLAY";
   public static final String ACTION_PAUSE =
           "com.quran.labs.androidquran.action.PAUSE";
   public static final String ACTION_STOP =
           "com.quran.labs.androidquran.action.STOP";
   public static final String ACTION_SKIP =
           "com.quran.labs.androidquran.action.SKIP";
   public static final String ACTION_REWIND =
           "com.quran.labs.androidquran.action.REWIND";

   // The volume we set the media player to when we lose audio focus, but are
   // allowed to reduce the volume instead of stopping playback.
   public static final float DUCK_VOLUME = 0.1f;

   // our media player
   MediaPlayer mPlayer = null;

   // our AudioFocusHelper object, if it's available (it's available on SDK
   // level >= 8). If not available, this will be null. Always check for null
   // before using!
   AudioFocusHelper mAudioFocusHelper = null;

   // object representing the current playing request
   AudioRequest mAudioRequest = null;

   // so user can pass in a serializable AudioRequest to the intent
   public static final String EXTRA_PLAY_INFO =
           "com.quran.labs.androidquran.PLAY_INFO";

   // ignore the passed in play info if we're already playing
   public static final String EXTRA_IGNORE_IF_PLAYING =
           "com.quran.labs.androidquran.IGNORE_IF_PLAYING";

   // indicates the state our service:
   enum State {
      Stopped,    // media player is stopped and not prepared to play
      Preparing,  // media player is preparing...
      Playing,    // playback active (media player ready!). (but the media
      // player may actually be  paused in this state if we don't have audio
      // focus. But we stay in this state so that we know we have to resume
      // playback once we get focus back)
      Paused      // playback paused (media player ready!)
   };

   State mState = State.Stopped;

   enum PauseReason {
      UserRequest,  // paused by user request
      FocusLoss,    // paused because of audio focus loss
   };

   // why did we pause? (only relevant if mState == State.Paused)
   PauseReason mPauseReason = PauseReason.UserRequest;

   // do we have audio focus?
   enum AudioFocus {
      NoFocusNoDuck,    // we don't have audio focus, and can't duck
      NoFocusCanDuck,   // we don't have focus, but can play at a low volume
      Focused           // we have full audio focus
   }
   AudioFocus mAudioFocus = AudioFocus.NoFocusNoDuck;

   String mNotificationName = "";
   
   // title of the audio we are currently playing
   String mAudioTitle = "";

   // whether the audio we are playing is streaming from the network
   boolean mIsStreaming = false;
   
   // Wifi lock that we hold when streaming files from the internet,
   // in order to prevent the device from shutting off the Wifi radio
   WifiLock mWifiLock;

   // The ID we use for the notification (the onscreen alert that appears
   // at the notification area at the top of the screen as an icon -- and
   // as text as well if the user expands the notification area).
   final int NOTIFICATION_ID = 4;

   // The component name of MusicIntentReceiver, for use with media button
   // and remote control APIs
   ComponentName mMediaButtonReceiverComponent;

   AudioManager mAudioManager;
   NotificationManager mNotificationManager;

   Notification mNotification = null;

   /**
    * Makes sure the media player exists and has been reset. This will create
    * the media player if needed, or reset the existing media player if one
    * already exists.
    */
   void createMediaPlayerIfNeeded() {
      if (mPlayer == null) {
         mPlayer = new MediaPlayer();
         
         // Make sure the media player will acquire a wake-lock while playing.
         // If we don't do that, the CPU might go to sleep while the song is
         // playing, causing playback to stop.
         //
         // Remember that to use this, we have to declare the
         // android.permission.WAKE_LOCK permission in AndroidManifest.xml.
         mPlayer.setWakeMode(getApplicationContext(),
                 PowerManager.PARTIAL_WAKE_LOCK);

         // we want the media player to notify us when it's ready preparing,
         // and when it's done playing:
         mPlayer.setOnPreparedListener(this);
         mPlayer.setOnCompletionListener(this);
         mPlayer.setOnErrorListener(this);
      }
      else
         mPlayer.reset();
   }

   @Override
   public void onCreate() {
      Log.i(TAG, "debug: Creating service");

      mWifiLock = ((WifiManager) getSystemService(Context.WIFI_SERVICE))
            .createWifiLock(WifiManager.WIFI_MODE_FULL, "audiolock");
      mNotificationManager = (NotificationManager)
              getSystemService(NOTIFICATION_SERVICE);
      mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

      // create the Audio Focus Helper, if the Audio Focus feature is available
      if (android.os.Build.VERSION.SDK_INT >= 8){
         mAudioFocusHelper = new AudioFocusHelper(
                 getApplicationContext(), this);
      }
      // no focus feature, so we always "have" audio focus
      else { mAudioFocus = AudioFocus.Focused; }

      mMediaButtonReceiverComponent = new ComponentName(
              this, AudioIntentReceiver.class);
      mNotificationName = getString(R.string.app_name);
   }

   /**
    * Called when we receive an Intent. When we receive an intent sent to us
    * via startService(), this is the method that gets called. So here we
    * react appropriately depending on the Intent's action, which specifies
    * what is being requested of us.
    */
   @Override
   public int onStartCommand(Intent intent, int flags, int startId) {
      String action = intent.getAction();

      if (action.equals(ACTION_PLAYBACK)){
         Serializable playInfo = intent.getSerializableExtra(EXTRA_PLAY_INFO);
         if (playInfo != null && playInfo instanceof AudioRequest){
            if (mState == State.Stopped &&
                    intent.getBooleanExtra(EXTRA_IGNORE_IF_PLAYING, false)){
               mAudioRequest = (AudioRequest)playInfo;
            }
         }

         processTogglePlaybackRequest();
      }
      else if (action.equals(ACTION_PLAY)){ processPlayRequest(); }
      else if (action.equals(ACTION_PAUSE)){ processPauseRequest(); }
      else if (action.equals(ACTION_SKIP)){ processSkipRequest(); }
      else if (action.equals(ACTION_STOP)){ processStopRequest(); }
      else if (action.equals(ACTION_REWIND)){ processRewindRequest(); }

      return START_NOT_STICKY; // Means we started the service, but don't want
      // it to restart in case it's killed.
   }

   void processTogglePlaybackRequest() {
      if (mState == State.Paused || mState == State.Stopped) {
         processPlayRequest();
      } else {
         processPauseRequest();
      }
   }

   void processPlayRequest() {
      if (mAudioRequest == null){ return; }
      tryToGetAudioFocus();

      // actually play the file

      if (mState == State.Stopped) {
         // If we're stopped, just go ahead to the next file and start playing
         playAudio();
      }
      else if (mState == State.Paused) {
         // If we're paused, just continue playback and restore the
         // 'foreground service' state.
         mState = State.Playing;
         setUpAsForeground(mAudioTitle + " (playing)");
         configAndStartMediaPlayer();
      }
   }

   void processPauseRequest() {
      if (mState == State.Playing) {
         // Pause media player and cancel the 'foreground service' state.
         mState = State.Paused;
         mPlayer.pause();
         // while paused, we always retain the MediaPlayer
         relaxResources(false);
      }
   }

   void processRewindRequest() {
      if (mState == State.Playing || mState == State.Paused){
         if (mPlayer.getCurrentPosition() > 1500){
            mPlayer.seekTo(0);
         }
         else {
            tryToGetAudioFocus();
            mAudioRequest.gotoPreviousAyah();
            playAudio();
         }
      }
   }

   void processSkipRequest() {
      if (mAudioRequest == null){ return; }
      if (mState == State.Playing || mState == State.Paused) {
         tryToGetAudioFocus();
         mAudioRequest.gotoNextAyah();
         playAudio();
      }
   }

   void processStopRequest() {
      processStopRequest(false);
   }

   void processStopRequest(boolean force) {
      if (mState == State.Playing || mState == State.Paused || force) {
         mState = State.Stopped;

         // let go of all resources...
         relaxResources(true);
         giveUpAudioFocus();

         // service is no longer necessary. Will be started again if needed.
         stopSelf();
      }
   }

   /**
    * Releases resources used by the service for playback. This includes the
    * "foreground service" status and notification, the wake locks and
    * possibly the MediaPlayer.
    *
    * @param releaseMediaPlayer Indicates whether the Media Player should also
    *                           be released or not
    */
   void relaxResources(boolean releaseMediaPlayer) {
      // stop being a foreground service
      stopForeground(true);

      // stop and release the Media Player, if it's available
      if (releaseMediaPlayer && mPlayer != null) {
         mPlayer.reset();
         mPlayer.release();
         mPlayer = null;
      }
      
      // we can also release the Wifi lock, if we're holding it
      if (mWifiLock.isHeld()) mWifiLock.release();
   }

   void giveUpAudioFocus() {
      if (mAudioFocus == AudioFocus.Focused && mAudioFocusHelper != null
            && mAudioFocusHelper.abandonFocus())
         mAudioFocus = AudioFocus.NoFocusNoDuck;
   }

   /**
    * Reconfigures MediaPlayer according to audio focus settings and
    * starts/restarts it. This method starts/restarts the MediaPlayer
    * respecting the current audio focus state. So if we have focus,
    * it will play normally; if we don't have focus, it will either
    * leave the MediaPlayer paused or set it to a low volume, depending
    * on what is allowed by the current focus settings. This method assumes
    * mPlayer != null, so if you are calling it, you have to do so from a
    * context where you are sure this is the case.
    */
   void configAndStartMediaPlayer() {
      if (mAudioFocus == AudioFocus.NoFocusNoDuck) {
         // If we don't have audio focus and can't duck, we have to pause,
         // even if mState is State.Playing. But we stay in the Playing state
         // so that we know we have to resume playback once we get focus back.
         if (mPlayer.isPlaying()) mPlayer.pause();
         return;
      }
      else if (mAudioFocus == AudioFocus.NoFocusCanDuck){
         // we'll be relatively quiet
         mPlayer.setVolume(DUCK_VOLUME, DUCK_VOLUME);
      }
      else { mPlayer.setVolume(1.0f, 1.0f); } // we can be loud

      if (!mPlayer.isPlaying()){ mPlayer.start(); }
   }

   void tryToGetAudioFocus() {
      if (mAudioFocus != AudioFocus.Focused && mAudioFocusHelper != null
            && mAudioFocusHelper.requestFocus())
         mAudioFocus = AudioFocus.Focused;
   }

   /**
    * Starts playing the next file.
    */
   void playAudio() {
      mState = State.Stopped;
      relaxResources(false); // release everything except MediaPlayer

      try {
         if (mAudioRequest == null || mAudioRequest.getUrl() == null){
            processStopRequest(true); // stop everything!
            return;
         }

         String url = mAudioRequest.getUrl();
         createMediaPlayerIfNeeded();
         mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
         mPlayer.setDataSource(url);
         mIsStreaming = url.startsWith("http:") || url.startsWith("https:");

         mAudioTitle = mAudioRequest.getTitle();

         mState = State.Preparing;
         setUpAsForeground(mAudioTitle + " (loading)");

         // Use the media button APIs (if available) to register ourselves
         // for media button events

         MediaButtonHelper.registerMediaButtonEventReceiverCompat(
               mAudioManager, mMediaButtonReceiverComponent);

         // starts preparing the media player in the background. When it's
         // done, it will call our OnPreparedListener (that is, the
         // onPrepared() method on this class, since we set the listener
         // to 'this').
         //
         // Until the media player is prepared, we *cannot* call start() on it!
         mPlayer.prepareAsync();
         
         // If we are streaming from the internet, we want to hold a Wifi lock,
         // which prevents the Wifi radio from going to sleep while the song is
         // playing. If, on the other hand, we are *not* streaming, we want to
         // release the lock if we were holding it before.
         if (mIsStreaming){ mWifiLock.acquire(); }
         else if (mWifiLock.isHeld()){ mWifiLock.release(); }
      }
      catch (IOException ex) {
         Log.e(TAG, "IOException playing file: " + ex.getMessage());
         ex.printStackTrace();
      }
   }

   /** Called when media player is done playing current file. */
   public void onCompletion(MediaPlayer player) {
      // The media player finished playing the current file, so
      // we go ahead and start the next.
      mAudioRequest.gotoNextAyah();
      playAudio();
   }

   /** Called when media player is done preparing. */
   public void onPrepared(MediaPlayer player) {
      // The media player is done preparing. That means we can start playing!
      mState = State.Playing;
      updateNotification(mAudioTitle + " (playing)");
      configAndStartMediaPlayer();
   }

   /** Updates the notification. */
   void updateNotification(String text) {
      PendingIntent pi = PendingIntent.getActivity(getApplicationContext(), 0,
            new Intent(getApplicationContext(), PagerActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT);
      mNotification.setLatestEventInfo(getApplicationContext(),
              mNotificationName, text, pi);
      mNotificationManager.notify(NOTIFICATION_ID, mNotification);
   }

   /**
    * Configures service as a foreground service. A foreground service
    * is a service that's doing something the user is actively aware of
    * (such as playing music), and must appear to the user as a notification.
    * That's why we create the notification here.
    */
   void setUpAsForeground(String text) {
      PendingIntent pi = PendingIntent.getActivity(getApplicationContext(), 0,
            new Intent(getApplicationContext(), PagerActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT);
      mNotification = new Notification();
      mNotification.tickerText = text;
      mNotification.icon = R.drawable.icon;
      mNotification.flags |= Notification.FLAG_ONGOING_EVENT;
      mNotification.setLatestEventInfo(getApplicationContext(),
              mNotificationName, text, pi);
      startForeground(NOTIFICATION_ID, mNotification);
   }

   /**
    * Called when there's an error playing media. When this happens, the media
    * player goes to the Error state. We warn the user about the error and
    * reset the media player.
    */
   public boolean onError(MediaPlayer mp, int what, int extra) {
      Toast.makeText(getApplicationContext(), "Media player error! Resetting.",
            Toast.LENGTH_SHORT).show();
      Log.e(TAG, "Error: what=" + String.valueOf(what) +
              ", extra=" + String.valueOf(extra));

      mState = State.Stopped;
      relaxResources(true);
      giveUpAudioFocus();
      return true; // true indicates we handled the error
   }

   public void onGainedAudioFocus() {
      Toast.makeText(getApplicationContext(), "gained audio focus.",
              Toast.LENGTH_SHORT).show();
      mAudioFocus = AudioFocus.Focused;

      // restart media player with new focus settings
      if (mState == State.Playing)
         configAndStartMediaPlayer();
   }

   public void onLostAudioFocus(boolean canDuck) {
      Toast.makeText(getApplicationContext(), "lost audio focus." +
              (canDuck ? "can duck" : "no duck"), Toast.LENGTH_SHORT).show();
      mAudioFocus = canDuck ? AudioFocus.NoFocusCanDuck :
              AudioFocus.NoFocusNoDuck;

      // start/restart/pause media player with new focus settings
      if (mPlayer != null && mPlayer.isPlaying()){
         configAndStartMediaPlayer();
      }
   }


   @Override
   public void onDestroy() {
      // Service is being killed, so make sure we release our resources
      mState = State.Stopped;
      relaxResources(true);
      giveUpAudioFocus();
   }

   @Override
   public IBinder onBind(Intent arg0) {
      return null;
   }
}
