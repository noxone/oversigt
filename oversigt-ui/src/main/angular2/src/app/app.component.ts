import { Component, OnInit } from '@angular/core';

export class MenuItem {
  id: string;
  title: string;
  link: string;
  children?: MenuItem[] = [];
  neededRole?: string;
  description?: string;
}

const PREFIX = '';

export const MENU_ITEMS: MenuItem[] = [
  { title: 'Event Sources', link: PREFIX + '/eventSources', id: 'eventsources', neededRole: 'server.dashboard.owner', children: []},
  { title: 'Dashboards', link: PREFIX + '/dashboards', id: 'dashboards', children: []},
  { title: 'Properties', link: PREFIX + '/properties', id: 'properties'},
  { title: 'System', link: PREFIX + '/system', id: 'system', neededRole: 'server.dashboard.owner', children: [
    // tslint:disable-next-line:max-line-length
    { title: 'Events', link: PREFIX + '/system/events', id: 'system-events', children: [], description: 'Show events the event sources have created.'},
    // tslint:disable-next-line:max-line-length
    { title: 'Loggers', link: PREFIX + '/system/loggers', id: 'system-loggers', neededRole: 'server.admin', children: [], description: 'Configure the logging facility of the server.'},
    // tslint:disable-next-line:max-line-length
    { title: 'Log Files', link: PREFIX + '/system/logfiles', id: 'system-logfiles', neededRole: 'server.admin', children: [], description: 'Display log file content.'},
    // tslint:disable-next-line:max-line-length
    { title: 'Threads', link: PREFIX + '/system/threads', id: 'system-threads', children: [], description: 'Display all running threads of the server with their current state.'},
    // tslint:disable-next-line:max-line-length
    { title: 'Configuration', link: PREFIX + '/system/config', id: 'system-config', neededRole: 'server.admin', children: [], description: 'Display the server configuration.'},
    // tslint:disable-next-line:max-line-length
    { title: 'Server', link: PREFIX + '/system/server', id: 'system-server', neededRole: 'server.admin', children: [], description: 'Control basic server functionality.'},
  ]},
];

export function getLinkForId(id: string): string {
  return MENU_ITEMS.find(mi => mi.id === id).link;
}

export function getLinkForProperty(id: string): string {
  return getLinkForId('properties') + '/' + id;
}

export function getLinkForEventSource(id: string): string {
  return getLinkForId('eventsources') + '/' + id;
}

export function getLinkForDashboards(): string {
  return getLinkForId('dashboards');
}

export function getLinkForDashboard(dashboardId: string): string {
  return getLinkForDashboards() + '/' + dashboardId;
}

export function getLinkForDashboardCreation(dashboardId: string): string {
  return getLinkForDashboards() + '/create/' + dashboardId;
}

export function getLinkForDashboardWidget(dashboardId: string, widgetId: number): string {
  return getLinkForDashboard(dashboardId) + '/' + String(widgetId);
}

export function getMenuItemForUrl(url: string): MenuItem {
  return MENU_ITEMS.find(mi => mi.link === url);
}

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit {
  title = 'oversigt-ui';

  constructor(
  ) {
  }

  ngOnInit(): void {
  }
}
