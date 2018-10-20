package com.hlag.oversigt.model;

import static com.hlag.oversigt.util.Utils.not;
import static com.hlag.oversigt.util.Utils.toList;
import static java.util.stream.Collectors.*;
import static java.util.stream.Collectors.toList;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;

import com.google.common.base.CaseFormat;
import com.google.common.base.Strings;
import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.Service.State;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.hlag.oversigt.core.OversigtEvent;
import com.hlag.oversigt.core.eventsource.EventSource;
import com.hlag.oversigt.core.eventsource.ScheduledEventSource;
import com.hlag.oversigt.core.eventsource.EventSource.NOP;
import com.hlag.oversigt.core.eventsource.annotation.EventId;
import com.hlag.oversigt.core.eventsource.annotation.Property;
import com.hlag.oversigt.properties.JsonBasedData;
import com.hlag.oversigt.properties.SerializableProperty;
import com.hlag.oversigt.properties.SerializablePropertyController;
import com.hlag.oversigt.security.Principal;
import com.hlag.oversigt.sources.data.JsonHint;
import com.hlag.oversigt.sources.event.ReloadEvent;
import com.hlag.oversigt.storage.Storage;
import com.hlag.oversigt.util.FileUtils;
import com.hlag.oversigt.util.JsonUtils;
import com.hlag.oversigt.util.SimpleReadWriteLock;
import com.hlag.oversigt.util.SneakyException;
import com.hlag.oversigt.util.StringUtils;
import com.hlag.oversigt.util.TypeUtils;
import com.hlag.oversigt.util.Utils;

@Singleton
public class DashboardController {
	private static final Logger LOGGER = LoggerFactory.getLogger(DashboardController.class);

	private static final Package CORE_EVENT_SOURCE_PACKAGE = ScheduledEventSource.class.getPackage();

	private static final Collection<String> RESERVED_DATA_BINDINGS = Arrays.asList("title");

	private static DashboardController instance = null;
	{
		instance = this;
		EventSourceKey.eventSourceRenamer.set(this::updateEventSourceClasses);
	}

	static DashboardController getInstance() {
		return instance;
	}

	@Inject
	private Storage storage;
	@Inject
	private JsonUtils json;
	@Inject
	private Injector injector;
	@Inject
	private EventSourceNameGenerator nameGenerator;
	@Inject
	private EventBus eventBus;
	@Inject
	private SerializablePropertyController spController;

	// Dashboards
	private final Map<String, Dashboard> dashboards = Collections.synchronizedMap(new HashMap<>());

	// Event Sources
	// this field will be written once and then only read several times -> no synchronization needed
	private final Collection<EventSourceDescriptor> eventSourceDescriptors = new HashSet<>();
	// access to this map is synchronized through the lock object
	private final SimpleReadWriteLock eventSourceInstances_lock = new SimpleReadWriteLock();
	private volatile Map<EventSourceInstance, Service> eventSourceInstances_internal = new HashMap<>();

	public Collection<String> getDashboardIds() {
		return dashboards.keySet();
	}

	public Dashboard getDashboard(String id) {
		return dashboards.get(id);
	}

	public void loadDashboards() {
		dashboards.clear();
		dashboards.putAll(storage//
				.loadDashboards()
				.stream()
				.peek(db -> LOGGER.info("Loading dashboard: {} ({})", db.getId(), db.getTitle()))
				.peek(db -> db.getModifiableWidgets()//
						.addAll(storage.loadWidgetDatas(db, this::getEventSourceInstance)))
				.collect(toMap(Dashboard::getId, Function.identity())));
	}

	Dashboard getDashboard(Widget widget) {
		return dashboards.values()
				.stream()
				.filter(d -> d.getWidgets()//
						.stream()
						.mapToInt(Widget::getId)
						.anyMatch(i -> i == widget.getId()))
				.findAny()
				.get();
	}

	/**
	 * Creates a new dashboard instance, persists it in the storage and makes it available for other
	 * users.
	 * 
	 * @param id the ID of the dashboard to be created
	 * @param owner the owner of the dashboard to be created
	 * @param enabled <code>true</code> if the dashboard should be enabled by default
	 * @return the newly created dashboard object or <code>null</code> if a dashboard with the given
	 *         ID already exists.
	 */
	public Dashboard createDashboard(String id, Principal owner, boolean enabled) {
		if (!dashboards.containsKey(id)) {
			Dashboard dashboard = new Dashboard(id, owner.getUsername(), enabled);
			storage.persistDashboard(dashboard);
			dashboards.put(id, dashboard);
			return dashboard;
		} else {
			return null;
		}
	}

	public boolean updateDashboard(Dashboard dashboard) {
		Dashboard originalDashboard = getDashboard(dashboard.getId());
		if (originalDashboard == null) {
			return false;
		}
		copyProperties(dashboard, originalDashboard);
		storage.persistDashboard(originalDashboard);
		return true;
	}

	public boolean deleteDashboard(String id) {
		Dashboard dashboard = getDashboard(id);
		if (dashboard != null) {
			return deleteDashboard(dashboard);
		} else {
			return false;
		}
	}

	public boolean deleteDashboard(Dashboard dashboard) {
		dashboards.remove(dashboard.getId());
		return storage.deleteDashboard(dashboard);
	}

