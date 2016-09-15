package com.flightaware.android.flightfeeder.analyzers;

import android.text.TextUtils;
import android.util.LruCache;

import com.flightaware.android.flightfeeder.analyzers.dump1090.ModeSMessage;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class Aircraft {

	private Integer mAltitude;
	private boolean mAltitudeHoldEnabled;
	private String mAltitudeSource;
	private long mAltitudeTimestamp;
	private boolean mAutoPilotEngaged;
	private Float mBaroSetting;
	private Integer mCategory;
	private ModeSMessage mEvenMessage;
	private RawPosition mEvenPosition;
	private Integer mHeading;
	private int mHeadingDelta;
	private long mHeadingTimestamp;
	private String mIcao;
	private String mIdentity;
	private Double mLatitude;
	private Double mLongitude;
	private int mMessageCount;
	private ModeSMessage mOddMessage;
	private RawPosition mOddPosition;
	private boolean mOnApproach;
	private boolean mOnGround;
	private boolean mReady;
	private long mSeen;
	private long mSeenLatLon;
	private Integer mSelectedAltitude;
	private Integer mSelectedHeading;
	private LruCache<String, Double> mSignalStrengthCache = new LruCache<String, Double>(
			8);
	private Integer mSquawk;
	private Integer mStatus;
	private boolean mTcasEnabled;
	private Integer mTrackAngle;
	private boolean mUat;
	private Integer mVelocity;
	private long mVelocityTimestamp;
	private boolean mVerticalNavEnabled;
	private Integer mVerticalRate;

	public Aircraft() {
	}

	public Aircraft(int addr) {
		mIcao = Integer.toHexString(addr).toUpperCase(Locale.US).trim();
	}

	public void addRawPosition(RawPosition rawPosition, boolean odd) {
		if (odd)
			setOddPosition(rawPosition);
		else
			setEvenPosition(rawPosition);
	}

	public void addSignalStrength(double signalStrength) {
		mSignalStrengthCache.put(UUID.randomUUID().toString(), signalStrength);
	}

	public Integer getAltitude() {
		return mAltitude;
	}

	public String getAltitudeSource() {
		return mAltitudeSource;
	}

	public double getAverageSignalStrength() {
		Map<String, Double> snapshot = mSignalStrengthCache.snapshot();

		double total = 0;
		for (double value : snapshot.values()) {
			total += value;
		}

		total += 1e-5;

		return 10 * Math.log10(total / 8);
	}

	public Float getBaroSetting() {
		return mBaroSetting;
	}

	public Integer getCategory() {
		return mCategory;
	}

	public ModeSMessage getEvenMessage() {
		return mEvenMessage;
	}

	public RawPosition getEvenPosition() {
		return mEvenPosition;
	}

	public Integer getHeading() {
		return mHeading;
	}

	public int getHeadingDelta() {
		return mHeadingDelta;
	}

	public String getIcao() {
		return mIcao;
	}

	public String getIdentity() {
		return mIdentity;
	}

	public Double getLatitude() {
		return mLatitude;
	}

	public Double getLongitude() {
		return mLongitude;
	}

	public int getMessageCount() {
		return mMessageCount;
	}

	public ModeSMessage getOddMessage() {
		return mOddMessage;
	}

	public RawPosition getOddPosition() {
		return mOddPosition;
	}

	public long getSeen() {
		return mSeen;
	}

	public long getSeenLatLon() {
		return mSeenLatLon;
	}

	public Integer getSelectedAltitude() {
		return mSelectedAltitude;
	}

	public Integer getSelectedHeading() {
		return mSelectedHeading;
	}

	public Integer getSquawk() {
		return mSquawk;
	}

	public Integer getStatus() {
		return mStatus;
	}

	public Integer getTrackAngle() {
		return mTrackAngle;
	}

	public Integer getVelocity() {
		return mVelocity;
	}

	public Integer getVerticalRate() {
		return mVerticalRate;
	}

	public boolean isAltitudeHoldEnabled() {
		return mAltitudeHoldEnabled;
	}

	public boolean isAutoPilotEngaged() {
		return mAutoPilotEngaged;
	}

	public boolean isOnApproach() {
		return mOnApproach;
	}

	public boolean isOnGround() {
		return mOnGround;
	}

	public boolean isReady(long timestamp) {
		if (TextUtils.isEmpty(mIcao))
			return false;

		if (mAltitude == null || timestamp - mAltitudeTimestamp > 30000)
			return false;

		if (mHeading == null || timestamp - mHeadingTimestamp > 30000)
			return false;

		if (mVelocity == null || timestamp - mVelocityTimestamp > 30000)
			return false;

		return mReady;
	}

	public boolean isTcasEnabled() {
		return mTcasEnabled;
	}

	public boolean isUat() {
		return mUat;
	}

	public boolean isVerticalNavEnabled() {
		return mVerticalNavEnabled;
	}

	public void setAltitude(Integer altitude, long timestamp) {
		mAltitude = altitude;
		mAltitudeTimestamp = timestamp;
	}

	public void setAltitudeHoldEnabled(boolean altitudeHoldEnabled) {
		mAltitudeHoldEnabled = altitudeHoldEnabled;
	}

	public void setAltitudeSource(String altitudeSource) {
		mAltitudeSource = altitudeSource;
	}

	public void setAutoPilotEngaged(boolean autoPilotEngaged) {
		mAutoPilotEngaged = autoPilotEngaged;
	}

	public void setBaroSetting(Float baroSetting) {
		mBaroSetting = baroSetting;
	}

	public void setCategory(int category) {
		mCategory = category;
	}

	public void setEvenMessage(ModeSMessage evenMessage) {
		mEvenMessage = evenMessage;
	}

	public void setEvenPosition(RawPosition evenPosition) {
		mEvenPosition = evenPosition;
	}

	public void setHeading(Integer heading, long timestamp) {
		if (heading != null && mHeading != null)
			mHeadingDelta = Math.abs(heading - mHeading);

		mHeading = heading;
		mHeadingTimestamp = timestamp;
	}

	public void setIcao(String icao) {
		mIcao = icao;
	}

	public void setIdentity(String identity) {
		mIdentity = identity;
	}

	public void setLatitude(Double latitude) {
		mLatitude = latitude;
	}

	public void setLongitude(Double longitude) {
		mLongitude = longitude;
	}

	public void setMessageCount(int messageCount) {
		mMessageCount = messageCount;
	}

	public void setOddMessage(ModeSMessage oddMessage) {
		mOddMessage = oddMessage;
	}

	public void setOddPosition(RawPosition oddPosition) {
		mOddPosition = oddPosition;
	}

	public void setOnApproach(boolean onApproach) {
		mOnApproach = onApproach;
	}

	public void setOnGround(boolean onGround) {
		mOnGround = onGround;
	}

	public void setReady(boolean ready) {
		mReady = ready;
	}

	public void setSeen(long seen) {
		mSeen = seen;
	}

	public void setSeenLatLon(long seenLatLon) {
		mSeenLatLon = seenLatLon;
	}

	public void setSelectedAltitude(Integer selectedAltitude) {
		mSelectedAltitude = selectedAltitude;
	}

	public void setSelectedHeading(Integer selectedHeading) {
		mSelectedHeading = selectedHeading;
	}

	public void setSquawk(Integer squawk) {
		mSquawk = squawk;
	}

	public void setStatus(Integer status) {
		mStatus = status;
	}

	public void setTcasEnabled(boolean tcasEnabled) {
		mTcasEnabled = tcasEnabled;
	}

	public void setTrackAngle(Integer trackAngle) {
		mTrackAngle = trackAngle;
	}

	public void setUat(boolean uat) {
		mUat = uat;
	}

	public void setVelocity(Integer velocity, long timestamp) {
		mVelocity = velocity;
		mVelocityTimestamp = timestamp;
	}

	public void setVerticalNavEnabled(boolean vNavEnabled) {
		mVerticalNavEnabled = vNavEnabled;
	}

	public void setVerticalRate(Integer verticalRate) {
		mVerticalRate = verticalRate;
	}
}
