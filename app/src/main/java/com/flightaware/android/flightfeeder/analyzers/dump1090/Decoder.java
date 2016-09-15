package com.flightaware.android.flightfeeder.analyzers.dump1090;

import android.location.Location;
import android.text.TextUtils;
import android.util.LruCache;
import android.util.SparseArray;

import com.flightaware.android.flightfeeder.BuildConfig;
import com.flightaware.android.flightfeeder.analyzers.Aircraft;
import com.flightaware.android.flightfeeder.analyzers.RawPosition;
import com.flightaware.android.flightfeeder.analyzers.RecentAircraftCache;
import com.flightaware.android.flightfeeder.services.LocationService;

public class Decoder {

	public static enum CorrectionLevel {
		NONE, ONE_BIT, TWO_BIT
	}

	private static final int MAX_RANGE = 200 * 1852; // 200 nm in meters, cannot
														// be larger than 360 nm
	private static final int GS_MAX_RANGE = Math.min(MAX_RANGE,
			360 * 1852 - MAX_RANGE);

	private static final char[] sAisCharSet = new char[] { '?', 'A', 'B', 'C',
			'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P',
			'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', '?', '?', '?',
			'?', '?', ' ', '?', '?', '?', '?', '?', '?', '?', '?', '?', '?',
			'?', '?', '?', '?', '?', '0', '1', '2', '3', '4', '5', '6', '7',
			'8', '9', '?', '?', '?', '?', '?', '?' };

	/*
	 * ===================== Mode S detection and decoding ===================
	 * 
	 * Parity table for MODE S Messages. The table contains 112 elements, every
	 * element corresponds to a bit set in the message, starting from the first
	 * bit of actual data after the preamble.
	 * 
	 * For messages of 112 bit, the whole table is used. For messages of 56 bits
	 * only the last 56 elements are used.
	 * 
	 * The algorithm is as simple as xoring all the elements in this table for
	 * which the corresponding bit on the message is set to 1.
	 * 
	 * The latest 24 elements in this table are set to 0 as the checksum at the
	 * end of the message should not affect the computation.
	 * 
	 * Note: this function can be used with DF11 and DF17, other modes have the
	 * CRC xored with the sender address as they are reply to interrogations,
	 * but a casual listener can't split the address from the checksum.
	 */
	private static final int[] sCheckSumTable = { 0x3935ea, 0x1c9af5, 0xf1b77e,
			0x78dbbf, 0xc397db, 0x9e31e9, 0xb0e2f0, 0x587178, 0x2c38bc,
			0x161c5e, 0x0b0e2f, 0xfa7d13, 0x82c48d, 0xbe9842, 0x5f4c21,
			0xd05c14, 0x682e0a, 0x341705, 0xe5f186, 0x72f8c3, 0xc68665,
			0x9cb936, 0x4e5c9b, 0xd8d449, 0x939020, 0x49c810, 0x24e408,
			0x127204, 0x093902, 0x049c81, 0xfdb444, 0x7eda22, 0x3f6d11,
			0xe04c8c, 0x702646, 0x381323, 0xe3f395, 0x8e03ce, 0x4701e7,
			0xdc7af7, 0x91c77f, 0xb719bb, 0xa476d9, 0xadc168, 0x56e0b4,
			0x2b705a, 0x15b82d, 0xf52612, 0x7a9309, 0xc2b380, 0x6159c0,
			0x30ace0, 0x185670, 0x0c2b38, 0x06159c, 0x030ace, 0x018567,
			0xff38b7, 0x80665f, 0xbfc92b, 0xa01e91, 0xaff54c, 0x57faa6,
			0x2bfd53, 0xea04ad, 0x8af852, 0x457c29, 0xdd4410, 0x6ea208,
			0x375104, 0x1ba882, 0x0dd441, 0xf91024, 0x7c8812, 0x3e4409,
			0xe0d800, 0x706c00, 0x383600, 0x1c1b00, 0x0e0d80, 0x0706c0,
			0x038360, 0x01c1b0, 0x00e0d8, 0x00706c, 0x003836, 0x001c1b,
			0xfff409, 0x000000, 0x000000, 0x000000, 0x000000, 0x000000,
			0x000000, 0x000000, 0x000000, 0x000000, 0x000000, 0x000000,
			0x000000, 0x000000, 0x000000, 0x000000, 0x000000, 0x000000,
			0x000000, 0x000000, 0x000000, 0x000000, 0x000000, 0x000000,
			0x000000 };

	/*
	 * setup a cache of previous defined bit errors and fixes
	 */
	private static final SparseArray<ErrorInfo> sErrorInfoArray = new SparseArray<ErrorInfo>();

	protected static CorrectionLevel sLevel = CorrectionLevel.NONE;

	private static long sNow;

	/*
	 * hold no more than 250 planes, each for no more than 15 minutes
	 */
	private static final LruCache<Integer, Integer> sRecentIcaoCache = new LruCache<Integer, Integer>(
			250);

	//
	// =========================================================================
	//
	private static double cprDlonFunction(double lat, boolean isOdd,
			boolean isGround) {
		return (isGround ? 90.0 : 360.0) / cprNFunction(lat, isOdd);
	}

	//
	// =========================================================================
	//
	// Always positive MOD operation, used for CPR decoding.
	//
	private static int cprModFunction(int a, int b) {
		int res = a % b;
		if (res < 0)
			res += b;
		return res;
	};

