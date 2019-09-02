package com.radixdlt.ledger;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.encoders.Hex;

import java.util.Objects;

@SerializerId2("ledger.index")
public final class LedgerIndex {
	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(DsonOutput.Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	// TODO change to int (byte for compatibility with legacy AtomStore IDType)
	@JsonProperty("prefix")
	@DsonOutput(DsonOutput.Output.ALL)
	private byte prefix;

	@JsonProperty("identifier")
	@DsonOutput(DsonOutput.Output.ALL)
	private byte[] identifier;

	private LedgerIndex() {
		// For serializer
	}

	public LedgerIndex(byte prefix, byte[] identifier) {
		this.prefix = prefix;
		this.identifier = Objects.requireNonNull(identifier, "identifier is required");
	}

	public LedgerIndex(byte[] key) {
		Objects.requireNonNull(key, "key is required");

		this.prefix = key[0];
		this.identifier = Arrays.copyOfRange(key, 1, key.length);
	}

	public int getPrefix() {
		return this.prefix;
	}

	public byte[] getIdentifier() {
		return this.identifier;
	}

	public byte[] asKey() {
		return from(this.prefix, this.identifier);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		LedgerIndex that = (LedgerIndex) o;
		return prefix == that.prefix && java.util.Arrays.equals(identifier, that.identifier);
	}

	@Override
	public int hashCode() {
		int result = Objects.hash(prefix);
		result = 31 * result + Arrays.hashCode(identifier);
		return result;
	}

	public String toHexString() {
		return Hex.toHexString(asKey());
	}

	@Override
	public String toString() {
		return String.format("%s[%s]",
			this.getClass().getSimpleName(),
			Hex.toHexString(asKey())
		);
	}

	public static byte[] from(byte prefix, byte[] identifier) {
		return Arrays.concatenate(new byte[]{prefix}, identifier);
	}

	public enum LedgerIndexType {
		UNIQUE, DUPLICATE
	}
}
