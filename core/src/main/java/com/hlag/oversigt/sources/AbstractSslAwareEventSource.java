package com.hlag.oversigt.sources;

import com.hlag.oversigt.core.event.OversigtEvent;
import com.hlag.oversigt.core.eventsource.Property;
import com.hlag.oversigt.core.eventsource.ScheduledEventSource;

public abstract class AbstractSslAwareEventSource<T extends OversigtEvent> extends ScheduledEventSource<T> {
	private boolean checkSSL = true;

	@Property(name = "Check SSL",
			description = "Check SSL details. If not checked all SSL connections (incl. insecure) will be accepted.")
	public boolean isCheckSSL() {
		return checkSSL;
	}

	public void setCheckSSL(final boolean checkSsl) {
		this.checkSSL = checkSsl;
	}
}
