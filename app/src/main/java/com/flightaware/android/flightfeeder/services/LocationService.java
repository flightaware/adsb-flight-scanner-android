package com.flightaware.android.flightfeeder.services;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.support.v4.content.ContextCompat;

import com.flightaware.android.flightfeeder.App;
import com.flightaware.android.flightfeeder.BuildConfig;
import com.flightaware.android.flightfeeder.R;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;

public class LocationService extends Service implements
		GoogleApiClient.ConnectionCallbacks,
		GoogleApiClient.OnConnectionFailedListener, LocationListener {

	public static volatile LatLng sLocation;
	private GoogleApiClient mGoogleApiClient;
	private WakeLock mWakeLock;
	private Thread mRetryThread;

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onConnected(Bundle bundle) {

		if (ContextCompat.checkSelfPermission(this,
				Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager
				.PERMISSION_GRANTED) {
			Location location = LocationServices.FusedLocationApi
					.getLastLocation(mGoogleApiClient);

			onLocationChanged(location);

			LocationRequest locationRequest = LocationRequest.create();

			// Use high accuracy
			locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

			// Set the update interval to 5 minutes
			locationRequest.setInterval(5 * 60 * 1000);

			LocationServices.FusedLocationApi.requestLocationUpdates(
					mGoogleApiClient, locationRequest, this);
		} else
			stopSelf();
	}

	@Override
	public void onConnectionFailed(ConnectionResult result) {
		if (mRetryThread != null) {
			mRetryThread.interrupt();
			mRetryThread = null;
		}

		mRetryThread = new Thread() {
			@Override
			public void run() {
				SystemClock.sleep(30000);

				if (mGoogleApiClient != null)
					mGoogleApiClient.connect();

				mRetryThread = null;
			}
		};

		mRetryThread.start();
	}

	@Override
	public void onConnectionSuspended(int cause) {
		/*
		 * Remove location updates for a listener. The current service is the
		 * listener, so the argument is "this".
		 */
		LocationServices.FusedLocationApi.removeLocationUpdates(
				mGoogleApiClient, this);
	}

	@SuppressLint("InlinedApi")
	@Override
	public void onCreate() {
		super.onCreate();

		onLocationChanged(null);

		String appName = getString(R.string.app_name);

		PowerManager mgr = (PowerManager) getSystemService(Context.POWER_SERVICE);
		mWakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, appName
				+ " Location Service");
		mWakeLock.acquire();

		mGoogleApiClient = new GoogleApiClient.Builder(this)
				.addApi(LocationServices.API).addConnectionCallbacks(this)
				.addOnConnectionFailedListener(this).build();

		mGoogleApiClient.connect();

		if (BuildConfig.DEBUG)
			System.out.println("Location service started");
	}

	@Override
	public void onDestroy() {
		if (mRetryThread != null)
			mRetryThread.interrupt();

		if (mGoogleApiClient != null) {
			mGoogleApiClient.disconnect();
			mGoogleApiClient = null;
		}

		if (mWakeLock != null && mWakeLock.isHeld()) {
			mWakeLock.release();
			mWakeLock = null;
		}

		if (BuildConfig.DEBUG)
			System.out.println("Location service stopped");

		super.onDestroy();
	}

	@Override
	public void onLocationChanged(Location location) {
		if (location == null) {
			double lat = App.sPrefs.getFloat("latitude", 0);
			double lon = App.sPrefs.getFloat("longitude", 0);

			if (lat == lon && lat == 0)
				return;

			sLocation = new LatLng(lat, lon);
		} else {
			sLocation = new LatLng(location.getLatitude(),
					location.getLongitude());

			Editor editor = App.sPrefs.edit();
			editor.putFloat("latitude", (float) sLocation.latitude);
			editor.putFloat("longitude", (float) sLocation.longitude);
			editor.commit();
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return Service.START_STICKY;
	}
}
