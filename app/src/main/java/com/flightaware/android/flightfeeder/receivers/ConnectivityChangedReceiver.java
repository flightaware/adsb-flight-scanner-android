package com.flightaware.android.flightfeeder.receivers;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import com.flightaware.android.flightfeeder.App;
import com.flightaware.android.flightfeeder.analyzers.NanoWebServer;
import com.flightaware.android.flightfeeder.services.ControllerService;
import com.flightaware.android.flightfeeder.services.LocationService;
import com.flightaware.android.flightfeeder.util.UsbDvbDetector;
import com.google.android.gms.maps.model.LatLng;

public class ConnectivityChangedReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		App.isInternetAvailable();

		if (App.sPrefs.getBoolean("pref_broadcast", true) && App.sOnAccessPoint) {
			if (App.sWebServer != null) {
				App.sWebServer.stop();
				App.sWebServer = null;
			}

			App.sWebServer = new NanoWebServer(context);

			try {
				App.sWebServer.start();
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else if (App.sWebServer != null) {
			App.sWebServer.stop();
			App.sWebServer = null;
		}

		UsbDevice device = UsbDvbDetector.isValidDeviceConnected(context);
		if (device != null) {
			UsbManager usbManager = (UsbManager) context
					.getSystemService(Context.USB_SERVICE);
			if (usbManager.hasPermission(device)
					|| App.sPrefs.getBoolean("usb_permission_granted", false)) {
				
				Intent service = new Intent(context, ControllerService.class);
				service.putExtra(UsbManager.EXTRA_DEVICE, device);

				if (!App.sPrefs.getBoolean("override_location", false))
					context.startService(new Intent(context,
							LocationService.class));
				else if (LocationService.sLocation == null
						&& App.sPrefs.contains("latitude")
						&& App.sPrefs.contains("longitude")) {
					float lat = App.sPrefs.getFloat("latitude", 0);
					float lon = App.sPrefs.getFloat("longitude", 0);

					LocationService.sLocation = new LatLng(lat, lon);
				}

				context.startService(service);
			}
		}
		
		// if there is a connection disable this component
		if (App.sOnAccessPoint) {
			context.getPackageManager().setComponentEnabledSetting(
					new ComponentName(context,
							ConnectivityChangedReceiver.class),
					PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
					PackageManager.DONT_KILL_APP);
		}
	}
}
