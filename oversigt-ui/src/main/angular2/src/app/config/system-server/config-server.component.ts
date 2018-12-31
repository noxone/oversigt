import { Component, OnInit } from '@angular/core';
import { EventSourceService, SystemService } from 'src/oversigt-client';
import { NotificationService } from 'src/app/notification.service';
import { ClrLoadingState } from '@clr/angular';

@Component({
  selector: 'app-config-server',
  templateUrl: './config-server.component.html',
  styleUrls: ['./config-server.component.css']
})
export class ConfigServerComponent implements OnInit {
  restartState: ClrLoadingState = ClrLoadingState.DEFAULT;

  constructor(
    private eventSourceService: EventSourceService,
    private systemService: SystemService,
    private notification: NotificationService,
  ) {}

  ngOnInit() {}

  shutdownServer(): void {
    if (!confirm('Do you really want to shut down the server?')) {
      return;
    }
    this.systemService.shutdown().subscribe(
      ok => {
        this.notification.info('The server is about to shut down. This configuration page will stop working now.');
      }, error => {
        alert(error);
        console.log(error);
        // TODO: error handling
      }
    );
  }

  startEventSources() {
    this.changeEventSourceStates(true, 'Event sources started.');
  }

  stopEventSources() {
    this.changeEventSourceStates(false, 'Event sources stopped.');
  }

  changeEventSourceStates(running: boolean, messageWhenDone: string) {
    this.restartState = ClrLoadingState.LOADING;
    const restarted: {[s: string]: boolean; } = {};

    const checkAll = () => {
      if (Object.values(restarted).reduce((a, b) => a && b)) {
        this.notification.success(messageWhenDone);
        this.restartState = ClrLoadingState.SUCCESS;
      }
    };
    this.eventSourceService.listInstances().subscribe(
      list => {
        // TODO: filter for event sources that can be started!
        list.forEach(item => restarted[item.id] = false);
        list.forEach(item => {
          this.eventSourceService.setInstanceRunning(item.id, running).subscribe(
            success => {
              console.log(item.id, 'done');
            },
            error => {
              console.error(error.error);
              // alert(error);
              // TODO: Error handling
              // TODO: this.restartState = ClrLoadingState.ERROR;
            },
            () => {
              restarted[item.id] = true;
              checkAll();
            }
          );
        });
      },
      error => {
        console.error(error);
        alert(error);
        // TODO: Error handling
        this.restartState = ClrLoadingState.ERROR;
      }
    );
  }
}
