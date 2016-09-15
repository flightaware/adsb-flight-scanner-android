package com.flightaware.android.flightfeeder.analyzers;

import java.net.ServerSocket;
import java.util.Iterator;
import java.util.Vector;

import javax.net.ServerSocketFactory;

import android.text.TextUtils;

import com.flightaware.android.flightfeeder.analyzers.dump1090.ModeSMessage;

public class AvrFormatExporter {

	private static final Vector<Client> sClientList = new Vector<Client>();
	private static Thread sConnectThread;
	public static volatile boolean sIsEnabled;
	private static Thread sSendThread;
	private static volatile ServerSocket sServerSocket;

	public static void start() {
		if (sIsEnabled)
			return;

		sIsEnabled = true;

		BeastFormatMessageQueue.clear();

		if (sConnectThread == null) {
			sConnectThread = new Thread() {
				@Override
				public void interrupt() {
					if (sServerSocket != null) {
						try {
							sServerSocket.close();
						} catch (Exception e) {
							e.printStackTrace();
						}
					}

					super.interrupt();
				}

				@Override
				public void run() {
					try {
						sServerSocket = ServerSocketFactory.getDefault()
								.createServerSocket(30002);

						Client client = new Client(sServerSocket.accept());

						sClientList.add(client);
					} catch (Exception e) {
						e.printStackTrace();
						interrupt();
					}
				}
			};

			sConnectThread.start();
		}

		if (sSendThread == null) {
			sSendThread = new Thread() {
				@Override
				public void interrupt() {
					Iterator<Client> it = sClientList.iterator();
					while (it.hasNext()) {
						Client client = it.next();
						client.close();
						it.remove();
					}

					super.interrupt();
				}

				@Override
				public void run() {
					while (sIsEnabled) {
						ModeSMessage message = AvrFormatMessageQueue.take();
						if (message == null || message.getBytes() == null)
							continue;

						String modeS = message.getBytesAsString();

						if (!TextUtils.isEmpty(modeS)) {
							modeS = "*" + modeS + ";\n";

							Iterator<Client> it = sClientList.iterator();
							while (it.hasNext()) {
								Client client = it.next();
								if (client.isConnected())
									client.send(modeS.getBytes());
								else {
									client.close();
									it.remove();
								}
							}
						}
					}
				}
			};

			sSendThread.start();
		}
	}

	public static void stop() {
		if (!sIsEnabled)
			return;

		sIsEnabled = false;

		if (sConnectThread != null) {
			sConnectThread.interrupt();
			sConnectThread = null;
		}

		if (sSendThread != null) {
			sSendThread.interrupt();
			sSendThread = null;
		}
	}

	private AvrFormatExporter() {
	}
}
