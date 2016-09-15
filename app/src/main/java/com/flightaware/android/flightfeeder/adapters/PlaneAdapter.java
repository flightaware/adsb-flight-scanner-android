package com.flightaware.android.flightfeeder.adapters;

import java.util.ArrayList;
import java.util.regex.Pattern;

import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.text.Html;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.flightaware.android.flightfeeder.App;
import com.flightaware.android.flightfeeder.R;
import com.flightaware.android.flightfeeder.analyzers.Aircraft;

public class PlaneAdapter extends BaseAdapter {

	// regex for 1 letter AND 1 number for ident
	private static final Pattern sIdentPattern = Pattern
			.compile("^(?=.*[0-9])(?=.*[a-zA-Z])([a-zA-Z0-9]+)$");

	private static final class ViewHolder {
		public TextView icao;
		public TextView ident;
		public TextView squawk;
		public TextView latitude;
		public TextView longitude;
		public TextView altitude;
		public TextView speed;
		public TextView heading;
		public ImageView uat;
	}

	private ArrayList<Aircraft> mPlaneList;
	private LayoutInflater mInflater;
	private int mWebLinkColor;
	private int mNormalColor;
	private boolean mBoth;

	public PlaneAdapter(Context context, ArrayList<Aircraft> planeList) {
		mInflater = LayoutInflater.from(context);
		mPlaneList = planeList;

		mWebLinkColor = ContextCompat.getColor(context, R.color.web_link);
		mNormalColor = ContextCompat.getColor(context,
				android.R.color.primary_text_light);

		mBoth = App.sPrefs.getString("pref_scan_mode", "ADSB").equals("BOTH");
	}

	@Override
	public int getCount() {
		return mPlaneList.size();
	}

	@Override
	public Aircraft getItem(int position) {
		if (position < mPlaneList.size())
			return mPlaneList.get(position);

		return null;
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(int position, View view, ViewGroup parent) {
		if (view == null)
			view = mInflater.inflate(R.layout.item_active_plane, parent, false);

		ViewHolder holder = null;
		if (view.getTag() == null) {
			holder = new ViewHolder();

			holder.icao = (TextView) view.findViewById(R.id.icao);
			holder.ident = (TextView) view.findViewById(R.id.ident);
			holder.squawk = (TextView) view.findViewById(R.id.squawk);
			holder.latitude = (TextView) view.findViewById(R.id.latitude);
			holder.longitude = (TextView) view.findViewById(R.id.longitude);
			holder.altitude = (TextView) view.findViewById(R.id.altitude);
			holder.speed = (TextView) view.findViewById(R.id.speed);
			holder.heading = (TextView) view.findViewById(R.id.heading);
			holder.uat = (ImageView) view.findViewById(R.id.uat_flag);
		} else
			holder = (ViewHolder) view.getTag();

		Aircraft aircraft = getItem(position);

		if (mBoth) {
			if (aircraft.isUat())
				holder.uat.setVisibility(View.VISIBLE);
			else
				holder.uat.setVisibility(View.GONE);
		} else
			holder.uat.setVisibility(View.GONE);

		holder.icao.setText(aircraft.getIcao());
		String ident = aircraft.getIdentity();

		if (isEnabled(position)) {
			holder.ident.setTextColor(mWebLinkColor);
			ident = "<u>" + ident + "</u>";
			holder.ident.setText(Html.fromHtml(ident));
		} else {
			holder.ident.setTextColor(mNormalColor);
			holder.ident.setText(ident);
		}

		if (aircraft.getSquawk() == null)
			holder.squawk.setText(null);
		else
			holder.squawk.setText(String.format("%4x", aircraft.getSquawk()));

		if (aircraft.getLatitude() == null)
			holder.latitude.setText(null);
		else
			holder.latitude.setText(String.format("%.3f",
					aircraft.getLatitude()));

		if (aircraft.getLongitude() == null)
			holder.longitude.setText(null);
		else
			holder.longitude.setText(String.format("%.3f",
					aircraft.getLongitude()));

		if (aircraft.getAltitude() == null)
			holder.altitude.setText(null);
		else
			holder.altitude.setText(String.valueOf(aircraft.getAltitude()));

		if (aircraft.getVelocity() == null)
			holder.speed.setText(null);
		else
			holder.speed.setText(String.valueOf(aircraft.getVelocity()));

		if (aircraft.getHeading() == null)
			holder.heading.setText(null);
		else
			holder.heading.setText(String.valueOf(aircraft.getHeading()));

		if (position % 2 == 0)
			view.setBackgroundResource(android.R.color.transparent);
		else
			view.setBackgroundResource(R.color.primary_light);

		return view;
	}

	@Override
	public boolean isEnabled(int position) {
		Aircraft aircraft = getItem(position);
		if (aircraft == null)
			return false;

		String ident = aircraft.getIdentity();
		return !TextUtils.isEmpty(ident)
				&& sIdentPattern.matcher(ident).matches();
	}

	@Override
	public void notifyDataSetChanged() {
		mBoth = App.sPrefs.getString("pref_scan_mode", "ADSB").equals("BOTH");
		super.notifyDataSetChanged();
	}
}
