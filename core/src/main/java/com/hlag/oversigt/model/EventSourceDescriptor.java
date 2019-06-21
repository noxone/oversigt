package com.hlag.oversigt.model;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.Nullable;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.Service;
import com.google.inject.Module;
import com.hlag.oversigt.core.event.OversigtEvent;
import com.hlag.oversigt.core.eventsource.ScheduledEventSource;

public class EventSourceDescriptor implements Comparable<EventSourceDescriptor> {
	@NotNull
	private final EventSourceKey key;

	@NotBlank
	@NotNull
	private final String displayName;

	private final String description;

	@NotNull
	private final Set<@NotNull EventSourceProperty> properties = new TreeSet<>();

	@NotNull
	private final Set<@NotNull EventSourceProperty> dataItems = new TreeSet<>();

	@JsonIgnore
	private final Set<@NotBlank String> dataItemsToHide = new TreeSet<>();

	@NotBlank
	@JsonProperty(access = Access.READ_ONLY, required = false)
	private final String view;

	private final Optional<Class<? extends Service>> serviceClass;

	private final Optional<Class<? extends OversigtEvent>> eventClass;

	@JsonIgnore
	private final Optional<Class<? extends Module>> moduleClass;

	private boolean standAlone = false;

	EventSourceDescriptor(@NotNull final EventSourceKey key,
			@NotBlank final String displayName,
			final String description,
			@NotBlank final String view,
			final boolean standAlone) {
		this(key, displayName, description, view, null, null, null);
		this.standAlone = standAlone;
	}

	EventSourceDescriptor(@NotNull final EventSourceKey key,
			@NotBlank final String displayName,
			final String description,
			@NotBlank final String view,
			@Nullable final Class<? extends Service> serviceClass,
			@Nullable final Class<? extends OversigtEvent> eventClass,
			@Nullable final Class<? extends Module> moduleClass) {
		this.key = key;
		this.displayName = displayName;
		this.description = description != null && description.trim().length() > 1 ? description : null;
		this.view = view;
		this.serviceClass = Optional.ofNullable(serviceClass);
		this.eventClass = Optional.ofNullable(eventClass);
		this.moduleClass = Optional.ofNullable(moduleClass);
	}

	public EventSourceKey getKey() {
		return key;
	}

	public String getView() {
		return view;
	}

	@Nullable
	public Class<? extends Service> getServiceClass() {
		return serviceClass.orElse(null);
	}

	@JsonProperty(access = Access.READ_ONLY, required = false)
	@Nullable
	public String getServiceClassName() {
		return serviceClass.map(Class::getName).orElse(null);
	}

	@JsonProperty(access = Access.READ_ONLY, required = false)
	@Nullable
	public String getEventClassName() {
		return eventClass.map(Class::getName).orElse(null);
	}

	@JsonIgnore
	public boolean isScheduledService() {
		return serviceClass.map(ScheduledEventSource.class::isAssignableFrom).orElse(false);
	}

	public boolean isDeprecated() {
		return serviceClass.map(clazz -> clazz.isAnnotationPresent(Deprecated.class)).orElse(false);
	}

	@Nullable
	public Class<? extends OversigtEvent> getEventClass() {
		return eventClass.orElse(null);
	}

	@Nullable
	public Class<? extends Module> getModuleClass() {
		return moduleClass.orElse(null);
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getDescription() {
		return description;
	}

	public Set<EventSourceProperty> getProperties() {
		return Collections.unmodifiableSet(properties);
	}

	public Set<EventSourceProperty> getDataItems() {
		return Collections.unmodifiableSet(dataItems);
	}

	public Set<String> getDataItemsToHide() {
		return Collections.unmodifiableSet(dataItemsToHide);
	}

	void addProperty(@NotNull final EventSourceProperty property) {
		properties.add(Objects.requireNonNull(property, "Property must not be null"));
	}

	void addDataItem(@NotNull final EventSourceProperty dataItem) {
		dataItems.add(Objects.requireNonNull(dataItem, "Data item must not be null"));
	}

	void addDataItemToHide(@NotBlank final String itemName) {
		dataItemsToHide
				.add(Objects.requireNonNull(Strings.emptyToNull(itemName), "Item name must not be null or empty"));
	}

	EventSourceProperty getProperty(@NotBlank final String name) {
		return properties.stream().filter(p -> name.equals(p.getName())).findAny().get();
	}

	EventSourceProperty getDataItem(@NotBlank final String name) {
		return dataItems.stream().filter(p -> name.equals(p.getName())).findAny().get();
	}

	boolean isStandAlone() {
		return standAlone;
	}

	@Override
	public int compareTo(final EventSourceDescriptor that) {
		return getDisplayName().compareToIgnoreCase(that.getDisplayName());
	}

	@Override
	public String toString() {
		return key + " (" + displayName + " / " + view + ")";
	}
}
