package com.hlag.oversigt.model;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.hlag.oversigt.properties.Color;

public class Dashboard implements Cloneable {
	private static Set<String> newSet(String... items) {
		return newSet(Arrays.asList(items));
	}

	private static Set<String> newSet(Collection<String> items) {
		TreeSet<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		set.addAll(items);
		return Collections.synchronizedSet(set);
	}

	static final int TILE_DISTANCE = 6;

	@NotNull
	@NotBlank
	@JsonPropertyDescription("The unique ID of the dashboard. It will be used to reference the dashboard.")
	private String id = null;
	@NotNull
	@NotBlank
	@JsonPropertyDescription("The title of the dashboard for display in the browser")
	private String title = "Dashboard";
	@NotNull
	@JsonPropertyDescription("Whether the dashboard is available to the public")
	private boolean enabled = false;
	@NotNull
	@Min(1)
	@JsonProperty(defaultValue = "1920")
	@JsonPropertyDescription("The screen width the dashboard will be optimized for")
	private int screenWidth = 1920;
	@NotNull
	@Min(1)
	@JsonProperty(defaultValue = "1080")
	@JsonPropertyDescription("The screen height the dashboard will be optimized for")
	private int screenHeight = 1080;
	@NotNull
	@Min(1)
	@JsonProperty(defaultValue = "15")
	@JsonPropertyDescription("The number of columns the dashboard will show if displayed without zoom")
	private int columns = 15;

	@NotNull
	@JsonProperty(defaultValue = "#222222")
	@JsonPropertyDescription("The background color of the dashboard")
	private Color backgroundColor = Color.parse("#222222");
	@NotNull
	@JsonPropertyDescription("The type of coloring the dashboard will use")
	private DashboardColorScheme colorScheme = DashboardColorScheme.COLORED;
	@NotNull
	@JsonPropertyDescription("The first color for the selected color scheme")
	private Color foregroundColorStart = Color.parse("#888888");
	@NotNull
	@JsonPropertyDescription("The second color for the selected color scheme")
	private Color foregroundColorEnd = Color.parse("#AAAAAA");

	@NotNull
	@JsonPropertyDescription("The user id of dashboard's owner")
	private Set<@NotBlank /* TODO @UserId */ String> owners = newSet();
	@NotNull
	@JsonPropertyDescription("A list of user ids of people who are allowed to edit the dashboard")
	private Set<@NotBlank /* TODO @UserId */ String> editors = newSet();

	@JsonIgnore
	private final Set<Widget> widgets = new TreeSet<>();

	Dashboard() {
	}

	public Dashboard(String id, String owner, boolean enabled) {
		this.id = id;
		this.title = id;
		this.owners.add(owner);
		this.enabled = enabled;
	}

	public Dashboard(String id,
			String title,
			boolean enabled,
			int screenWidth,
			int screenHeight,
			int columns,
			Color backgroundColor,
			DashboardColorScheme colorScheme,
			Color foregroundColorStart,
			Color foregroundColorEnd,
			Collection<String> owners,
			Collection<String> editors) {
		this.id = id;
		this.title = title;
		this.enabled = enabled;
		this.screenWidth = screenWidth;
		this.screenHeight = screenHeight;
		this.columns = columns;
		this.backgroundColor = backgroundColor;
		this.colorScheme = colorScheme;
		this.foregroundColorStart = foregroundColorStart;
		this.foregroundColorEnd = foregroundColorEnd;
		this.owners.addAll(owners);
		this.editors.addAll(editors);
	}

	public String getId() {
		return Objects.requireNonNull(id);
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public int getScreenWidth() {
		return screenWidth;
	}

	public void setScreenWidth(int screenWidth) {
		this.screenWidth = screenWidth;
	}

	public int getScreenHeight() {
		return screenHeight;
	}

	public void setScreenHeight(int screenHeight) {
		this.screenHeight = screenHeight;
	}

	public int getColumns() {
		return columns;
	}

	public void setColumns(int columns) {
		this.columns = columns;
	}

	public Color getBackgroundColor() {
		return backgroundColor;
	}

	public void setBackgroundColor(Color backgroundColor) {
		this.backgroundColor = backgroundColor;
	}

	public void setBackgroundColor(String backgroundColor) {
		setBackgroundColor(Color.parse(backgroundColor));
	}

	public DashboardColorScheme getColorScheme() {
		return colorScheme;
	}

	public void setColorScheme(DashboardColorScheme colorScheme) {
		this.colorScheme = colorScheme;
	}

	public void setColorScheme(String string) {
		setColorScheme(DashboardColorScheme.fromString(string));
	}

	public Color getForegroundColorStart() {
		return foregroundColorStart;
	}

	public void setForegroundColorStart(Color foregroundColorStart) {
		this.foregroundColorStart = foregroundColorStart;
	}

	public void setForegroundColorStart(String foregroundColorStart) {
		setForegroundColorStart(Color.parse(foregroundColorStart));
	}

	public Color getForegroundColorEnd() {
		return foregroundColorEnd;
	}

	public void setForegroundColorEnd(Color foregroundColorEnd) {
		this.foregroundColorEnd = foregroundColorEnd;
	}

	public void setForegroundColorEnd(String foregroundColorEnd) {
		setForegroundColorEnd(Color.parse(foregroundColorEnd));
	}

	public Collection<String> getOwners() {
		return owners;
	}

	public void setOwners(Collection<String> owners) {
		this.owners = newSet(owners);
	}

	public Collection<String> getEditors() {
		return editors;
	}

	public void setEditors(Collection<String> editors) {
		this.editors = newSet(editors);
	}

	public boolean isEditor(String username) {
		return getEditors().contains(username);
	}

	@JsonIgnore
	public Collection<Widget> getWidgets() {
		return Collections.unmodifiableCollection(widgets);
	}

	public Widget getWidget(int id) {
		return getWidgets().stream().filter(w -> w.getId() == id).findFirst().get();
	}

	Collection<Widget> getModifiableWidgets() {
		return widgets;
	}

	@JsonIgnore
	public int getComputedTileWidth() {
		return (getScreenWidth() - TILE_DISTANCE * getColumns()) / getColumns();
	}

	@JsonIgnore
	public int getComputedTileHeight() {
		int height = getComputedTileWidth() - 2;
		height = getScreenHeight() / height;
		height = (getScreenHeight() - TILE_DISTANCE * height) / height;
		return height;
	}

	@Override
	public String toString() {
		return ToStringBuilder.reflectionToString(this, ToStringStyle.JSON_STYLE);
	}

	@Override
	public Dashboard clone() {
		Dashboard dashboard = new Dashboard(getId(),
				getTitle(),
				isEnabled(),
				getScreenWidth(),
				getScreenHeight(),
				getColumns(),
				getBackgroundColor(),
				getColorScheme(),
				getForegroundColorStart(),
				getForegroundColorEnd(),
				getOwners(),
				getEditors());
		dashboard.getModifiableWidgets().addAll(getWidgets());
		return dashboard;
	}
}
