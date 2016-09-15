package com.flightaware.android.flightfeeder.analyzers;

import android.os.SystemClock;

public class RawPosition {
	private int mNucp;
	private int mRawLatitude;
	private int mRawLongitude;
	private long mTimestamp;

	public RawPosition(int rawLatitude, int rawLongitude, int nucp) {
		mNucp = nucp;
		mRawLatitude = rawLatitude;
		mRawLongitude = rawLongitude;
		mTimestamp = SystemClock.uptimeMillis();
	}

	public int getNucp() {
		return mNucp;
	}

	public int getRawLatitude() {
		return mRawLatitude;
	}

	public int getRawLongitude() {
		return mRawLongitude;
	}

	public long getTimestamp() {
		return mTimestamp;
	}

	public void setNucp(int nucp) {
		mNucp = nucp;
	}

	public void setRawLatitude(int rawLatitude) {
		mRawLatitude = rawLatitude;
	}

	public void setRawLongitude(int rawLongitude) {
		mRawLongitude = rawLongitude;
	}

	public void setTimestamp(long timestamp) {
		mTimestamp = timestamp;
	}
}
