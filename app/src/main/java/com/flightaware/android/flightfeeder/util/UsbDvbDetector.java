package com.flightaware.android.flightfeeder.util;

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import com.flightaware.android.flightfeeder.R;

import org.xmlpull.v1.XmlPullParser;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

public class UsbDvbDetector {

	private static HashSet<String> sDevices = new HashSet<String>();

	public static UsbDevice isValidDeviceConnected(Context context) {
		if (sDevices.size() == 0) {
			XmlResourceParser xrp = null;
			try {
				xrp = context.getResources().getXml(R.xml.device_filter);
				xrp.next();
				int eventType = xrp.getEventType();
				while (eventType != XmlPullParser.END_DOCUMENT) {
					if (eventType == XmlPullParser.START_TAG
							&& xrp.getName().equalsIgnoreCase("usb-device")) {
						String ident = xrp.getAttributeIntValue(0, -1) + "-"
								+ xrp.getAttributeIntValue(1, -1);

						if (!ident.contains("-1"))
							sDevices.add(ident);
					}

					eventType = xrp.next();
				}
			} catch (Exception ex) {
				// swallow
			} finally {
				if (xrp != null)
					xrp.close();
			}
		}

		UsbManager usbManager = (UsbManager) context
				.getSystemService(Context.USB_SERVICE);

		HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
		Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
		while (deviceIterator.hasNext()) {
			UsbDevice device = deviceIterator.next();
			String ident = device.getVendorId() + "-" + device.getProductId();

			if (sDevices.contains(ident))
				return device;
		}

		return null;
	}

	// prevent construction
	private UsbDvbDetector() {

	}
}
