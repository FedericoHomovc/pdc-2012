package org.chinux.pdc.nio.handlers.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.chinux.pdc.nio.dispatchers.EventDispatcher;
import org.chinux.pdc.nio.events.api.DataEvent;
import org.chinux.pdc.nio.events.impl.ServerDataEvent;
import org.chinux.pdc.nio.handlers.api.NIOServerHandler;
import org.chinux.pdc.nio.receivers.api.DataReceiver;
import org.chinux.pdc.nio.services.util.ChangeRequest;

public class ServerHandler implements NIOServerHandler, DataReceiver<DataEvent> {

	private ByteBuffer readBuffer;
	private Selector selector;

	private List<ChangeRequest> changeRequests = new LinkedList<ChangeRequest>();

	private Map<SocketChannel, ArrayList<ByteBuffer>> pendingData = new HashMap<SocketChannel, ArrayList<ByteBuffer>>();

	private EventDispatcher<DataEvent> dispatcher;

	public ServerHandler(final EventDispatcher<DataEvent> dispatcher) {
		this.dispatcher = dispatcher;
		readBuffer = ByteBuffer.allocate(1024);
	}

	@Override
	public void setSelector(final Selector selector) {
		this.selector = selector;
	}

	@Override
	public void receiveEvent(final DataEvent dataEvent) {

		if (!(dataEvent instanceof ServerDataEvent)) {
			throw new RuntimeException("Must receive a NIOServerDataEvent");
		}

		final ServerDataEvent event = (ServerDataEvent) dataEvent;

		final SocketChannel socket = event.getChannel();
		final byte[] data = event.getData();

		synchronized (changeRequests) {
			// Indicate we want the interest ops set changed
			changeRequests.add(new ChangeRequest(socket,
					ChangeRequest.CHANGEOPS, SelectionKey.OP_WRITE));

			// And queue the data we want written
			synchronized (pendingData) {
				ArrayList<ByteBuffer> queue = pendingData.get(socket);
				if (queue == null) {
					queue = new ArrayList<ByteBuffer>();
					pendingData.put(socket, queue);
				}
				queue.add(ByteBuffer.wrap(data));
			}
		}

		// Finally, wake up our selecting thread so it can make the required
		// changes
		selector.wakeup();
	}

	@Override
	public void closeConnection(final DataEvent dataEvent) {

		if (!(dataEvent instanceof ServerDataEvent)) {
			throw new RuntimeException("Must receive a NIOServerDataEvent");
		}

		final ServerDataEvent event = (ServerDataEvent) dataEvent;

		synchronized (changeRequests) {
			// Indicate we want the interest ops set changed
			changeRequests.add(new ChangeRequest(event.getChannel(),
					ChangeRequest.CHANGEOPS, SelectionKey.OP_WRITE));
		}
	}

	@Override
	public void handleAccept(final SelectionKey key) throws IOException {
		// For an accept to be pending the channel must be a server socket
		// channel.
		final ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key
				.channel();

		// Accept the connection and make it non-blocking
		final SocketChannel socketChannel = serverSocketChannel.accept();
		socketChannel.configureBlocking(false);

		// Register the new SocketChannel with our Selector, indicating
		// we'd like to be notified when there's data waiting to be read
		socketChannel.register(selector, SelectionKey.OP_READ);
	}

	@Override
	public void handleRead(final SelectionKey key) throws IOException {

		final SocketChannel socketChannel = (SocketChannel) key.channel();

		// Clear out our read buffer so it's ready for new data
		readBuffer.clear();

		// No se porqué falla con esto
		final ByteBuffer readBuffer = ByteBuffer.allocate(1024);

		// Attempt to read off the channel
		int numRead;
		try {
			numRead = socketChannel.read(readBuffer);
		} catch (final IOException e) {
			// The remote forcibly closed the connection, cancel
			// the selection key and close the channel.
			key.cancel();
			socketChannel.close();
			return;
		}

		if (numRead == -1) {
			// Remote entity shut the socket down cleanly. Do the
			// same from our end and cancel the channel.
			key.channel().close();
			key.cancel();
			return;
		}
		// Hand the data off to our worker thread
		final byte[] data = (numRead > 0) ? readBuffer.array() : new byte[] {};

		dispatcher.processData(new ServerDataEvent(socketChannel, data, this));
	}

	@Override
	public void handleWrite(final SelectionKey key) throws IOException {
		final SocketChannel socketChannel = (SocketChannel) key.channel();

		synchronized (pendingData) {
			final ArrayList<ByteBuffer> queue = pendingData.get(socketChannel);

			// Write until there's not more data ...
			while (!queue.isEmpty()) {
				final ByteBuffer buf = queue.get(0);
				socketChannel.write(buf);
				if (buf.remaining() > 0) {
					// ... or the socket's buffer fills up
					break;
				}
				queue.remove(0);
			}

			if (queue.isEmpty()) {
				// We wrote away all data, so we're no longer interested
				// in writing on this socket. Switch back to waiting for
				// data.
				key.interestOps(SelectionKey.OP_READ);
			}
		}
	}

	@Override
	public void handlePendingChanges() {
		synchronized (changeRequests) {
			final Iterator<ChangeRequest> changes = changeRequests.iterator();
			while (changes.hasNext()) {
				final ChangeRequest change = changes.next();
				SelectionKey key;
				switch (change.type) {
				case ChangeRequest.CHANGEOPS:
					key = change.socket.keyFor(selector);
					key.interestOps(change.ops);
					break;
				case ChangeRequest.CLOSE:
					key = change.socket.keyFor(selector);
					try {
						change.socket.close();
					} catch (final IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					key.cancel();
					break;
				}
			}
			changeRequests.clear();
		}
	}
}
