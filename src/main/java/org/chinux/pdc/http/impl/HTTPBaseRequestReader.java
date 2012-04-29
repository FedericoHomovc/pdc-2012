package org.chinux.pdc.http.impl;

import org.chinux.pdc.http.api.HTTPReader;
import org.chinux.pdc.http.api.HTTPRequestHeader;

public class HTTPBaseRequestReader implements HTTPReader {

	private static HTTPBaseRequestReader singleton;
	private HTTPRequestHeader requestheader;
	private boolean finished;
	private HTTPPostRequestReader postereader;

	public HTTPBaseRequestReader(final HTTPRequestHeader requestheader) {
		this.requestheader = requestheader;
		this.finished = false;
		this.postereader = null;
	}

	@Override
	public byte[] processData(final byte[] data) {
		final String method = this.requestheader.getMethod();

		if (method.equals("HEAD") || method.equals("GET")) {
			this.finished = true;
			return data;
		} else if (method.equals("POST")) {
			this.postereader = this.getPostReader();
			if (this.postereader.isFinished()) {
				this.finished = true;
			}
			return this.postereader.processData(data);
		} else {
			throw new RuntimeException();
		}
	}

	private HTTPPostRequestReader getPostReader() {
		if (this.postereader == null) {
			this.postereader = new HTTPPostRequestReader(this.requestheader);
		}
		return this.postereader;
	}

	@Override
	public boolean isFinished() {
		return this.finished;
	}

}
