package com.radixdlt.tempo.actions.messaging;

import com.google.common.collect.ImmutableList;
import com.radixdlt.common.AID;
import com.radixdlt.tempo.TempoAction;
import com.radixdlt.tempo.messages.DeliveryRequestMessage;
import org.radix.network.peers.Peer;

import java.util.Objects;

public class SendDeliveryRequestAction implements TempoAction {
	private final ImmutableList<AID> aids;
	private final Peer peer;

	public SendDeliveryRequestAction(ImmutableList<AID> aids, Peer peer) {
		this.aids = Objects.requireNonNull(aids, "aids is required");
		this.peer = Objects.requireNonNull(peer, "peer is required");
	}

	public ImmutableList<AID> getAids() {
		return aids;
	}

	public Peer getPeer() {
		return peer;
	}

	public DeliveryRequestMessage toMessage() {
		return new DeliveryRequestMessage(aids);
	}

	public static SendDeliveryRequestAction from(DeliveryRequestMessage message, Peer peer) {
		return new SendDeliveryRequestAction(ImmutableList.copyOf(message.getAids()), peer);
	}
}
