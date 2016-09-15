package com.flightaware.android.flightfeeder.analyzers;

import android.text.TextUtils;
import android.util.LruCache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;

public class RecentAircraftCache {

	private static final LruCache<Integer, Aircraft> sRecentAircraft = new LruCache<Integer, Aircraft>(
			300);

	public static void add(Integer icao, Aircraft aircraft) {
		String strIcao = aircraft.getIcao();
		if (TextUtils.isEmpty(strIcao) || strIcao.length() != 6)
			return;

		sRecentAircraft.put(icao, aircraft);
	}

	public static void clear() {
		sRecentAircraft.evictAll();
	}

	public static Aircraft get(Integer icao) {
		return sRecentAircraft.get(icao);
	}

	public static ArrayList<Aircraft> getActiveAircraftList(boolean sort) {
		ArrayList<Aircraft> aircraftList = new ArrayList<Aircraft>();

		Map<Integer, Aircraft> planes = sRecentAircraft.snapshot();

		long now = System.currentTimeMillis();
		for (Aircraft aircraft : planes.values()) {
			long seen = (now - aircraft.getSeen()) / 1000;
			if (seen <= 60)
				aircraftList.add(aircraft);
		}

		if (sort && aircraftList.size() > 1) {
			Collections.sort(aircraftList, new Comparator<Aircraft>() {
				@Override
				public int compare(Aircraft lhs, Aircraft rhs) {
					return lhs.getIcao().compareTo(rhs.getIcao());
				}
			});
		}

		return aircraftList;
	}
}