	public void reloadDashboards(Dashboard... dashboards) {
		if (dashboards.length > 0) {
			eventBus.post(new ReloadEvent(toList(dashboards, Dashboard::getId)));
		}
	}

	public Collection<Dashboard> getDashboards(EventSourceInstance instance) {
		return dashboards.values()
				.stream()
				.filter(d -> d.getWidgets()//
						.stream()
						.anyMatch(w -> w.getEventSourceInstance() == instance))
				.collect(toList());
	}

	private void reloadDashboardsWithEventSourceInstance(EventSourceInstance instance) {
		reloadDashboards(dashboards.values()//
				.stream()
				.filter(d -> d.getWidgets()//
						.stream()
						.map(Widget::getEventSourceInstance)
						.anyMatch(i -> i == instance))
				.toArray(Dashboard[]::new));
	}

	public Widget createWidgetForDashboard(Dashboard dashboard, String eventSourceInstanceId) {
		Widget widget = new Widget(getEventSourceInstance(eventSourceInstanceId));
		storage.createWidget(dashboard, widget);
		dashboard.getModifiableWidgets().add(widget);
		reloadDashboards(dashboard);
		return widget;
	}

	public void updateWidget(Widget widget) {
		Dashboard dashboard = getDashboard(widget);
		if (!dashboard.getWidgets().contains(widget)) {
			// adapt properties to original widget
			Widget originalWidget = dashboard.getWidget(widget.getId());
			if (originalWidget.getEventSourceInstance() != widget.getEventSourceInstance()) {
				throw new RuntimeException("The widgets have different EventSourceInstances");
			}
			copyProperties(widget, originalWidget, "eventSourceInstance");
			// update data
			for (EventSourceProperty property : widget.getEventSourceInstance().getDescriptor().getDataItems()) {
				String value = widget.getWidgetData(property);
				if (value != null) {
					originalWidget.setWidgetData(property, value);
				} else {
					originalWidget.removeWidgetData(property);
				}
			}
		}
		storage.updateWidget(widget);
		reloadDashboards(dashboard);
	}

	public void deleteWidget(Widget widget) {
		Dashboard dashboard = getDashboard(widget);
		widget = dashboard.getWidget(widget.getId());
		dashboard.getModifiableWidgets().remove(widget);
		storage.deleteWidget(widget);
		reloadDashboards(dashboard);
	}

