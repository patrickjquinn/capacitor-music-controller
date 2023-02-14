package com.ingageco.capacitormusiccontrols;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

@CapacitorPlugin(name = "CapacitorMusicControls")
public class CapacitorMusicControls extends Plugin {

	private static final String TAG = "CapacitorMusicControls";

	private MusicControlsBroadcastReceiver mMessageReceiver;
	private MusicControlsNotification notification;
	private MediaSessionCompat mediaSessionCompat;
	private final int notificationID = 7824;
	private AudioManager mAudioManager;
	private PendingIntent mediaButtonPendingIntent;
	private boolean mediaButtonAccess = true;
	private ServiceConnection mConnection;
	private MediaPlayer mMediaPlayer;

	private AudioManager.OnAudioFocusChangeListener changedListener = new AudioManager.OnAudioFocusChangeListener() {
		@Override
		public void onAudioFocusChange(int focusChange) {
//			JSObject ret = new JSObject();
//			switch (focusChange) {
//				case AudioManager.AUDIOFOCUS_GAIN:
//					if (mMediaPlayer != null) {
//						mMediaPlayer.stop();
//						mMediaPlayer.release();
//					}
//					mMediaPlayer = MediaPlayer.create(getActivity().getApplicationContext(), R.raw.silence);
//					mMediaPlayer.start();
//					mMediaPlayer.setLooping(true);
//				case AudioManager.AUDIOFOCUS_LOSS:
//					Log.d(TAG, "AUDIOFOCUS_LOSS");
//					ret.put("message", "music-controls-pause");
//					controlsNotification(ret);
//					break;
//				case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
//					ret.put("message", "music-controls-pause");
//					controlsNotification(ret);
//					Log.d(TAG, "AUDIOFOCUS_LOSS_TRANSIENT");
//					break;
//				case AudioManager.AUDIOFOCUS_REQUEST_FAILED:
//					Log.d(TAG, "AUDIOFOCUS_DENIED");
//				case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
//					Log.d(TAG, "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK");
//					// Lower volume
//					break;
//			}
		}
	};
	private AudioManager audioManager;
	private MediaSessionCallback mMediaSessionCallback;

	private void askForAudioFocus() {
		mAudioManager.requestAudioFocus(changedListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
	}

	@PluginMethod()
	public void create(PluginCall call) {
		JSObject options = call.getData();

		final Context context = getActivity().getApplicationContext();
		final Activity activity = getActivity();

		// unregisterMediaButtonEvent();
		// if (mConnection != null) {
		// Intent stopServiceIntent = new Intent(activity, CMCNotifyKiller.class);
		// activity.unbindService(mConnection);
		// activity.stopService(stopServiceIntent);
		// mConnection = null;
		// }
		this.destroyLocal();
		initialize();
		try {
			final MusicControlsInfos infos = new MusicControlsInfos(options);

			final MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder();

			notification.updateNotification(infos);

			// track title
			metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, infos.track);
			// artists
			metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, infos.artist);
			// album
			metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, infos.album);

