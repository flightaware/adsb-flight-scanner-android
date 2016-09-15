package com.flightaware.android.flightfeeder.activities;

import com.flightaware.android.flightfeeder.R;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_settings);

		getSupportActionBar().setTitle(R.string.text_settings);
	}
}
