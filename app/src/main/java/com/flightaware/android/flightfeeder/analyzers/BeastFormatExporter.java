package com.flightaware.android.flightfeeder.analyzers;

import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Vector;

import javax.net.ServerSocketFactory;

import com.flightaware.android.flightfeeder.analyzers.dump1090.ModeSMessage;

public class BeastFormatExporter {

	private static final Vector<Client> sClientList = new Vector<Client>();
	private static Thread sConnectThread;
	public static volatile boolean sIsEnabled;
	private static Thread sSendThread;
	private static volatile ServerSocket sServerSocket;

	private static byte[] prepareBeastOutput(ModeSMessage message) {
		int length = message.getBytes().length;

		ByteBuffer buffer = ByteBuffer.allocate(2 + 2 * (7 + length));

		buffer.put((byte) 0x1a);

		if (length == 7)
			buffer.put((byte) '2');
		else if (length == 14)
			buffer.put((byte) '3');
		else
			return null;

		// timestamp
		long timestamp = message.getClockCount();
		byte bite = (byte) (timestamp >> 40);
		buffer.put(bite);
		if (bite == 0x1A)
			buffer.put(bite);

		bite = (byte) (timestamp >> 32);
		buffer.put(bite);
		if (bite == 0x1A)
			buffer.put(bite);

		bite = (byte) (timestamp >> 24);
		buffer.put(bite);
		if (bite == 0x1A)
			buffer.put(bite);

		bite = (byte) (timestamp >> 16);
		buffer.put(bite);
		if (bite == 0x1A)
			buffer.put(bite);

		bite = (byte) (timestamp >> 8);
		buffer.put(bite);
		if (bite == 0x1A)
			buffer.put(bite);

		bite = (byte) timestamp;
		buffer.put(bite);
		if (bite == 0x1A)
			buffer.put(bite);

		// *p++ = ch = (char) round(sqrt(mm->signalLevel) * 255);
		// if (0x1A == ch) {*p++ = ch; }

		// signal level is not currently implemented
		// this is just a placeholder for now.

		buffer.put((byte) 200);

		for (byte i = 0; i < length; i++) {
			bite = (byte) message.getBytes()[i];
			buffer.put(bite);
			if (bite == 0x1A)
				buffer.put(bite);
		}

		length = buffer.position();

		byte[] bytes = new byte[length];

		buffer.position(0);

		buffer.get(bytes, 0, length);

		buffer.clear();

		return bytes;
	}

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
								.createServerSocket(30005);

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
						ModeSMessage message = BeastFormatMessageQueue.take();
						if (message == null || message.getBytes() == null)
							continue;

						byte[] bytes = prepareBeastOutput(message);
						if (bytes != null) {
							Iterator<Client> it = sClientList.iterator();
							while (it.hasNext()) {
								Client client = it.next();
								if (client.isConnected())
									client.send(bytes);
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

	private BeastFormatExporter() {
	}
}
