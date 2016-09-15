package com.flightaware.android.flightfeeder.analyzers.dump978;

import java.util.concurrent.LinkedBlockingQueue;

class FrameQueue {
	private static final LinkedBlockingQueue<int[]> sFrameQueue = new LinkedBlockingQueue<int[]>(
			1000);

	public static void offer(int[] data) {
		while (!sFrameQueue.offer(data))
			sFrameQueue.poll();
	}

	public static int[] take() {
		try {
			return sFrameQueue.take();
		} catch (Exception e) {
			// swallow
			return null;
		}
	}

	public static int size() {
		return sFrameQueue.size();
	}
}
