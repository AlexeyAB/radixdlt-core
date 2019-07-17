package com.radixdlt.universe;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.radixdlt.atoms.Atom;
import com.radixdlt.utils.Offset;
import org.radix.containers.BasicContainer;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.ECSignature;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.utils.Bytes;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

@SerializerId2("radix.universe")
public class Universe extends BasicContainer
{

	/**
	 * Universe builder.
	 */
	public static class Builder {
		private Integer port;
		private String name;
		private String description;
		private UniverseType type;
		private Long timestamp;
		private Long planck;
		private ECPublicKey creator;
		private final ImmutableList.Builder<Atom> genesis = ImmutableList.builder();

		private Builder() {
			// Nothing to do here
		}

		/**
		 * Sets the TCP/UDP port for the universe.
		 *
		 * @param port The TCP/UDP port for the universe to use, {@code 0 <= port <= 65,535}.
		 * @return A reference to {@code this} to allow method chaining.
		 */
		public Builder port(int port) {
			if (port < 0 || port > 65535) {
				throw new IllegalArgumentException("Invalid port number: " + port);
			}
			this.port = port;
			return this;
		}

		/**
		 * Sets the name of the universe.
		 * Ideally the universe name is a short identifier for the universe.
		 *
		 * @param name The name of the universe.
		 * @return A reference to {@code this} to allow method chaining.
		 */
		public Builder name(String name) {
			this.name = Objects.requireNonNull(name);
			return this;
		}

		/**
		 * Set the description of the universe.
		 * The universe description is a longer description of the universe.
		 *
		 * @param description The description of the universe.
		 * @return A reference to {@code this} to allow method chaining.
		 */
		public Builder description(String description) {
			this.description = Objects.requireNonNull(description);
			return this;
		}

		/**
		 * Sets the type of the universe, one of {@link UniverseType}.
		 *
		 * @param type The type of the universe.
		 * @return A reference to {@code this} to allow method chaining.
		 */
		public Builder type(UniverseType type) {
			this.type = Objects.requireNonNull(type);
			return this;
		}

		/**
		 * Sets the creation timestamp of the universe.
		 *
		 * @param timestamp The creation timestamp of the universe.
		 * @return A reference to {@code this} to allow method chaining.
		 */
		public Builder timestamp(long timestamp) {
			if (timestamp < 0) {
				throw new IllegalArgumentException("Invalid timestamp: " + timestamp);
			}
			this.timestamp = timestamp;
			return this;
		}

		/**
		 * Sets the planck period of the universe.
		 *
		 * @param planckPeriod The planck period of the universe.
		 * @return A reference to {@code this} to allow method chaining.
		 */
		public Builder planckPeriod(long planckPeriod) {
			if (planckPeriod <= 0) {
				throw new IllegalArgumentException("Invalid planck period: " + planckPeriod);
			}
			this.planck = planckPeriod;
			return this;
		}

		/**
		 * Sets the universe creators public key.
		 *
		 * @param creator The universe creators public key.
		 * @return A reference to {@code this} to allow method chaining.
		 */
		public Builder creator(ECPublicKey creator) {
			this.creator = Objects.requireNonNull(creator);
			return this;
		}

		/**
		 * Adds an atom to the genesis atom list.
		 *
		 * @param genesisAtom The atom to add to the genesis atom list.
		 * @return A reference to {@code this} to allow method chaining.
		 */
		public Builder addAtom(Atom genesisAtom) {
			Objects.requireNonNull(genesisAtom);
			this.genesis.add(genesisAtom);
			return this;
		}

		/**
		 * Adds a list of atoms to the genesis atom list.
		 *
		 * @param genesisAtoms The atoms to add to the genesis atom list.
		 * @return A reference to {@code this} to allow method chaining.
		 */
		public Builder addAtoms(Iterable<Atom> genesisAtoms) {
			genesisAtoms.forEach(this::addAtom);
			return this;
		}

		/**
		 * Validate and build a universe from the specified data.
		 *
		 * @return The freshly build universe object.
		 */
		public Universe build() {
			require(this.port, "Port number");
			require(this.name, "Name");
			require(this.description, "Description");
			require(this.type, "Universe type");
			require(this.timestamp, "Timestamp");
			require(this.planck, "Planck");
			require(this.creator, "Creator");
			return new Universe(this);
		}

