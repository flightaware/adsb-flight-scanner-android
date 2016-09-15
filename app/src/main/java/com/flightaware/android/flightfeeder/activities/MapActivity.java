package com.flightaware.android.flightfeeder.activities;

import java.util.Locale;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Patterns;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.flightaware.android.flightfeeder.App;
import com.flightaware.android.flightfeeder.R;
import com.flightaware.android.flightfeeder.analyzers.NanoWebServer;

public class MapActivity extends AppCompatActivity {

	private WebView mWebView;
	private NanoWebServer mLocalWebServer;

	@SuppressLint("SetJavaScriptEnabled")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (App.sWebServer == null || !App.sWebServer.isAlive()
				|| !App.sOnAccessPoint) {
			mLocalWebServer = new NanoWebServer(this, "127.0.0.1");
			try {
				mLocalWebServer.start();
			} catch (Exception e) {
				// swallow
			}
		}

		mWebView = new WebView(this);
		mWebView.setWebViewClient(new WebClient());
		mWebView.getSettings().setJavaScriptEnabled(true);
		mWebView.getSettings().setDomStorageEnabled(true);
		setContentView(mWebView);

		mWebView.loadUrl("http://127.0.0.1:8080");

		String ipStr = "127.0.0.1";
		if (App.sPrefs.getBoolean("pref_broadcast", true) && App.sOnAccessPoint) {
			WifiManager wifiMan = (WifiManager) getSystemService(Context.WIFI_SERVICE);
			WifiInfo info = wifiMan.getConnectionInfo();
			if (info != null) {
				int ipAddress = info.getIpAddress();

				ipStr = String.format(Locale.US, "%d.%d.%d.%d",
						(ipAddress & 0xff), (ipAddress >> 8 & 0xff),
						(ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));

				if (ipStr.equals("0.0.0.0"))
					ipStr = "127.0.0.1";
			}
		}

		ActionBar actionBar = getSupportActionBar();
		String title = getString(R.string.text_map) + " - " + ipStr + ":8080";
		actionBar.setTitle(title);
	}

	@Override
	public void onDestroy() {
		if (mLocalWebServer != null)
			mLocalWebServer.stop();

		super.onDestroy();
	}

	@Override
	public void onResume() {
		super.onResume();

		mWebView.setKeepScreenOn(App.sPrefs.getBoolean("pref_keep_screen_on",
				false));
	}

	private final class WebClient extends WebViewClient {

		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
			if (App.sIsConnected && !TextUtils.isEmpty(url)
					&& Patterns.WEB_URL.matcher(url).matches()) {

				Uri uri = Uri.parse(url);
				Intent intent = new Intent(Intent.ACTION_VIEW, uri);
				intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

				try {
					startActivity(intent);
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}

			return true;
		}
	}
}
