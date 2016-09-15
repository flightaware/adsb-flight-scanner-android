package com.flightaware.android.flightfeeder.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AppCompatDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.widget.EditText;
import android.widget.Toast;

import com.flightaware.android.flightfeeder.App;
import com.flightaware.android.flightfeeder.R;
import com.flightaware.android.flightfeeder.services.LocationService;
import com.google.android.gms.maps.model.LatLng;

public class AntennaLocationDialogFragment extends DialogFragment implements
		OnClickListener {

	public interface OnLocationModeChangeListener {
		public void onLocatonModeChange(boolean auto);
	}

	private EditText mLatitude;
	private OnLocationModeChangeListener mListener;
	private EditText mLongitude;

	@Override
	public void onClick(View view) {
		int id = view.getId();

		if (id == R.id.buttonCancel)
			dismiss();
		else if (id == R.id.buttonAuto) {
			Editor editor = App.sPrefs.edit();
			editor.putBoolean("override_location", false);
			editor.commit();

			Context context = getActivity();
			context.startService(new Intent(context, LocationService.class));

			mListener.onLocatonModeChange(true);

			dismiss();
		} else if (id == R.id.buttonSet) {
			Float lat = null;
			Float lon = null;

			String input = mLatitude.getText().toString().trim();

			try {
				lat = Float.parseFloat(input);
				if (lat > 90 || lat < -90) {
					showError();
					return;
				}

			} catch (Exception ex) {
				showError();
				return;
			}

			input = mLongitude.getText().toString().trim();
			try {
				lon = Float.parseFloat(input);
				if (lon > 360) {
					showError();
					return;
				}

				if (lon == 360)
					lon = 0f;

				if (lon > 180)
					lon = 180f - lon;

			} catch (Exception ex) {
				showError();
				return;
			}

			if (lat == null || lon == null) {
				showError();
				return;
			}

			mLatitude.setText(String.format("%.6f", lat));
			mLongitude.setText(String.format("%.6f", lon));

			Context context = getActivity();
			context.stopService(new Intent(context, LocationService.class));

			LocationService.sLocation = new LatLng(lat, lon);

			Editor editor = App.sPrefs.edit();
			editor.putFloat("latitude", lat);
			editor.putFloat("longitude", lon);
			editor.putBoolean("override_location", true);
			editor.commit();

			mListener.onLocatonModeChange(false);

			dismiss();
		}
	}

	@Override
	public AppCompatDialog onCreateDialog(Bundle savedInstanceState) {
		AppCompatDialog dialog = new AppCompatDialog(getActivity(), getTheme());
		dialog.setTitle(R.string.prefs_antenna_location_title);
		dialog.setCanceledOnTouchOutside(false);

		return dialog;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_dialog_antenna_location,
				container, false);
	}

	@Override
	public void onStart() {
		super.onStart();

		if (getDialog() != null) {
			Rect displayRectangle = new Rect();
			Window window = getActivity().getWindow();
			window.getDecorView()
					.getWindowVisibleDisplayFrame(displayRectangle);

			getDialog().getWindow().setLayout(
					(int) (displayRectangle.width() * 0.9f),
					LayoutParams.WRAP_CONTENT);
		}
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		mLatitude = (EditText) view.findViewById(R.id.latitude);
		if (LocationService.sLocation != null)
			mLatitude.setText(String.format("%.6f",
					LocationService.sLocation.latitude));

		mLongitude = (EditText) view.findViewById(R.id.longitude);
		if (LocationService.sLocation != null)
			mLongitude.setText(String.format("%.6f",
					LocationService.sLocation.longitude));

		view.findViewById(R.id.buttonCancel).setOnClickListener(this);
		view.findViewById(R.id.buttonAuto).setOnClickListener(this);
		view.findViewById(R.id.buttonSet).setOnClickListener(this);
	}

	public void setLocationModeChangeListener(
			OnLocationModeChangeListener listener) {
		mListener = listener;
	}

	private void showError() {
		Toast.makeText(getActivity(), R.string.text_invalid_location,
				Toast.LENGTH_LONG).show();
	}
}
