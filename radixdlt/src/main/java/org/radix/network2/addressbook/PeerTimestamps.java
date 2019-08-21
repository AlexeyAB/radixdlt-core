package org.radix.network2.addressbook;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;

public final class PeerTimestamps {
	private static final long NEVER = Long.MIN_VALUE;
	private static LongSupplier timeSource = System::currentTimeMillis;

	@VisibleForTesting
	static void setTimeSource(LongSupplier timeSource) {
		PeerTimestamps.timeSource = timeSource;
	}

	public static PeerTimestamps of(long active, long banned) {
		return new PeerTimestamps(active, banned);
	}

	public static PeerTimestamps of(long active) {
		return new PeerTimestamps(active, NEVER);
	}

	public static PeerTimestamps never() {
		// It never happened
		return new PeerTimestamps(NEVER, NEVER);
	}

	// Volatile to avoid tearing
	private volatile long active;
	private volatile long banned;

	@VisibleForTesting
	PeerTimestamps(long active, long banned) {
		this.active = active;
		this.banned = banned;
	}

	@JsonCreator
	private PeerTimestamps(Map<String, Long> props) {
		// Constructor for serialization
		this.active = props.getOrDefault("active", NEVER);
		this.banned = props.getOrDefault("banned", NEVER);
	}

	@JsonValue
	private Map<String, Long> getSerializableTimestamps() {
		// Getter for serialization
		return ImmutableMap.of(
			"active", this.active,
			"banned", this.banned
		);
	}

	public long getActive() {
		return active;
	}

	public void setActive(long active) {
		this.active = active;
	}

	public void setActive() {
		this.active = timeSource.getAsLong();
	}

	public long getBanned() {
		return banned;
	}

	public void setBanned(long banned) {
		this.banned = banned;
	}

	public void banFor(long duration, TimeUnit timeUnit) {
		this.banned = timeSource.getAsLong() + timeUnit.toMillis(duration);
	}

	@Override
	public int hashCode() {
		return Long.hashCode(active) * 31 + Long.hashCode(banned);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj instanceof PeerTimestamps) {
			PeerTimestamps other = (PeerTimestamps) obj;
			return other.active == this.active && other.banned == this.banned;
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s[active=%s, banned=%s]", getClass().getSimpleName(), active, banned);
	}
}