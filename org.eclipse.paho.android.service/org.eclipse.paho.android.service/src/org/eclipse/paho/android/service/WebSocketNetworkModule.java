package org.eclipse.paho.android.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.internal.NetworkModule;

import android.util.Log;
import de.tavendo.autobahn.WebSocketConnection;
import de.tavendo.autobahn.WebSocketConnectionHandler;
import de.tavendo.autobahn.WebSocketException;
import de.tavendo.autobahn.WebSocketOptions;

public class WebSocketNetworkModule extends WebSocketConnectionHandler implements NetworkModule {

	private final String TAG = "WebSocketNetworkModule";
	/**
	 * WebSocket URI
	 */
	private final URI uri;

	/**
	 * Sub-Protocol
	 */
	private final String subProtocol;

	private Object syncObject = new Object();

	/**
	 * A stream for outgonig data
	 */
	private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream() {
		@Override
		public void flush() throws IOException {
			final ByteBuffer byteBuffer;
			synchronized (this) {
				byteBuffer = ByteBuffer.wrap(toByteArray());
				reset();
			}
			// Asynchronous call
			webSocketClient.sendBinaryMessage(byteBuffer.array());
		}
	};

	/**
	 * A pair of streams for incoming data
	 */
	private final PipedOutputStream receiverStream = new PipedOutputStream();
	private final PipedInputStream inputStream;

	private WebSocketConnection webSocketClient;
	private int conTimeout;
	
	private boolean doConnect = true;

	/**
	 * Constructs a new WebSocketNetworkModule using the specified URI.
	 * 
	 * @param uri
	 * @param subProtocol
	 * @param resourceContext
	 */
	public WebSocketNetworkModule(URI uri, String subProtocol,
			String resourceContext) {

		this.uri = uri;
		this.subProtocol = subProtocol;
		try {
			this.inputStream = new PipedInputStream(receiverStream);
		} catch (IOException unexpected) {
			throw new IllegalStateException(unexpected);
		}
	}

	@Override
	public InputStream getInputStream() throws IOException {
		return inputStream;
	}

	@Override
	public OutputStream getOutputStream() throws IOException {
		return outputStream;
	}

	@Override
	public void start() throws IOException, MqttException {
		Log.d(TAG, "start on the mqtt connection called");
		webSocketClient = new WebSocketConnection();
		WebSocketOptions options = new WebSocketOptions();
		options.setSocketConnectTimeout(this.conTimeout * 1000);
		options.setSocketReceiveTimeout(this.conTimeout * 1000);
		options.setValidateIncomingUtf8(true);
		try {
			if(doConnect){
				webSocketClient.connect(this.uri.toString(), new String[] {subProtocol} , this, options, null);
			}
			else{
				webSocketClient.reconnect();
			}
			synchronized (syncObject) {
				try {
					syncObject.wait(this.conTimeout * 1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					throw new MqttException(
							MqttException.REASON_CODE_SERVER_CONNECT_ERROR, e);
				}
			}


			Log.d(TAG, "web scoket connection opened");
		} catch (WebSocketException e) {
			throw new MqttException(
					MqttException.REASON_CODE_SERVER_CONNECT_ERROR, e);
		}
	}

	@Override
	public void stop() throws IOException {
		Log.d(TAG, "Stop on the mqtt connection called");
		this.receiverStream.close();
		this.outputStream.close();
		this.inputStream.close();
		if(webSocketClient.isConnected())
			webSocketClient.disconnect();
		
	}


	/**
	 * Fired when the WebSockets connection has been established.
	 * After this happened, messages may be sent.
	 */
	@Override
	public void onOpen() {
		Log.d(TAG, "Web socket connection opened");
		doConnect = false;
		
		super.onOpen();
		synchronized (syncObject) {
			syncObject.notify();
	    }
	}

	/**
	 * Fired when the WebSockets connection has deceased (or could
	 * not established in the first place).
	 *
	 * @param code       Close code.
	 * @param reason     Close reason (human-readable).
	 */
	@Override
	public void onClose(int code, String reason) {
		Log.d(TAG, "Web socket connection closed: " + code + " with reason :" + reason);
		super.onClose(code, reason);
	}

	/**
	 * Fired when a text message has been received (and text
	 * messages are not set to be received raw).
	 *
	 * @param payload    Text message payload or null (empty payload).
	 */
	@Override
	public void onTextMessage(String payload) {
		this.onBinaryMessage(payload.getBytes());
	}

	/**
	 * Fired when a text message has been received (and text
	 * messages are set to be received raw).
	 *
	 * @param payload    Text message payload as raw UTF-8 or null (empty payload).
	 */
	@Override
	public void onRawTextMessage(byte[] payload) {
		this.onBinaryMessage(payload);
	}

	/**
	 * Fired when a binary message has been received.
	 *
	 * @param payload    Binary message payload or null (empty payload).
	 */
	@Override
	public void onBinaryMessage(byte[] payload) {
		try {
			Log.d(TAG, "Received msg on web socket:" + new String(payload));
			this.receiverStream.write(payload);
			this.receiverStream.flush();
		} catch (IOException e) {
			e.printStackTrace();
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Set the maximum time in seconds to wait for a socket to be established
	 * 
	 * @param timeout
	 *            in seconds
	 */
	public void setConnectTimeout(int timeout) {
		this.conTimeout = timeout;
	}

}
