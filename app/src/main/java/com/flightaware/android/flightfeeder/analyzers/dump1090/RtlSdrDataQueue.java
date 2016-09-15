package com.flightaware.android.flightfeeder.analyzers.dump1090;

import java.util.concurrent.LinkedBlockingQueue;

class RtlSdrDataQueue {
	private static final LinkedBlockingQueue<byte[]> sDataQueue = new LinkedBlockingQueue<byte[]>(
			100);

	public static void offer(byte[] data) {
		while (!sDataQueue.offer(data))
			sDataQueue.poll();
	}

	public static byte[] take() {
		try {
			return sDataQueue.take();
		} catch (Exception e) {
			// swallow
			return null;
		}
	}

	public static int size() {
		return sDataQueue.size();
	}
}
