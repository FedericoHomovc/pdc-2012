package org.chinux;

import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;

public class EchoWorkerTest {
	Mockery context = new Mockery() {
		{
			this.setImposteriser(ClassImposteriser.INSTANCE);
		}
	};

	@Test
	public void doWorkTest() {
		// final SocketChannel socket = this.context.mock(SocketChannel.class);
		// // final Receiv socket = this.context.mock(SocketChannel.class);
		//
		// final EchoWorker worker = new EchoWorker();
		//
		// final byte[] data = "data".getBytes();
		//
		// // final NIODataEvent event = new NIODataEvent(socket, data, );
		//
		// final NIODataEvent echoEvent = worker.DoWork(event);
		//
		// Assert.assertEquals(new String(event.data), new
		// String(echoEvent.data));
		// Assert.assertEquals(true, echoEvent.canSend());
	}
}