		private void require(Object item, String what) {
			if (item == null) {
				throw new IllegalStateException(what + " must be specified");
			}
		}
	}

	/**
	 * Construct a new {@link Builder}.
	 *
	 * @return The freshly constructed builder.
	 */
	public static Builder newBuilder() {
		return new Builder();
	}

	/**
	 * Computes universe magic number from specified parameters.
	 *
	 * @param creator {@link ECPublicKey} of universe creator to use when calculating universe magic
	 * @param timestamp universe timestamp to use when calculating universe magic
	 * @param port universe port to use when calculating universe magic
	 * @param type universe type to use when calculating universe magic
	 * @param planck universe planck to use when calculating universe magic
	 * @return The universe magic
	 */
	public static int computeMagic(ECPublicKey creator, long timestamp, int port, UniverseType type, long planck) {
		return 31 * ((int) creator.getUID().getLow()) * 13 * (int) timestamp * 7 * port + type.ordinal(); // + planck;
	}

	/**
	 * Computes quantised planck time from specified parameters.
	 *
	 * @param timestamp Timestamp in milliseconds since epoch
	 * @param planck Planck quantum in milliseconds
	 * @param offset Offset for resultant planck time unit
	 * @return The possibly offset planck time unit calculated from specified parameters
	 */
	public static int computePlanck(long timestamp, long planck, Offset offset) {
		return (int) (timestamp / planck) + offset.getOffset();
	}

	@Override
	public short VERSION() { return 100; }

	public enum UniverseType
	{
		PRODUCTION,
		TEST,
		DEVELOPMENT;
	}

	@JsonProperty("name")
	@DsonOutput(Output.ALL)
	private String 		name;

	@JsonProperty("description")
	@DsonOutput(Output.ALL)
	private String 		description;

	@JsonProperty("timestamp")
	@DsonOutput(Output.ALL)
	private long 		timestamp;

	@JsonProperty("planck")
	@DsonOutput(Output.ALL)
	private long		planck;

	@JsonProperty("port")
	@DsonOutput(Output.ALL)
	private int			port;

	private UniverseType type;

	@JsonProperty("genesis")
	@DsonOutput(Output.ALL)
	private ImmutableList<Atom> genesis;

	private ECPublicKey creator;

	private ECSignature signature;
	private BigInteger sigR;
	private BigInteger sigS;

	Universe() {
		// No-arg constructor for serializer
	}

	private Universe(Builder builder) {
		super();

		this.port = builder.port.intValue();
		this.name = builder.name;
		this.description = builder.description;
		this.type = builder.type;
		this.timestamp = builder.timestamp.longValue();
		this.planck = builder.planck.longValue();
		this.creator = builder.creator;
		this.genesis = builder.genesis.build();
	}

	/**
	 * Magic identifier for Universe.
	 *
	 * @return
	 */
	@JsonProperty("magic")
	@DsonOutput(value = Output.HASH, include = false)
	public int getMagic() {
		return computeMagic(creator, timestamp, port, type, planck);
	}

	/**
	 * The name of Universe.
	 *
	 * @return
	 */
	public String getName()
	{
		return name;
	}

	/**
	 * The Universe description.
	 *
	 * @return
	 */
	public String getDescription()
	{
		return description;
	}

	/**
	 * The default TCP/UDP port for the Universe.
	 *
	 * @return
	 */
	public int getPort()
	{
		return port;
	}

	/**
	 * The UTC 'BigBang' timestamp for the Universe.
	 *
	 * @return
	 */
	public long getTimestamp()
	{
		return timestamp;
	}

	/**
	 * The Planck period of the Universe in milliseconds.<br><br>Used primarily for mass calculations.
	 *
	 * @return
	 */
	public long getPlanck()
	{
		return this.planck;
	}

	public int toPlanck(long timestamp, Offset offset)
	{
		int planck = computePlanck(timestamp, this.planck, offset);

		if (planck < this.timestamp / this.planck)
			planck = (int) (this.timestamp / this.planck);

		return planck;
	}

