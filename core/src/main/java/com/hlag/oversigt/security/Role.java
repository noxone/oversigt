package com.hlag.oversigt.security;

import java.util.HashMap;
import java.util.Map;

public class Role {
	public static final String ROLE_NAME_SERVER_ADMIN = "server.admin";
	public static final String ROLE_NAME_GENERAL_DASHBOARD_OWNER = "server.dashboard.owner";
	public static final String ROLE_NAME_GENERAL_DASHBOARD_EDITOR = "server.dashboard.editor";

	static Role SERVER_ADMIN = new Role(null, ROLE_NAME_SERVER_ADMIN);
	static Role DASHBOARD_OWNER = new Role(SERVER_ADMIN, ROLE_NAME_GENERAL_DASHBOARD_OWNER);
	static Role DASHBOARD_EDITOR = new Role(DASHBOARD_OWNER, ROLE_NAME_GENERAL_DASHBOARD_EDITOR);

	private static Map<String, Role> dashboardOwnerRoles = new HashMap<>();
	private static Map<String, Role> dashboardEditorRoles = new HashMap<>();

	public static Role getDashboardOwnerRole(String dashboardId) {
		return dashboardOwnerRoles.computeIfAbsent(dashboardId,
				id -> new Role(DASHBOARD_OWNER, "dashboard." + id + ".owner"));
	}

	public static Role getDashboardEditorRole(String dashboardId) {
		return dashboardEditorRoles.computeIfAbsent(dashboardId,
				id -> new Role(DASHBOARD_EDITOR, "dashboard." + id + ".editor"));
	}

	private final Role parent;
	private final String name;

	private Role(Role parent, String name) {
		this.parent = parent;
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public Role getParent() {
		return parent;
	}

	public Role getDashboardSpecificRole(String dashboardId) {
		if (this == DASHBOARD_OWNER) {
			return getDashboardOwnerRole(dashboardId);
		} else if (this == DASHBOARD_EDITOR) {
			return getDashboardEditorRole(dashboardId);
		} else {
			throw new RuntimeException("No dashboard specific role available for: " + this.toString());
		}
	}

	@Override
	public String toString() {
		if (parent != null) {
			return name + " (" + parent.toString() + ")";
		} else {
			return name;
		}
	}
}