	//
	// =========================================================================
	//
	private static int cprNFunction(double lat, boolean isOdd) {
		int nl = cprNLFunction(lat) - (isOdd ? 1 : 0);
		if (nl < 1)
			nl = 1;
		return nl;
	}

	//
	// =========================================================================
	//
	// The NL function uses the precomputed table from 1090-WP-9-14
	//
	private static int cprNLFunction(double lat) {
		if (lat < 0)
			lat = -lat; // Table is symmetric about the equator
		if (lat < 10.47047130)
			return 59;
		if (lat < 14.82817437)
			return 58;
		if (lat < 18.18626357)
			return 57;
		if (lat < 21.02939493)
			return 56;
		if (lat < 23.54504487)
			return 55;
		if (lat < 25.82924707)
			return 54;
		if (lat < 27.93898710)
			return 53;
		if (lat < 29.91135686)
			return 52;
		if (lat < 31.77209708)
			return 51;
		if (lat < 33.53993436)
			return 50;
		if (lat < 35.22899598)
			return 49;
		if (lat < 36.85025108)
			return 48;
		if (lat < 38.41241892)
			return 47;
		if (lat < 39.92256684)
			return 46;
		if (lat < 41.38651832)
			return 45;
		if (lat < 42.80914012)
			return 44;
		if (lat < 44.19454951)
			return 43;
		if (lat < 45.54626723)
			return 42;
		if (lat < 46.86733252)
			return 41;
		if (lat < 48.16039128)
			return 40;
		if (lat < 49.42776439)
			return 39;
		if (lat < 50.67150166)
			return 38;
		if (lat < 51.89342469)
			return 37;
		if (lat < 53.09516153)
			return 36;
		if (lat < 54.27817472)
			return 35;
		if (lat < 55.44378444)
			return 34;
		if (lat < 56.59318756)
			return 33;
		if (lat < 57.72747354)
			return 32;
		if (lat < 58.84763776)
			return 31;
		if (lat < 59.95459277)
			return 30;
		if (lat < 61.04917774)
			return 29;
		if (lat < 62.13216659)
			return 28;
		if (lat < 63.20427479)
			return 27;
		if (lat < 64.26616523)
			return 26;
		if (lat < 65.31845310)
			return 25;
		if (lat < 66.36171008)
			return 24;
		if (lat < 67.39646774)
			return 23;
		if (lat < 68.42322022)
			return 22;
		if (lat < 69.44242631)
			return 21;
		if (lat < 70.45451075)
			return 20;
		if (lat < 71.45986473)
			return 19;
		if (lat < 72.45884545)
			return 18;
		if (lat < 73.45177442)
			return 17;
		if (lat < 74.43893416)
			return 16;
		if (lat < 75.42056257)
			return 15;
		if (lat < 76.39684391)
			return 14;
		if (lat < 77.36789461)
			return 13;
		if (lat < 78.33374083)
			return 12;
		if (lat < 79.29428225)
			return 11;
		if (lat < 80.24923213)
			return 10;
		if (lat < 81.19801349)
			return 9;
		if (lat < 82.13956981)
			return 8;
		if (lat < 83.07199445)
			return 7;
		if (lat < 83.99173563)
			return 6;
		if (lat < 84.89166191)
			return 5;
		if (lat < 85.75541621)
			return 4;
		if (lat < 86.53536998)
			return 3;
		if (lat < 87.00000000)
			return 2;
		else
			return 1;
	}

	private static int decodeAc12Field(int ac12Field) {
		int q_bit = ac12Field & 0x10; // Bit 48 = Q

		if (q_bit == 0x10) {
			// / N is the 11 bit integer resulting from the removal of bit Q at
			// bit 4
			int n = ((ac12Field & 0x0FE0) >> 1) | (ac12Field & 0x000F);
			// The final altitude is the resulting number multiplied by 25,
			// minus 1000.
			return ((n * 25) - 1000);
		} else {
			// Make N a 13 bit Gillham coded altitude by inserting M=0 at bit 6
			int n = ((ac12Field & 0x0FC0) << 1) | (ac12Field & 0x003F);
			n = modeAToModeC(decodeId13Field(n));
			if (n < -12)
				n = 0;

			return (100 * n);
		}
	}

	private static int decodeAc13Field(int ac13Field) {
		int m_bit = ac13Field & 0x0040; // set = meters, clear = feet
		int q_bit = ac13Field & 0x0010; // set = 25 ft encoding, clear = Gillham
										// Mode C encoding

		if (m_bit != 0x0040) {
			if (q_bit == 0x0010) {
				// N is the 11 bit integer resulting from the removal of bit Q
				// and M
				int n = ((ac13Field & 0x1F80) >> 2)
						| ((ac13Field & 0x0020) >> 1) | (ac13Field & 0x000F);
				// The final altitude is resulting number multiplied by 25,
				// minus 1000.
				return ((n * 25) - 1000);
			} else {
				// N is an 11 bit Gillham coded altitude
				int n = modeAToModeC(decodeId13Field(ac13Field));
				if (n < -12)
					n = 0;

				return (100 * n);
			}
		}

		return 0;
	}