	public long fromPlanck(int period, Offset offset)
	{
		return (period * this.planck);
	}

	/**
	 * Whether this is a production Universe.
	 *
	 * @return
	 */
	public boolean isProduction()
	{
		return type.equals(UniverseType.PRODUCTION);
	}

	/**
	 * Whether this is a test Universe.
	 *
	 * @return
	 */
	public boolean isTest()
	{
		return type.equals(UniverseType.TEST);
	}

	/**
	 * Whether this is a development Universe.
	 *
	 * @return
	 */
	public boolean isDevelopment()
	{
		return type.equals(UniverseType.DEVELOPMENT);
	}

	/**
	 * Gets this Universe's immutable Genesis collection.
	 *
	 * @return
	 */
	public List<Atom> getGenesis()
	{
		return genesis;
	}

	/**
	 * Get creator key.
	 *
	 * @return
	 */
	public ECPublicKey getCreator()
	{
		return creator;
	}

	public ECSignature getSignature()
	{
		return signature;
	}

	public void setSignature(ECSignature signature)
	{
		this.signature = signature;
	}

	public void sign(ECKeyPair key) throws CryptoException
	{
		this.signature = key.sign(getHash());
	}

	public boolean verify(ECPublicKey key)
	{
		return key.verify(getHash(), signature);
	}

	/**
	 * Check whether a given universe is valid
	 */
	public void validate() {
		// Check signature
		if (!creator.verify(getHash(), signature)) {
			throw new IllegalStateException("Invalid universe signature");
		}

		// Check if it has a temporal proof (RLAU-467)
		boolean missingTemporalProofs = genesis.stream()
			.anyMatch(atom -> atom.getTemporalProof().isEmpty());

		if (missingTemporalProofs) {
			throw new IllegalStateException("All atoms in genesis need to have non-empty temporal proofs");
		}
	}


	// Type - 1 getter, 1 setter
	// Better option would be to output string enum value (as with other enums), rather than ordinal
	@JsonProperty("type")
	@DsonOutput(Output.ALL)
	private int getJsonType() {
		return this.type.ordinal();
	}

	@JsonProperty("type")
	private void setJsonType(int type) {
		this.type = UniverseType.values()[type];
	}

	// Signature - 1 getter, 1 setter.
	// Better option would be to make public keys primitive types as they are
	// very common, or alternatively serialize as an embedded object.
	@JsonProperty("creator")
	@DsonOutput(Output.ALL)
	private byte[] getJsonCreator() {
		return this.creator.getBytes();
	}

	@JsonProperty("creator")
	private void setJsonCreator(byte[] bytes) throws CryptoException {
		this.creator = new ECPublicKey(bytes);
	}

	// Signature - 2 getters, 2 setters.
	// FIXME: Better option would be to serialize as a ECSignature embedded object
	// rather than the two individual components directly in the object.
	@JsonProperty("signature.r")
	@DsonOutput(value = Output.HASH, include = false)
	private byte[] getJsonSignatureR() {
		return Bytes.trimLeadingZeros(signature.getR().toByteArray());
	}

	@JsonProperty("signature.s")
	@DsonOutput(value = Output.HASH, include = false)
	private byte[] getJsonSignatureS() {
		return Bytes.trimLeadingZeros(signature.getS().toByteArray());
	}

	@JsonProperty("signature.r")
	private void setJsonSignatureR(byte[] r) {
		// Set sign to positive to stop BigInteger interpreting high bit as sign
		this.sigR = new BigInteger(1, r);
		if (this.sigS != null) {
			signature = new ECSignature(this.sigR, this.sigS);
			this.sigS = null;
			this.sigR = null;
		}
	}

	@JsonProperty("signature.s")
	private void setJsonSignatureS(byte[] s) {
		// Set sign to positive to stop BigInteger interpreting high bit as sign
		this.sigS = new BigInteger(1, s);
		if (this.sigR != null) {
			signature = new ECSignature(this.sigR, this.sigS);
			this.sigS = null;
			this.sigR = null;
		}
	}
}