package com.radixdlt.consensus;

import java.util.OptionalLong;

/**
 * Interface for an event coordinator to manage the pacemaker
 */
public interface Pacemaker {
	long getCurrentRound();
	boolean processLocalTimeout(long round);
	OptionalLong processQC(long round);
}