	private static boolean decodeCpr(Aircraft aircraft, boolean useOdd) {
		RawPosition evenPosition = aircraft.getEvenPosition();
		RawPosition oddPosition = aircraft.getOddPosition();

		if (evenPosition == null || oddPosition == null)
			return false;

		if (Math.abs(evenPosition.getTimestamp() - oddPosition.getTimestamp()) > 10000)
			return false;

		double AirDlat0 = (aircraft.isOnGround() ? 90.0 : 360.0) / 60.0;
		double AirDlat1 = (aircraft.isOnGround() ? 90.0 : 360.0) / 59.0;
		double lat0 = evenPosition.getRawLatitude();
		double lat1 = oddPosition.getRawLatitude();
		double lon0 = evenPosition.getRawLongitude();
		double lon1 = oddPosition.getRawLongitude();

		// Compute the Latitude Index "j"
		int j = (int) Math.floor(((59 * lat0 - 60 * lat1) / 131072) + 0.5);
		double rlat0 = AirDlat0 * (cprModFunction(j, 60) + lat0 / 131072);
		double rlat1 = AirDlat1 * (cprModFunction(j, 59) + lat1 / 131072);

		// initialize to something bogus to test for below
		double surface_rlat = -1000;
		double surface_rlon = -1000;

		if (aircraft.isOnGround()) {
			// If we're on the ground, make sure we have a (likely) valid
			// Lat/Lon
			if (sNow - aircraft.getSeenLatLon() < 60000) {
				surface_rlat = aircraft.getLatitude();
				surface_rlon = aircraft.getLongitude();
			} else if (LocationService.sLocation != null) {
				surface_rlat = LocationService.sLocation.latitude;
				surface_rlon = LocationService.sLocation.longitude;
			}

			if (surface_rlat != -1000 && surface_rlon != -1000) {
				// Move from 1st quadrant to our quadrant
				rlat0 += Math.floor(surface_rlat / 90.0) * 90.0;
				rlat1 += Math.floor(surface_rlat / 90.0) * 90.0;
			}
		} else {
			if (rlat0 >= 270)
				rlat0 -= 360;
			if (rlat1 >= 270)
				rlat1 -= 360;
		}

		// Check to see that the latitude is in range: -90 .. +90
		if (rlat0 < -90 || rlat0 > 90 || rlat1 < -90 || rlat1 > 90)
			return false;

		// Check that both are in the same latitude zone, or abort.
		if (cprNLFunction(rlat0) != cprNLFunction(rlat1))
			return false;

		// Compute ni and the Longitude Index "m"
		double lat = 0;
		double lon = 0;
		if (useOdd) {
			int ni = cprNFunction(rlat1, true);
			int m = (int) Math
					.floor((((lon0 * (cprNLFunction(rlat1) - 1)) - (lon1 * cprNLFunction(rlat1))) / 131072.0) + 0.5);
			lon = cprDlonFunction(rlat1, true, aircraft.isOnGround())
					* (cprModFunction(m, ni) + lon1 / 131072);
			lat = rlat1;
		} else {
			int ni = cprNFunction(rlat0, false);
			int m = (int) Math
					.floor((((lon0 * (cprNLFunction(rlat0) - 1)) - (lon1 * cprNLFunction(rlat0))) / 131072) + 0.5);
			lon = cprDlonFunction(rlat0, false, aircraft.isOnGround())
					* (cprModFunction(m, ni) + lon0 / 131072);
			lat = rlat0;
		}

		if (aircraft.isOnGround() && surface_rlon != -1000)
			// Move from 1st quadrant to our quadrant
			lon += Math.floor(surface_rlon / 90.0) * 90.0;
		else if (lon > 180)
			lon -= 360;

		if (LocationService.sLocation == null)
			return false;

		float[] results = new float[1];
		Location.distanceBetween(LocationService.sLocation.latitude,
				LocationService.sLocation.longitude, lat, lon, results);

		// make sure the position is within the reasonable range
		// filters out bad transponder data
		if (results[0] > MAX_RANGE)
			return false;

		aircraft.setLatitude(lat);
		aircraft.setLongitude(lon);

		aircraft.setSeenLatLon(sNow);

		// throw these away, can't use them again
		evenPosition = null;
		oddPosition = null;

		aircraft.setEvenPosition(null);
		aircraft.setOddPosition(null);

		return true;
	}

