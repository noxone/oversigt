import { Component, OnInit } from '@angular/core';
import { SystemService, ThreadInfo } from 'src/oversigt-client';
import { getLinkForEventSource } from 'src/app/app.component';
import { ErrorHandlerService } from 'src/app/services/error-handler.service';

export class ThreadInfoComposite {
  threadInfo: ThreadInfo;
  visible = false;

  constructor(threadInfo: ThreadInfo) {
    this.threadInfo = threadInfo;
  }
}

const THREAD_STATE_VALUES = ['RUNNABLE', 'BLOCKED', 'NEW', 'WAITING', 'TIMED_WAITING', 'TERMINATED'];

@Component({
  selector: 'app-config-threads',
  templateUrl: './config-threads.component.html',
  styleUrls: ['./config-threads.component.css']
})
export class ConfigThreadsComponent implements OnInit {
  threadInfos: ThreadInfoComposite[] = [];
  filter = '';

  constructor(
    private ss: SystemService,
    private errorHandler: ErrorHandlerService,
  ) { }

  ngOnInit() {
    this.reloadThreads();
  }

  reloadThreads(): void {
    const _this = this;
    this.threadInfos = [];
    this.ss.getThreads().subscribe(
      threads => {
        _this.threadInfos = threads.map(t => new ThreadInfoComposite(t)).sort((a, b) => _this.compare(a, b));
      },
      this.errorHandler.createErrorHandler('Reading thread information'));
  }

  private compare(a: ThreadInfoComposite, b: ThreadInfoComposite): number {
    const aIsEst = this.isEventSourceThread(a);
    const bIsEst = this.isEventSourceThread(b);
    if (aIsEst !== bIsEst) {
      return bIsEst ? 1 : -1;
    }

    const r = THREAD_STATE_VALUES.indexOf(a.threadInfo.state) - THREAD_STATE_VALUES.indexOf(b.threadInfo.state);
    if (r !== 0) {
      return r;
    }

    return a.threadInfo.name.toLowerCase() > b.threadInfo.name.toLowerCase() ? 1 : -1;
  }

  isEventSourceThread(com: ThreadInfoComposite): boolean {
    const name = com.threadInfo.name;
    return name.includes('[eventID=') && !name.startsWith('NightlyDashboardReloaderService');
  }

  getEventSourceId(com: ThreadInfoComposite): string {
    const name = com.threadInfo.name;
    return com.threadInfo.name.substring(com.threadInfo.name.indexOf('[eventID=') + 9, com.threadInfo.name.lastIndexOf(']'));
  }

  getThreadDetails(info: ThreadInfo): string {
    return '';
  }

  getEventSourceLink(id: string): string {
    return getLinkForEventSource(id);
  }
}
