package com.flightaware.android.flightfeeder.analyzers.dump978;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;

import com.flightaware.android.flightfeeder.BuildConfig;

public class ReadTestDataFileThread extends Thread {
	public static final int BUFFER_SIZE = 128 * 1024;

	public ReadTestDataFileThread() {
		setName("ReadTestDataFileThread");
	}

	@Override
	public void run() {
		if (BuildConfig.DEBUG)
			System.out.println("Started reading test data file");

		BufferedInputStream stream = null;

		try {
			File file = new File("/mnt/sdcard", "sample1843.bin");
			FileInputStream fis = new FileInputStream(file);
			stream = new BufferedInputStream(fis);

			byte[] buffer = new byte[BUFFER_SIZE];

			int length = 0;

			while (!Dump978.sExit) { // && length != BUFFER_SIZE) {
				length = stream.read(buffer);

				if (length == -1)
					break;

				RtlSdrDataQueue.offer(Arrays.copyOf(buffer, length));
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (stream != null) {
				try {
					stream.close();
				} catch (Exception e) {
					// swallow
				}
			}
		}

		if (BuildConfig.DEBUG)
			System.out.println("Stopped reading test data file");
	}
}
