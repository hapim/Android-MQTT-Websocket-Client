package org.eclipse.paho.android.service;

import java.net.URI;

import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttClientPersistence;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttPingSender;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;
import org.eclipse.paho.client.mqttv3.TimerPingSender;
import org.eclipse.paho.client.mqttv3.internal.NetworkModule;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;

public class MqttWebSocketAsyncClient extends MqttAsyncClient{

	private final String serverURI;

	/**
	 * Create a dummy URI in order to by-pass MqttConnectOptions.validateURI()
	 * validation.
	 * 
	 * @param original
	 * @return
	 */
	static String createDummyURI(String original) {
		if (!original.startsWith("ws:") && !original.startsWith("wss:")) {
			return original;
		}
		final URI uri = URI.create(original);
		return "tcp://DUMMY-" + uri.getHost() + ":"
				+ (uri.getPort() > 0 ? uri.getPort() : 80);
	}

	static boolean isDummyURI(String uri) {
		return uri.startsWith("tcp://DUMMY-");
	}

	public MqttWebSocketAsyncClient(String serverURI, String clientId,
			MqttClientPersistence persistence, MqttPingSender pingSender)
			throws MqttException {

		super(createDummyURI(serverURI), clientId, persistence, pingSender);
		this.serverURI = serverURI;
	}

	public MqttWebSocketAsyncClient(String serverURI, String clientId,
			MqttClientPersistence persistence) throws MqttException {
		this(serverURI, clientId, persistence, new TimerPingSender());
	}

	public MqttWebSocketAsyncClient(String serverURI, String clientId)
			throws MqttException {
		this(serverURI, clientId, new MqttDefaultFilePersistence());
	}

	/**
	 * Same as super{@link #createNetworkModules(String, MqttConnectOptions)}
	 */
	@Override
	protected NetworkModule[] createNetworkModules(String address,
			MqttConnectOptions options) throws MqttException,
			MqttSecurityException {
		
		NetworkModule[] networkModules = null;
		String[] serverURIs = options.getServerURIs();
		String[] array = null;
		if (serverURIs == null) {
			array = new String[] { address };
		} else if (serverURIs.length == 0) {
			array = new String[] { address };
		} else {
			array = serverURIs;
		}

		networkModules = new NetworkModule[array.length];
		for (int i = 0; i < array.length; i++) {
			networkModules[i] = createNetworkModule(array[i], options);
		}
		
		return networkModules;
	}

	/**
	 * Factory method to create the correct network module, based on the
	 * supplied address URI.
	 *
	 * @param address
	 *            the URI for the server.
	 * @param options
	 *            MQTT connect options
	 * @return a network module appropriate to the specified address.
	 */
	protected NetworkModule createNetworkModule(String input,
			MqttConnectOptions options) throws MqttException,
			MqttSecurityException {
		final String address = isDummyURI(input) ? this.serverURI : input;
		if (!address.startsWith("ws:") && !address.startsWith("wss:")) {
			return super.createNetworkModules(address, options)[0];
		}

		final String subProtocol;
		if (options.getMqttVersion() == MqttConnectOptions.MQTT_VERSION_3_1) {
			// http://wiki.eclipse.org/Paho/Paho_Websockets#Ensuring_implementations_can_inter-operate
			subProtocol = "mqttv3.1";
		} else {
			// http://docs.oasis-open.org/mqtt/mqtt/v3.1.1/cs01/mqtt-v3.1.1-cs01.html#_Toc388534418
			subProtocol = "mqtt";
		}

		final WebSocketNetworkModule netModule = new WebSocketNetworkModule(
				URI.create(address), subProtocol, getClientId());
		netModule.setConnectTimeout(options.getConnectionTimeout());
		return netModule;
	}
}
