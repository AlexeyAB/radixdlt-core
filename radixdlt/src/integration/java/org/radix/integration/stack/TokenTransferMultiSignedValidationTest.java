package org.radix.integration.stack;

import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import com.radixdlt.atommodel.tokens.TokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokenDefinitionParticle.TokenTransition;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.atommodel.tokens.TokenPermission;
import com.radixdlt.atoms.Atom;
import org.radix.atoms.AtomStore;
import org.radix.atoms.PreparedAtom;
import com.radixdlt.atomos.RRI;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.constraintmachine.CMAtom;
import com.radixdlt.atoms.Spin;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.utils.Offset;
import org.radix.integration.RadixTestWithStores;
import org.radix.modules.Modules;
import org.radix.properties.RuntimeProperties;
import org.radix.time.NtpService;
import org.radix.time.Time;
import com.radixdlt.utils.UInt256;
import com.radixdlt.universe.Universe;
import org.radix.validation.ValidationHandler;

public class TokenTransferMultiSignedValidationTest extends RadixTestWithStores {
	private ECKeyPair identity;
	private RadixAddress universeAddress;
	private Universe universe;

	@Before
	public void createIdentity() throws Exception {
		String universeKeyPath = Modules.get(RuntimeProperties.class).get("universe.key.path", "universe.key");
		identity = ECKeyPair.fromFile(new File(universeKeyPath), true);
		universe = Modules.get(Universe.class);
		universeAddress = RadixAddress.from(universe, identity.getPublicKey());
	}

	private static long currentPlanckTime() {
		return Modules.get(Universe.class).toPlanck(Modules.get(NtpService.class).getUTCTimeMS(), Offset.NONE);
	}

	private static TransferrableTokensParticle createTransfer(RRI token, UInt256 amount, RadixAddress owner) {
		return new TransferrableTokensParticle(owner, amount, UInt256.ONE,
			token,
			currentPlanckTime(),
			ImmutableMap.of(TokenTransition.MINT, TokenPermission.ALL, TokenTransition.BURN, TokenPermission.NONE));
	}

	@Test
	public void testMultignedAtom() throws Exception {
		// Create RADIX TokenDefinition
		TokenDefinitionParticle tokenDefinition = new TokenDefinitionParticle(universeAddress,
			"RADIX",
			"Radix Token",
			"Just a test token",
			UInt256.ONE,
			null,
			ImmutableMap.of(
				TokenTransition.MINT, TokenPermission.ALL,
				TokenTransition.BURN, TokenPermission.NONE
			)
		);

		UnallocatedTokensParticle unallocatedTokensParticle = new UnallocatedTokensParticle(
			UInt256.MAX_VALUE,
			UInt256.ONE,
			tokenDefinition.getRRI(),
			ImmutableMap.of(
				TokenTransition.MINT, TokenPermission.ALL,
				TokenTransition.BURN, TokenPermission.NONE
			)
		);

		RRIParticle rriParticle = new RRIParticle(
			tokenDefinition.getRRI()
		);

		Atom atom = new Atom(Time.currentTimestamp());
		atom.addParticleGroupWith(
			tokenDefinition, Spin.UP,
			unallocatedTokensParticle, Spin.UP,
			rriParticle, Spin.DOWN
		);
		addTemporalVertex(atom); // Can't store atom without vertex from this node
		atom.sign(identity);

		CMAtom cmAtom = Modules.get(ValidationHandler.class).validate(atom);
		Modules.get(ValidationHandler.class).stateCheck(cmAtom);
		PreparedAtom preparedAtom = new PreparedAtom(cmAtom);
		Modules.get(AtomStore.class).storeAtom(preparedAtom);

		ECKeyPair other = new ECKeyPair();

		// Mint some RADIX tokens
		TransferrableTokensParticle mintParticle = createTransfer(tokenDefinition.getRRI(), UInt256.TWO.pow(5), universeAddress);
		UnallocatedTokensParticle leftOver = new UnallocatedTokensParticle(
			UInt256.MAX_VALUE.subtract(mintParticle.getAmount()),
			UInt256.ONE,
			tokenDefinition.getRRI(),
			ImmutableMap.of(
				TokenTransition.MINT, TokenPermission.ALL,
				TokenTransition.BURN, TokenPermission.NONE
			)
		);

		TransferrableTokensParticle toOther = createTransfer(tokenDefinition.getRRI(), UInt256.ONE, RadixAddress.from(universe, other.getPublicKey()));
		TransferrableTokensParticle toSelf = createTransfer(tokenDefinition.getRRI(), mintParticle.getAmount().subtract(toOther.getAmount()), universeAddress);

		final Atom transferAtom = new Atom(Time.currentTimestamp());
		transferAtom.addParticleGroupWith(
			unallocatedTokensParticle, Spin.DOWN,
			mintParticle, Spin.UP,
			leftOver, Spin.UP
		);
		transferAtom.addParticleGroupWith(
			mintParticle, Spin.DOWN,
			toOther, Spin.UP,
			toSelf, Spin.UP
		);

		transferAtom.sign(identity);
		addTemporalVertex(transferAtom); // Can't store atom without vertex from this node
		CMAtom cmAtom1 = Modules.get(ValidationHandler.class).validate(transferAtom);
		Modules.get(ValidationHandler.class).stateCheck(cmAtom1);
		PreparedAtom preparedAtom1 = new PreparedAtom(cmAtom1);
		Modules.get(AtomStore.class).storeAtom(preparedAtom1);

		final Atom multiSigAtom = new Atom(Time.currentTimestamp());
		multiSigAtom.addParticleGroupWith(
			toSelf, Spin.DOWN,
			toOther, Spin.DOWN,
			createTransfer(tokenDefinition.getRRI(), toSelf.getAmount().add(toOther.getAmount()), RadixAddress.from(universe, new ECKeyPair().getPublicKey())), Spin.UP
		);

		multiSigAtom.sign(Arrays.asList(identity, other));
		addTemporalVertex(multiSigAtom); // Can't store atom without vertex from this node
		CMAtom cmAtom2 = Modules.get(ValidationHandler.class).validate(multiSigAtom);
		Modules.get(ValidationHandler.class).stateCheck(cmAtom2);
		PreparedAtom preparedAtom2 = new PreparedAtom(cmAtom2);
		Modules.get(AtomStore.class).storeAtom(preparedAtom2);
	}
}