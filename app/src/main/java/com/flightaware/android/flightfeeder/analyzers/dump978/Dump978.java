package com.flightaware.android.flightfeeder.analyzers.dump978;

import marto.rtl_tcp_andro.core.RtlTcp;
import marto.rtl_tcp_andro.core.RtlTcp.RtlTcpProcessListener;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.text.TextUtils;

import com.flightaware.android.flightfeeder.BuildConfig;
import com.flightaware.android.flightfeeder.analyzers.Analyzer;

public class Dump978 extends Analyzer {

	static {
		Fec.init();
	}

	private static Thread sConvertThread;
	private static Thread sDetectThread;
	public static volatile boolean sExit;

	private static RtlTcpProcessListener sListener = new RtlTcpProcessListener() {

		@Override
		public void onProcessStarted() {
			// Start threads in this order so that each consumer thread is
			// running before its upstream producer
			if (sDecodeThread == null) {
				sDecodeThread = new DecodeFramesThread();
				sDecodeThread.start();
			}

			if (sDetectThread == null) {
				sDetectThread = new DetectFramesThread();
				sDetectThread.start();
			}

			if (sConvertThread == null) {
				sConvertThread = new ConvertToPhiThread();
				sConvertThread.start();
			}

			if (sReadThread == null) {
				// if (BuildConfig.DEBUG)
				// sReadThread = new ReadTestDataFileThread();
				// else
				sReadThread = new GetRtlSdrDataThread();

				sReadThread.start();
			}
		}

		@Override
		public void onProcessStdOutWrite(String line) {
			if (BuildConfig.DEBUG)
				System.out.println(line);
		}

		@Override
		public void onProcessStopped(int exitCode, Exception e) {
			sExit = true;

			if (sReadThread != null) {
				sReadThread.interrupt();
				sReadThread = null;
			}

			if (sConvertThread != null) {
				sConvertThread.interrupt();
				sConvertThread = null;
			}

			if (sDetectThread != null) {
				sDetectThread.interrupt();
				sDetectThread = null;
			}

			if (sDecodeThread != null) {
				sDecodeThread.interrupt();
				sDecodeThread = null;
			}
		}
	};

	public static void start(UsbManager usbManager, UsbDevice usbDevice)
			throws Exception {
		UsbDeviceConnection connection = usbManager.openDevice(usbDevice);

		int fileDescriptor = connection.getFileDescriptor();
		String deviceName = getDeviceName(usbDevice.getDeviceName());

		if (fileDescriptor == -1 || TextUtils.isEmpty(deviceName))
			throw new RuntimeException(
					"USB file descriptor or device name is invalid");

		sExit = false;

		RtlTcp.start("-f 978e6 -s 2083334", fileDescriptor, deviceName,
				sListener);
	}

	public static void stop() {
		RtlTcp.stop();

		sExit = true;
	}
}