			Bitmap art = getBitmapCover(infos.cover);
			if (art != null) {
				metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art);
				metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, art);
			}

			if (infos.duration > 0){
				long duration = infos.duration;
				if (duration > 0) {
					metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, infos.duration * 1000);
				}
			}

			if (mediaSessionCompat != null) {
				mediaSessionCompat.setMetadata(metadataBuilder.build());
			}


			if (infos.isPlaying)
				setMediaPlaybackState(PlaybackStateCompat.STATE_PLAYING, infos.elapsed);
			else
				setMediaPlaybackState(PlaybackStateCompat.STATE_PAUSED, infos.elapsed);

			call.resolve();
		} catch (JSONException e) {
			call.reject("error in initializing MusicControlsInfos " + e.toString());
		}
	}

	public void initialize() {

		final Activity activity = getActivity();

		final Context context = activity.getApplicationContext();

		mMessageReceiver = new MusicControlsBroadcastReceiver(this);
		registerBroadcaster(mMessageReceiver);

		if (mediaSessionCompat != null) {
			mediaSessionCompat.release();
		}

		mediaSessionCompat = new MediaSessionCompat(context, "capacitor-music-controls-media-session", null,
				mediaButtonPendingIntent);
		mediaSessionCompat.setFlags(
				MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

		setMediaPlaybackState(PlaybackStateCompat.STATE_PAUSED, 0);
		mediaSessionCompat.setActive(true);

		mMediaSessionCallback = new MediaSessionCallback(this);

		mediaSessionCompat.setCallback(mMediaSessionCallback);

		notification = new MusicControlsNotification(activity, notificationID, mediaSessionCompat.getSessionToken());
		final MusicControlsNotification my_notification = notification;

		// Register media (headset) button event receiver
		try {
			mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
			Intent headsetIntent = new Intent("music-controls-media-button");
			mediaButtonPendingIntent = PendingIntent.getBroadcast(context, 0, headsetIntent,
					PendingIntent.FLAG_IMMUTABLE);
			registerMediaButtonEvent();
		} catch (Exception e) {
			mediaButtonAccess = false;
			e.printStackTrace();
		}

		// Notification Killer
		ServiceConnection newMConnection = new ServiceConnection() {
			public void onServiceConnected(ComponentName className, IBinder binder) {
				Log.i(TAG, "onServiceConnected");
				final CMCNotifyKiller service = (CMCNotifyKiller) ((KillBinder) binder).service;

				service.setActivity(activity).setConnection(this).setBounded(true);
				my_notification.setKillerService(service);
				service.startService(new Intent(activity, CMCNotifyKiller.class));
				Log.i(TAG, "service Started");
			}

			public void onServiceDisconnected(ComponentName className) {
				Log.i(TAG, "service Disconnected");
			}
		};

		Intent startServiceIntent = new Intent(activity, CMCNotifyKiller.class);
		startServiceIntent.putExtra("notificationID", notificationID);
		activity.bindService(startServiceIntent, newMConnection, Context.BIND_AUTO_CREATE);

		mConnection = newMConnection;
	}

	@PluginMethod()
	public void destroy(PluginCall call) {

		final Activity activity = getActivity();
		final Context context = activity.getApplicationContext();

		this.destroyPlayerNotification();
		// mMessageReceiver.stopListening();

		try {
			context.unregisterReceiver(mMessageReceiver);
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		}

		unregisterMediaButtonEvent();

		if (mConnection != null) {
			Intent stopServiceIntent = new Intent(activity, CMCNotifyKiller.class);
			activity.unbindService(mConnection);
			activity.stopService(stopServiceIntent);
			mConnection = null;
		}
		call.resolve();
	}

	private void destroyLocal() {
		final Activity activity = getActivity();
		final Context context = activity.getApplicationContext();

		// this.destroyPlayerNotification();
		// mMessageReceiver.stopListening();

		try {

			context.unregisterReceiver(mMessageReceiver);

		} catch (IllegalArgumentException e) {

			e.printStackTrace();

		}

		unregisterMediaButtonEvent();

		// if (mConnection != null) {
		// Intent stopServiceIntent = new Intent(activity, CMCNotifyKiller.class);
		// activity.unbindService(mConnection);
		// activity.stopService(stopServiceIntent);
		// mConnection = null;
		// }
	}

	public void fullDestroyLocal() {
		final Activity activity = getActivity();
		final Context context = activity.getApplicationContext();

		this.destroyPlayerNotification();
		// mMessageReceiver.stopListening();

		try {
			context.unregisterReceiver(mMessageReceiver);
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		}

		unregisterMediaButtonEvent();

		if (mConnection != null) {
			Intent stopServiceIntent = new Intent(activity, CMCNotifyKiller.class);
			activity.unbindService(mConnection);
			activity.stopService(stopServiceIntent);
			mConnection = null;
		}
	}

	@PluginMethod()
	public void updateIsPlaying(PluginCall call) {

		if (this.notification == null) {
			call.resolve();
			return;
		}

		JSObject options = call.getData();

		try {
			final boolean isPlaying = options.getBoolean("isPlaying");
			final long elapsed = options.getLong("elapsed");
			this.notification.updateIsPlaying(isPlaying);

			if (isPlaying) {
//				this.askForAudioFocus();
				setMediaPlaybackState(PlaybackStateCompat.STATE_PLAYING, elapsed);
			} else {
				setMediaPlaybackState(PlaybackStateCompat.STATE_PAUSED, elapsed);
				// this.askForAudioFocus();
			}
			call.resolve();
		} catch (JSONException e) {
			System.out.println("toString(): " + e.toString());
			System.out.println("getMessage(): " + e.getMessage());
			System.out.println("StackTrace: ");
			e.printStackTrace();
			call.reject("error in updateIsPlaying");
		}
	}

	@PluginMethod()
	public void updateElapsed(PluginCall call) {
		if (this.notification == null) {
			call.resolve();
			return;
		}

		JSObject params = call.getData();

		// final JSONObject params = args.getJSONObject(0);
		try {
			final boolean isPlaying = params.getBoolean("isPlaying");
			final long elapsed = params.getLong("elapsed");
			this.notification.updateIsPlaying(isPlaying);

			if (isPlaying) {
//				this.askForAudioFocus();
				setMediaPlaybackState(PlaybackStateCompat.STATE_PLAYING,elapsed);
			} else {
				setMediaPlaybackState(PlaybackStateCompat.STATE_PAUSED,elapsed);
			}
			call.resolve();
		} catch (JSONException e) {
			call.reject("error in updateElapsed");
		} catch (NullPointerException e) {
			e.printStackTrace();
		}
	}

	@PluginMethod()
	public void updateDismissable(PluginCall call) {
		if (this.notification == null) {
			call.resolve();
			return;
		}
		JSObject params = call.getData();
		// final JSONObject params = args.getJSONObject(0);
		try {
			final boolean dismissable = params.getBoolean("dismissable");
			this.notification.updateDismissable(dismissable);
			call.resolve();
		} catch (JSONException e) {
			call.reject("error in updateDismissable");
		}

	}

	public void controlsNotification(JSObject ret) {
		Log.i(TAG, "controlsNotification fired " + ret.getString("message"));
		notifyListeners("controlsNotification", ret);
	}

	private void registerBroadcaster(MusicControlsBroadcastReceiver mMessageReceiver) {
		final Context context = getActivity().getApplicationContext();
		context.registerReceiver(mMessageReceiver, new IntentFilter("music-controls-previous"));
		context.registerReceiver(mMessageReceiver, new IntentFilter("music-controls-pause"));
		context.registerReceiver(mMessageReceiver, new IntentFilter("music-controls-play"));
		context.registerReceiver(mMessageReceiver, new IntentFilter("music-controls-next"));
		context.registerReceiver(mMessageReceiver, new IntentFilter("music-controls-media-button-next"));
		context.registerReceiver(mMessageReceiver, new IntentFilter("music-controls-media-button-previous"));
		context.registerReceiver(mMessageReceiver, new IntentFilter("music-controls-media-button"));
		context.registerReceiver(mMessageReceiver, new IntentFilter("music-controls-destroy"));
		context.registerReceiver(mMessageReceiver, new IntentFilter("music-controls-dislike"));
		context.registerReceiver(mMessageReceiver, new IntentFilter("music-controls-liked"));
		context.registerReceiver(mMessageReceiver, new IntentFilter("music-controls-headset-unplugged"));

		// Listen for headset plug/unplug
		context.registerReceiver(mMessageReceiver, new IntentFilter(Intent.ACTION_HEADSET_PLUG));
	}

	// Register pendingIntent for broacast
	public void registerMediaButtonEvent() {
		if (this.mediaSessionCompat != null) {
			this.mediaSessionCompat.setMediaButtonReceiver(this.mediaButtonPendingIntent);
		}
	}

	public void unregisterMediaButtonEvent() {
		if (this.mediaSessionCompat != null) {
			this.mediaSessionCompat.setMediaButtonReceiver(null);
		}
	}

	public void destroyPlayerNotification() {
		if (this.notification != null) {
			try {
				this.notification.destroy();
				this.notification = null;
			} catch (NullPointerException e) {
				e.printStackTrace();
			}
		}
	}

	private void setMediaPlaybackState(int state, long elapsed) {
		PlaybackStateCompat.Builder playbackstateBuilder = new PlaybackStateCompat.Builder();
		if (state == PlaybackStateCompat.STATE_PLAYING) {
			playbackstateBuilder.setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_PAUSE
					| PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
					PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID |
					PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH);
			playbackstateBuilder.setState(state, elapsed, 1.0f);
		} else {
			playbackstateBuilder.setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_PLAY
					| PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
					PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID |
					PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH);
			playbackstateBuilder.setState(state, elapsed, 0);
		}
		this.mediaSessionCompat.setPlaybackState(playbackstateBuilder.build());
	}

	// Get image from url
	private Bitmap getBitmapCover(String coverURL) {
		try {
			if (coverURL.matches("^(https?|ftp)://.*$"))
				// Remote image
				return getBitmapFromURL(coverURL);
			else {
				// Local image
				return getBitmapFromLocal(coverURL);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		}
	}

	// get Local image
	private Bitmap getBitmapFromLocal(String localURL) {
		try {
			Uri uri = Uri.parse(localURL);
			File file = new File(uri.getPath());
			FileInputStream fileStream = new FileInputStream(file);
			BufferedInputStream buf = new BufferedInputStream(fileStream);
			Bitmap myBitmap = BitmapFactory.decodeStream(buf);
			buf.close();
			return myBitmap;
		} catch (Exception ex) {
			try {
				InputStream fileStream = getActivity().getAssets().open("public/" + localURL);
				BufferedInputStream buf = new BufferedInputStream(fileStream);
				Bitmap myBitmap = BitmapFactory.decodeStream(buf);
				buf.close();
				return myBitmap;
			} catch (Exception ex2) {
				ex.printStackTrace();
				ex2.printStackTrace();
				return null;
			}
		}
	}

	// get Remote image
	private Bitmap getBitmapFromURL(String strURL) {
		try {
			URL url = new URL(strURL);
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setDoInput(true);
			connection.connect();
			InputStream input = connection.getInputStream();
			Bitmap myBitmap = BitmapFactory.decodeStream(input);
			return myBitmap;
		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		}
	}

	@Override
	protected void handleOnDestroy() {
		fullDestroyLocal();
		super.handleOnDestroy();
	}
}
