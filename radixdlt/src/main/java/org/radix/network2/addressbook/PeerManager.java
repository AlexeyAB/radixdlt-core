package org.radix.network2.addressbook;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import com.radixdlt.common.EUID;
import org.radix.common.executors.ScheduledExecutable;
import org.radix.events.Events;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.modules.Plugin;
import org.radix.modules.exceptions.ModuleException;
import org.radix.network.discovery.BootstrapDiscovery;
import org.radix.network.messages.GetPeersMessage;
import org.radix.network.messages.PeerPingMessage;
import org.radix.network.messages.PeerPongMessage;
import org.radix.network.messages.PeersMessage;
import org.radix.network.peers.events.PeerAvailableEvent;
import org.radix.network2.messaging.MessageCentral;
import org.radix.network2.transport.TransportException;
import com.radixdlt.universe.Universe;
import org.radix.time.Time;
import org.radix.time.Timestamps;
import org.radix.universe.system.LocalSystem;
import org.radix.universe.system.SystemMessage;

// FIXME: static dependency on Modules.get(Universe.class).getPlanck()
// FIXME: static dependency on BootstrapDiscovery.getInstance().discover(...)
// FIXME: static dependency on LocalSystem.getInstance().getNID()
public class PeerManager extends Plugin {
	private static final Logger log = Logging.getLogger("peermanager");

	private static final long PROBE_TIMEOUT_SECONDS = 20;

	private final Random rand = new Random(); // No need for cryptographically secure here
	private final Map<Peer, Long> probes = new HashMap<>();

	private final AddressBook addressbook;
	private final MessageCentral messageCentral;
	private final Events events;

	private final int peersBroadcastIntervalSec;
	private final int peersProbeIntervalSec;
	private final long peerProbeDelayMs;


	private class ProbeTask implements Runnable {
		private LinkedList<Peer> peersToProbe = new LinkedList<>();
		private int numPeers = 0;

		@Override
		public void run() {
			try {
				int numProbes = (int) (this.numPeers / TimeUnit.MILLISECONDS.toSeconds(Modules.get(Universe.class).getPlanck()));

				if (numProbes == 0) {
					numProbes = 16;
				}

				if (peersToProbe.isEmpty()) {
					addressbook.peers()
						.filter(StandardFilters.standardFilter())
						.forEachOrdered(peersToProbe::add);
					this.numPeers = peersToProbe.size();
				}

				numProbes = Math.min(numProbes, peersToProbe.size());
				if (numProbes > 0) {
					List<Peer> toProbe = peersToProbe.subList(0, numProbes);
					toProbe.forEach(PeerManager.this::probe);
					toProbe.clear();
				}
			} catch (Exception ex) {
				log.error("Peer probing failed", ex);
			}
		}
	}

	PeerManager(PeerManagerConfiguration config, AddressBook addressbook, MessageCentral messageCentral, Events events) {
		super();

		this.addressbook = Objects.requireNonNull(addressbook);
		this.messageCentral = Objects.requireNonNull(messageCentral);
		this.events = Objects.requireNonNull(events);

		this.peersBroadcastIntervalSec = config.networkPeersBroadcastInterval(30);
		this.peersProbeIntervalSec = config.networkPeersProbeInterval(1);
		this.peerProbeDelayMs = TimeUnit.SECONDS.toMillis(config.networkPeerProbeDelay(30));

		log.info(String.format("%s started, broadcastInterval=%s, probeInterval=%s, probeDelay=%s",
			this.getClass().getSimpleName(), this.peersBroadcastIntervalSec, this.peersProbeIntervalSec, this.peerProbeDelayMs / 1000L));
	}

	@Override
	public void start_impl() throws ModuleException {
		// Listen for messages
		register(PeersMessage.class, this::handlePeersMessage);
		register(GetPeersMessage.class, this::handleGetPeersMessage);
		register(PeerPingMessage.class, this::handlePeerPingMessage);
		register(PeerPongMessage.class, this::handlePeerPongMessage);

		// Tasks
		scheduleAtFixedRate(scheduledExecutable(10L, 10L, TimeUnit.SECONDS, this::heartbeatPeers));
		scheduleWithFixedDelay(scheduledExecutable(60, peersBroadcastIntervalSec, TimeUnit.SECONDS, this::peersHousekeeping));
		scheduleWithFixedDelay(scheduledExecutable(0,  peersProbeIntervalSec, TimeUnit.SECONDS, new ProbeTask()));
		scheduleWithFixedDelay(scheduledExecutable(1, 60, TimeUnit.SECONDS, this::discoverPeers));
	}

	@Override
	public void stop_impl() throws ModuleException {
		// Nothing to do here
	}

	@Override
	public String getName() {
		return "Peer Manager";
	}

	private void heartbeatPeers() {
		// System Heartbeat
		SystemMessage msg = new SystemMessage();
		addressbook.recentPeers().forEachOrdered(peer -> {
			try {
				messageCentral.send(peer, msg);
			} catch (TransportException ioex) {
				log.error("Could not send System heartbeat to " + peer, ioex);
			}
		});
	}

