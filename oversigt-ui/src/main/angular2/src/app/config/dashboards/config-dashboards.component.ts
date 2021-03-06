import { Component, OnInit, OnDestroy } from '@angular/core';
import { DashboardService, DashboardInfo, Dashboard } from 'src/oversigt-client';
import { Subscription } from 'rxjs';
import { ActivatedRoute, Router } from '@angular/router';
import { UserService } from 'src/app/services/user-service.service';
import { NotificationService } from 'src/app/services/notification.service';
import { getLinkForDashboard } from 'src/app/app.component';
import { ErrorHandlerService } from 'src/app/services/error-handler.service';

@Component({
  selector: 'app-config-dashboards',
  templateUrl: './config-dashboards.component.html',
  styleUrls: ['./config-dashboards.component.css']
})
export class ConfigDashboardsComponent implements OnInit, OnDestroy {
  private subscription: Subscription = null;

  dashboards: DashboardInfo[] = [];
  dashboardFilter = '';

  createDashboardModelOpen = false;
  newDashboardName = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private ds: DashboardService,
    private userService: UserService,
    private notification: NotificationService,
    private errorHandler: ErrorHandlerService,
  ) { }

  ngOnInit() {
    this.subscription = this.route.url.subscribe(_ => {
      this.loadDashboards();
    });
  }

  ngOnDestroy() {
    if (this.subscription) {
      this.subscription.unsubscribe();
    }
  }

  private loadDashboards(): void {
    this.ds.listDashboardIds().subscribe(
      list => {
        const isAdmin = this.userService.hasRole('server.admin');
        this.dashboards = list
              .filter(d => isAdmin || d.enabled)
              .sort((a, b) => a.title.toLowerCase() > b.title.toLowerCase() ? 1 : -1);
      },
      this.errorHandler.createErrorHandler('Reading dashboards'));
  }

  createDashboard(id: string) {
    if (id.trim().length === 0) {
      this.notification.warning('The ID you entered was empty. Cannot create dashboard.');
      return;
    }
    this.ds.createDashboard(id, this.userService.getUserId(), this.userService.hasRole('server.admin')).subscribe(
      (dashboard: Dashboard) => {
        this.userService.reloadRoles();
        this.notification.success('Dashboard "' + dashboard.id + '" has been created.');
        this.router.navigateByUrl(getLinkForDashboard(dashboard.id));
      },
      this.errorHandler.createErrorHandler('Creating the dashboard'));
  }
}
