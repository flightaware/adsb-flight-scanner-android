package com.flightaware.android.flightfeeder.analyzers.dump978;

import android.text.TextUtils;

import com.flightaware.android.flightfeeder.analyzers.RecentAircraftCache;
import com.flightaware.android.flightfeeder.analyzers.Aircraft;

public class Decoder {

	private static final char[] sBase40Alphabet = new char[] { '0', '1', '2',
			'3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F',
			'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S',
			'T', 'U', 'V', 'W', 'X', 'Y', 'Z', ' ', ' ', '.', '.' };

	private static void decodeAuxSv(int[] frame, Aircraft aircraft) {
		int raw_alt = (frame[29] << 4) | ((frame[30] & 0xf0) >> 4);

		if (raw_alt != 0) {
			int sec_altitude = (raw_alt - 1) * 25 - 1000;

			if (aircraft.getAltitude() == 0)
				aircraft.setAltitude(sec_altitude, sNow);
		}
	}

	private static long sNow;

	private static void decodeMs(int[] frame, Aircraft aircraft) {
		char[] callSign = new char[9];

		int v = (frame[17] << 8) | (frame[18]);
		callSign[0] = sBase40Alphabet[(v / 40) % 40];
		callSign[1] = sBase40Alphabet[v % 40];

		v = (frame[19] << 8) | (frame[20]);
		callSign[2] = sBase40Alphabet[(v / 1600) % 40];
		callSign[3] = sBase40Alphabet[(v / 40) % 40];
		callSign[4] = sBase40Alphabet[v % 40];

		v = (frame[21] << 8) | (frame[22]);
		callSign[5] = sBase40Alphabet[(v / 1600) % 40];
		callSign[6] = sBase40Alphabet[(v / 40) % 40];
		callSign[7] = sBase40Alphabet[v % 40];

		callSign[8] = 0;

		if (callSign[0] > 0) {
			String text = new String(callSign).trim();

			if (TextUtils.isEmpty(text))
				return;

			if ((frame[26] & 0x02) == 0x02)
				aircraft.setIdentity(text);
			else if (TextUtils.isDigitsOnly(text))
				aircraft.setSquawk(Integer.parseInt(text, 16));
		}
	}

