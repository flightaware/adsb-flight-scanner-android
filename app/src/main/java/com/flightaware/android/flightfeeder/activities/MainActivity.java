package com.flightaware.android.flightfeeder.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.design.widget.NavigationView;
import android.support.design.widget.NavigationView.OnNavigationItemSelectedListener;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.flightaware.android.flightfeeder.App;
import com.flightaware.android.flightfeeder.R;
import com.flightaware.android.flightfeeder.adapters.PlaneAdapter;
import com.flightaware.android.flightfeeder.analyzers.Aircraft;
import com.flightaware.android.flightfeeder.analyzers.Analyzer;
import com.flightaware.android.flightfeeder.analyzers.RecentAircraftCache;
import com.flightaware.android.flightfeeder.services.ControllerService;
import com.flightaware.android.flightfeeder.services.LocationService;
import com.flightaware.android.flightfeeder.util.MovingAverage;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements
        OnNavigationItemSelectedListener, OnItemClickListener,
        ServiceConnection {

    public static final String ACTION_LOGIN = "com.flightaware.android.flightfeeder.LOGIN";
    public static final String ACTION_MODE_CHANGE = "com.flightaware.android.flightfeeder.MODE_CHANGE";
    public static final String ACTION_UPDATE_1HERTZ = "com.flightaware.android.flightfeeder.UPDATE_1HERTZ";
    public static final String ACTION_UPDATE_RX = "com.flightaware.android.flightfeeder.UPDATE_RX";
    public static final String ACTION_UPDATE_TX = "com.flightaware.android.flightfeeder.UPDATE_TX";
    private static final String ACTION_USB_PERMISSION = "com.flightaware.android.flightfeeder.USB_PERMISSION";

    private static final int LOCATION_REQUEST_CODE = 100;

    private Thread m1HertzUpdater;
    private AlertDialog mAlert;
    private long mBackPressedTime;
    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;
    private IntentFilter mFilter;
    private NavigationView mNavView;
    private int mOffDelay = 50;
    private BroadcastReceiver mPermissionReceiver;
    private PlaneAdapter mPlaneAdapter;
    private ListView mPlaneList;
    private ArrayList<Aircraft> mPlanes = new ArrayList<Aircraft>();
    private Toast mPressBackToast;
    private TextView mRange;
    private TextView mRate;
    private BroadcastReceiver mReceiver;
    private ImageView mRx;
    private ImageView mTx;
    private ControllerService mService;
    private TextView mUsernameView;

    @Override
    public void onBackPressed() {
        if (mDrawerLayout.isDrawerVisible(mNavView)) {
            mDrawerLayout.closeDrawers();
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - mBackPressedTime > 2000 /* Toast.LENGTH_LONG  */) {
            mPressBackToast.show();
            mBackPressedTime = currentTime;
        } else {
            mPressBackToast.cancel();
            super.onBackPressed();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    @SuppressLint("ShowToast")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startService(new Intent(this, ControllerService.class));

        if (!App.sPrefs.getBoolean("override_location", false))
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager
                    .PERMISSION_GRANTED) {

                startService(new Intent(this, LocationService.class));
            } else {
                ActivityCompat
                        .requestPermissions(
                                this,
                                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                LOCATION_REQUEST_CODE);
            }
        else if (LocationService.sLocation == null
                && App.sPrefs.contains("latitude")
                && App.sPrefs.contains("longitude")) {
            float lat = App.sPrefs.getFloat("latitude", 0);
            float lon = App.sPrefs.getFloat("longitude", 0);

            LocationService.sLocation = new LatLng(lat, lon);
        }

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mNavView = (NavigationView) findViewById(R.id.navigation_view);
        mNavView.setNavigationItemSelectedListener(this);

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, toolbar,
                R.string.app_name, R.string.app_name);
        mDrawerLayout.setDrawerListener(mDrawerToggle);

        mRx = (ImageView) findViewById(R.id.rx);
        mTx = (ImageView) findViewById(R.id.tx);
        mRate = (TextView) findViewById(R.id.rate);
        mRange = (TextView) findViewById(R.id.range);
        mPlaneList = (ListView) findViewById(R.id.output);
        mPressBackToast = Toast.makeText(this, R.string.text_press_again,
                Toast.LENGTH_LONG);

        mPlaneAdapter = new PlaneAdapter(this, mPlanes);
        mPlaneList.setAdapter(mPlaneAdapter);
        mPlaneList.setOnItemClickListener(this);

        final Runnable rxOff = new Runnable() {
            @Override
            public void run() {
                mRx.setImageResource(R.drawable.data_off);
            }
        };

        final Runnable txOff = new Runnable() {
            @Override
            public void run() {
                mTx.setImageResource(R.drawable.data_off);
            }
        };

        mFilter = new IntentFilter();
        mFilter.addAction(ACTION_UPDATE_RX);
        mFilter.addAction(ACTION_UPDATE_TX);
        mFilter.addAction(ACTION_UPDATE_1HERTZ);
        mFilter.addAction(ACTION_MODE_CHANGE);
        mFilter.addAction(ACTION_LOGIN);

        mReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (!TextUtils.isEmpty(action)) {
                    if (action.equals(ACTION_UPDATE_RX)) {
                        mRx.removeCallbacks(rxOff);
                        mRx.setImageResource(R.drawable.data_on);
                        mRx.postDelayed(rxOff, mOffDelay);
                    } else if (action.equals(ACTION_UPDATE_TX)) {
                        mTx.removeCallbacks(txOff);
                        mTx.setImageResource(R.drawable.data_on);
                        mTx.postDelayed(txOff, 200);
                    } else if (action.equals(ACTION_UPDATE_1HERTZ)) {
                        mRange.setText(String.format("%.1f", Analyzer.sRange));

                        double rate = MovingAverage.getCurrentAverage();

                        int delay = (int) (1000 / rate);
                        mOffDelay = Math.min(delay, 200);

                        mRate.setText(String.format("%.1f", rate));

                        mPlanes.clear();
                        mPlanes.addAll(RecentAircraftCache
                                .getActiveAircraftList(true));

                        mPlaneAdapter.notifyDataSetChanged();
                    } else if (action.equals(ACTION_MODE_CHANGE)) {
                        mRate.setText("0.0");
                        mRange.setText("0.0");

                        if (mService != null)
                            setTitle(mService.isUat());
                    }
                }
            }
        };

        mPermissionReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice) intent
                            .getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(
                            UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            Editor editor = App.sPrefs.edit();
                            editor.putBoolean("usb_permission_granted", true);
                            editor.commit();

                            startListening(device);
                        } else {
                            Editor editor = App.sPrefs.edit();
                            editor.putBoolean("usb_permission_granted", false);
                            editor.commit();
                        }
                    } else {
                        Editor editor = App.sPrefs.edit();
                        editor.putBoolean("usb_permission_granted", false);
                        editor.commit();

                        finish();
                    }
                }
            }

        };

        registerReceiver(mPermissionReceiver, new IntentFilter(
                ACTION_USB_PERMISSION));

        bindService(new Intent(this, ControllerService.class), this,
                Context.BIND_IMPORTANT);
    }

    @Override
    public void onDestroy() {
        if (mAlert != null)
            mAlert.dismiss();

        if (!App.sPrefs.getBoolean("pref_background", true) || mService == null
                || !mService.isScanning())
            stopService(new Intent(this, ControllerService.class));
        else
            mService.showNotification();

        unregisterReceiver(mPermissionReceiver);

        unbindService(this);

        super.onDestroy();
    }

    @Override
    public void onItemClick(AdapterView<?> listView, View view, int position,
                            long id) {
        Aircraft aircraft = (Aircraft) listView.getItemAtPosition(position);

        if (aircraft == null)
            return;

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("https://flightaware.com/live/flight/"
                + aircraft.getIdentity()));

        startActivity(intent);
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem menuItem) {
        mDrawerLayout.closeDrawers();

        int id = menuItem.getItemId();

        if (id == R.id.drawer_map)
            startActivity(new Intent(this, MapActivity.class));
        else if (id == R.id.drawer_settings)
            startActivity(new Intent(this, SettingsActivity.class));

        return true;
    }

    @Override
    public void onNewIntent(Intent intent) {
        if (intent != null) {
            String action = intent.getAction();

            if (TextUtils.isEmpty(action))
                return;

            UsbDevice device = null;

            if (action
                    .equals("android.hardware.usb.action.USB_DEVICE_ATTACHED")) {
                synchronized (this) {
                    device = (UsbDevice) intent
                            .getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (device != null)
                        startListening(device);
                    else if (mAlert == null || !mAlert.isShowing())
                        showNoDongle();
                }
            } else if (action
                    .equals("android.hardware.usb.action.USB_DEVICE_DETACHED")) {
                if (mAlert == null || !mAlert.isShowing())
                    showNoDongle();

                if (mService != null)
                    mService.stopScanning(false);
            } else {
//                device = UsbDvbDetector.isValidDeviceConnected(this);

                if (device != null) {
                    UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
                    if (usbManager.hasPermission(device)
                            || App.sPrefs.getBoolean("usb_permission_granted",
                            false))
                        startListening(device);
                    else {
                        PendingIntent permission = PendingIntent.getBroadcast(
                                this, 0, new Intent(ACTION_USB_PERMISSION), 0);

                        usbManager.requestPermission(device, permission);
                    }
                } else if (mAlert == null || !mAlert.isShowing())
                    showNoDongle();
            }
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        mDrawerToggle.syncState();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        if (requestCode == LOCATION_REQUEST_CODE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startService(new Intent(this, LocationService.class));
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        String scanMode = App.sPrefs.getString("pref_scan_mode", "ADSB");

        if (scanMode.equals("ADSB"))
            setTitle(false);
        else if (scanMode.equals("UAT"))
            setTitle(true);
        else if (mService != null)
            setTitle(mService.isUat());

        mPlaneList.setKeepScreenOn(App.sPrefs.getBoolean("pref_keep_screen_on",
                false));

        if (mService != null)
            mService.stopForeground(true);

        App.sBroadcastManager.registerReceiver(mReceiver, mFilter);

        if (m1HertzUpdater == null) {
            m1HertzUpdater = new Thread() {
                @Override
                public void run() {
                    while (true) {
                        App.sBroadcastManager.sendBroadcast(new Intent(
                                ACTION_UPDATE_1HERTZ));

                        SystemClock.sleep(1000);
                    }
                }
            };
            m1HertzUpdater.start();
        }
    }

    @Override
    public void onStop() {
        if (m1HertzUpdater != null) {
            m1HertzUpdater.interrupt();
            m1HertzUpdater = null;
        }

        App.sBroadcastManager.unregisterReceiver(mReceiver);

        super.onStop();
    }

    private void setTitle(boolean uatMode) {
        String title = getString(R.string.app_name);

        if (uatMode)
            title += " - " + getString(R.string.text_978_mhz);
        else
            title += " - " + getString(R.string.text_1090_mhz);

        getSupportActionBar().setTitle(title);
    }

    private void showNoDongle() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this,
                R.style.Theme_AppCompat_Light_Dialog_Alert);
        builder.setTitle(R.string.dialog_no_usb_device_title);
        builder.setMessage(R.string.dialog_no_usb_device_msg);
        builder.setPositiveButton(android.R.string.ok, null);

        mAlert = builder.create();
        mAlert.show();
    }

    private void startListening(UsbDevice device) {
        System.out.println("1");
        if (mService != null) {
            mService.setUsbDevice(device);

            mService.startScanning();
        }

        if (mAlert != null && mAlert.isShowing())
            mAlert.dismiss();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        mService = ((ControllerService.LocalBinder) service).getService();
        mService.stopForeground(true);

        onNewIntent(getIntent());
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
    }
}
