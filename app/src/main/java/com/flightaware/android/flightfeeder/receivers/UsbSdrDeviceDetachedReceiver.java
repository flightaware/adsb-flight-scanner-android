package com.flightaware.android.flightfeeder.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.flightaware.android.flightfeeder.activities.MainActivity;

public class UsbSdrDeviceDetachedReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		intent.setClass(context, MainActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

		context.startActivity(intent);
	}
}