	private static boolean decodeCprRelative(Aircraft aircraft, boolean useOdd) {
		RawPosition evenPosition = aircraft.getEvenPosition();
		RawPosition oddPosition = aircraft.getOddPosition();

		if ((useOdd && oddPosition == null)
				|| (!useOdd && evenPosition == null))
			return false;

		double AirDlat;
		double AirDlon;
		double lat;
		double lon;
		double lonr = -1000, latr = -1000;
		double rlon, rlat;
		int j, m;
		int maxRange = MAX_RANGE;

		if (sNow - aircraft.getSeenLatLon() < 60000
				&& aircraft.getLatitude() != null
				&& aircraft.getLongitude() != null) { // Ok to try aircraft
														// relative
			// first
			latr = aircraft.getLatitude();
			lonr = aircraft.getLongitude();
		} else if (LocationService.sLocation != null) { // Try ground station
														// relative next
			latr = LocationService.sLocation.latitude;
			lonr = LocationService.sLocation.longitude;
			maxRange = GS_MAX_RANGE;
		} else
			return false;

		if (useOdd) { // odd
			AirDlat = (aircraft.isOnGround() ? 90.0 : 360.0) / 59.0;
			lat = oddPosition.getRawLatitude();
			lon = oddPosition.getRawLongitude();
		} else { // even
			AirDlat = (aircraft.isOnGround() ? 90.0 : 360.0) / 60.0;
			lat = evenPosition.getRawLatitude();
			lon = evenPosition.getRawLongitude();
		}
		// Compute the Latitude Index "j"
		j = (int) (Math.floor(latr / AirDlat) + trunc(0.5
				+ cprModFunction((int) latr, (int) AirDlat) / AirDlat - lat
				/ 131072));
		rlat = AirDlat * (j + lat / 131072);
		if (rlat >= 270)
			rlat -= 360;

		// Check to see that the latitude is in range: -90 .. +90
		if (rlat < -90 || rlat > 90)
			return false; // Time to give up - Latitude error

		// Check to see that answer is reasonable - ie no more than 1/2 cell
		// away
		if (Math.abs(rlat - latr) > (AirDlat / 2))
			return false; // Time to give up - Latitude error

		// Compute the Longitude Index "m"
		AirDlon = cprDlonFunction(rlat, useOdd, aircraft.isOnGround());
		m = (int) (Math.floor(lonr / AirDlon) + trunc(0.5
				+ cprModFunction((int) lonr, (int) AirDlon) / AirDlon - lon
				/ 131072));
		rlon = AirDlon * (m + lon / 131072);
		if (rlon > 180)
			rlon -= 360;

		// Check to see that answer is reasonable - ie no more than 1/2 cell
		// away
		if (Math.abs(rlon - lonr) > (AirDlon / 2))
			return false; // Time to give up - Longitude error

		float[] results = new float[1];
		Location.distanceBetween(LocationService.sLocation.latitude,
				LocationService.sLocation.longitude, rlat, rlon, results);

		// make sure the position is within the reasonable range
		// filters out bad transponder data
		if (results[0] > maxRange)
			return false;

		aircraft.setLatitude(rlat);
		aircraft.setLongitude(rlon);

		aircraft.setSeenLatLon(sNow);

		return true;
	}

	private static int decodeId13Field(int id13Field) {
		int hexGillham = 0;

		if ((id13Field & 0x1000) == 0x1000) {
			hexGillham |= 0x0010;
		} // Bit 12 = C1
		if ((id13Field & 0x0800) == 0x0800) {
			hexGillham |= 0x1000;
		} // Bit 11 = A1
		if ((id13Field & 0x0400) == 0x0400) {
			hexGillham |= 0x0020;
		} // Bit 10 = C2
		if ((id13Field & 0x0200) == 0x0200) {
			hexGillham |= 0x2000;
		} // Bit 9 = A2
		if ((id13Field & 0x0100) == 0x0100) {
			hexGillham |= 0x0040;
		} // Bit 8 = C4
		if ((id13Field & 0x0080) == 0x0080) {
			hexGillham |= 0x4000;
		} // Bit 7 = A4
			// if ((id13Field & 0x0040) == 0x0040) {
			// hexGillham |= 0x0800;
			// } // Bit 6 = X or M
		if ((id13Field & 0x0020) == 0x0020) {
			hexGillham |= 0x0100;
		} // Bit 5 = B1
		if ((id13Field & 0x0010) == 0x0010) {
			hexGillham |= 0x0001;
		} // Bit 4 = D1 or Q
		if ((id13Field & 0x0008) == 0x0008) {
			hexGillham |= 0x0200;
		} // Bit 3 = B2
		if ((id13Field & 0x0004) == 0x0004) {
			hexGillham |= 0x0002;
		} // Bit 2 = D2
		if ((id13Field & 0x0002) == 0x0002) {
			hexGillham |= 0x0400;
		} // Bit 1 = B4
		if ((id13Field & 0x0001) == 0x0001) {
			hexGillham |= 0x0004;
		} // Bit 0 = D4

		return (hexGillham);
	}

	private static String decodeIdentity(ModeSMessage message) {
		char[] ident = new char[8];

		int chars = (message.mBytes[5] << 16) | (message.mBytes[6] << 8)
				| message.mBytes[7];
		ident[3] = sAisCharSet[chars & 0x3F];
		chars >>= 6;
		ident[2] = sAisCharSet[chars & 0x3F];
		chars >>= 6;
		ident[1] = sAisCharSet[chars & 0x3F];
		chars >>= 6;
		ident[0] = sAisCharSet[chars & 0x3F];

		chars = (message.mBytes[8] << 16) | (message.mBytes[9] << 8)
				| message.mBytes[10];
		ident[7] = sAisCharSet[chars & 0x3F];
		chars >>= 6;
		ident[6] = sAisCharSet[chars & 0x3F];
		chars >>= 6;
		ident[5] = sAisCharSet[chars & 0x3F];
		chars >>= 6;
		ident[4] = sAisCharSet[chars & 0x3F];

		String identStr = new String(ident).replace(" ", "").replace("?", "")
				.trim();

		if (TextUtils.isEmpty(identStr))
			return null;

		return identStr;
	}

