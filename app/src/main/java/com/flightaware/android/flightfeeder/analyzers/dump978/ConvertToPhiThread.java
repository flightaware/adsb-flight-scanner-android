package com.flightaware.android.flightfeeder.analyzers.dump978;

import com.flightaware.android.flightfeeder.BuildConfig;

public class ConvertToPhiThread extends Thread {

	private static final int[][] sPhiLut = new int[256][256];

	static {
		buildPhiLookUpTable();
	}

	public ConvertToPhiThread() {
		setName("ConvertToPhiThread");
	}

	private static void buildPhiLookUpTable() {
		double d_i = 0;
		double d_q = 0;
		double ang = 0;
		double scaledAng = 0;

		for (int i = 0; i < 256; i++) {
			for (int q = 0; q < 256; q++) {
				d_i = i - 127.5;
				d_q = q - 127.5;
				ang = Math.atan2(d_q, d_i) + Math.PI; // atan2 returns
														// [-pi..pi],
														// normalize to
														// [0..2*pi]
				scaledAng = Math.round(32767.5 * ang / Math.PI);

				// bound the value between 0 and 65535
				scaledAng = Math.max(0, scaledAng);
				scaledAng = Math.min(65535, scaledAng);

				sPhiLut[i][q] = (char) scaledAng;
			}
		}
	}

	private void convertToPhi(byte[] data) {
		int[] phi = new int[data.length / 2];
		int i = 0;
		int q = 0;

		for (int j = 0; j < data.length; j += 2) {
			i = data[j] & 0xFF;
			q = data[j + 1] & 0xFF;

			phi[j / 2] = sPhiLut[i][q];
		}

		PhiQueue.offer(phi);
	}

	@Override
	public void run() {
		if (BuildConfig.DEBUG)
			System.out.println("Started computing phi");

		while (!Dump978.sExit) {
			byte[] data = RtlSdrDataQueue.take();

			if (data == null)
				continue;

			convertToPhi(data);
		}

		if (BuildConfig.DEBUG)
			System.out.println("Stopped computing phi");
	}
}
