package com.flightaware.android.flightfeeder.analyzers.dump978;

import java.util.Arrays;

import com.flightaware.android.flightfeeder.BuildConfig;

public class DetectFramesThread extends Thread {

	private static final long ADSB_SYNC_WORD = 0xEACDDA4E2L;
	private static final int LONG_FRAME_DATA_BITS = 272;
	private static final int LONG_FRAME_BITS = LONG_FRAME_DATA_BITS + 112;
	protected static final int LONG_FRAME_DATA_BYTES = LONG_FRAME_DATA_BITS / 8;
	private static final int MAX_SYNC_ERRORS = 4;
	private static final int SHORT_FRAME_DATA_BITS = 144;
	private static final int SHORT_FRAME_BITS = SHORT_FRAME_DATA_BITS + 96;
	protected static final int SHORT_FRAME_DATA_BYTES = SHORT_FRAME_DATA_BITS / 8;
	private static final int SYNC_BITS = 36;
	private static final long SYNC_MASK = 0xFFFFFFFFFL;
	private static final int UPLINK_BLOCK_DATA_BITS = 576;
	private static final int UPLINK_BLOCK_BITS = UPLINK_BLOCK_DATA_BITS + 160;
	private static final byte UPLINK_FRAME_BLOCKS = 6;
	private static final int UPLINK_FRAME_BITS = UPLINK_FRAME_BLOCKS
			* UPLINK_BLOCK_BITS;
	private static final int UPLINK_FRAME_BYTES = UPLINK_FRAME_BITS / 8;
	private static final int UPLINK_FRAME_DATA_BITS = UPLINK_FRAME_BLOCKS
			* UPLINK_BLOCK_DATA_BITS;
	protected static final int UPLINK_FRAME_DATA_BYTES = UPLINK_FRAME_DATA_BITS / 8;
	private static final long UPLINK_SYNC_WORD = 0x153225B1DL;

	private int mCenterDeltaPhi;
	private int[] mDemodBufA = new int[UPLINK_FRAME_BYTES];
	private int[] mDemodBufB = new int[UPLINK_FRAME_BYTES];
	private int[] mInterleaved = new int[UPLINK_FRAME_BYTES];
	private int[] mPhi;
	private int mRsErrorsA;
	private int mRsErrorsB;

	public DetectFramesThread() {
		setName("DetectFramesThread");
	}

	// check that there is a valid sync word starting at 'phi' that matches the
	// sync word 'pattern'. Place the dphi threshold to use for bit slicing in
	// 'mCenterDPhi'. Return 1 if the sync word is OK, 0 on failure
	private boolean checkSyncWord(int index, long pattern) {
		int deltaPhiZeroTotal = 0;
		int zeroBits = 0;
		int deltaPhiOneTotal = 0;
		int oneBits = 0;
		int errorBits = 0;
		int deltaPhi = 0;
		long mask = 0;
		byte i = 0;

		// find mean dphi for zero and one bits; take the mean of the two as our
		// central value
		for (i = 0; i < SYNC_BITS; i++) {
			deltaPhi = phiDifference(mPhi[index + i * 2], mPhi[index + i * 2
					+ 1]);

			mask = 1L << (35 - i);

			if ((pattern & mask) == mask) {
				oneBits++;
				deltaPhiOneTotal += deltaPhi;
			} else {
				zeroBits++;
				deltaPhiZeroTotal += deltaPhi;
			}
		}

		deltaPhiZeroTotal /= zeroBits;
		deltaPhiOneTotal /= oneBits;

		mCenterDeltaPhi = (deltaPhiOneTotal + deltaPhiZeroTotal) / 2;

		// recheck sync word using our center value
		errorBits = 0;
		for (i = 0; i < SYNC_BITS && errorBits <= MAX_SYNC_ERRORS; i++) {
			deltaPhi = phiDifference(mPhi[index + i * 2], mPhi[index + i * 2
					+ 1]);

			mask = 1L << (35 - i);

			if ((pattern & mask) == mask) {
				// this should be a '1', above the center value
				if (deltaPhi < mCenterDeltaPhi)
					errorBits++;
			} else {
				// this should be a '0', below the center value
				if (deltaPhi >= mCenterDeltaPhi)
					errorBits++;
			}
		}

		return errorBits <= MAX_SYNC_ERRORS;
	}

