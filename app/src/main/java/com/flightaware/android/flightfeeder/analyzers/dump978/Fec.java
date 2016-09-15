package com.flightaware.android.flightfeeder.analyzers.dump978;


public class Fec {
	static {
		System.loadLibrary("fec");
	}

	public static native void init();

	public static native void correctAdsbFrame(int[] to, int[] retvals);

	public static native void correctUplinkFrame(int[] from, int[] to,
			int[] retvals);
}
