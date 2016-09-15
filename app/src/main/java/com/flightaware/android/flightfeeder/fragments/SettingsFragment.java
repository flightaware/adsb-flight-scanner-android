package com.flightaware.android.flightfeeder.fragments;

import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.Preference;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.support.v7.preference.Preference.OnPreferenceClickListener;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.webkit.WebView;

import com.flightaware.android.flightfeeder.App;
import com.flightaware.android.flightfeeder.R;
import com.flightaware.android.flightfeeder.analyzers.AvrFormatExporter;
import com.flightaware.android.flightfeeder.analyzers.BeastFormatExporter;
import com.flightaware.android.flightfeeder.fragments.AntennaLocationDialogFragment.OnLocationModeChangeListener;
import com.flightaware.android.flightfeeder.services.LocationService;

public class SettingsFragment extends PreferenceFragmentCompat implements
		OnPreferenceClickListener, OnPreferenceChangeListener,
		OnLocationModeChangeListener {

	private Preference mLocation;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);

		Preference pref = findPreference("pref_scan_mode");
		pref.setOnPreferenceChangeListener(this);
		pref.setEnabled(App.sPrefs.getBoolean("in_us", false));

		setScanModeSummary(pref, App.sPrefs.getString("pref_scan_mode", "ADSB"));

		pref = findPreference("pref_avr");
		pref.setOnPreferenceChangeListener(this);

		pref = findPreference("pref_beast");
		pref.setOnPreferenceChangeListener(this);

		pref = findPreference("pref_licenses");
		pref.setOnPreferenceClickListener(this);

		pref = findPreference("pref_version");
		pref.setSummary(App.sVersion);

		mLocation = findPreference("pref_location");
		mLocation.setOnPreferenceClickListener(this);

		onLocatonModeChange(!App.sPrefs.getBoolean("override_location", false));
	}

	@Override
	public void onCreatePreferences(Bundle bundle, String key) {
	}

	@Override
	public void onLocatonModeChange(boolean auto) {
		if (auto)
			mLocation.setSummary(R.string.text_auto);
		else if (LocationService.sLocation != null) {
			String prompt = getString(R.string.text_manually_set);
			mLocation.setSummary(String.format("%s: %.6f, %.6f", prompt,
					LocationService.sLocation.latitude,
					LocationService.sLocation.longitude));
		} else
			mLocation.setSummary("Unknown");
	}

	@Override
	public boolean onPreferenceChange(Preference pref, Object newValue) {
		String key = pref.getKey();

		if (key.equals("pref_scan_mode"))
			setScanModeSummary(pref, (String) newValue);
		else if (key.equals("pref_avr")) {
			boolean start = (boolean) newValue;

			if (start)
				AvrFormatExporter.start();
			else
				AvrFormatExporter.stop();
		} else if (key.equals("pref_beast")) {
			boolean start = (boolean) newValue;

			if (start)
				BeastFormatExporter.start();
			else
				BeastFormatExporter.stop();
		}

		return true;
	}

	@Override
	public boolean onPreferenceClick(Preference pref) {
		String key = pref.getKey();

		if (key.equals("pref_licenses")) {
			WebView webview = new WebView(getActivity());
			webview.loadUrl("file:///android_asset/licenses.html");

			AlertDialog.Builder alert = new AlertDialog.Builder(getActivity(),
					R.style.Theme_AppCompat_Light_Dialog_Alert);
			alert.setView(webview);
			alert.setPositiveButton(android.R.string.ok, null);
			alert.show();
		} else if (key.equals("pref_location")) {
			AntennaLocationDialogFragment fragment = new AntennaLocationDialogFragment();
			fragment.setLocationModeChangeListener(this);
			fragment.show(getFragmentManager(), null);
		}

		return true;
	}

	private void setScanModeSummary(Preference pref, String scanMode) {
		String summary = null;

		if (scanMode.equals("ADSB"))
			summary = getString(R.string.prefs_text_1090);
		else if (scanMode.equals("UAT"))
			summary = getString(R.string.prefs_text_978_mhz);
		else
			summary = getString(R.string.prefs_text_both);

		pref.setSummary(summary);
	}
}