	private static int decodeSv(int[] frame, Aircraft aircraft) {
		int pri_alt = 0;

		int nic = frame[11] & 15;

		long rawLat = (frame[4] << 15) | (frame[5] << 7) | (frame[6] >> 1);
		long rawLon = ((frame[6] & 0x01) << 23) | (frame[7] << 15)
				| (frame[8] << 7) | (frame[9] >> 1);

		if (nic != 0 || rawLat != 0 || rawLon != 0) {
			double lat = rawLat * 360.0 / 16777216.0;
			if (lat > 90)
				lat -= 180;
			double lon = rawLon * 360.0 / 16777216.0;
			if (lon > 180)
				lon -= 360;

			aircraft.setLatitude(lat);
			aircraft.setLongitude(lon);
			aircraft.setReady(true);
		}

		long rawAlt = (frame[10] << 4) | ((frame[11] & 0xF0) >> 4);
		if (rawAlt != 0) {
			pri_alt = (int) ((rawAlt - 1) * 25 - 1000);
			aircraft.setAltitude(pri_alt, sNow);
		}

		aircraft.setOnGround(((frame[12] >> 6) & 0x03) == 2);

		if (aircraft.isOnGround()) {
			int raw_gs = ((frame[12] & 0x1f) << 6) | ((frame[13] & 0xfc) >> 2);
			if (raw_gs != 0) {
				int speed = ((raw_gs & 0x3ff) - 1);
				aircraft.setVelocity(speed, sNow);
			}

			int raw_heading = ((frame[13] & 0x03) << 9) | (frame[14] << 1)
					| ((frame[15] & 0x80) >> 7);
			if (((raw_heading & 0x0600) >> 9) > 0) {
				int heading = (raw_heading & 0x1ff) * 360 / 512;
				aircraft.setHeading(heading, sNow);
			}

			// mdb->position_offset = (frame[15] & 0x04) ? 1 : 0;
		} else {
			int raw_vvel = 0, ns_vel = 0, ew_vel = 0;
			boolean ew_vel_valid = false, ns_vel_valid = false;

			int raw_ns = ((frame[12] & 0x1f) << 6) | ((frame[13] & 0xfc) >> 2);
			if ((raw_ns & 0x3ff) != 0) {
				ns_vel_valid = true;
				ns_vel = ((raw_ns & 0x3ff) - 1);
				if ((raw_ns & 0x400) == 0x400)
					ns_vel *= -1;
				if (((frame[12] >> 6) & 0x03) == 1)
					ns_vel *= 4;
			}

			int raw_ew = ((frame[13] & 0x03) << 9) | (frame[14] << 1)
					| ((frame[15] & 0x80) >> 7);
			if ((raw_ew & 0x3ff) != 0) {
				ew_vel_valid = true;
				ew_vel = ((raw_ew & 0x3ff) - 1);
				if ((raw_ew & 0x400) == 0x400)
					ew_vel *= -1;
				if (((frame[12] >> 6) & 0x03) == 1)
					ew_vel *= 4;
			}

			if (ew_vel_valid && ns_vel_valid) {
				if (ns_vel != 0 || ew_vel != 0) {
					int heading = (int) (360 + 90 - Math.atan2(ns_vel, ew_vel)
							* 180 / Math.PI) % 360;
					aircraft.setHeading(heading, sNow);
				}

				int speed = (int) Math.sqrt(ns_vel * ns_vel + ew_vel * ew_vel);
				aircraft.setVelocity(speed, sNow);
			}

			raw_vvel = ((frame[15] & 0x7f) << 4) | ((frame[16] & 0xf0) >> 4);
			if ((raw_vvel & 0x1ff) != 0) {
				int vert_rate = ((raw_vvel & 0x1ff) - 1) * 64;
				if ((raw_vvel & 0x200) == 0x200)
					vert_rate *= -1;

				aircraft.setVerticalRate(vert_rate);
			}
		}

		return pri_alt;
	}

	public static Aircraft decodeUatFrame(int[] frame) {
		sNow = System.currentTimeMillis();

		if (frame.length == DetectFramesThread.UPLINK_FRAME_DATA_BYTES) {
			// decode uplink frame
			// we actually don't care about this frame
			// it does not have any aircraft data in it.

			// TODO - add the code anyway for documentation purposes
		} else {
			int icao = (frame[1] << 16) | (frame[2] << 8) | frame[3];

			if (icao <= 0)
				return null;

			Aircraft aircraft = RecentAircraftCache.get(icao);
			if (aircraft == null) {
				aircraft = new Aircraft(icao);
				RecentAircraftCache.add(icao, aircraft);
			}

			aircraft.setUat(true);
			aircraft.setReady(false);
			aircraft.setMessageCount(aircraft.getMessageCount() + 1);
			aircraft.setSeen(System.currentTimeMillis());

			switch ((frame[0] >> 3) & 0x1F) {
			case 0:
			case 4:
			case 7:
			case 8:
			case 9:
			case 10:
				decodeSv(frame, aircraft);
				break;
			case 1:
				decodeMs(frame, aircraft);
				if (decodeSv(frame, aircraft) == 0)
					decodeAuxSv(frame, aircraft);
				break;
			case 2:
			case 5:
			case 6:
				if (decodeSv(frame, aircraft) == 0)
					decodeAuxSv(frame, aircraft);
				break;
			case 3:
				decodeSv(frame, aircraft);
				decodeMs(frame, aircraft);
				break;
			}

			return aircraft;
		}

		return null;
	}

	private Decoder() {
	}
}
