package com.flightaware.android.flightfeeder.services;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.BitmapFactory;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;

import com.flightaware.android.flightfeeder.App;
import com.flightaware.android.flightfeeder.BuildConfig;
import com.flightaware.android.flightfeeder.R;
import com.flightaware.android.flightfeeder.activities.MainActivity;
import com.flightaware.android.flightfeeder.analyzers.Analyzer;
import com.flightaware.android.flightfeeder.analyzers.AvrFormatExporter;
import com.flightaware.android.flightfeeder.analyzers.BeastFormatExporter;
import com.flightaware.android.flightfeeder.analyzers.dump1090.Dump1090;
import com.flightaware.android.flightfeeder.analyzers.dump978.Dump978;
import com.flightaware.android.flightfeeder.util.MovingAverage;

public class ControllerService extends Service implements
		OnSharedPreferenceChangeListener {

	/**
	 * Class for clients to access. Because we know this service always runs in
	 * the same process as its clients, we don't need to deal with IPC.
	 */
	public class LocalBinder extends Binder {
		public ControllerService getService() {
			return ControllerService.this;
		}
	}

	private IBinder mBinder;
	private UsbDevice mDevice;
	private volatile boolean mDoUat;
	private boolean mIsScanning;
	private Thread mScanTimer;
	private UsbManager mUsbManager;
	private WakeLock mWakeLock;
	private WifiLock mWifiLock;

	private void changeModes() {
		if (!Dump1090.sExit)
			Dump1090.stop();

		if (!Dump978.sExit)
			Dump978.stop();

		mDoUat = !mDoUat;

		startScanning();

		App.sBroadcastManager.sendBroadcast(new Intent(
				MainActivity.ACTION_MODE_CHANGE));
	}

	public boolean isScanning() {
		return mIsScanning;
	}

	public boolean isUat() {
		return mDoUat;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	@SuppressLint("InlinedApi")
	@Override
	public void onCreate() {
		super.onCreate();
		mBinder = new LocalBinder();

		String appName = getString(R.string.app_name);

		PowerManager mgr = (PowerManager) getSystemService(Context.POWER_SERVICE);
		mWakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, appName
				+ " Controller Service");
		mWakeLock.acquire();

		WifiManager wifiMan = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		mWifiLock = wifiMan.createWifiLock(
				WifiManager.WIFI_MODE_FULL_HIGH_PERF, appName
						+ " Controller Service");
		mWifiLock.acquire();

		mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

		App.sPrefs.registerOnSharedPreferenceChangeListener(this);

		if (App.sPrefs.getBoolean("pref_beast", false))
			BeastFormatExporter.start();

		if (App.sPrefs.getBoolean("pref_avr", false))
			AvrFormatExporter.start();

		if (BuildConfig.DEBUG)
			System.out.println("Controller service started");
	}

	@Override
	public void onDestroy() {
		App.sPrefs.unregisterOnSharedPreferenceChangeListener(this);

		if (mWakeLock != null && mWakeLock.isHeld()) {
			mWakeLock.release();
			mWakeLock = null;
		}

		if (mWifiLock != null && mWifiLock.isHeld()) {
			mWifiLock.release();
			mWifiLock = null;
		}

		stopScanning(false);

		BeastFormatExporter.stop();

		AvrFormatExporter.stop();

		if (BuildConfig.DEBUG)
			System.out.println("Controller service stopped");

		super.onDestroy();
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		if (key.equals("pref_scan_mode")) {
			if (mScanTimer != null) {
				mScanTimer.interrupt();
				mScanTimer = null;
			}

			changeModes();
		} else if (key.equals("pref_mlat")) {
			stopScanning(false);

			startScanning();
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (!mIsScanning && intent != null
				&& intent.hasExtra(UsbManager.EXTRA_DEVICE)) {
			UsbDevice device = intent
					.getParcelableExtra(UsbManager.EXTRA_DEVICE);
			if (device != null) {
				mDevice = device;
				startScanning();
				showNotification();
			}
		}

		return Service.START_STICKY;
	}

	public void setUsbDevice(UsbDevice device) {
		mDevice = device;
	}

	public void showNotification() {
		Intent resultIntent = new Intent(this, MainActivity.class);

		PendingIntent resultPendingIntent = PendingIntent.getActivity(this, 0,
				resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);

		Intent stopIntent = new Intent(
				"com.flightaware.android.flightfeeder.STOP");

		PendingIntent pendingStopIntent = PendingIntent.getBroadcast(this, 0,
				stopIntent, PendingIntent.FLAG_UPDATE_CURRENT);

		String title = getString(R.string.app_name);
		String notice = getString(R.string.text_running_in_background);

		NotificationCompat.BigTextStyle style = new NotificationCompat.BigTextStyle();
		style.setBigContentTitle(title).bigText(notice);

		NotificationCompat.Builder builder = new NotificationCompat.Builder(
				this)
				.setContentTitle(title)
				.setContentText(notice)
				.setStyle(new NotificationCompat.BigTextStyle())
				.setTicker(notice)
				.setSmallIcon(R.drawable.ic_stat_notify)
				.setLargeIcon(
						BitmapFactory.decodeResource(getResources(),
								R.drawable.ic_launcher))
				.setContentIntent(resultPendingIntent)
				.setOngoing(true)
				.setWhen(0)
				.setStyle(style)
				.setPriority(NotificationCompat.PRIORITY_MAX)
				.addAction(R.drawable.ic_delete, getString(R.string.text_stop),
						pendingStopIntent);

		startForeground(R.string.app_name, builder.build());
	}

	public void startScanning() {
		Analyzer.sFrameCount = 0;
		MovingAverage.reset();

		String scanMode = App.sPrefs.getString("pref_scan_mode", "ADSB");

		try {
			if (scanMode.equals("ADSB")) {
				mDoUat = false;
				Dump1090.start(mUsbManager, mDevice);
			} else if (scanMode.equals("UAT")) {
				mDoUat = true;
				Dump978.start(mUsbManager, mDevice);
			} else {
				if (mDoUat)
					Dump978.start(mUsbManager, mDevice);
				else {
					Dump1090.start(mUsbManager, mDevice);
				}

				mScanTimer = new Thread() {
					@Override
					public void run() {
						if (mDoUat)
							SystemClock.sleep(20000); // 20 seconds
						else
							SystemClock.sleep(40000); // 40 seconds

						changeModes();
					}
				};
				mScanTimer.start();
			}

			mIsScanning = true;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void stopScanning(boolean allowAutoRestart) {
		if (mScanTimer != null) {
			mScanTimer.interrupt();
			mScanTimer = null;
		}

		if (!Dump1090.sExit)
			Dump1090.stop();

		if (!Dump978.sExit)
			Dump978.stop();

		mIsScanning = allowAutoRestart;
	}
}
