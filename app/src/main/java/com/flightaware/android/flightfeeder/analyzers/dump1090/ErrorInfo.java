package com.flightaware.android.flightfeeder.analyzers.dump1090;

class ErrorInfo {

	protected byte mBitCount; // Number of bit positions to fix
	protected int[] mBitPositions = new int[] { -1, -1 }; // Bit positions
															// corrected by this
															// syndrome
	protected int mSyndrome; // CRC syndrome

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;

		if (!(o instanceof ErrorInfo))
			return false;

		ErrorInfo lhs = (ErrorInfo) o;

		return lhs.mSyndrome == mSyndrome;
	}

	public byte getBitCount() {
		return mBitCount;
	}

	public int[] getBitPositions() {
		return mBitPositions;
	}

	public int getSyndrome() {
		return mSyndrome;
	}

	@Override
	public int hashCode() {
		return mSyndrome;
	}

	public void setBitCount(byte bitCount) {
		mBitCount = bitCount;
	}

	public void setBitPositions(int[] bitPositions) {
		mBitPositions = bitPositions;
	}

	public void setSyndrome(int syndrome) {
		mSyndrome = syndrome;
	}
}
