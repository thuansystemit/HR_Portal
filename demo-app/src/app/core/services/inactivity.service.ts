import { Injectable, NgZone, inject, OnDestroy } from '@angular/core';
import { fromEvent, merge, Subscription } from 'rxjs';
import { throttleTime } from 'rxjs/operators';
import { AuthService } from '../../auth/services/auth';
import { DialogService } from '../../shared/components/dialog/dialog.service';

const TIMEOUT_MS     = 15 * 60 * 1000;  // 15 minutes
const WARN_BEFORE_MS =  1 * 60 * 1000;  // warn 1 minute before

const ACTIVITY_EVENTS = ['mousemove', 'mousedown', 'keydown', 'scroll', 'touchstart', 'click'];

@Injectable({ providedIn: 'root' })
export class InactivityService implements OnDestroy {
  private readonly auth   = inject(AuthService);
  private readonly dialog = inject(DialogService);
  private readonly zone   = inject(NgZone);

  private warnHandle?:   ReturnType<typeof setTimeout>;
  private logoutHandle?: ReturnType<typeof setTimeout>;
  private warnOpen  = false;
  private actSub?:  Subscription;

  start(): void {
    this.zone.runOutsideAngular(() => {
      const activity$ = merge(
        ...ACTIVITY_EVENTS.map(e => fromEvent(document, e)),
      ).pipe(throttleTime(500));

      this.actSub = activity$.subscribe(() => this.onActivity());
    });

    this.schedule();
  }

  stop(): void {
    this.clearTimers();
    this.actSub?.unsubscribe();
    this.actSub = undefined;
    this.warnOpen = false;
  }

  ngOnDestroy(): void { this.stop(); }

  private onActivity(): void {
    if (this.warnOpen) return;   // don't reset while warning is visible
    this.schedule();
  }

  private schedule(): void {
    this.clearTimers();

    this.warnHandle = setTimeout(() => {
      this.zone.run(() => this.showWarning());
    }, TIMEOUT_MS - WARN_BEFORE_MS);

    this.logoutHandle = setTimeout(() => {
      this.zone.run(() => this.auth.logout());
    }, TIMEOUT_MS);
  }

  private clearTimers(): void {
    clearTimeout(this.warnHandle);
    clearTimeout(this.logoutHandle);
  }

  private showWarning(): void {
    if (this.warnOpen) return;
    this.warnOpen = true;

    this.dialog.confirm(
      'Session Expiring Soon',
      'You have been inactive for 14 minutes. Your session will expire in 1 minute. Do you want to stay logged in?',
      { confirmLabel: 'Stay Logged In', cancelLabel: 'Log Out', type: 'warning' },
    ).then(stayIn => {
      this.warnOpen = false;
      if (stayIn) {
        this.schedule();   // reset the full 15-minute timer
      } else {
        this.clearTimers();
        this.auth.logout();
      }
    });
  }
}
