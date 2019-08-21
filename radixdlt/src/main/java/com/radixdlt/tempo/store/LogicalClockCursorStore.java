package com.radixdlt.tempo.store;

import com.radixdlt.common.EUID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.tempo.TempoException;
import com.radixdlt.tempo.LogicalClockCursor;
import com.radixdlt.utils.Longs;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseNotFoundException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;
import org.radix.database.DatabaseEnvironment;
import org.radix.logging.Logger;
import org.radix.logging.Logging;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class LogicalClockCursorStore implements Store {
	private static final String ITERATIVE_CURSORS_DB_NAME = "tempo2.sync.iterative.cursors";
	private static final Logger logger = Logging.getLogger("CursorStore");

	private final Supplier<DatabaseEnvironment> dbEnv;
	private Database cursors;

	public LogicalClockCursorStore(Supplier<DatabaseEnvironment> dbEnv) {
		this.dbEnv = Objects.requireNonNull(dbEnv, "dbEnv is required");
	}

	private void fail(String message) {
		logger.error(message);
		throw new TempoException(message);
	}

	private void fail(String message, Exception cause) {
		logger.error(message, cause);
		throw new TempoException(message, cause);
	}

	@Override
	public void open() {
		DatabaseConfig primaryConfig = new DatabaseConfig();
		primaryConfig.setAllowCreate(true);
		primaryConfig.setTransactional(true);

		try {
			Environment dbEnv = this.dbEnv.get().getEnvironment();
			this.cursors = dbEnv.openDatabase(null, ITERATIVE_CURSORS_DB_NAME, primaryConfig);
		} catch (Exception e) {
			throw new TempoException("Error while opening database", e);
		}

		if (System.getProperty("db.check_integrity", "1").equals("1")) {
			// TODO implement intergrity check
		}
	}

	@Override
	public void reset() {
		Transaction transaction = null;
		try {
			dbEnv.get().lock();

			Environment env = this.dbEnv.get().getEnvironment();
			transaction = env.beginTransaction(null, new TransactionConfig().setReadUncommitted(true));
			env.truncateDatabase(transaction, ITERATIVE_CURSORS_DB_NAME, false);
			transaction.commit();
		} catch (DatabaseNotFoundException e) {
			if (transaction != null) {
				transaction.abort();
			}
			logger.warn("Error while resetting database, database not found", e);
		} catch (Exception e) {
			if (transaction != null) {
				transaction.abort();
			}
			throw new TempoException("Error while resetting databases", e);
		} finally {
			dbEnv.get().unlock();
		}
	}

	@Override
	public void close() {
		if (this.cursors != null) {
			this.cursors.close();
		}
	}

	public void put(EUID nid, CursorType cursorType, long cursor) {
		Transaction transaction = dbEnv.get().getEnvironment().beginTransaction(null, null);
		try {
			DatabaseEntry key = new DatabaseEntry(toPKey(nid, cursorType));
			DatabaseEntry value = new DatabaseEntry(Longs.toByteArray(cursor));

			OperationStatus status = this.cursors.put(transaction, key, value);
			if (status != OperationStatus.SUCCESS) {
				fail("Database returned status " + status + " for put operation");
			}

			transaction.commit();
		} catch (Exception e) {
			transaction.abort();
			fail("Error while storing cursor for '" + nid + "'", e);
		}
	}

	public Optional<Long> get(EUID nid, CursorType cursorType) {
		try {
			DatabaseEntry key = new DatabaseEntry(toPKey(nid, cursorType));
			DatabaseEntry value = new DatabaseEntry();

			OperationStatus status = this.cursors.get(null, key, value, LockMode.DEFAULT);
			if (status == OperationStatus.NOTFOUND) {
				return Optional.empty();
			} else if (status != OperationStatus.SUCCESS) {
				fail("Database returned status " + status + " for get operation");
			} else {
				long cursor = Longs.fromByteArray(value.getData());
				return Optional.of(cursor);
			}
		} catch (Exception e) {
			fail("Error while getting cursor for '" + nid + "'", e);
		}

		return Optional.empty();
	}

	private byte[] toPKey(EUID nid, CursorType type) {
		byte[] pKey = new byte[EUID.BYTES + 1];
		System.arraycopy(nid.toByteArray(), 0, pKey, 1, EUID.BYTES);
		pKey[0] = type.prefix;
		return pKey;
	}

	public enum CursorType {
		DISCOVERY((byte) 0),
		LAG((byte) 1);

		private final byte prefix;

		CursorType(byte prefix) {
			this.prefix = prefix;
		}
	}
}