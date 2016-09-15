package com.flightaware.android.flightfeeder.analyzers.dump978;

import java.util.concurrent.LinkedBlockingQueue;

class PhiQueue {
	private static final LinkedBlockingQueue<int[]> sPhiQueue = new LinkedBlockingQueue<int[]>(
			100);

	public static void offer(int[] vector) {
		while (!sPhiQueue.offer(vector))
			sPhiQueue.poll();
	}

	public static int[] take() {
		try {
			return sPhiQueue.take();
		} catch (Exception e) {
			// swallow
			return null;
		}
	}

	public static int size() {
		return sPhiQueue.size();
	}
}
