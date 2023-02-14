package com.ingageco.capacitormusiccontrols;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.ingageco.capacitormusiccontrols.capacitormusiccontrolsplugin.R;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;

public class MusicControlsNotification {
	private static final String TAG = "CMCNotification";

	private Activity cordovaActivity;
	private NotificationManager notificationManager;
	private NotificationCompat.Builder notificationBuilder;
	private int notificationID;
	private MusicControlsInfos infos;
	private Bitmap bitmapCover;
	private String CHANNEL_ID;
	private MediaSessionCompat.Token token;


	public WeakReference<CMCNotifyKiller> killer_service;

	public MusicControlsNotification(Activity cordovaActivity,int id, MediaSessionCompat.Token token){
		this.CHANNEL_ID ="rad-music-channel-id";
		this.notificationID = id;
		this.cordovaActivity = cordovaActivity;
		Context context = cordovaActivity.getApplicationContext();
		this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		this.token = token;
		if (Build.VERSION.SDK_INT >= 26) {
			CharSequence name = "rad-music-controls-plugin";
			String description = "Rad music controller notification";
			int importance = NotificationManager.IMPORTANCE_LOW;
			NotificationChannel mChannel = new NotificationChannel(this.CHANNEL_ID, name,importance);

			mChannel.setDescription(description);

			this.notificationManager.createNotificationChannel(mChannel);
    	}

	}

	public void updateNotification(MusicControlsInfos newInfos){

		// Log.i(TAG, "updateNotification: infos: " + newInfos.toString());
		// if (!newInfos.cover.isEmpty() && (this.infos == null || !newInfos.cover.equals(this.infos.cover))){
		// 	this.getBitmapCover(newInfos.cover);
		// }
		// this.infos = newInfos;
		// this.createBuilder();
		// this.createNotification();
		if (!newInfos.cover.isEmpty() && (this.infos == null || !newInfos.cover.equals(this.infos.cover))) {
			new Thread(() -> {
				this.getBitmapCover(newInfos.cover);
				this.infos = newInfos;
				this.createBuilder();
				this.createNotification();
			}).start();
		} else {
			this.infos = newInfos;
			this.createBuilder();
			this.createNotification();
		}
	}

	private void createNotification() {
		final Notification noti = this.notificationBuilder.build();

		// noti.flags = Notification.FLAG_AUTO_CANCEL;
		if (killer_service != null) {
			killer_service.get().setNotification(noti);
		}
		this.notificationManager.notify(this.notificationID, noti);
	}

	public void setKillerService(CMCNotifyKiller s) {
		this.killer_service = new WeakReference<CMCNotifyKiller>(s);
	}

	private boolean hasNotification() {
		return this.killer_service != null && this.killer_service.get().getNotification() != null;
	}

	// Toggle the play/pause button
	public void updateIsPlaying(boolean isPlaying) {

		Log.i(TAG, "updateIsPlaying: isPlaying: " + isPlaying);


		if (this.infos == null || (isPlaying == this.infos.isPlaying && this.hasNotification())) {
			return;  // Not recreate the notification with the same data
		}

		Log.i(TAG, "updateIsPlaying: pre:this.infos.isPlaying: " + this.infos.isPlaying);

		this.infos.isPlaying=isPlaying;


		Log.i(TAG, "updateIsPlaying: post:this.infos.isPlaying: " + this.infos.isPlaying);

		this.createBuilder();
		this.createNotification();
	}

	// Toggle the dismissable status
	public void updateDismissable(boolean dismissable) {
		if (dismissable == this.infos.dismissable && hasNotification()) {
			return;  // Not recreate the notification with the same data
		}
		this.infos.dismissable=dismissable;
		this.createBuilder();
		this.createNotification();
	}

	public void updateIsPlayingDismissable(boolean isPlaying, boolean dismissable){
		if (dismissable == this.infos.dismissable && isPlaying == this.infos.isPlaying && hasNotification()) {
			return;  // Not recreate the notification with the same data
		}
		this.infos.isPlaying=isPlaying;
		this.infos.dismissable=dismissable;
		this.createBuilder();
		this.createNotification();
	}

