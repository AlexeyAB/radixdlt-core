package com.radixdlt.tempo.epics;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.MomentumUtils;
import com.radixdlt.tempo.SampleSelector;
import com.radixdlt.tempo.TempoAction;
import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.TempoEpic;
import com.radixdlt.tempo.TempoException;
import com.radixdlt.tempo.TempoState;
import com.radixdlt.tempo.TempoStateBundle;
import com.radixdlt.tempo.actions.OnConflictResolvedAction;
import com.radixdlt.tempo.actions.RaiseConflictAction;
import com.radixdlt.tempo.actions.OnSamplingCompleteAction;
import com.radixdlt.tempo.actions.RequestSamplingAction;
import com.radixdlt.tempo.actions.ResolveConflictAction;
import com.radixdlt.tempo.state.ConflictsState;
import com.radixdlt.tempo.state.LivePeersState;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network.peers.Peer;
import org.radix.time.TemporalProof;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class MomentumResolverEpic implements TempoEpic {
	private static final Logger logger = Logging.getLogger("Conflicts");

	private final SampleSelector sampleSelector;

	public MomentumResolverEpic(SampleSelector sampleSelector) {
		this.sampleSelector = sampleSelector;
	}

	@Override
	public Set<Class<? extends TempoState>> requiredState() {
		return ImmutableSet.of(
			LivePeersState.class,
			ConflictsState.class
		);
	}

	@Override
	public Stream<TempoAction> epic(TempoStateBundle bundle, TempoAction action) {
		if (action instanceof RaiseConflictAction) {
			RaiseConflictAction conflict = (RaiseConflictAction) action;
			ConflictsState conflicts = bundle.get(ConflictsState.class);

			// if the conflict is already raised and not yet resolved, early out
			if (conflicts.isPending(conflict.getTag())) {
				logger.warn("Conflict with tag '" + conflict.getTag() + "' is already pending");
				return Stream.empty();
			} else {
				// if the conflict is not currently pending, resolve it
				return Stream.of(new ResolveConflictAction(conflict.getAtom(), conflict.getConflictingAtoms(), conflict.getTag()));
			}
		} else if (action instanceof ResolveConflictAction) {
			ResolveConflictAction conflict = (ResolveConflictAction) action;
			ImmutableSet<AID> allAids = conflict.allAids().collect(ImmutableSet.toImmutableSet());
			LivePeersState livePeers = bundle.get(LivePeersState.class);

			// to resolve a conflict, select some peers to sample
			ImmutableSet<Peer> samplePeers = sampleSelector.selectSamples(livePeers.getNids(), conflict.getAtom()).stream()
				.map(livePeers::getPeer)
				.filter(Optional::isPresent)
				.map(Optional::get)
				.collect(ImmutableSet.toImmutableSet());
			logger.info(String.format("Resolving conflict with tag '%s' between '%s', initiating sampling",
				conflict.getTag(), allAids));
			// sample the selected peers
			return Stream.of(new RequestSamplingAction(samplePeers, allAids, conflict.getTag()));
		} else if (action instanceof OnSamplingCompleteAction) {
			OnSamplingCompleteAction result = (OnSamplingCompleteAction) action;
			Collection<TemporalProof> allSamples = result.getAllSamples();
			EUID tag = result.getTag();
			ConflictsState conflicts = bundle.get(ConflictsState.class);
			Set<AID> allConflictingAids = conflicts.getAids(tag);

			TempoAtom winningAtom;
			if (allSamples.isEmpty()) {
				logger.warn("No samples available for any of '" + allConflictingAids +  "', resolving to current preference");
				winningAtom = conflicts.getCurrentAtom(tag);
			} else {
				// decide on winner using samples
				Map<AID, List<EUID>> preferences = MomentumUtils.extractPreferences(allSamples);
				Map<AID, Long> momenta = MomentumUtils.measure(preferences, nid -> 1L);
				AID winner = momenta.entrySet().stream()
					.max(Comparator.comparingLong(Map.Entry::getValue))
					.map(Map.Entry::getKey)
					.orElseThrow(() -> new TempoException("Internal error while measuring momenta"));
				logger.info(String.format("Resolved conflict with tag '%s' to %s, measured momenta to be %s for %s",
					result.getTag(), winner, momenta, allConflictingAids));
				winningAtom = conflicts.getAtom(tag, winner);
			}

			return Stream.of(new OnConflictResolvedAction(winningAtom, allConflictingAids, tag));
		}

		return Stream.empty();
	}

}

