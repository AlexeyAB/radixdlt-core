package org.radix.api.jsonrpc;

import io.undertow.websockets.core.BufferedTextMessage;
import java.util.function.BiConsumer;
import org.everit.json.schema.Schema;
import org.json.JSONException;
import org.json.JSONObject;
import org.radix.api.AtomQuery;
import org.radix.api.services.AtomsService;
import com.radixdlt.atomos.RadixAddress;
import org.radix.logging.Logger;
import org.radix.logging.Logging;

import org.radix.modules.Modules;
import com.radixdlt.serialization.Serialization;

/**
 * A Stateful JSON RPC 2.0 Server and Client for duplex communication
 */
public class RadixJsonRpcPeer {
	private static final Logger LOGGER = Logging.getLogger("api");

	private final BiConsumer<RadixJsonRpcPeer, String> callback;

	/**
	 * Epic for managing atom submission requests
	 */
	private final SubmitAtomAndSubscribeEpic submitAtomAndSubscribeEpic;

	/**
	 * Epic for managing atom subscriptions
	 */
	private final AtomsSubscribeEpic atomsSubscribeEpic;

	private final AtomStatusEpic atomStatusEpic;

	private final RadixJsonRpcServer server;

	public RadixJsonRpcPeer(
		RadixJsonRpcServer server,
		AtomsService atomsService,
		Schema atomSchema,
		BiConsumer<RadixJsonRpcPeer, String> callback
	) {
		this.server = server;
		this.callback = callback;

		this.atomStatusEpic = new AtomStatusEpic(atomsService, json -> callback.accept(this, json.toString()));
		this.submitAtomAndSubscribeEpic = new SubmitAtomAndSubscribeEpic(atomsService, atomSchema, Modules.get(Serialization.class),
			atomJson -> callback.accept(this, atomJson.toString()));
		this.atomsSubscribeEpic = new AtomsSubscribeEpic(atomsService, Modules.get(Serialization.class),
			queryJson -> new AtomQuery(RadixAddress.from(queryJson.getString("address"))), atomJson -> callback.accept(this, atomJson.toString()));

		callback.accept(this, JsonRpcUtil.notification("Radix.welcome", new JSONObject().put("message", "Hello!")).toString());
	}

	/**
	 * Handle the text message and send a corresponding response
	 *
	 * @param message The message
	 */
	// TODO: multithreading issues - should get resolved once we use a better async framework
	public void onMessage(BufferedTextMessage message) {

		final String msg = message.getData();

		final JSONObject jsonRpcRequest;
		try {
			jsonRpcRequest = new JSONObject(msg);
		} catch (JSONException e) {
			callback.accept(this, JsonRpcUtil.errorResponse(null, -32000, e.getMessage()).toString());
			return;
		}

		if (!jsonRpcRequest.has("id")) {
			callback.accept(this, JsonRpcUtil.errorResponse(null, -32000, "JSON-RPC: No id").toString());
			return;
		}

		if (!jsonRpcRequest.has("method")) {
			callback.accept(this, JsonRpcUtil.errorResponse(null, -32000, "JSON-RPC: No method").toString());
			return;
		}

		if (!jsonRpcRequest.has("params")) {
			callback.accept(this, JsonRpcUtil.errorResponse(null, -32000, "JSON-RPC: No params").toString());
			return;
		}

		final String jsonRpcMethod = jsonRpcRequest.getString("method");

		switch (jsonRpcMethod) {
			case "Atoms.subscribe":
			case "Atoms.cancel":
				if (!jsonRpcRequest.getJSONObject("params").has("subscriberId")) {
					callback.accept(this, JsonRpcUtil.errorResponse(null, -32000, "JSON-RPC: No subscriberId").toString());
					return;
				}

				atomsSubscribeEpic.action(jsonRpcRequest);
				break;
			case "Universe.submitAtomAndSubscribe":
				submitAtomAndSubscribeEpic.action(jsonRpcRequest);
				break;
			case "Atoms.getAtomStatusNotifications":
			case "Atoms.closeAtomStatusNotifications":
				if (!jsonRpcRequest.getJSONObject("params").has("subscriberId")) {
					callback.accept(this, JsonRpcUtil.errorResponse(null, -32000, "JSON-RPC: No subscriberId").toString());
					break;
				}
				atomStatusEpic.action(jsonRpcRequest);
				break;
			default:
				callback.accept(this, server.handleChecked(msg));
				break;
		}
	}

	// TODO: need to synchronize this with the whole peer
	public void close() {
		LOGGER.info("Closing peer");

		atomStatusEpic.dispose();
		atomsSubscribeEpic.dispose();
	}
}