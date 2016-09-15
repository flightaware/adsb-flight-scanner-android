package com.flightaware.android.flightfeeder.analyzers.dump1090;

import com.flightaware.android.flightfeeder.BuildConfig;

public class ComputeMagnitudeVectorThread extends Thread {

	private static final int[][] sMagLut = new int[256][256];
	static {
		buildMagnitudeLookUpTable();
	}

	public ComputeMagnitudeVectorThread() {
		setName("ComputeMagnitudeVectorThread");
	}

	private static void buildMagnitudeLookUpTable() {
		// I rewrote this method to use a more natural 2-d array instead of
		// a 1-d array with a weird index.

		float mag_i = 0;
		float mag_q = 0;

		for (int i = 0; i < 256; i++) {
			mag_i = i * 2 - 255;

			for (int q = 0; q < 256; q++) {
				mag_q = q * 2 - 255;

				sMagLut[i][q] = (int) Math.round((Math.sqrt(mag_i * mag_i
						+ mag_q * mag_q) * 258.433254) - 365.4798);
			}
		}
	}

	private void computeMagnitudeVector(byte[] data) {
		int[] vector = new int[data.length / 2];
		int i = 0;
		int q = 0;

		try {
			for (int j = 0; j < data.length; j += 2) {
				// In the original C file, the 'data' array here is an unsigned
				// char
				// data type, which is an 8-bit integer. Java has no equivalent
				// data
				// type. The closest thing is a byte, which is also 8 bits, but
				// signed.
				//
				// To get back to the original functionality, we must do a
				// bitwise
				// AND with 255 and store the result in an integer, and then
				// continue with the algorithm.

				i = data[j] & 0xFF;
				q = data[j + 1] & 0xFF;

				vector[j / 2] = sMagLut[i][q];
			}

			MagnitudeVectorQueue.offer(vector);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	@Override
	public void run() {
		if (BuildConfig.DEBUG)
			System.out.println("Started generating vectors");

		while (!Dump1090.sExit) {
			byte[] data = RtlSdrDataQueue.take();

			if (data == null)
				continue;

			computeMagnitudeVector(data);
		}

		if (BuildConfig.DEBUG)
			System.out.println("Stopped generating vectors");
	}
}
