package marto.rtl_tcp_andro.core;

import android.os.SystemClock;
import android.util.Log;

import com.flightaware.android.flightfeeder.BuildConfig;


public class RtlTcp {
	public static interface RtlTcpProcessListener {
		void onProcessStarted();

		/**
		 * Whenever the process writes something to its stdout, this will get
		 * called
		 */
		void onProcessStdOutWrite(final String line);

		void onProcessStopped(final int exitCode, Exception e);
	}

	final static int EXIT_CANNOT_CLOSE = 6;
	final static int EXIT_CANNOT_RESTART = 5;
	final static int EXIT_FAILED_TO_OPEN_DEVICE = 4;
	final static int EXIT_INVALID_FD = 2;
	final static int EXIT_NO_DEVICES = 3;
	final static int EXIT_NOT_ENOUGH_POWER = 9;
	final static int EXIT_OK = 0;
	final static int EXIT_SIGNAL_CAUGHT = 8;
	final static int EXIT_UNKNOWN = 7;
	final static int EXIT_WRONG_ARGS = 1;

	private static volatile int sExitCode = EXIT_UNKNOWN;
	private static RtlTcpProcessListener sListener;
	private final static String TAG = "RTLTCP";

	static {
		System.loadLibrary("rtltcp");
	}

	private static native int close();// throws RtlTcpException;

	public static native boolean isNativeRunning();

	private static void onclose(int exitcode) {
		sExitCode = exitcode;

		if (BuildConfig.DEBUG) {
			if (exitcode != EXIT_OK)
				Log.e(TAG, "Exitcode " + exitcode);
			else
				Log.d(TAG, "Exited with success");
		}
	}

	private static void onopen() {
		if (sListener != null)
			sListener.onProcessStarted();

		if (BuildConfig.DEBUG)
			Log.d(TAG, "Device open");
	}

	private static native void open(final String args, final int descriptor,
			final String usbFsPath);// throws RtlTcpException;

	private static void printf_receiver(final String data) {
		if (sListener != null)
			sListener.onProcessStdOutWrite(data);

		if (BuildConfig.DEBUG)
			Log.d(TAG, data);
	}

	private static void printf_stderr_receiver(final String data) {
		if (sListener != null)
			sListener.onProcessStdOutWrite(data);

		if (BuildConfig.DEBUG)
			Log.w(TAG, data);
	}

	public static void start(final String args, final int fd,
			final String uspfs_path, final RtlTcpProcessListener listener)
			throws Exception {

		new Thread() {
			public void run() {
				long maxWait = 10000;
				Exception e = null;

				if (isNativeRunning()) {
					close();

					long waited = 0;
					while (isNativeRunning() && waited < maxWait) {
						SystemClock.sleep(100);

						waited += 100;
					}

					if (isNativeRunning() && sListener != null) {
						sListener.onProcessStopped(sExitCode, new Exception(
								String.valueOf(EXIT_CANNOT_RESTART)));

						return;
					}
				}

				sListener = listener;

				sExitCode = EXIT_UNKNOWN;

				open(args, fd, uspfs_path);

				if (isNativeRunning()) {
					close();

					long waited = 0;
					while (isNativeRunning() && waited < maxWait) {
						SystemClock.sleep(100);

						waited += 100;
					}

					if (isNativeRunning())
						sExitCode = EXIT_CANNOT_CLOSE;
				}

				if (sExitCode != EXIT_OK)
					e = new Exception(String.valueOf(sExitCode));

				if (sListener != null)
					sListener.onProcessStopped(sExitCode, e);
			};
		}.start();
	}

	public static void stop() {
		if (!isNativeRunning())
			return;

		close();
	}
}
