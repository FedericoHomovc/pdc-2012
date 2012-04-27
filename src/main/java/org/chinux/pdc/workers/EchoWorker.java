package org.chinux.pdc.workers;

import org.chinux.pdc.nio.events.api.DataEvent;

public class EchoWorker implements Worker<DataEvent> {

	@Override
	public DataEvent DoWork(final DataEvent dataEvent) {
		dataEvent.setCanSend(true);
		return dataEvent; // An echo is just 'send the same' isn't it ?
	}
}
