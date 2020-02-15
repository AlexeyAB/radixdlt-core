/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.radixdlt.consensus.DumbMemPool;
import com.radixdlt.consensus.DumbNetwork;
import com.radixdlt.consensus.DumbPacemaker;
import com.radixdlt.consensus.MemPool;
import com.radixdlt.consensus.NetworkRx;
import com.radixdlt.consensus.NetworkSender;
import com.radixdlt.consensus.Pacemaker;
import com.radixdlt.consensus.PacemakerRx;
import com.radixdlt.consensus.tempo.Scheduler;
import com.radixdlt.consensus.tempo.SingleThreadedScheduler;

public class CerberusModule extends AbstractModule {
	@Override
	protected void configure() {
		// dependencies
		bind(Scheduler.class).toProvider(SingleThreadedScheduler::new);
		bind(MemPool.class).to(DumbMemPool.class).in(Scopes.SINGLETON);

		bind(DumbPacemaker.class).in(Scopes.SINGLETON);
		bind(PacemakerRx.class).to(DumbPacemaker.class);
		bind(Pacemaker.class).to(DumbPacemaker.class);

		bind(DumbNetwork.class).in(Scopes.SINGLETON);
		bind(NetworkRx.class).to(DumbNetwork.class);
		bind(NetworkSender.class).to(DumbNetwork.class);
	}
}