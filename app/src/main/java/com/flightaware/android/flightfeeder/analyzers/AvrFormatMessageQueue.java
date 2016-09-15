package com.flightaware.android.flightfeeder.analyzers;

import java.util.concurrent.LinkedBlockingQueue;

import com.flightaware.android.flightfeeder.analyzers.dump1090.ModeSMessage;

public class AvrFormatMessageQueue {
	private static final LinkedBlockingQueue<ModeSMessage> sMessageQueue = new LinkedBlockingQueue<ModeSMessage>(
			100);

	public static void offer(ModeSMessage message) {
		if (message != null)
			while (!sMessageQueue.offer(message))
				sMessageQueue.poll();
	}

	public static ModeSMessage take() {
		try {
			return sMessageQueue.take();
		} catch (Exception e) {
			// swallow
			return null;
		}
	}

	public static int size() {
		return sMessageQueue.size();
	}

	public static void clear() {
		sMessageQueue.clear();
	}
}
