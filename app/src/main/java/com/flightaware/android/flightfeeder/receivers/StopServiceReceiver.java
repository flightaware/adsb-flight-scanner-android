package com.flightaware.android.flightfeeder.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.flightaware.android.flightfeeder.services.ControllerService;

public class StopServiceReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		context.stopService(new Intent(context, ControllerService.class));
	}
}
