package org.radix.time;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import org.radix.collections.WireableSet;
import com.radixdlt.common.EUID;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.ECSignature;
import com.radixdlt.crypto.Hash;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("tempo.temporal_vertex")
public class TemporalVertex extends ChronologicObject
{
	@Override
	public short VERSION()
	{
		return 100;
	}

	@JsonProperty("previous")
	@DsonOutput(Output.ALL)
	private EUID 			previous;

	@JsonProperty("commitment")
	@DsonOutput(Output.ALL)
	private Hash			commitment;

	@JsonProperty("clock")
	@DsonOutput(Output.ALL)
	private long			clock;

	@JsonProperty("rclock")
	@DsonOutput(Output.ALL)
	private long			rclock;

	@JsonProperty("owner")
	@DsonOutput(Output.ALL)
	private ECPublicKey owner;

	@JsonProperty("signature")
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	private ECSignature		signature;

	@JsonProperty("nids")
	@DsonOutput(Output.ALL)
	private final WireableSet<EUID> NIDS = new WireableSet<>();

	public TemporalVertex()
	{
		super();
	}

	public TemporalVertex(ECPublicKey owner, long clock, long rclock, Hash commitment, EUID previous, EUID ... NIDS)
	{
		this();

		this.previous = previous;
		this.owner = owner;
		this.clock = clock;
		this.rclock = rclock;
		this.commitment = commitment;

		if (NIDS != null && NIDS.length > 0)
			for (EUID NID : NIDS)
				this.NIDS.add(NID);
	}

	public TemporalVertex(ECPublicKey owner, long clock, long rclock, Hash commitment, EUID previous, Collection<EUID> NIDS)
	{
		this();

		this.previous = previous;
		this.owner = owner;
		this.clock = clock;
		this.rclock = rclock;
		this.commitment = commitment;

		if (NIDS != null)
			this.NIDS.addAll(NIDS);
	}

	@Override
	public void reset(ECKeyPair accessor)
	{
		super.reset(accessor);

		if (this.signature != null)
			this.signature = null;
	}

	public long getClock()
	{
		return this.clock;
	}

	public long getRClock() { return this.rclock; }


	public Hash getCommitment()
	{
		return this.commitment;
	}

	public EUID getPrevious()
	{
		return this.previous;
	}

	public ECPublicKey getOwner()
	{
		return this.owner;
	}

	public Set<EUID> getNIDS()
	{
		return Collections.unmodifiableSet(this.NIDS);
	}

	@Override
	public String toString()
	{
		return this.getHID()+":"+this.clock+":"+this.commitment+":"+this.previous+":"+this.owner.getUID()+" -> "+this.NIDS.toString();
	}

	public ECSignature getSignature()
	{
		return this.signature;
	}

	public void setSignature(ECSignature signature)
	{
		this.signature = signature;
	}

	public boolean verify(ECPublicKey key) throws CryptoException
	{
		if (this.signature == null)
			throw new CryptoException("No signature set, can not verify");

		return key.verify(getHash(), signature);
	}
}