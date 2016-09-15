package com.flightaware.android.flightfeeder.analyzers;

import android.content.Context;
import android.os.SystemClock;
import android.text.TextUtils;

import com.flightaware.android.flightfeeder.App;
import com.flightaware.android.flightfeeder.services.LocationService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class NanoWebServer extends NanoHTTPD {

	private static final String MODES_CONTENT_TYPE_CSS = "text/css;charset=utf-8";
	private static final String MODES_CONTENT_TYPE_HTML = "text/html;charset=utf-8";
	private static final String MODES_CONTENT_TYPE_ICON = "image/x-icon";
	private static final String MODES_CONTENT_TYPE_JS = "application/javascript;charset=utf-8";
	private static final String MODES_CONTENT_TYPE_JSON = "application/json;charset=utf-8";
	private static final String MODES_CONTENT_TYPE_PLAIN = "text/plain;charset=utf-8";
	private static final String sRoot = "public_html";

	private File mCacheDir;
	private Context mContext;
	private Thread mHistoryThread;

	public NanoWebServer(Context context) {
		this(context, null);
	}

	public NanoWebServer(Context context, String ipAddress) {
		super(ipAddress, 8080);

		mContext = context;
		mCacheDir = context.getCacheDir();
	}

	private JSONArray buildJsonArray(ArrayList<Aircraft> aircraftList) {
		JSONArray array = new JSONArray();

		for (Aircraft aircraft : aircraftList) {
			if (aircraft.getMessageCount() < 2)
				continue;

			String icao = aircraft.getIcao();
			if (TextUtils.isEmpty(icao) || icao.length() != 6)
				continue;

			long now = System.currentTimeMillis();
			long seen = (now - aircraft.getSeen()) / 1000;

			JSONObject plane = new JSONObject();
			try {
				plane.put("hex", icao.toLowerCase(Locale.US));

				Integer squawk = aircraft.getSquawk();
				if (squawk != null)
					plane.put("squawk",
							String.format("%04x", aircraft.getSquawk()));

				String ident = aircraft.getIdentity();
				if (!TextUtils.isEmpty(ident))
					plane.put("flight", ident);

				Double lat = aircraft.getLatitude();
				Double lon = aircraft.getLongitude();
				if (lat != null && lon != null) {
					plane.put("lat", lat);
					plane.put("lon", lon);
					plane.put("seen_pos",
							(now - aircraft.getSeenLatLon()) / 1000);
				}

				if (aircraft.isOnGround())
					plane.put("altitude", "ground");
				else
					plane.putOpt("altitude", aircraft.getAltitude());

				plane.putOpt("vert_rate", aircraft.getVerticalRate());

				plane.putOpt("track", aircraft.getHeading());

				plane.putOpt("speed", aircraft.getVelocity());

				Integer category = aircraft.getCategory();
				if (category != null)
					plane.put("category", String.format("%02X", category));

				plane.putOpt("messages", aircraft.getMessageCount());

				plane.putOpt("seen", seen);

				plane.putOpt("rssi", aircraft.getAverageSignalStrength());

				array.put(plane);
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}

		return array;
	}

	private JSONObject generateAircraftJson() {
		try {
			JSONObject aircraft = new JSONObject();

			aircraft.put("now", System.currentTimeMillis() / 1000);
			aircraft.put("messages", Analyzer.sFrameCount);

			ArrayList<Aircraft> aircraftList = RecentAircraftCache
					.getActiveAircraftList(true);

			aircraft.put("aircraft", buildJsonArray(aircraftList));

			return aircraft;
		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	@SuppressWarnings("deprecation")
	@Override
	public Response serve(String uri, Method method,
			Map<String, String> headers, Map<String, String> parms,
			Map<String, String> files) {

		uri = sRoot + uri;

		InputStream is = null;

		try {
			if (uri.contains("/data/aircraft.json")) {
				JSONObject aircraft = generateAircraftJson();

				if (aircraft != null) {
					try {
						is = new ByteArrayInputStream(aircraft.toString(2)
								.getBytes());
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			} else if (uri.contains("/data/history_")) {
				String[] elements = uri.split("_");

				String idx = elements[elements.length - 1].replace(".json", "");

				int index = -1;
				try {
					index = Integer.parseInt(idx);
				} catch (Exception ex) {
					ex.printStackTrace();
					return null;
				}

				if (index < 0 || index >= 120)
					return null;

				File file = new File(mCacheDir, "history_" + index + ".json");
				FileInputStream fis = new FileInputStream(file);

				is = new BufferedInputStream(fis);
			} else if (uri.contains("/data/receiver.json")) {
				JSONObject receiver = new JSONObject();
				if (LocationService.sLocation != null) {
					try {
						receiver.put("lat", LocationService.sLocation.latitude);
						receiver.put("lon", LocationService.sLocation.longitude);
						receiver.put("version", App.sVersion);
						receiver.put("refresh", 1000);
						receiver.put("history", 120);

						is = new ByteArrayInputStream(receiver.toString()
								.getBytes());
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			} else {
				if (uri.equalsIgnoreCase(sRoot)
						|| uri.equalsIgnoreCase(sRoot + "/")
						|| uri.contains(".."))
					uri = uri + "gmap.html";

				is = new BufferedInputStream(mContext.getAssets().open(uri));
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		if (is != null) {
			String mimeType = null;

			if (uri.endsWith(".html") || uri.endsWith("htm"))
				mimeType = MODES_CONTENT_TYPE_HTML;
			else if (uri.endsWith(".css"))
				mimeType = MODES_CONTENT_TYPE_CSS;
			else if (uri.endsWith(".js"))
				mimeType = MODES_CONTENT_TYPE_JS;
			else if (uri.endsWith(".json"))
				mimeType = MODES_CONTENT_TYPE_JSON;
			else if (uri.endsWith(".ico"))
				mimeType = MODES_CONTENT_TYPE_ICON;

			if (TextUtils.isEmpty(mimeType))
				mimeType = MODES_CONTENT_TYPE_PLAIN;

			return new Response(Response.Status.OK, mimeType, is);
		}

		return super.serve(uri, method, headers, parms, files);
	}

	@Override
	public void start() throws IOException {
		super.start();

		if (mHistoryThread == null) {
			mHistoryThread = new Thread() {
				@Override
				public void run() {
					int i = 0;

					while (true) {
						SystemClock.sleep(30000);

						JSONObject aircraft = generateAircraftJson();
						if (aircraft != null) {
							try {
								File file = new File(mCacheDir, "history_" + i
										+ ".json");
								FileOutputStream fos = new FileOutputStream(
										file);
								BufferedOutputStream bos = new BufferedOutputStream(
										fos);
								bos.write(aircraft.toString(2).getBytes());
								bos.flush();
								bos.close();

								if (++i == 120)
									i = 0;
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					}
				}
			};

			mHistoryThread.setName("History JSON Writer Thread");
			mHistoryThread.start();
		}
	}

	@Override
	public void stop() {
		super.stop();

		if (mHistoryThread != null) {
			mHistoryThread.interrupt();
			mHistoryThread = null;
		}
	}
}