	/*
	 * Decode a raw Mode S message demodulated as a stream of bytes by
	 * detectModeS(), and split it into fields Aircraft structure.
	 */
	public static final Aircraft decodeModeS(ModeSMessage message) {

		int length = message.mBytes.length;
		if (length != 14 && length != 7)
			return null;

		int[] rawMsg = new int[length];
		System.arraycopy(message.mBytes, 0, rawMsg, 0, length);

		message.mCrc = getChecksum(message.mBytes);

		if (!message.isValid()) {
			//
			// Fixing single bit errors in DF-11 is a bit dodgy because we have
			// no way to know for sure if the crc is supposed to be 0 or not -
			// it could be any value less than 80. Therefore, attempting to fix
			// DF-11 errors can result in a multitude of possible crc solutions,
			// only one of which is correct.
			//

			fixBitErrors(message);

			if (message.mNumCorrectedBits > 0)
				message.mCrc = getChecksum(message.mBytes);
		}

		int format = message.getFormat();
		int icao = 0;
		if (message.isValid() && (format == 11 || format == 17 || format == 18)) {
			icao = message.getIcao();

			message.mCa = message.mBytes[0] & 0x07;

			if (icao > 0)
				sRecentIcaoCache.put(icao, 1);
		} else { // All other DF's

			// reset the message to the original bytes
			message = new ModeSMessage(rawMsg, message.mClockCount);
			message.mCrc = getChecksum(message.mBytes);

			if (message.getFormat() != 11
					|| (message.getFormat() == 11 && message.mCrc < 80)) {

				// Compare the checksum with the whitelist of recently seen ICAO
				// addresses. If it matches one, then declare the message as
				// valid
				if (sRecentIcaoCache.get(message.mCrc) != null) {
					icao = message.mCrc;
					message.mCrc = 0; // this makes the message valid
				}
			}
		}

		if (icao == 0 || !message.isValid())
			return null;

		sNow = System.currentTimeMillis();
		boolean positionFound = false;

		Aircraft aircraft = RecentAircraftCache.get(icao);
		if (aircraft == null) {
			aircraft = new Aircraft(icao);
			RecentAircraftCache.add(icao, aircraft);
		}

		aircraft.addSignalStrength(message.getSignalLevel());
		aircraft.setUat(false);
		aircraft.setReady(false);
		aircraft.setMessageCount(aircraft.getMessageCount() + 1);
		aircraft.setSeen(sNow);

		if (format == 5 || format == 21) {
			int id13Field = ((message.mBytes[2] << 8) | message.mBytes[3]) & 0x1FFF;
			if (id13Field > 0)
				aircraft.setSquawk(decodeId13Field(id13Field));
		}

		if (format == 4 || format == 5 || format == 16 || format == 20) {
			int ac13Field = ((message.mBytes[2] << 8) | message.mBytes[3]) & 0x1FFF;
			if (ac13Field > 0)
				aircraft.setAltitude(decodeAc13Field(ac13Field), sNow);
		}

		// Fields for DF4, DF5, DF20, DF21
		if (format == 4 || format == 5 || format == 20 || format == 21)
			aircraft.setStatus(message.mBytes[0] & 7); // Flight status for
														// DF4,5,20,21
		if (format == 17
				|| (format == 18 && (message.mCa == 0 || message.mCa == 1 || message.mCa == 6))) {
			if (length != 14)
				return null;

			int type = message.mBytes[4] >> 3; // Extended squitter message
												// type
			int subType = type == 29 ? (message.mBytes[4] & 6) >> 1
					: message.mBytes[4] & 7;

			if (type >= 1 && type <= 4) {
				aircraft.setIdentity(decodeIdentity(message));
				aircraft.setCategory(((0x0E - type) << 4) | subType);
			} else if (type == 19) { // velocity
				if (subType >= 1 && subType <= 4) {
					int vertRate = ((message.mBytes[8] & 0x07) << 6)
							| (message.mBytes[9] >> 2);
					if (vertRate > 0) {
						--vertRate;
						if ((message.mBytes[8] & 0x08) == 0x08)
							vertRate *= -1;

						vertRate *= 64;

						aircraft.setVerticalRate(vertRate);
					}
				}

				int velocity = Integer.MIN_VALUE;
				int heading = Integer.MAX_VALUE;

				if (subType == 1 || subType == 2) {
					int ewRaw = ((message.mBytes[5] & 0x03) << 8)
							| message.mBytes[6];
					int ewVel = ewRaw - 1;
					int nsRaw = ((message.mBytes[7] & 0x7F) << 3)
							| (message.mBytes[8] >> 5);
					int nsVel = nsRaw - 1;

					if (subType == 2) { // If (supersonic) unit is 4 kts
						nsVel = nsVel << 2;
						ewVel = ewVel << 2;
					}

					if ((message.mBytes[5] & 0x04) == 0x04)
						ewVel *= -1;

					if ((message.mBytes[7] & 0x80) == 0x80)
						nsVel *= -1;

					if (ewRaw > 0 && nsRaw > 0)
						velocity = (int) Math.hypot(nsVel, ewVel);

					if (velocity > 0)
						heading = (int) Math
								.toDegrees(Math.atan2(ewVel, nsVel));
				} else if (subType == 3 || subType == 4) {
					velocity = ((message.mBytes[7] & 0x7f) << 3)
							| (message.mBytes[8] >> 5);
					if (velocity > 0) {
						--velocity;
						if (subType == 4) // If (supersonic) unit is 4 kts
							velocity = velocity << 2;
					}

					if ((message.mBytes[5] & 0x04) == 0x04)
						heading = ((((message.mBytes[5] & 0x03) << 8) | message.mBytes[6]) * 45) >> 7;
				}

				if (heading < 0)
					heading += 360;
				else if (heading == 360)
					heading = 0;

				if (velocity != Integer.MIN_VALUE)
					aircraft.setVelocity(velocity, sNow);

				if (heading != Integer.MAX_VALUE)
					aircraft.setHeading(heading, sNow);
			} else if (type >= 5 && type <= 22) { // Position Message
				aircraft.setOnGround(type < 9);

				int altitude = Integer.MIN_VALUE;
				int velocity = Integer.MIN_VALUE;
				int heading = Integer.MAX_VALUE;

				if (aircraft.isOnGround()) { // ground
					int movement = ((message.mBytes[4] << 4) | (message.mBytes[5] >> 4)) & 0x007F;
					if (movement > 0 && movement < 125)
						velocity = decodeMovementField(movement);

					if ((message.mBytes[5] & 0x08) == 0x08)
						heading = ((((message.mBytes[5] << 4) | (message.mBytes[6] >> 4)) & 0x007F) * 45) >> 4;
				} else { // airborne
					int ac12Field = ((message.mBytes[5] << 4) | (message.mBytes[6] >> 4)) & 0x0FFF;
					if (ac12Field > 0)
						altitude = decodeAc12Field(ac12Field);
				}

				if (heading < 0)
					heading += 360;
				else if (heading == 360)
					heading = 0;

				if (altitude != Integer.MIN_VALUE)
					aircraft.setAltitude(altitude, sNow);

				if (velocity != Integer.MIN_VALUE)
					aircraft.setVelocity(velocity, sNow);

				if (heading != Integer.MAX_VALUE && !positionFound)
					aircraft.setHeading(heading, sNow);

				boolean odd = (message.mBytes[6] & 0x04) == 0x04;

				int rawLatitude = ((message.mBytes[6] & 3) << 15)
						| (message.mBytes[7] << 7) | (message.mBytes[8] >> 1);
				int rawLongitude = ((message.mBytes[8] & 1) << 16)
						| (message.mBytes[9] << 8) | (message.mBytes[10]);

				// Seen from at least:
				// 400F3F (Eurocopter ECC155 B1) - Bristow Helicopters
				// 4008F3 (BAE ATP) - Atlantic Airlines
				// 400648 (BAE ATP) - Atlantic Airlines
				// altitude == 0, longitude == 0, type == 15 and zeros in
				// latitude LSB.
				// Can alternate with valid reports having type == 14
				if (altitude > 0 || rawLongitude > 0
						|| (rawLatitude & 0x0fff) > 0 || type != 15) {

					int nucp = 0;
					switch (type) {
					case 5:
					case 9:
					case 20:
						nucp = 9;
						break;
					case 6:
					case 10:
					case 21:
						nucp = 8;
						break;
					case 7:
					case 11:
						nucp = 7;
						break;
					case 8:
					case 12:
						nucp = 6;
						break;
					case 13:
						nucp = 5;
						break;
					case 14:
						nucp = 4;
						break;
					case 15:
						nucp = 3;
						break;
					case 16:
						nucp = 2;
						break;
					case 17:
						nucp = 1;
						break;
					}

					// for mlat
					if (nucp > 5) {
						if (odd)
							aircraft.setOddMessage(message);
						else
							aircraft.setEvenMessage(message);
					}

					aircraft.addRawPosition(new RawPosition(rawLatitude,
							rawLongitude, nucp), odd);

					positionFound = decodeCpr(aircraft, odd);
				}

				if (!positionFound)
					positionFound = decodeCprRelative(aircraft, odd);

				aircraft.setReady(positionFound);
			} else if (type == 23) { // Test metype squawk field
				if (subType == 7) { // (see 1090-WP-15-20)
					int id13Field = (((message.mBytes[5] << 8) | message.mBytes[6]) & 0xFFF1) >> 3;
					if (id13Field > 0)
						aircraft.setSquawk(decodeId13Field(id13Field));
				}
			} else if (type == 24) { // Reserved for Surface System Status
			} else if (type == 28) { // Extended Squitter Aircraft Status
				if (subType == 1) { // Emergency status squawk field
					int id13Field = (((message.mBytes[5] << 8) | message.mBytes[6]) & 0x1FFF);
					if (id13Field > 0)
						aircraft.setSquawk(decodeId13Field(id13Field));
				}
			} else if (type == 29) { // Aircraft Trajectory Intent
				if (BuildConfig.DEBUG) {
					System.out.println(aircraft.getIdentity());
					System.out.println("29:" + subType);
				}

				if (subType == 0) {
					aircraft.setAltitudeSource((message.mBytes[5] & 0x40) == 0x40 ? "ASL"
							: "BARO");
					int selectedAlt = ((message.mBytes[5] << 9)
							| (message.mBytes[6] << 1) | (message.mBytes[7] >> 7)) & 0x3FF;
					if (selectedAlt < 1011) { // 1011 --- 1023 invalid
						selectedAlt = selectedAlt * 100 - 1000;

						aircraft.setSelectedAltitude(selectedAlt);

						if (BuildConfig.DEBUG)
							System.out.println("Alt: " + selectedAlt + " "
									+ aircraft.getAltitudeSource());
					}

					int selectedHeading = ((message.mBytes[7] << 4) | (message.mBytes[8] >> 4)) & 0x1FF;
					if (selectedHeading < 360) { // 360 --- 511 invalid
						if ((message.mBytes[8] & 0x8) == 0x0) { // heading
							aircraft.setSelectedHeading(selectedHeading);
							aircraft.setTrackAngle(null);

							if (BuildConfig.DEBUG)
								System.out.println("Head: " + selectedHeading);
						} else { // track angle
							aircraft.setSelectedHeading(null);
							aircraft.setTrackAngle(selectedHeading);

							if (BuildConfig.DEBUG)
								System.out.println("Track: " + selectedHeading);
						}
					}

					if (BuildConfig.DEBUG)
						System.out
								.println("Auto pilot: "
										+ ((message.mBytes[5] & 0x4) == 0x4 || (message.mBytes[8] & 0x4) == 0x4));

					aircraft.setAutoPilotEngaged((message.mBytes[8] & 0x4) == 0x4
							|| (message.mBytes[8] & 0x4) == 0x4);

					int eCode = message.mBytes[10] & 0x7;
					if (eCode == 0x1)
						aircraft.setSquawk(0x7700);
					else if (eCode == 0x4)
						aircraft.setSquawk(0x7600);
					else if (eCode == 0x5)
						aircraft.setSquawk(0x7500);
				} else if (subType == 1) {
					// Altitude
					aircraft.setAltitudeSource((message.mBytes[5] & 0x80) == 0x80 ? "FMS"
							: "MCP/FCU");
					int selectedAlt = ((message.mBytes[5] << 4) | (message.mBytes[6] >> 4)) & 0x7FF;
					if (selectedAlt > 0) {
						selectedAlt = selectedAlt * 32 - 32;

						aircraft.setSelectedAltitude(selectedAlt);

						if (BuildConfig.DEBUG)
							System.out.println("Alt: " + selectedAlt + " "
									+ aircraft.getAltitudeSource());
					}

					// Barometer
					float selectedBaro = ((message.mBytes[6] << 5) | (message.mBytes[7] >> 3)) & 0x1FF;
					selectedBaro = selectedBaro * 0.8f - 0.8f;

					aircraft.setBaroSetting(selectedBaro);

					if (BuildConfig.DEBUG)
						System.out.println("Baro: " + selectedBaro);

					// Heading
					if ((message.mBytes[7] & 0x4) == 0x4) { // validity check
						int selectedHeading = ((message.mBytes[7] << 7) | (message.mBytes[8] >> 1)) & 0xFF;
						selectedHeading = (int) Math
								.round(selectedHeading * 180.0 / 256);
						if ((message.mBytes[7] & 0x10) == 0x10) // negative bit
							selectedHeading *= -1;

						if (selectedHeading < 0)
							selectedHeading += 360;
						else if (selectedHeading == 360)
							selectedHeading = 0;

						aircraft.setSelectedHeading(selectedHeading);

						if (BuildConfig.DEBUG)
							System.out.println("Track: " + selectedHeading);
					}

					// Status
					if ((message.mBytes[9] & 0x2) == 0x2) {
						if (BuildConfig.DEBUG) {
							System.out.println("Auto pilot: "
									+ ((message.mBytes[9] & 0x1) == 0x1));
							System.out.println("VNav: "
									+ ((message.mBytes[10] & 0x80) == 0x80));
							System.out.println("Hold: "
									+ ((message.mBytes[10] & 0x40) == 0x40));
							System.out.println("Approach: "
									+ ((message.mBytes[10] & 0x10) == 0x10));
							System.out.println("TCAS: "
									+ ((message.mBytes[10] & 0x8) == 0x8));
						}

						aircraft.setAutoPilotEngaged((message.mBytes[9] & 0x1) == 0x1);
						aircraft.setVerticalNavEnabled((message.mBytes[10] & 0x80) == 0x80);
						aircraft.setAltitudeHoldEnabled((message.mBytes[10] & 0x40) == 0x40);
						aircraft.setOnApproach((message.mBytes[10] & 0x10) == 0x10);
						aircraft.setTcasEnabled((message.mBytes[10] & 0x8) == 0x8);
					} else {
						if (BuildConfig.DEBUG) {
							System.out.println("Auto pilot: false");
							System.out.println("VNav: false");
							System.out.println("Hold: false");
							System.out.println("Approach: false");
							System.out.println("TCAS: false");
						}

						aircraft.setAutoPilotEngaged(false);
						aircraft.setVerticalNavEnabled(false);
						aircraft.setAltitudeHoldEnabled(false);
						aircraft.setOnApproach(false);
						aircraft.setTcasEnabled(false);
					}
				}
			} else if (type == 30) { // Aircraft Operational Coordination

			} else if (type == 31) { // Aircraft Operational Status

			} else { // Other types

			}
		} else if (format == 20 || format == 21) {
			// Fields for DF20, DF21 Comm-B
			if (message.mBytes[4] == 0x20) // Aircraft Identification
				aircraft.setIdentity(decodeIdentity(message));
		}

		return aircraft;
	}

