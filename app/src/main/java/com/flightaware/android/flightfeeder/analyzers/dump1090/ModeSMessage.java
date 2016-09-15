package com.flightaware.android.flightfeeder.analyzers.dump1090;

import java.util.Locale;

public class ModeSMessage {

	protected int[] mBytes; // Binary message.
	protected int mCa; // Responder capabilities
	protected long mClockCount;
	protected int mCrc; // crc from the raw message
	protected byte mNumCorrectedBits; // Number of bits corrected
	protected double mSignalLevel;

	public ModeSMessage(int[] msg, long clockCount) {
		mBytes = msg;
		mClockCount = clockCount;
	}

	public int[] getBytes() {
		return mBytes;
	}

	public String getBytesAsString() {
		StringBuilder builder = new StringBuilder();
		for (int bite : mBytes) {
			String hex = String.format("%02X", bite & 0xFF);

			builder.append(hex);
		}

		return builder.toString().toUpperCase(Locale.US);
	}

	public long getClockCount() {
		return mClockCount;
	}

	public int getFormat() {
		return mBytes[0] >> 3;
	}

	public int getIcao() {
		return (mBytes[1] << 16) | (mBytes[2] << 8) | mBytes[3];
	}

	public byte getNumCorrectedBits() {
		return mNumCorrectedBits;
	}

	public double getSignalLevel() {
		return mSignalLevel;
	}

	public boolean isValid() {
		return mCrc == 0;
	}
}