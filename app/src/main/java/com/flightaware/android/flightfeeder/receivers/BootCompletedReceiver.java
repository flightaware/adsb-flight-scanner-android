package com.flightaware.android.flightfeeder.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.SystemClock;

import com.flightaware.android.flightfeeder.App;
import com.flightaware.android.flightfeeder.services.ControllerService;
import com.flightaware.android.flightfeeder.services.LocationService;
import com.flightaware.android.flightfeeder.util.UsbDvbDetector;
import com.google.android.gms.maps.model.LatLng;

public class BootCompletedReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(final Context context, Intent intent) {
		if (App.sPrefs.getBoolean("pref_auto_start_boot", true)) {
			final UsbDevice device = UsbDvbDetector.isValidDeviceConnected(context);
			
			if (device != null) {
				UsbManager usbManager = (UsbManager) context
						.getSystemService(Context.USB_SERVICE);
				if (usbManager.hasPermission(device)
						|| App.sPrefs.getBoolean("usb_permission_granted",
								false)) {
					new Thread() {
						@Override
						public void run() {
							SystemClock.sleep(300000);

							Intent service = new Intent(context, ControllerService.class);
							service.putExtra(UsbManager.EXTRA_DEVICE, device);

							if (!App.sPrefs.getBoolean("override_location",
									false))
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
					}.start();
				}
			}
		}
	}
}