	private void handlePeersMessage(Peer peer, PeersMessage peersMessage) {
		List<Peer> peers = peersMessage.getPeers();
		if (peers != null) {
			EUID localNid = LocalSystem.getInstance().getNID();
			peers.stream()
				.filter(Peer::hasSystem)
				.filter(p -> !localNid.equals(p.getNID()))
				.forEachOrdered(addressbook::updatePeer);
		}
	}

	private void handleGetPeersMessage(Peer peer, GetPeersMessage getPeersMessage) {
		try {
			// Deliver known Peers in its entirety, filtered on whitelist and activity
			// Chunk the sending of Peers so that UDP can handle it
			PeersMessage peersMessage = new PeersMessage();
			List<Peer> peers = addressbook.peers()
				.filter(Peer::hasNID)
				.filter(StandardFilters.standardFilter())
				.filter(StandardFilters.recentlyActive())
				.collect(Collectors.toList());

			for (Peer p : peers) {
				if (p.getNID().equals(peer.getNID())) {
					// Know thyself
					continue;
				}

				peersMessage.getPeers().add(p);
				if (peersMessage.getPeers().size() == 64) {
					messageCentral.send(peer, peersMessage);
					peersMessage = new PeersMessage();
				}
			}

			if (!peersMessage.getPeers().isEmpty()){
				messageCentral.send(peer, peersMessage);
			}
		} catch (Exception ex) {
			log.error("peers.get " + peer, ex);
		}
	}

	private void handlePeerPingMessage(Peer peer, PeerPingMessage message) {
		try {
			long nonce = message.getNonce();
			log.debug("peer.ping from " + peer + " with nonce '" + nonce + "'");
			messageCentral.send(peer, new PeerPongMessage(nonce));
			events.broadcast(new PeerAvailableEvent(peer));
		} catch (Exception ex) {
			log.error("peer.ping " + peer, ex);
		}
	}

	private void handlePeerPongMessage(Peer peer, PeerPongMessage message) {
		try {
			synchronized (this.probes) {
				long nonce = message.getNonce();
				if (this.probes.containsKey(peer) && this.probes.get(peer).longValue() == nonce) {
					this.probes.remove(peer);
					log.debug("Got peer.pong from " + peer + " with nonce '" + nonce + "'");
					events.broadcast(new PeerAvailableEvent(peer));
				} else {
					// Probably a transport-only peer which we now have a system for
					log.debug("Got peer.pong without matching probe from " + peer + " with nonce '" + nonce + "'");
				}
			}
		} catch (Exception ex) {
			log.error("peer.pong " + peer, ex);
		}
	}

	private void peersHousekeeping() {
		try {
			// Request peers information from connected nodes
			List<Peer> peers = addressbook.recentPeers().collect(Collectors.toList());
			if (!peers.isEmpty()) {
				int index = rand.nextInt(peers.size());
				Peer peer = peers.get(index);
				try {
					messageCentral.send(peer, new GetPeersMessage());
				} catch (TransportException ioex) {
					log.info("Failed to request peer information from " + peer, ioex);
				}
			}
		} catch (Exception t) {
			log.error("Peers update failed", t);
		}
	}

	private boolean probe(Peer peer) {
		try {
			synchronized(this.probes) {
				if (peer != null && (Time.currentTimestamp() - peer.getTimestamp(Timestamps.PROBED) < peerProbeDelayMs)) {
					return false;
				}

				if (peer != null && !this.probes.containsKey(peer)) {
					PeerPingMessage ping = new PeerPingMessage();
					this.probes.put(peer, ping.getNonce());

					schedule(scheduledExecutable(PROBE_TIMEOUT_SECONDS, 0, TimeUnit.SECONDS, () -> handleProbeTimeout(peer, ping.getNonce())));

					log.debug("Probing "+peer+" with nonce '"+ping.getNonce()+"'");
					messageCentral.send(peer, ping);
					peer.setTimestamp(Timestamps.PROBED, Time.currentTimestamp());
					return true;
				}
			}
		} catch (Exception ex) {
			log.error("Probe of peer " +peer + " failed", ex);
		}

		return false;
	}

	private void handleProbeTimeout(Peer peer, long nonce) {
		synchronized (this.probes) {
			Long value = this.probes.get(peer);
			if (value != null && value.longValue() == nonce) {
				log.info("Removing peer " + peer + " because of probe timeout");
				this.probes.remove(peer);
				this.addressbook.removePeer(peer);
			}
		}
	}

	private void discoverPeers() {
		// Probe all the bootstrap hosts so that they know about us
		GetPeersMessage msg = new GetPeersMessage();
		BootstrapDiscovery.getInstance().discover(StandardFilters.standardFilter()).stream()
			.map(addressbook::peer)
			.forEachOrdered(peer -> {
				probe(peer);
				messageCentral.send(peer, msg);
			});
	}

	private ScheduledExecutable scheduledExecutable(long initialDelay, long recurrentDelay, TimeUnit units, Runnable r) {
		return new ScheduledExecutable(initialDelay, recurrentDelay, units) {
			@Override
			public void execute() {
				r.run();
			}
		};
	}
}
