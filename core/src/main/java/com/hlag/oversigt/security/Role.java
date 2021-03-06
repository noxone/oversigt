package com.hlag.oversigt.security;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class Role {
	public static final String ROLE_NAME_SERVER_ADMIN = "server.admin";

	public static final String ROLE_NAME_GENERAL_DASHBOARD_OWNER = "server.dashboard.owner";

	public static final String ROLE_NAME_GENERAL_DASHBOARD_EDITOR = "server.dashboard.editor";

	static final Role SERVER_ADMIN = new Role(ROLE_NAME_SERVER_ADMIN);

	static final Role DASHBOARD_OWNER = new Role(SERVER_ADMIN, ROLE_NAME_GENERAL_DASHBOARD_OWNER);

	static final Role DASHBOARD_EDITOR = new Role(DASHBOARD_OWNER, ROLE_NAME_GENERAL_DASHBOARD_EDITOR);

	private static Map<String, Role> dashboardOwnerRoles = new HashMap<>();

	private static Map<String, Role> dashboardEditorRoles = new HashMap<>();

	public static Role getDashboardOwnerRole(final String dashboardId) {
		return dashboardOwnerRoles.computeIfAbsent(dashboardId,
				id -> new Role(DASHBOARD_OWNER, "dashboard." + id + ".owner"));
	}

	public static Role getDashboardEditorRole(final String dashboardId) {
		return dashboardEditorRoles.computeIfAbsent(dashboardId,
				id -> new Role(DASHBOARD_EDITOR, "dashboard." + id + ".editor"));
	}

	private final Optional<Role> parent;

	private final String name;

	private Role(final String name) {
		parent = Optional.empty();
		this.name = name;
	}

	private Role(final Role parent, final String name) {
		this.parent = Optional.of(parent);
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public Optional<Role> getParent() {
		return parent;
	}

	public Role getDashboardSpecificRole(final String dashboardId) {
		if (this == DASHBOARD_OWNER) {
			return getDashboardOwnerRole(dashboardId);
		} else if (this == DASHBOARD_EDITOR) {
			return getDashboardEditorRole(dashboardId);
		} else {
			throw new RuntimeException("No dashboard specific role available for: " + toString());
		}
	}

	@Override
	public String toString() {
		return name + getParent().map(s -> " (" + s.toString() + ")").orElse("");
	}
}
