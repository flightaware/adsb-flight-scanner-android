package com.flightaware.android.flightfeeder.analyzers.dump1090;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.text.TextUtils;

import com.flightaware.android.flightfeeder.BuildConfig;
import com.flightaware.android.flightfeeder.analyzers.Analyzer;
import com.flightaware.android.flightfeeder.analyzers.dump1090.Decoder.CorrectionLevel;

import marto.rtl_tcp_andro.core.RtlTcp;
import marto.rtl_tcp_andro.core.RtlTcp.RtlTcpProcessListener;

public class Dump1090 extends Analyzer {

	static {
		Decoder.init(CorrectionLevel.ONE_BIT);
	}

	private static Thread sComputeThread;
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
				sDetectThread = new DetectModeSThread();
				sDetectThread.start();
			}

			if (sComputeThread == null) {
				sComputeThread = new ComputeMagnitudeVectorThread();
				sComputeThread.start();
			}

			if (sReadThread == null) {
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
			if (sReadThread != null) {
				sReadThread.interrupt();
				sReadThread = null;
			}

			if (sComputeThread != null) {
				sComputeThread.interrupt();
				sComputeThread = null;
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

		RtlTcp.start("-f 1090e6 -s 2.4e6", fileDescriptor, deviceName,
				sListener);
	}

	public static void stop() {
		RtlTcp.stop();

		sExit = true;
	}
}