	private static int decodeMovementField(int movement) {
		int gspeed;

		// Note : movement codes 0, 125, 126 and 127 are all invalid, but they
		// are trapped before this function is called.

		if (movement > 123)
			gspeed = 199; // > 175kt
		else if (movement > 108)
			gspeed = ((movement - 108) * 5) + 100;
		else if (movement > 93)
			gspeed = ((movement - 93) * 2) + 70;
		else if (movement > 38)
			gspeed = ((movement - 38)) + 15;
		else if (movement > 12)
			gspeed = ((movement - 11) >> 1) + 2;
		else if (movement > 8)
			gspeed = ((movement - 6) >> 2) + 1;
		else
			gspeed = 0;

		return gspeed;
	}

	/*
	 * =========================================================================
	 * 
	 * Compute the table of all syndromes for 1-bit and 2-bit error vectors
	 */
	private static void fillErrorInfoList() {
		sErrorInfoArray.clear();

		if (sLevel == CorrectionLevel.ONE_BIT
				|| sLevel == CorrectionLevel.TWO_BIT) {
			int[] msg = new int[14];

			for (int i = 0; i < 112; i++) {
				int bytepos0 = i / 8;
				int mask0 = 1 << i % 8;
				msg[bytepos0] ^= mask0; // create error0
				ErrorInfo errorInfo0 = new ErrorInfo();
				errorInfo0.mSyndrome = getChecksum(msg);
				errorInfo0.mBitCount = 1;
				errorInfo0.mBitPositions[0] = i;

				sErrorInfoArray.put(errorInfo0.mSyndrome, errorInfo0);

				if (sLevel == CorrectionLevel.TWO_BIT) {
					for (int j = i + 1; j < 112; j++) {
						int bytepos1 = j / 8;
						int mask1 = 1 << j % 8;
						msg[bytepos1] ^= mask1; // create error1
						ErrorInfo errorInfo1 = new ErrorInfo();
						errorInfo1.mSyndrome = getChecksum(msg);
						errorInfo1.mBitCount = 2;
						errorInfo1.mBitPositions[0] = i;
						errorInfo1.mBitPositions[1] = j;

						sErrorInfoArray.put(errorInfo1.mSyndrome, errorInfo1);

						msg[bytepos1] ^= mask1; // revert error1
					}
				}

				msg[bytepos0] ^= mask0; // revert error0
			}
		}
	}

