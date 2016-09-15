package com.flightaware.android.flightfeeder.analyzers.dump978;

import android.content.Intent;
import android.os.SystemClock;

import com.flightaware.android.flightfeeder.App;
import com.flightaware.android.flightfeeder.BuildConfig;
import com.flightaware.android.flightfeeder.activities.MainActivity;
import com.flightaware.android.flightfeeder.analyzers.Aircraft;
import com.flightaware.android.flightfeeder.analyzers.Analyzer;
import com.flightaware.android.flightfeeder.util.MovingAverage;

public class DecodeFramesThread extends Thread {
	public DecodeFramesThread() {
		setName("DecodeUatFramesThread");
	}

	@Override
	public void run() {
		if (BuildConfig.DEBUG)
			System.out.println("Started decoding uat frames");

		// Start a child thread to compute the frame rate at 1 Hertz
		Thread thread = new Thread() {
			public void run() {
				while (!Dump978.sExit) {
					MovingAverage.addSample(Analyzer.sFrameCount);
					Analyzer.sFrameCount = 0;

					SystemClock.sleep(1000);
				}
			}
		};
		thread.setName("MovingAverage");
		thread.start();

		Intent intent = new Intent(MainActivity.ACTION_UPDATE_RX);

		while (!Dump978.sExit) {
			int[] frame = FrameQueue.take();

			if (frame == null)
				continue;

			Aircraft aircraft = Decoder.decodeUatFrame(frame);

			if (aircraft == null)
				continue;

			Analyzer.sFrameCount++;

			App.sBroadcastManager.sendBroadcast(intent);

			long now = SystemClock.uptimeMillis();
			if (!aircraft.isReady(now))
				continue;

			Analyzer.computeRange(aircraft);

			int altitude = aircraft.getAltitude();
			Integer vertRate = aircraft.getVerticalRate();
			Integer headingDelta = aircraft.getHeadingDelta();
		}

		if (BuildConfig.DEBUG)
			System.out.println("Stopped decoding uat frames");
	}
}
