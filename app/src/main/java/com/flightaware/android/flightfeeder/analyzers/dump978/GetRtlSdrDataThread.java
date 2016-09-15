package com.flightaware.android.flightfeeder.analyzers.dump978;

import java.io.InputStream;
import java.net.Socket;
import java.util.Arrays;

import javax.net.SocketFactory;

import android.os.SystemClock;

import com.flightaware.android.flightfeeder.BuildConfig;

public class GetRtlSdrDataThread extends Thread {
	public static final int BUFFER_SIZE = 128 * 1024;

	public GetRtlSdrDataThread() {
		setName("GetRtlSdrDataThread");
	}

	@Override
	public void run() {
		if (BuildConfig.DEBUG)
			System.out.println("Started reading RTLSDR data socket");

		Socket socket = null;
		long maxWait = 3000;
		long waited = 0;
		try {
			while (socket == null && waited < maxWait) {
				SystemClock.sleep(100);
				try {
					socket = SocketFactory.getDefault().createSocket(
							"127.0.0.1", 1234);
				} catch (Exception e) {
					// swallow
					if (socket != null) {
						socket.close();
						socket = null;
					}
				} finally {
					waited += 100;
				}
			}

			InputStream stream = socket.getInputStream();

			byte[] buffer = new byte[BUFFER_SIZE];

			int length = 0;

			while (!Dump978.sExit) { // && length != BUFFER_SIZE) {
				length = stream.read(buffer);

				if (length != BUFFER_SIZE)
					continue;

				RtlSdrDataQueue.offer(Arrays.copyOf(buffer, length));
			}
		} catch (Exception e) {
			// swallow;
		} finally {
			if (socket != null) {
				try {
					socket.close();
				} catch (Exception e) {
					// swallow
				}
			}
		}

		if (BuildConfig.DEBUG)
			System.out.println("Stopped reading RTLSDR data socket");
	}
}