	private static void fixBitErrors(ModeSMessage message) {
		ErrorInfo ei = sErrorInfoArray.get(message.mCrc);

		if (ei == null)
			return;

		if (ei.mBitCount > sLevel.ordinal())
			return;

		int length = message.getBytes().length * 8;
		int offset = 112 - length;
		for (byte i = 0; i < ei.mBitCount; i++) {
			int bitpos = ei.mBitPositions[i] - offset;
			if ((bitpos < 0) || (bitpos >= length)) {
				return;
			}
		}

		byte res = 0;
		for (byte i = 0; i < ei.mBitCount; i++) {
			int bitpos = ei.mBitPositions[i] - offset;
			message.getBytes()[bitpos / 8] ^= 1 << bitpos % 8;
			res++;
		}

		message.mNumCorrectedBits = res;
	}

	private static int getChecksum(int[] testMsg) {
		int crc = 0;
		int length = testMsg.length;
		int bits = length * 8;
		int offset = bits == 112 ? 0 : 56;
		int bite = 0;

		bits -= 24; // exclude crc bits

		for (byte i = 0; i < bits; i++) {
			if (i % 8 == 0)
				bite = testMsg[i / 8];

			/* If bit is set, xor with corresponding table entry. */
			if ((bite & 0x80) == 0x80)
				crc ^= sCheckSumTable[i + offset];

			bite <<= 1;
		}

		int rem = (testMsg[length - 3] << 16) | (testMsg[length - 2] << 8)
				| testMsg[length - 1];

		return (crc ^ rem) & 0xFFFFFF;
	}

