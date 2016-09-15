package com.flightaware.android.flightfeeder.util;

import java.util.Iterator;
import java.util.Map;

import android.os.SystemClock;
import android.util.LruCache;

/**
 * @author Baron
 * 
 *         This class is thread-safe. All operations are synchronized.
 * 
 * @param <K>
 *            An object to be used as a key.
 * @param <V>
 *            An object that you wish to store and retrieve
 */
public class TimedLruCache<K, V> {

	public static final class ValueHolder<V> {
		private long mTimestamp;
		private V mValue;

		public ValueHolder(V value, long timestamp) {
			mValue = value;
			mTimestamp = timestamp;
		}

		public V getValue() {
			return mValue;
		}
	}

	private LruCache<K, ValueHolder<V>> mCache;
	private long mMaxAge;

	/**
	 * A TimedLruCache configured with a default expiration of 60 seconds.
	 * 
	 * @param maxSize
	 *            The maximum number of items this cache can store before
	 *            evicting old items.
	 */
	public TimedLruCache(int maxSize) {
		this(maxSize, 60 * 1000);
	}

	/**
	 * @param maxSize
	 *            The maximum number of items this cache can store before
	 *            evicting old items.
	 * @param maxAge
	 *            The maximum time an item can live in the cache without being
	 *            accessed - milliseconds
	 */
	public TimedLruCache(int maxSize, long maxAge) {
		mCache = new LruCache<K, ValueHolder<V>>(maxSize);
		mMaxAge = maxAge;
	}

	public void evictAll() {
		synchronized (mCache) {
			mCache.evictAll();
		}
	}

	public V get(K key) {
		synchronized (mCache) {
			long now = SystemClock.uptimeMillis();

			removeExpired(now);

			ValueHolder<V> holder = mCache.get(key);

			if (holder == null)
				return null;

			holder.mTimestamp = now;

			return holder.mValue;
		}
	}

	public V put(K key, V value) {
		synchronized (mCache) {
			if (key == null || value == null)
				return null;

			ValueHolder<V> holder = mCache.put(key, new ValueHolder<V>(value,
					SystemClock.uptimeMillis()));

			if (holder == null)
				return null;

			return holder.mValue;
		}
	}

	public V remove(K key) {
		synchronized (mCache) {
			ValueHolder<V> holder = mCache.remove(key);

			if (holder == null)
				return null;

			return holder.mValue;
		}
	}

	private void removeExpired(long now) {
		synchronized (mCache) {
			Iterator<ValueHolder<V>> it = mCache.snapshot().values().iterator();
			while (it.hasNext()) {
				ValueHolder<V> holder = it.next();

				if (now - mMaxAge > holder.mTimestamp)
					it.remove();

			}
		}
	}

	public Map<K, ValueHolder<V>> snapshot() {
		synchronized (mCache) {
			return mCache.snapshot();
		}
	}
}
