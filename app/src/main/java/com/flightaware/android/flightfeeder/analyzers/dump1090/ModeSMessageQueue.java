package com.flightaware.android.flightfeeder.analyzers.dump1090;

import java.util.concurrent.LinkedBlockingQueue;

class ModeSMessageQueue {
	private static final LinkedBlockingQueue<ModeSMessage> sMessageQueue = new LinkedBlockingQueue<ModeSMessage>(
			1000);

	public static void offer(ModeSMessage message) {
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
}
