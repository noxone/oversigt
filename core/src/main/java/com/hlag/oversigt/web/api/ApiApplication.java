package com.hlag.oversigt.web.api;

import static com.hlag.oversigt.core.HttpHandlers.MAPPING_API;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.ws.rs.core.Application;

import com.fasterxml.jackson.databind.JavaType;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.hlag.oversigt.properties.Color;
import com.hlag.oversigt.web.resources.Authentication;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.swagger.converter.ModelConverter;
import io.swagger.converter.ModelConverterContext;
import io.swagger.converter.ModelConverters;
import io.swagger.jaxrs.config.BeanConfig;
import io.swagger.models.Model;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.PropertyBuilder;
import io.swagger.util.Json;

@Singleton
public class ApiApplication extends Application {
	@Inject
	public ApiApplication(@Named("hostname") final String hostname) {
		// Initialize Swagger settings
		final URL url;
		try {
			url = new URL(hostname);
		} catch (final MalformedURLException e) {
			throw new RuntimeException(e);
		}
		// Configure Swagger
		final BeanConfig beanConfig = new BeanConfig();
		beanConfig.setSchemes(new String[] { url.getProtocol() });
		beanConfig.setHost(url.getHost()
				+ (url.getPort() != -1 && url.getPort() != url.getDefaultPort() ? ":" + url.getPort() : ""));
		beanConfig.setBasePath(MAPPING_API);
		beanConfig.setResourcePackage(
				Authentication.class.getPackage().getName() + "," + Api.class.getPackage().getName());
		beanConfig.setScan(true);

		// Change model display for swagger
		ModelConverters.getInstance().addConverter(new ColorConverter());
		ModelConverters.getInstance().addConverter(new DurationConverter());
	}

	/** {@inheritDoc} */
	@Override
	public Set<Class<?>> getClasses() {
		final Set<Class<?>> classes = new HashSet<>();

		// Add Swagger Resources
		classes.add(io.swagger.jaxrs.listing.ApiListingResource.class);
		classes.add(io.swagger.jaxrs.listing.SwaggerSerializers.class);

		// All other resources will be found by Guice module in ApiBootstrapListener

		return classes;
	}

	private static final class ColorConverter implements ModelConverter {
		private ColorConverter() {
			// no fields to be initialized
		}

		@Nullable
		@Override
		public Property resolveProperty(@Nullable final Type type,
				@Nullable final ModelConverterContext context,
				@Nullable final Annotation[] annotations,
				@Nullable final Iterator<ModelConverter> chain) {
			final JavaType jType = Json.mapper().constructType(type);
			if (jType == null) {
				return null;
			}
			final Class<?> cls = jType.getRawClass();
			if (!cls.equals(Color.class)) {
				return Objects.requireNonNull(chain).next().resolveProperty(type, context, annotations, chain);
			}
			final Map<PropertyBuilder.PropertyId, Object> map = new HashMap<>();
			// map.put(PropertyBuilder.PropertyId.FORMAT, "#rrggbbaa");
			map.put(PropertyBuilder.PropertyId.TYPE, "string");
			map.put(PropertyBuilder.PropertyId.EXAMPLE, "#11223344");
			return PropertyBuilder.build("string", "#rrggbbaa", map);
		}

		@Nullable
		@Override
		public Model resolve(@Nullable final Type type,
				@Nullable final ModelConverterContext context,
				@Nullable final Iterator<ModelConverter> chain) {
			return Objects.requireNonNull(chain).next().resolve(type, context, chain);
		}
	}

	private static final class DurationConverter implements ModelConverter {
		private DurationConverter() {
			// no fields to be initialized
		}

		@Nullable
		@Override
		public Property resolveProperty(@Nullable final Type type,
				@Nullable final ModelConverterContext context,
				@Nullable final Annotation[] annotations,
				@Nullable final Iterator<ModelConverter> chain) {
			final JavaType jType = Json.mapper().constructType(type);
			if (jType == null) {
				return null;
			}
			final Class<?> cls = jType.getRawClass();
			if (!cls.equals(Duration.class)) {
				return Objects.requireNonNull(chain).next().resolveProperty(type, context, annotations, chain);
			}
			final Map<PropertyBuilder.PropertyId, Object> map = new HashMap<>();
			// map.put(PropertyBuilder.PropertyId.FORMAT, "#rrggbbaa");
			map.put(PropertyBuilder.PropertyId.TYPE, "string");
			map.put(PropertyBuilder.PropertyId.EXAMPLE, "PT1H");
			map.put(PropertyBuilder.PropertyId.PATTERN,
					"^P(?:(0+)Y)?(?:(0+)M)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+(?:\\.\\d+)?)S)?)?$");
			return PropertyBuilder.build("string", null, map);
		}

		@Nullable
		@Override
		public Model resolve(@Nullable final Type type,
				@Nullable final ModelConverterContext context,
				@Nullable final Iterator<ModelConverter> chain) {
			return Objects.requireNonNull(chain).next().resolve(type, context, chain);
		}
	}
}
