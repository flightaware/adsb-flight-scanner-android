package com.flightaware.android.flightfeeder.analyzers;

import android.location.Location;
import android.text.TextUtils;

import com.flightaware.android.flightfeeder.services.LocationService;

public class Analyzer {

	private static final String DEFAULT_USBFS_PATH = "/dev/bus/usb";

	protected static Thread sDecodeThread;
	public static volatile int sFrameCount;
	public static volatile float sRange;
	protected static Thread sReadThread;

	protected static String getDeviceName(String deviceName) {
		deviceName = deviceName.trim();

		if (TextUtils.isEmpty(deviceName))
			return DEFAULT_USBFS_PATH;

		final String[] paths = deviceName.split("/");

		final StringBuilder sb = new StringBuilder();

		for (int i = 0; i < paths.length - 2; i++)
			if (i == 0)
				sb.append(paths[i]);
			else
				sb.append("/" + paths[i]);

		final String stripped_name = sb.toString().trim();

		if (stripped_name.isEmpty())
			return DEFAULT_USBFS_PATH;
		else
			return stripped_name;
	}

	public static void computeRange(final Aircraft aircraft) {
		if (LocationService.sLocation == null)
			return;

		float[] results = new float[1];
		Location.distanceBetween(LocationService.sLocation.latitude,
				LocationService.sLocation.longitude, aircraft.getLatitude(),
				aircraft.getLongitude(), results);

		// convert to nautical miles
		float tempRange = results[0] / 1852;
		if (tempRange > sRange && tempRange < 300)
			sRange = tempRange;
	}
}