	private static Collection<Path> findAddonJarFiles(Path folder) {
		try (Stream<Path> files = Files.walk(folder)) {
			return files//
					.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".jar"))
					.collect(toSet());
		} catch (IOException e) {
			throw new RuntimeException("Unable to scan path: " + folder.toAbsolutePath().toString(), e);
		}
	}

	public void loadEventSourceDescriptors(Collection<Package> packagesToScan,
			Collection<Path> addonFolders,
			Collection<String> widgetsPaths) {
		// load event sources from classes
		LOGGER.debug("Scanning classpath for EventSources...");
		List<EventSourceDescriptor> descriptorsFromClasses_ = //
				packagesToScan.stream()//
						.flatMap(p -> TypeUtils.findClasses(p, Service.class, EventSource.class))
						.map(this::loadEventSourceFromClass)
						.collect(toList());
		LOGGER.info("Loaded {} EventSources from package {}",
				descriptorsFromClasses_.size(),
				packagesToScan.stream().map(Package::getName).collect(joining(", ")));

		LOGGER.debug("Scanning addon folders for EventSources...");
		URL[] jarFileUrls = addonFolders.stream()
				.map(DashboardController::findAddonJarFiles)
				.flatMap(Collection::stream)
				.map(Path::toUri)
				.map(SneakyException.sneaky(URI::toURL))
				.collect(toSet())
				.toArray(new URL[0]);
		List<String> classNamesToLoad;
		try {
			classNamesToLoad = TypeUtils.listClassesInJarFiles(jarFileUrls);
		} catch (IOException e1) {
			throw new RuntimeException("Unable to scan JAR files", e1);
		}
		ClassLoader addonClassLoader = URLClassLoader.newInstance(jarFileUrls, ClassLoader.getSystemClassLoader());
		List<EventSourceDescriptor> descriptorsFromAddons_ = TypeUtils
				.findClasses(addonClassLoader, classNamesToLoad, Service.class, EventSource.class)
				.map(this::loadEventSourceFromClass)
				.collect(toList());
		LOGGER.info("Loaded {} EventSources from addon folders {}",
				descriptorsFromAddons_.size(),
				addonFolders.stream().map(Path::toAbsolutePath).map(Object::toString).collect(joining(", ")));

		final List<EventSourceDescriptor> descriptorsJavaBased = new ArrayList<>();
		descriptorsJavaBased.addAll(descriptorsFromClasses_);
		descriptorsJavaBased.addAll(descriptorsFromAddons_);

		// load event sources without class
		LOGGER.debug("Scanning file system for EventSources...");
		List<EventSourceDescriptor> descriptorsFromFileSystem_ = loadMultipleEventSourceFromResources(widgetsPaths);
		LOGGER.info("Loaded {} EventSources from Resources", descriptorsFromFileSystem_.size());

		// add properties from views into class' event sources
		List<EventSourceDescriptor> standAloneDescriptorsFromFileSystem = descriptorsFromFileSystem_.stream()
				.filter(EventSourceDescriptor::isStandAlone)
				.collect(toList());

		for (EventSourceDescriptor dfc : descriptorsJavaBased) {
			EventSourceDescriptor descriptorForView = descriptorsFromFileSystem_.stream()
					.filter(d -> d.getView().equals(dfc.getView()))
					.findAny()
					.get();
			descriptorForView.getDataItems()
					.stream()
					.filter(di -> !dfc.getDataItemsToHide().contains(di.getName()))
					.forEach(dfc::addDataItem);
		}

		// Done
		eventSourceDescriptors.clear();
		eventSourceDescriptors.addAll(descriptorsJavaBased);
		eventSourceDescriptors.addAll(standAloneDescriptorsFromFileSystem);
	}

	public Collection<EventSourceKey> getEventSourceKeys() {
		return eventSourceDescriptors.stream().map(EventSourceDescriptor::getKey).collect(toList());
	}

	@SuppressWarnings("unchecked")
	private <S extends Service> S getService(EventSourceInstance instance) {
		return eventSourceInstances_lock.read(() -> (S) eventSourceInstances_internal.get(instance));
	}

	private void setService(EventSourceInstance instance, Service service) {
		eventSourceInstances_lock.write(() -> eventSourceInstances_internal.put(instance, service));
	}

	private void unsetService(EventSourceInstance instance) {
		eventSourceInstances_lock.write(() -> eventSourceInstances_internal.put(instance, null));
	}

	private void removeService(EventSourceInstance instance) {
		eventSourceInstances_lock.write(() -> eventSourceInstances_internal.remove(instance));
	}

	public Collection<EventSourceInstance> getEventSourceInstances() {
		return eventSourceInstances_lock.read(() -> new HashSet<>(eventSourceInstances_internal.keySet()));
	}

	public EventSourceInstance getEventSourceInstance(String id) {
		return eventSourceInstances_lock.read(() -> eventSourceInstances_internal.keySet()
				.stream()
				.filter(i -> id.equals(i.getId()))
				.findAny()
				.get());
	}

	private EventSourceDescriptor getEventSourceDescriptor(String className, String viewname) {
		if (className != null) {
			return getEventSourceDescriptor(EventSourceKey.getKey(EventSourceKey.PREFIX_CLASS + className));
		} else {
			return getEventSourceDescriptor(EventSourceKey.getKey(EventSourceKey.PREFIX_WIDGET + viewname));
		}
	}

	public EventSourceDescriptor getEventSourceDescriptor(EventSourceKey key) {
		return eventSourceDescriptors.stream().filter(d -> d.getKey().equals(key)).findAny().get();
	}

	public void updateEventSourceInstance(EventSourceInstance updatedInstance) {
		EventSourceInstance origInstance = getEventSourceInstance(updatedInstance.getId());

		if (origInstance != updatedInstance) {
			copyProperties(updatedInstance, origInstance);
			for (EventSourceProperty p : origInstance.getDescriptor().getProperties()) {
				origInstance.setProperty(p, updatedInstance.getPropertyValue(p));
			}
			for (EventSourceProperty p : origInstance.getDescriptor().getDataItems()) {
				if (updatedInstance.hasPropertyValue(p)) {
					origInstance.setProperty(p, updatedInstance.getPropertyValue(p));
				} else {
					origInstance.removeProperty(p);
				}
			}
		}

		storage.persistEventSourceInstance(origInstance);
		boolean running = isRunning(origInstance);
		boolean hasStopped = false;
		if (!origInstance.isEnabled() && running) {
			stopInstance(origInstance.getId());
			hasStopped = true;
		}
		if (running && origInstance.isEnabled()) {
			if (!hasStopped) {
				stopInstance(origInstance.getId());
			}
			startInstance(origInstance.getId());
			reloadDashboardsWithEventSourceInstance(origInstance);
		}
	}

	private String createUniqueId(EventSourceDescriptor descriptor) {
		final Set<String> existingIds = Collections.unmodifiableSet(getEventSourceInstances()//
				.stream()
				.map(EventSourceInstance::getId)
				.collect(toSet()));

		String id;
		do {
			id = (descriptor.getServiceClass() != null
					? descriptor.getServiceClass().getSimpleName()
					: descriptor.getView()) + "__" + UUID.randomUUID().toString().replace("-", "_");
		} while (existingIds.contains(id));
		return id;
	}

	public EventSourceInstance createEventSourceInstance(@NotNull EventSourceDescriptor descriptor,
			Principal createdBy) {
		String id = createUniqueId(descriptor);
		String name = nameGenerator.createEventSourceInstanceName(descriptor);
		boolean enabled = false;
		Duration frequency = Duration.ofMinutes(15);

		EventSourceInstance instance = new EventSourceInstance(descriptor,
				id,
				name,
				enabled,
				frequency,
				createdBy.getUsername(),
				createdBy.getUsername());

		if (descriptor.getServiceClass() != null) {
			adoptDefaultEventSourceProperties(instance, descriptor);
		}

		unsetService(instance);
		storage.persistEventSourceInstance(instance);

		return instance;
	}

	private void adoptDefaultEventSourceProperties(EventSourceInstance instance, EventSourceDescriptor descriptor) {
		// Create a new object of the source to retrieve the default values
		Service service = createServiceInstance(descriptor.getServiceClass(), descriptor.getModuleClass(), "dummy");
		instance.getDescriptor().getProperties().forEach(
				SneakyException.sneakc(p -> instance.setProperty(p, p.getGetter().invoke(service))));
	}

	String getValueString(EventSourceProperty property, final Object value) {
		try {
			if (TypeUtils.isOfType(property.getClazz(), SerializableProperty.class)) {
				return spController.toString((SerializableProperty) value);
			} else if (value != null && value.getClass().isArray()
					&& TypeUtils.isOfType(value.getClass().getComponentType(), SerializableProperty.class)) {
				throw new NotImplementedException(
						"Arrays of Objects of type SerializableProperty are not supported yet. Please refer to developers to implement this feature.");
			} else if (value == null) {
				return "";
			} else if (property.isJson() || TypeUtils.isOfType(property.getClazz(), JsonBasedData.class)) {
				return json.toJson(value);
			} else if (value.getClass().isArray()) {
				return Arrays.deepToString((Object[]) value);
			} else {
				return value.toString();
			}
		} catch (IllegalArgumentException | SecurityException e) {
			throw new RuntimeException("Unable to convert property '" + property.getName() + "' to string", e);
		}
	}

	private static String getView(String id) {
		if (id.contains(":")) {
			return id.substring(0, id.indexOf(":"));
		} else {
			return id.substring(0, id.indexOf("__"));
		}
	}

	private void updateEventSourceClasses(String oldClassName, String newClassName) {
		LOGGER.info("Rename event source from [{}] to [{}]", oldClassName, newClassName);
		storage.updateEventSourceClasses(oldClassName, newClassName);
	}

	public void loadEventSourceInstances() {
		storage.getEventSourcesIds().stream().map(this::loadEventSourceInstance).forEach(this::unsetService);
	}

	private EventSourceInstance loadEventSourceInstance(@NotBlank String id) {
		try {
			EventSourceInstance instance = storage.loadEventSourceInstance(id,
					(c, v) -> getEventSourceDescriptor(c, Strings.isNullOrEmpty(v) ? getView(id) : v));
			Map<String, String> propertyStrings = storage.getEventSourceInstanceProperties(id);
			Map<String, String> dataItemStrings = storage.getEventSourceInstanceDataItems(id);

			if (instance.getDescriptor().getServiceClass() != null) {
				adoptDefaultEventSourceProperties(instance, instance.getDescriptor());
			}
			instance.getDescriptor()
					.getProperties()
					.stream()
					.filter(p -> propertyStrings.containsKey(p.getName()))
					.forEach(p -> instance.setPropertyString(p, propertyStrings.get(p.getName())));
			instance.getDescriptor()
					.getDataItems()
					.stream()
					.filter(p -> dataItemStrings.containsKey(p.getName()))
					.forEach(p -> instance.setPropertyString(p, dataItemStrings.get(p.getName())));

			return instance;
		} catch (Exception e) {
			throw new RuntimeException("Unable to load event source instance: " + id, e);
		}
	}

	public void startAllInstances() {
		getEventSourceInstances()//
				.stream()
				.filter(i -> i.getDescriptor().getServiceClass() != null)
				.filter(EventSourceInstance::isEnabled)
				.map(EventSourceInstance::getId)
				.forEach(this::startInstance);
	}

	public void startInstance(String id) {
		final EventSourceInstance instance = getEventSourceInstance(id);

		if (!instance.isEnabled()) {
			throw new RuntimeException("Instance " + id + " is not enabled.");
		}

		synchronized (instance) {
			// check everything
			if (getService(instance) != null) {
				throw new RuntimeException("Instance " + id + " is already started.");
			}
			if (instance.getDescriptor().getServiceClass() == null) {
				throw new RuntimeException("Instance " + id + " does not have a service class.");
			}
			if (!instance.isEnabled()) {
				throw new RuntimeException("Instance " + id + " is not enabled.");
			}

			// create class instance
			Service service = createServiceInstance(instance.getDescriptor().getServiceClass(),
					instance.getDescriptor().getModuleClass(),
					id);
			if (service instanceof ScheduledEventSource) {
				((ScheduledEventSource<?>) service).setFrequency(instance.getFrequency());
			}

			// set property values
			instance//
					.getDescriptor()
					.getProperties()
					.stream()
					.filter(instance::hasPropertyValue)
					.forEach(SneakyException//
							.sneakc(p -> p.getSetter().invoke(service, instance.getPropertyValue(p))));

			// start service
			LOGGER.info("Starting event source: " + id + " (" + instance.getName() + ")");
			setService(instance, service);
			service.startAsync();
			service.awaitRunning();
			LOGGER.info("Started event source: " + id + " (" + instance.getName() + ")");
		}
	}

	public void stopAllInstances() {
		getEventSourceInstances()//
				.stream()
				.filter(i -> i.getDescriptor().getServiceClass() != null)
				.filter(this::isRunning)
				.map(EventSourceInstance::getId)
				.forEach(this::stopInstance);
	}

	public void stopInstance(String id) {
		final EventSourceInstance instance = getEventSourceInstance(id);

		synchronized (instance) {
			final Service service = getService(instance);
			// check everything
			if (service == null) {
				throw new RuntimeException("Instance " + id + " is not running");
			}

			// stop service
			if (service.state() != State.TERMINATED && service.state() != State.STOPPING) {
				LOGGER.info("Stopping event source: " + id + " (" + instance.getName() + ")");
				service.stopAsync();
			}
			if (service.state() != State.TERMINATED) {
				if (LOGGER.isDebugEnabled()) {
					LOGGER.debug("Awaiting termination: " + id + " (" + instance.getName() + ")");
				}
				service.awaitTerminated();
			}
			unsetService(instance);
			LOGGER.info("Stopped event source: " + id + " (" + instance.getName() + ")");
		}
	}

	public void restartInstance(String id, boolean wait) {
		ForkJoinTask<?> task = ForkJoinPool.commonPool().submit(() -> {
			stopInstance(id);
			startInstance(id);
		});
		if (wait) {
			task.join();
		}
	}

	public void restartInstancesUsingSerializableProperty(SerializableProperty prop) {
		getEventSourceInstances()//
				.stream()
				.filter(this::isRunning)
				.filter(i -> i.getDescriptor()//
						.getProperties()
						.stream()
						.anyMatch(p -> p.getClazz() == prop.getClass()
								&& ((SerializableProperty) i.getPropertyValue(p)).getId() == prop.getId()))
				.map(EventSourceInstance::getId)
				.forEach(id -> restartInstance(id, false));
	}

	public boolean isRunning(EventSourceInstance instance) {
		return getService(instance) != null;
	}

	public ZonedDateTime getLastRun(EventSourceInstance instance) {
		return Optional.ofNullable((ScheduledEventSource<?>) getService(instance))
				.map(ScheduledEventSource::getLastRun)
				.orElse(null);
	}

	public ZonedDateTime getLastSuccessfulRun(EventSourceInstance instance) {
		return Optional.ofNullable((ScheduledEventSource<?>) getService(instance))
				.map(ScheduledEventSource::getLastSuccessfulRun)
				.orElse(null);
	}

	public ZonedDateTime getLastFailureDateTime(EventSourceInstance instance) {
		return Optional.ofNullable((ScheduledEventSource<?>) getService(instance))
				.map(ScheduledEventSource::getLastFailureDateTime)
				.orElse(null);
	}

	public String getLastFailureDescription(EventSourceInstance instance) {
		return Optional.ofNullable((ScheduledEventSource<?>) getService(instance))
				.map(ScheduledEventSource::getLastFailureDescription)
				.orElse(null);
	}

	public String getLastFailureException(EventSourceInstance instance) {
		return Optional.ofNullable((ScheduledEventSource<?>) getService(instance))
				.map(ScheduledEventSource::getLastFailureException)
				.orElse(null);
	}

	public void disableEventSourceInstance(String id) {
		EventSourceInstance instance = getEventSourceInstance(id);
		if (isRunning(instance)) {
			stopInstance(id);
		}
		instance.setEnabled(false);
		updateEventSourceInstance(instance);
	}

	public void enableEventSourceInstance(String id) {
		EventSourceInstance instance = getEventSourceInstance(id);
		instance.setEnabled(true);
		updateEventSourceInstance(instance);
	}

	/**
	 * Delete an event source instance if the instance is not used any more
	 * 
	 * @param eventSourceId the ID of the event source to remove
	 * @return <code>null</code> if the removal was successful or the names of all dashboards
	 *         containing widgets that use this event source instance
	 */
	public Set<String> deleteEventSourceInstance(String eventSourceId) {
		return deleteEventSourceInstance(eventSourceId, false);
	}

	public Set<String> deleteEventSourceInstance(String eventSourceId, boolean force) {
		EventSourceInstance instance = getEventSourceInstance(eventSourceId);

		List<Widget> widgetsToDelete = dashboards.values()
				.stream()
				.flatMap(d -> d.getWidgets().stream())
				.filter(w -> w.getEventSourceInstance().getId().equals(eventSourceId))
				.collect(toList());

		if (!force && !widgetsToDelete.isEmpty()) {
			return dashboards.values()
					.stream()
					.filter(d -> d.getWidgets().stream().anyMatch(
							w -> w.getEventSourceInstance().getId().equals(eventSourceId)))
					.map(Dashboard::getId)
					.collect(toSet());
		} else if (force && !widgetsToDelete.isEmpty()) {
			widgetsToDelete.forEach(this::deleteWidget);
		}

		if (isRunning(instance)) {
			stopInstance(eventSourceId);
		}

		storage.deleteEventSourceInstance(eventSourceId);
		removeService(instance);

		return null;
	}

	private <T extends Service> T createServiceInstance(Class<T> serviceClass,
			Class<? extends Module> moduleClass,
			String id) {
		Injector childInjector = injector.createChildInjector(binder -> {
			binder.bind(String.class).annotatedWith(EventId.class).toInstance(id);
			binder.bind(serviceClass);
		}, createChildModule(moduleClass));
		return childInjector.getInstance(serviceClass);
	}

	private static Module createChildModule(Class<? extends Module> moduleClass) {
		if (moduleClass == NOP.class) {
			return binder -> {
			};
		} else {
			return TypeUtils.createInstance(moduleClass);
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Object createObjectFromString(EventSourceProperty property, String string) {
		final Class<?> type = property.getClazz();
		try {
			if (type == null || String.class == type) {
				return string;
				//method.invoke(eventSource, string);
			} else if (type.isPrimitive()) {
				if (type == boolean.class) {
					return string == null ? false : Boolean.parseBoolean(string);
				} else if (type == byte.class) {
					return Byte.parseByte(string);
				} else if (type == short.class) {
					return Short.parseShort(string);
				} else if (type == int.class) {
					return Integer.parseInt(string);
				} else if (type == long.class) {
					return Long.parseLong(string);
				} else if (type == float.class) {
					return Float.parseFloat(string);
				} else if (type == double.class) {
					return Double.parseDouble(string);
				} else if (type == char.class) {
					return string.charAt(0);
				} else {
					throw new RuntimeException("Unknown primitive type: " + type);
				}
			} else if (Enum.class.isAssignableFrom(type)) {
				Enum enumValue = null;
				if (string != null) {
					enumValue = Enum.valueOf((Class<Enum>) type, string);
				} else {
					enumValue = (Enum) type.getEnumConstants()[0];
				}
				//method.invoke(eventSource, enumValue);
				return enumValue;
			} else if (string == null) {
				//method.invoke(eventSource, string);
				return string;
			} else if (SerializableProperty.class.isAssignableFrom(type)) {
				return spController.getProperty((Class<SerializableProperty>) type, Integer.parseInt(string));
			} else if (property.isJson() || TypeUtils.isOfType(type, JsonBasedData.class)) {
				Object value = json.fromJson(string, type);
				return value;
			} else if (type.isArray()) {
				throw new RuntimeException("Unable to deserialize type " + type);
			} else if (Path.class == type) {
				return Paths.get(string);
			} else if (TemporalAmount.class == type) {
				// This is quite difficult. Java has many classes that are temporal amount.
				// We need to test a bit, which one can interpret the String.
				return TypeUtils
						.tryToCreateInstance(TemporalAmount.class, string, () -> null, Duration.class, Period.class);
			} else if (type.isInterface()) {
				String message = "Type " + type.getName()
						+ " is an interface. Please use concrete classes for getters and setters.";
				LOGGER.error(message);
				throw new RuntimeException(message);
			} else {
				Object value = TypeUtils.createInstance(type, string);
				return value;
			}
		} catch (Throwable e) {
			LOGGER.error("Unable to set property value of type " + type + " from string '" + string + "'", e);
			return null;
		}
	}

	private EventSourceDescriptor loadEventSourceFromClass(final Class<? extends Service> serviceClass) {
		final EventSource eventSourceAnnotation = Objects.requireNonNull(serviceClass.getAnnotation(EventSource.class));

		// collect easy event source information
		final EventSourceKey key = EventSourceKey.createKeyFromClass(serviceClass);
		final String displayName = Objects.requireNonNull(eventSourceAnnotation.displayName());
		final String description = eventSourceAnnotation.description();
		final String view = Objects.requireNonNull(eventSourceAnnotation.view());
		final Class<? extends OversigtEvent> eventClass = Objects.requireNonNull(getEventClass(serviceClass));
		final Class<? extends Module> moduleClass = eventSourceAnnotation.explicitConfiguration();
		final EventSourceDescriptor descriptor = new EventSourceDescriptor(key,
				displayName,
				description,
				view,
				serviceClass,
				eventClass,
				moduleClass);

		// Find fields of the event
		final Set<String> eventFields = TypeUtils//
				.getMembers(eventClass)//
				.map(s -> CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_HYPHEN, s))
				.collect(toSet());

		// add event source defined data items
		Stream.of(eventSourceAnnotation.dataItems())
				.filter(not(Strings::isNullOrEmpty))
				.filter(not(eventFields::contains))
				.map(this::createDummyEventSourceProperty)
				.forEach(descriptor::addDataItem);

		// list data items to be hidden from view
		Stream.of(eventSourceAnnotation.hiddenDataItems())//
				.filter(not(Strings::isNullOrEmpty))
				.forEach(descriptor::addDataItemToHide);

		// find class properties
		final BeanInfo beanInfo;
		try {
			beanInfo = Introspector.getBeanInfo(serviceClass, Object.class);
		} catch (IntrospectionException e) {
			throw new RuntimeException(String.format("Unable to examine class %s", serviceClass), e);
		}
		Stream.of(Objects.requireNonNull(beanInfo.getPropertyDescriptors()))//
				// we need both getters and setters
				.filter(p -> Objects.nonNull(p.getReadMethod()))//
				.filter(p -> Objects.nonNull(p.getWriteMethod()))//
				// getters should not return anything
				.filter(p -> p.getReadMethod().getParameterCount() == 0)//
				// setters should accept exactly one parameter
				.filter(p -> p.getWriteMethod().getParameterCount() == 1)//
				// getters and setters from base classes are not relevant
				.filter(p -> !p.getReadMethod().getDeclaringClass().getPackage().equals(CORE_EVENT_SOURCE_PACKAGE))//
				// getters and setters without annotation are not relevant
				.filter(p -> p.getReadMethod().isAnnotationPresent(Property.class)
						|| p.getWriteMethod().isAnnotationPresent(Property.class))
				// convert into our own structure
				.map(this::createEventSourceProperty)//
				.forEach(descriptor::addProperty);

		return descriptor;
	}

	private EventSourceProperty createDummyEventSourceProperty(String name) {
		return new EventSourceProperty(name, name, null, "text", true, null, null, null, null, false, null);
	}

	private EventSourceProperty createEventSourceProperty(PropertyDescriptor descriptor) {
		if (descriptor.getReadMethod().getAnnotation(Property.class) != null
				&& descriptor.getWriteMethod().getAnnotation(Property.class) != null) {
			throw new RuntimeException("Unable to load property '" + descriptor.getName() + "' from class "
					+ descriptor.getReadMethod().getDeclaringClass().getName()
					+ " because both the read and the write method are declaring a @" + Property.class.getSimpleName()
					+ " annotation.");
		}

		// find Property annotation
		Property property = descriptor.getReadMethod().getAnnotation(Property.class);
		if (property == null) {
			property = descriptor.getWriteMethod().getAnnotation(Property.class);
		}

		final String name = descriptor.getName();
		final String displayName = getOrDefault(property, Property::name, name);
		final String description = getOrDefault(property, Property::description, "");
		final boolean customValuesAllowed = false;
		final Method getter = descriptor.getReadMethod();
		final Method setter = descriptor.getWriteMethod();
		final Class<?> clazz = descriptor.getPropertyType();
		JsonHint hint = descriptor.getReadMethod().getAnnotation(JsonHint.class);
		if (hint == null) {
			hint = descriptor.getWriteMethod().getAnnotation(JsonHint.class);
		}
		if (hint == null) {
			Class<?> tmpClass = clazz;
			while (tmpClass.isArray()) {
				tmpClass = tmpClass.getComponentType();
			}
			hint = tmpClass.getAnnotation(JsonHint.class);
		}
		final boolean json = getOrDefault(property, Property::json, false) //
				|| JsonBasedData.class.isAssignableFrom(clazz)
				|| clazz.isArray() && JsonBasedData.class.isAssignableFrom(clazz.getComponentType());
		final String jsonSchema = json ? this.json.toJsonSchema(clazz, hint) : null;

		final String inputType = getType(name,
				getOrDefault(property, Property::type, null),
				descriptor.getPropertyType(),
				json,
				null);

		EventSourceProperty esProperty = new EventSourceProperty(name,
				displayName,
				description,
				inputType,
				customValuesAllowed,
				getter,
				setter,
				clazz,
				hint,
				json,
				jsonSchema);

		collectAllowedValues(clazz).forEach(esProperty::addAllowedValue);
		return esProperty;
	}

	private Map<String, String> collectAllowedValues(Class<?> clazz) {
		if (clazz.isEnum()) {
			return Utils.toLinkedMap(Stream.of(clazz.getEnumConstants())//
					.map(e -> (Enum<?>) e), Enum::name, Enum::toString);
		} else if (clazz == ZoneId.class) {
			return Utils.toLinkedMap(ZoneId.getAvailableZoneIds()//
					.stream()
					.sorted(String::compareToIgnoreCase), Function.identity(), Function.identity());
		} else if (clazz == Locale.class) {
			return Utils.toLinkedMap(
					Stream.of(Locale.getAvailableLocales())
							.sorted((a, b) -> a.getDisplayName().compareTo(b.getDisplayName())),
					l -> l.toString().toLowerCase(),
					Locale::getDisplayName);
		}
		return Collections.emptyMap();
	}

	private String getType(String name, String propertyType, Class<?> type, boolean json, Collection<?> allowedValues) {
		if (propertyType != null && propertyType.trim().length() > 0) {
			return propertyType;
		} else if (Enum.class.isAssignableFrom(type) || allowedValues != null && !allowedValues.isEmpty()) {
			return "enum";
		} else if (json || TypeUtils.isOfType(type, JsonBasedData.class)) {
			return "json";
		} else if (type == LocalDate.class) {
			return "date";
		} else if (type == LocalTime.class) {
			return "time";
		} else if (type == LocalDateTime.class) {
			return "datetime-local";
		} else if (type == ZonedDateTime.class) {
			return "datetime";
		} else if (type == Duration.class) {
			return "duration";
		} else if (type == boolean.class) {
			return "boolean";
		} else if (SerializableProperty.class.isAssignableFrom(type)) {
			return "value_" + type.getSimpleName();
		} else if (type == int.class || type == long.class || type == short.class || type == byte.class
				|| type == Integer.class || type == Long.class || type == Short.class || type == Byte.class) {
			return "number";
		} else if (name.toLowerCase().contains("password")) {
			return "password";
		} else if (type == URL.class || type == String.class
				&& (name.toLowerCase().endsWith("url") || name.toLowerCase().startsWith("url"))) {
			return "url";
		} else {
			return "text";
		}
	}

	private List<EventSourceDescriptor> loadMultipleEventSourceFromResources(Collection<String> widgetsPaths) {
		Collection<String> allowedPaths = widgetsPaths.stream()
				.flatMap(wp -> Stream.of(wp.replace('\\', '/'), wp.replace('/', '\\')))
				.collect(toSet());

		Predicate<Path> allowedPathsFilter = path -> {
			String filename = path.getFileName().toString();
			String fullpath = path.toString();
			return filename.toLowerCase().endsWith(".html") //
					&& allowedPaths.stream().anyMatch(fullpath::contains);
		};

		return FileUtils.streamResourcesFromClasspath()
				.filter(allowedPathsFilter)
				.map(Path::getParent)
				.map(this::loadEventSourceFromPath)
				.filter(Utils::isNotNull)
				.collect(toList());
	}

	private EventSourceDescriptor loadEventSourceFromPath(Path folder) {
		try {
			return loadEventSourceFromPath_unsafe(folder);
		} catch (IOException e) {
			throw new RuntimeException("Unable to load widget from: " + folder, e);
		}
	}

	private EventSourceDescriptor loadEventSourceFromPath_unsafe(Path folder) throws IOException {
		LOGGER.trace("Inspecting path: %s", folder);

		// find name of EventSource
		final String name = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, folder.getFileName().toString());
		String displayName = name;
		final EventSourceKey key = EventSourceKey.createKeyFromWidget(name, name);
		String description = null;

		// other info
		final boolean standAlone;

		// load additional information
		Path prop = folder.resolve(folder.getFileName().toString() + ".properties");
		final Properties properties;
		if (Files.exists(prop)) {
			properties = new Properties();
			try (Reader in = Files.newBufferedReader(prop)) {
				properties.load(in);
			}

			// Check if this view is already used and if yes if it may be used stand-alone
			standAlone = Boolean
					.parseBoolean(Strings.nullToEmpty(properties.getProperty("standalone-available", "true")));

			// maybe change display name
			String newName = Strings.emptyToNull(properties.getProperty("displayName"));
			if (newName != null) {
				displayName = newName;
			}

			description = properties.getProperty("description");
		} else {
			standAlone = true;
			properties = null;
		}

		// check if we should continue
		//		final boolean available = standAlone;// && !isViewUsedByClass.test(name);
		//		if (!available) {
		//			return null;
		//		}
		final EventSourceDescriptor descriptor = new EventSourceDescriptor(key,
				displayName,
				description,
				name,
				standAlone);

		// Load data items
		Set<String> dataItems = new HashSet<>();
		addDataItemsFromHtml(dataItems, folder);
		addDataItemsFromCoffeeScript(dataItems, folder);
		dataItems.removeAll(RESERVED_DATA_BINDINGS);
		if (properties != null) {
			dataItems.removeAll(StringUtils.list(properties.getProperty("hiddenDataItems")));
			dataItems.addAll(StringUtils.list(properties.getProperty("additionalDataItems")));
		}

		dataItems.stream()//
				.map(d -> createEventSourceProperty(d, properties))
				.forEach(descriptor::addDataItem);

		return descriptor;
	}

	private EventSourceProperty createEventSourceProperty(final String name, final Properties properties) {
		final String displayName;
		final String description;
		final String inputType;
		final boolean customValuesAllowed;
		List<String> allowedValues = new ArrayList<>();

		if (properties != null) {
			displayName = properties.getProperty("dataItem." + name + ".title", name);
			description = properties.getProperty("dataItem." + name + ".description");
			inputType = properties.getProperty("dataItem." + name + ".type", "text");
			customValuesAllowed = Boolean
					.parseBoolean(properties.getProperty("dataItem." + name + ".customValuesAllowed", "false"));
			allowedValues.addAll(StringUtils.list(properties.getProperty("dataItem." + name + ".values")));
		} else {
			displayName = name;
			description = null;
			inputType = "text";
			customValuesAllowed = false;
		}

		EventSourceProperty property = new EventSourceProperty(name,
				displayName,
				Strings.emptyToNull(description),
				getType(displayName, inputType, null, false, allowedValues),
				customValuesAllowed,
				null,
				null,
				null,
				null,
				false,
				null);
		allowedValues.forEach(v -> property.addAllowedValue(v, v));
		return property;
	}

	private static void addDataItemsFromHtml(Collection<String> dataItems, Path folder) throws IOException {
		Path html = folder.resolve(folder.getFileName().toString() + ".html");
		if (Files.exists(html)) {
			Stream<String> lines = Files.lines(html);
			dataItems.addAll(Utils.findDataBindings(lines));
		}
	}

	private static void addDataItemsFromCoffeeScript(Collection<String> dataItems, Path folder) throws IOException {
		Path coffee = folder.resolve(folder.getFileName().toString() + ".coffee");
		if (Files.exists(coffee)) {
			List<String> lines = Files.lines(coffee)
					.filter(l -> !l.trim().startsWith("#"))
					.filter(not(Strings::isNullOrEmpty))
					.collect(toList());
			Set<String> gets = Utils.findGets(lines);
			Set<String> sets = Utils.findSets(lines);
			dataItems.addAll(gets);
			dataItems.removeAll(sets);
		}
	}

	private static <T, I> T getOrDefault(I input, Function<I, T> extractor, T defaultValue) {
		if (input != null) {
			return extractor.apply(input);
		} else {
			return defaultValue;
		}
	}

	@SuppressWarnings("unchecked")
	private static Class<? extends OversigtEvent> getEventClass(Class<? extends Service> clazz) {
		Method method = TypeUtils.getMethod(clazz,
				Arrays.asList("produceEventFromData", "produceCachedEvent", "produceEvent"),
				new Class<?>[0]);
		if (method != null) {
			if (TypeUtils.isOfType(method.getReturnType(), OversigtEvent.class)) {
				return (Class<? extends OversigtEvent>) method.getReturnType();
			} else {
				throw new RuntimeException("Event producing method does not return " + OversigtEvent.class.getName());
			}
		} else {
			return OversigtEvent.class;
		}
	}

	private static <T> void copyProperties(T source, T target, String... ignoreProperties) {
		BeanUtils.copyProperties(source, target, ignoreProperties);
	}
}