	// Demodulate an adsb frame
	// with the first sync bit in 'phi', storing the frame into 'to'
	// of length up to LONG_FRAME_BYTES. Set '*rs_errors' to the
	// number of corrected errors, or 9999 if demodulation failed.
	// Return 0 if demodulation failed, or the number of bits (not
	// samples) consumed if demodulation was OK.
	private int demodAdsbFrame(int index, boolean isA) {
		if (!checkSyncWord(index, ADSB_SYNC_WORD)) {
			if (isA)
				mRsErrorsA = 9999;
			else
				mRsErrorsB = 9999;

			return 0;
		}

		int[] results = new int[2];

		if (isA) {
			demodFrame(index + SYNC_BITS * 2, mDemodBufA);
			Fec.correctAdsbFrame(mDemodBufA, results);
			mRsErrorsA = results[1];
		} else {
			demodFrame(index + SYNC_BITS * 2, mDemodBufB);
			Fec.correctAdsbFrame(mDemodBufB, results);
			mRsErrorsB = results[1];
		}

		int frameType = results[0];
		if (frameType == 2) {
			return (SYNC_BITS + LONG_FRAME_BITS);
		} else if (frameType == 1) {
			return (SYNC_BITS + SHORT_FRAME_BITS);
		} else
			return 0;
	}

	// demodulate bytes from samples at 'mPhi[index]' into 'frame', using
	// 'mCenterDPhi' as the bit slicing threshold
	private void demodFrame(int index, int[] frame) {
		int b;
		for (int i = 0; i < frame.length; i++) {
			b = 0;
			if (phiDifference(mPhi[index], mPhi[index + 1]) > mCenterDeltaPhi)
				b |= 0x80;
			if (phiDifference(mPhi[index + 2], mPhi[index + 3]) > mCenterDeltaPhi)
				b |= 0x40;
			if (phiDifference(mPhi[index + 4], mPhi[index + 5]) > mCenterDeltaPhi)
				b |= 0x20;
			if (phiDifference(mPhi[index + 6], mPhi[index + 7]) > mCenterDeltaPhi)
				b |= 0x10;
			if (phiDifference(mPhi[index + 8], mPhi[index + 9]) > mCenterDeltaPhi)
				b |= 0x08;
			if (phiDifference(mPhi[index + 10], mPhi[index + 11]) > mCenterDeltaPhi)
				b |= 0x04;
			if (phiDifference(mPhi[index + 12], mPhi[index + 13]) > mCenterDeltaPhi)
				b |= 0x02;
			if (phiDifference(mPhi[index + 14], mPhi[index + 15]) > mCenterDeltaPhi)
				b |= 0x01;

			frame[i] = b;

			index += 16;
		}
	}

	// Demodulate an uplink frame
	// with the first sync bit in 'phi', storing the frame into 'to'
	// of length up to UPLINK_FRAME_BYTES. Set '*rs_errors' to the
	// number of corrected errors, or 9999 if demodulation failed.
	// Return 0 if demodulation failed, or the number of bits (not
	// samples) consumed if demodulation was OK.
	private int demodUplinkFrame(int index, boolean isA) {
		if (!checkSyncWord(index, UPLINK_SYNC_WORD)) {
			if (isA)
				mRsErrorsA = 9999;
			else
				mRsErrorsB = 9999;

			return 0;
		}

		demodFrame(index + SYNC_BITS * 2, mInterleaved);

		int[] results = new int[2];

		// deinterleave and correct
		if (isA) {
			Fec.correctUplinkFrame(mInterleaved, mDemodBufA, results);
			mRsErrorsA = results[1];
		} else {
			Fec.correctUplinkFrame(mInterleaved, mDemodBufB, results);
			mRsErrorsB = results[1];
		}

		int frametype = results[0];
		if (frametype == 1) {
			return (UPLINK_FRAME_BITS + SYNC_BITS);
		} else
			return 0;
	}

	private void handleAdsbFrame(int[] frame, int errors) {
		if (frame[0] >> 3 == 0)
			FrameQueue.offer(Arrays.copyOf(frame, SHORT_FRAME_DATA_BYTES));
		else
			FrameQueue.offer(Arrays.copyOf(frame, LONG_FRAME_DATA_BYTES));
	}

	private void handleUplinkFrame(int[] frame, int errors) {
		// UatFrameQueue.offer(Arrays.copyOf(frame, UPLINK_FRAME_DATA_BYTES));
	}

	private int phiDifference(int from, int to) {
		int difference = to - from; // lies in the range -65535 .. +65535
		if (difference >= 32768) // +32768..+65535
			return difference - 65536; // -> -32768..-1: always in range
		else if (difference < -32768) // -65535..-32769
			return difference + 65536; // -> +1..32767: always in range
		else
			return difference;
	}