	private void getBitmapCover(String coverURL){
		try{
			if(coverURL.matches("^(https?|ftp)://.*$"))
				this.bitmapCover = getBitmapFromURL(coverURL);
			else{
				this.bitmapCover = getBitmapFromLocal(coverURL);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private Bitmap getBitmapFromLocal(String localURL){
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
				InputStream fileStream = cordovaActivity.getAssets().open("public/" + localURL);
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

	private void createBuilder(){
		Context context = cordovaActivity.getApplicationContext();
		NotificationCompat.Builder builder = new NotificationCompat.Builder(context,CHANNEL_ID);

		if (Build.VERSION.SDK_INT >= 26) {
			builder.setChannelId(this.CHANNEL_ID);
		}

		builder.setContentTitle(this.infos.track);
		if (!this.infos.artist.isEmpty()){
			builder.setContentText(this.infos.artist);
		}
		builder.setWhen(0);

		if (this.infos.dismissable){
			builder.setOngoing(false);
			Intent dismissIntent = new Intent("music-controls-destroy");
			PendingIntent dismissPendingIntent;
			if (Build.VERSION.SDK_INT >= 23) {
				dismissPendingIntent = PendingIntent.getBroadcast(context, 1, dismissIntent, PendingIntent.FLAG_IMMUTABLE);
			} else {
				dismissPendingIntent = PendingIntent.getBroadcast(context, 1, dismissIntent, 0);
			}
			builder.setDeleteIntent(dismissPendingIntent);
		} else {
			builder.setOngoing(true);
		}
		if (!this.infos.ticker.isEmpty()){
			builder.setTicker(this.infos.ticker);
		}
		
		builder.setPriority(Notification.PRIORITY_MAX);
		builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

		boolean usePlayingIcon = this.infos.notificationIcon.isEmpty();
		if(!usePlayingIcon){
			int resId = this.getResourceId(this.infos.notificationIcon, 0);
			usePlayingIcon = resId == 0;
			if(!usePlayingIcon) {
				builder.setSmallIcon(resId);
			}
		}

		if(usePlayingIcon){
			if (this.infos.isPlaying){
				builder.setSmallIcon(this.getResourceId(this.infos.playIcon, android.R.drawable.ic_media_play));
			} else {
				builder.setSmallIcon(this.getResourceId(this.infos.pauseIcon, android.R.drawable.ic_media_pause));
			}
		}

		if (!this.infos.cover.isEmpty() && this.bitmapCover != null){
			builder.setLargeIcon(this.bitmapCover);
		}

		Intent resultIntent = new Intent(context, cordovaActivity.getClass());
		resultIntent.setAction(Intent.ACTION_MAIN);
		resultIntent.addCategory(Intent.CATEGORY_LAUNCHER);
		PendingIntent resultPendingIntent;
		if (Build.VERSION.SDK_INT >= 31) {
			resultPendingIntent = PendingIntent.getActivity(context, 0, resultIntent, PendingIntent.FLAG_IMMUTABLE);
		} else {
			resultPendingIntent = PendingIntent.getActivity(context, 0, resultIntent, 0);
		}
		builder.setContentIntent(resultPendingIntent);

		//Controls
		int nbControls=0;

		if (this.infos.hasOptions) {
			nbControls++;
			Intent optionsLikeIntent = new Intent("music-controls-liked");
			PendingIntent previousPendingIntent;
			if (Build.VERSION.SDK_INT >= 31) {
				previousPendingIntent = PendingIntent.getBroadcast(context, 1, optionsLikeIntent, PendingIntent.FLAG_IMMUTABLE);
			} else {
				previousPendingIntent = PendingIntent.getBroadcast(context, 1, optionsLikeIntent, 0);
			}
			builder.addAction(this.getResourceId(this.infos.thumbsUpIcon, R.drawable.thumb_up), "", previousPendingIntent);
		}

		if (this.infos.isPlaying){
			nbControls++;
			Intent pauseIntent = new Intent("music-controls-pause");
			PendingIntent pausePendingIntent;
			if (Build.VERSION.SDK_INT >= 31) {
				pausePendingIntent = PendingIntent.getBroadcast(context, 1, pauseIntent, PendingIntent.FLAG_IMMUTABLE);
			} else {
				pausePendingIntent = PendingIntent.getBroadcast(context, 1, pauseIntent, 0);
			}
			builder.addAction(this.getResourceId(this.infos.pauseIcon, android.R.drawable.ic_media_pause), "", pausePendingIntent);
		} else {
			nbControls++;
			Intent playIntent = new Intent("music-controls-play");
			PendingIntent playPendingIntent;
			if (Build.VERSION.SDK_INT >= 31) {
				playPendingIntent = PendingIntent.getBroadcast(context, 1, playIntent, PendingIntent.FLAG_IMMUTABLE);
			} else {
				playPendingIntent = PendingIntent.getBroadcast(context, 1, playIntent, 0);
			}
			builder.addAction(this.getResourceId(this.infos.playIcon, android.R.drawable.ic_media_play), "", playPendingIntent);
		}
		if (this.infos.hasNext){
			nbControls++;
			Intent nextIntent = new Intent("music-controls-next");
			PendingIntent nextPendingIntent;
			if (Build.VERSION.SDK_INT >= 31) {
				nextPendingIntent = PendingIntent.getBroadcast(context, 1, nextIntent, PendingIntent.FLAG_IMMUTABLE);
			} else {
				nextPendingIntent = PendingIntent.getBroadcast(context, 1, nextIntent, 0);
			}			
			builder.addAction(this.getResourceId(this.infos.nextIcon, android.R.drawable.ic_media_next), "", nextPendingIntent);
		}

		if (this.infos.hasOptions) {
			nbControls++;
			Intent optionsDislikeIntent = new Intent("music-controls-dislike");
			PendingIntent previousPendingIntent;
			if (Build.VERSION.SDK_INT >= 31) {
				previousPendingIntent = PendingIntent.getBroadcast(context, 1, optionsDislikeIntent, PendingIntent.FLAG_IMMUTABLE);
			} else {
				previousPendingIntent = PendingIntent.getBroadcast(context, 1, optionsDislikeIntent, 0);
			}
			builder.addAction(this.getResourceId(this.infos.thumbsUpIcon, R.drawable.thumb_down), "", previousPendingIntent);
		}

		if (this.infos.hasClose){
			nbControls++;
			Intent destroyIntent = new Intent("music-controls-destroy");
			PendingIntent destroyPendingIntent;
			if (Build.VERSION.SDK_INT >= 31) {
				destroyPendingIntent = PendingIntent.getBroadcast(context, 1, destroyIntent, PendingIntent.FLAG_IMMUTABLE);
			} else {
				destroyPendingIntent = PendingIntent.getBroadcast(context, 1, destroyIntent, 0);
			}			
			builder.addAction(this.getResourceId(this.infos.closeIcon, android.R.drawable.ic_menu_close_clear_cancel), "", destroyPendingIntent);
		}

		int[] args = new int[nbControls];
		for (int i = 0; i < nbControls; ++i) {
			args[i] = i;
		}
		androidx.media.app.NotificationCompat.MediaStyle  mediaStyle = new androidx.media.app.NotificationCompat.MediaStyle();
		mediaStyle.setMediaSession(this.token);
		mediaStyle.setShowActionsInCompactView(args);
		builder.setStyle(mediaStyle);
		
		this.notificationBuilder = builder;
	}

	private NotificationCompat.Action createAction(String drawableRes, int fallbackRes, PendingIntent intent) {
		int icon = getResourceId(drawableRes, fallbackRes);
		return new NotificationCompat.Action(icon, "", intent);
	}

	private int getResourceId(String name, int fallback){
		try{
			if(name.isEmpty()){
				return fallback;
			}

			int resId = this.cordovaActivity.getResources().getIdentifier(name, "drawable", this.cordovaActivity.getPackageName());
			return resId == 0 ? fallback : resId;
		}
		catch(Exception ex){
			return fallback;
		}
	}

	public void destroy(){
		Log.i(TAG, "Destroying notification");
		if (this.killer_service !=null) {
			this.killer_service.get().setNotification(null);
		}
		this.notificationManager.cancel(this.notificationID);
		Log.i(TAG, "Notification destroyed");
	}
}
