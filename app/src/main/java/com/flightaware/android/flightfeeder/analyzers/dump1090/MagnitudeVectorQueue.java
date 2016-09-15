package com.flightaware.android.flightfeeder.analyzers.dump1090;

import java.util.concurrent.LinkedBlockingQueue;

class MagnitudeVectorQueue {
	private static final LinkedBlockingQueue<int[]> sVectorQueue = new LinkedBlockingQueue<int[]>(
			100);

	public static void offer(int[] vector) {
		while (!sVectorQueue.offer(vector))
			sVectorQueue.poll();
	}

	public static int[] take() {
		try {
			return sVectorQueue.take();
		} catch (Exception e) {
			// swallow
			return null;
		}
	}

	public static int size() {
		return sVectorQueue.size();
	}
}