	public static void init(CorrectionLevel level) {
		sLevel = level;

		fillErrorInfoList();
	}

	private static int modeAToModeC(int modeA) {
		int fiveHundreds = 0;
		int oneHundreds = 0;

		if (((modeA & 0xFFFF888B) > 0 // D1 set is illegal. D2 set is > 62700ft
										// which
										// is unlikely
		|| ((modeA & 0x000000F0) == 0))) // C1,,C4 cannot be Zero
			return -9999;

		if ((modeA & 0x0010) == 0x0010) {
			oneHundreds ^= 0x007;
		} // C1
		if ((modeA & 0x0020) == 0x0020) {
			oneHundreds ^= 0x003;
		} // C2
		if ((modeA & 0x0040) == 0x0040) {
			oneHundreds ^= 0x001;
		} // C4

		// Remove 7s from OneHundreds (Make 7->5, and 5->7).
		if ((oneHundreds & 5) == 5) {
			oneHundreds ^= 2;
		}

		// Check for invalid codes, only 1 to 5 are valid
		if (oneHundreds > 5) {
			return -9999;
		}

		// if (ModeA & 0x0001) {FiveHundreds ^= 0x1FF;} // D1 never used for
		// altitude
		if ((modeA & 0x0002) == 0x0002) {
			fiveHundreds ^= 0x0FF;
		} // D2
		if ((modeA & 0x0004) == 0x0004) {
			fiveHundreds ^= 0x07F;
		} // D4

		if ((modeA & 0x1000) == 0x1000) {
			fiveHundreds ^= 0x03F;
		} // A1
		if ((modeA & 0x2000) == 0x2000) {
			fiveHundreds ^= 0x01F;
		} // A2
		if ((modeA & 0x4000) == 0x4000) {
			fiveHundreds ^= 0x00F;
		} // A4

		if ((modeA & 0x0100) == 0x0100) {
			fiveHundreds ^= 0x007;
		} // B1
		if ((modeA & 0x0200) == 0x0200) {
			fiveHundreds ^= 0x003;
		} // B2
		if ((modeA & 0x0400) == 0x0400) {
			fiveHundreds ^= 0x001;
		} // B4

		// Correct order of OneHundreds.
		if ((fiveHundreds & 1) == 1) {
			oneHundreds = 6 - oneHundreds;
		}

		return ((fiveHundreds * 5) + oneHundreds - 13);
	}

	private static double trunc(double d) {
		return (double) ((long) d);
	}

	private Decoder() {
	}
}