	private void processPhi() {
		long syncA = 0;
		long syncB = 0;
		int startBit = 0;
		boolean isAdsbSyncA = false;
		boolean isUplinkSyncA = false;
		int index = 0;
		int skipA = 0;
		int skipB = 0;

		// We expect samples at twice the UAT bitrate.
		// We look at phase difference between pairs of adjacent samples, i.e.
		// sample 1 - sample 0 -> sync0
		// sample 2 - sample 1 -> sync1
		// sample 3 - sample 2 -> sync0
		// sample 4 - sample 3 -> sync1
		// ...
		//
		// We accumulate bits into two buffers, sync0 and sync1.
		// Then we compare those buffers to the expected 36-bit sync word that
		// should be at the start of each UAT frame. When (if) we find it,
		// that tells us which sample to start decoding from.

		// Stop when we run out of remaining samples for a max-sized frame.

		int lenbits = mPhi.length / 2 - SYNC_BITS - UPLINK_FRAME_BITS;
		for (int bit = 0; bit < lenbits; bit++) {
			syncA = ((syncA << 1) | (phiDifference(mPhi[bit * 2],
					mPhi[bit * 2 + 1]) > mCenterDeltaPhi ? 1 : 0)) & SYNC_MASK;
			syncB = ((syncB << 1) | (phiDifference(mPhi[bit * 2 + 1],
					mPhi[bit * 2 + 2]) > mCenterDeltaPhi ? 1 : 0)) & SYNC_MASK;

			if (bit < SYNC_BITS)
				continue; // haven't fully populated sync0/1 yet

			// see if we have (the start of) a valid sync word
			// It would be nice to look at popcount(expected ^ sync)
			// so we can tolerate some errors, but that turns out
			// to be very expensive to do on every sample

			// when we find a match, try to demodulate both with that match
			// and with the next position, and pick the one with fewer
			// errors.

			// check for downlink frames:
			isAdsbSyncA = syncWordFuzzyCompare(syncA, ADSB_SYNC_WORD);

			if (isAdsbSyncA || syncWordFuzzyCompare(syncB, ADSB_SYNC_WORD)) {
				startBit = bit - SYNC_BITS + 1;
				index = startBit * 2 + (isAdsbSyncA ? 0 : 1);

				mRsErrorsA = mRsErrorsB = -1;

				skipA = demodAdsbFrame(index, true);
				skipB = demodAdsbFrame(index + 1, false);

				if (skipA > 0 && mRsErrorsA <= mRsErrorsB) {
					handleAdsbFrame(mDemodBufA, mRsErrorsA);
					bit = startBit + skipA;
				} else if (skipB > 0 && mRsErrorsB <= mRsErrorsA) {
					handleAdsbFrame(mDemodBufB, mRsErrorsB);
					bit = startBit + skipB;
				} else {
					// demod failed
				}

				continue;
			}

			// check for uplink frames
			isUplinkSyncA = syncWordFuzzyCompare(syncA, UPLINK_SYNC_WORD);

			if (isUplinkSyncA || syncWordFuzzyCompare(syncB, UPLINK_SYNC_WORD)) {
				startBit = bit - SYNC_BITS + 1;
				index = startBit * 2 + (isUplinkSyncA ? 0 : 1);

				mRsErrorsA = mRsErrorsB = -1;

				skipA = demodUplinkFrame(index, true);
				skipB = demodUplinkFrame(index + 1, false);

				if (skipA > 0 && mRsErrorsA <= mRsErrorsB) {
					handleUplinkFrame(mDemodBufA, mRsErrorsA);
					bit = startBit + skipA;
				} else if (skipB > 0 && mRsErrorsB <= mRsErrorsA) {
					handleUplinkFrame(mDemodBufB, mRsErrorsB);
					bit = startBit + skipB;
				} else {
					// demod failed
				}
			}
		}
	}

	@Override
	public void run() {
		if (BuildConfig.DEBUG)
			System.out.println("Started analyzing phi");

		while (!Dump978.sExit) {
			int[] phi = PhiQueue.take();

			if (phi == null)
				continue;

			mPhi = phi;

			processPhi();
		}

		if (BuildConfig.DEBUG)
			System.out.println("Stopped analyzing phi");
	}

	private boolean syncWordFuzzyCompare(long syncWord, long compareTo) {
		if (syncWord == compareTo)
			return true;

		long diff = (syncWord ^ compareTo); // guaranteed nonzero

		// This is a bit-twiddling popcount hack, tweaked as we only care about
		// "<N" or ">=N" set bits for fixed N - so we can bail out early after
		// seeing N set bits.
		//
		// It relies on starting with a nonzero value with zero or more trailing
		// clear bits after the last set bit:
		//
		// 010101010101010000
		// ^
		// Subtracting one, will flip the bits starting at the last set bit:
		//
		// 010101010101001111
		// ^
		// then we can use that as a bitwise-and mask to clear the lowest set
		// bit:
		//
		// 010101010101000000
		// ^
		// And repeat until the value is zero or we have seen too many set bits.

		// >= 1 bit
		diff &= (diff - 1); // clear lowest set bit
		if (diff == 0)
			return true; // 1 bit error

		// >= 2 bits
		diff &= (diff - 1); // clear lowest set bit
		if (diff == 0)
			return true; // 2 bit errors

		// >= 3 bits
		diff &= (diff - 1); // clear lowest set bit
		if (diff == 0)
			return true; // 3 bit errors

		// >= 4 bits
		diff &= (diff - 1); // clear lowest set bit
		if (diff == 0)
			return true; // 4 bit errors

		// > 4 bit errors, give up
		return false;
	}
}
