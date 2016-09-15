package com.flightaware.android.flightfeeder;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;

import com.flightaware.android.flightfeeder.analyzers.NanoWebServer;
import com.flightaware.android.flightfeeder.receivers.ConnectivityChangedReceiver;

public class App extends Application implements
		OnSharedPreferenceChangeListener {

	public static LocalBroadcastManager sBroadcastManager;
	private static ComponentName sComponentName;
	private static ConnectivityManager sConnectivityManager;
	public static Context sContext;
	public static volatile boolean sIsConnected;
	private static volatile long sLastCheck;
	public static volatile boolean sOnAccessPoint;
	private static PackageManager sPackageManager;
	public static SharedPreferences sPrefs;
	public static long sStartTime;
	public static String sVersion;
	public static NanoWebServer sWebServer;

	public static boolean isInternetAvailable() {
		long now = SystemClock.uptimeMillis();

		if (now - sLastCheck > 2000) {
			sLastCheck = now;

			NetworkInfo info = sConnectivityManager.getActiveNetworkInfo();

			sIsConnected = info != null && info.isAvailable()
					&& info.isConnected();

			sOnAccessPoint = sIsConnected
					&& info != null
					&& (info.getType() == ConnectivityManager.TYPE_WIFI || info
							.getType() == ConnectivityManager.TYPE_ETHERNET);

			int state = sPackageManager
					.getComponentEnabledSetting(sComponentName);

			// if there is no access point and the component is disabled, enable
			// it
			if (!sOnAccessPoint) {
				if (App.sWebServer != null) {
					App.sWebServer.stop();
					App.sWebServer = null;
				}

				if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
					sPackageManager.setComponentEnabledSetting(sComponentName,
							PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
							PackageManager.DONT_KILL_APP);
				}
			}
		}

		return sIsConnected;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		sContext = this;

		sPrefs = PreferenceManager.getDefaultSharedPreferences(this);

		sPrefs.registerOnSharedPreferenceChangeListener(this);

		sConnectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

		sComponentName = new ComponentName(this,
				ConnectivityChangedReceiver.class);

		sPackageManager = getPackageManager();

		sBroadcastManager = LocalBroadcastManager.getInstance(this);

		sStartTime = SystemClock.uptimeMillis();

		try {
			PackageInfo packageInfo = sPackageManager.getPackageInfo(
					getPackageName(), 0);
			sVersion = packageInfo.versionName;
		} catch (Exception e) {
			e.printStackTrace();
		}

		isInternetAvailable();
		if (sPrefs.getBoolean("pref_broadcast", true) && sOnAccessPoint) {
			sWebServer = new NanoWebServer(this);

			try {
				sWebServer.start();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		new Thread() {
			@Override
			public void run() {
				isInternetAvailable();

				SystemClock.sleep(5000);
			}
		}.start();
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {

		if (key.equals("pref_broadcast")) {
			isInternetAvailable();

			if (sPrefs.getBoolean("pref_broadcast", true) && sOnAccessPoint) {
				if (sWebServer != null) {
					sWebServer.stop();
					sWebServer = null;
				}

				sWebServer = new NanoWebServer(this);

				try {
					sWebServer.start();
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else if (sWebServer != null) {
				sWebServer.stop();
				sWebServer = null;
			}
		}
	}
}
