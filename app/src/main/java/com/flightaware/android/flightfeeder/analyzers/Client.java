package com.flightaware.android.flightfeeder.analyzers;

import java.io.OutputStream;
import java.net.Socket;

public class Client {

	private volatile boolean mConnected = true;
	private Socket mSocket;

	public Client(Socket socket) {
		mSocket = socket;
	}

	public void close() {
		if (mSocket != null) {
			try {
				mSocket.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public boolean isConnected() {
		return mConnected;
	}

	public void send(final byte[] bytes) {
		new Thread() {
			@Override
			public void run() {
				try {
					OutputStream out = mSocket.getOutputStream();
					out.write(bytes);
					out.flush();
				} catch (Exception e) {
					e.printStackTrace();

					mConnected = false;
				}
			}
		}.start();
	}
}
