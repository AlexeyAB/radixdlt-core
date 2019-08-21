package org.radix.network2.addressbook;

import java.util.stream.Stream;

import org.radix.network2.transport.TransportInfo;
import org.radix.network2.transport.TransportMetadata;

import com.radixdlt.common.EUID;

public interface Peer {

	/**
	 * Returns the Node ID of the {@link Peer}.
	 *
	 * @return Return the Node ID of the {@link Peer}
	 */
	EUID getNID();

	/**
	 * Returns the timestamps associated with the {@link Peer}.
	 *
	 * @return Return the timestamps associated with the {@link Peer}.
	 */
	PeerTimestamps getTimestamps();

	/**
	 * Returns {@code true} or {@code false} indicating if this {@link Peer}
	 * supports the specified transport.
	 *
	 * @param transportName The transport to test for
	 * @return {@code true} if the {@link Peer} supports the transport, {@code false} otherwise
	 */
	boolean supportsTransport(String transportName);

	/**
	 * Returns a {@link Stream} of the transports supported by the {@link Peer}.
	 *
	 * @return a {@link Stream} of the transports supported by the {@link Peer}
	 */
	Stream<TransportInfo> supportedTransports();

	/**
	 * Return the connection data required to connect to this peer using the
	 * specified transport.
	 *
	 * @param transportName The transport for which the {@link TransportMetadata} is required
	 * @return The {@link TransportMetadata}
	 * @throws TransportException if the transport is not supported, or another error occurs
	 */
	TransportMetadata connectionData(String transportName);

}