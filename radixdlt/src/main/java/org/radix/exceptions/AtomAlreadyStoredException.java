package org.radix.exceptions;

import com.radixdlt.atoms.Atom;

public class AtomAlreadyStoredException extends IllegalStateException {
	private final Atom atom;

	public AtomAlreadyStoredException(Atom atom) {
		super("Atom is already stored: " + atom.getAID());

		this.atom = atom;
	}

	public Atom getAtom() {
		return atom;
	}
}