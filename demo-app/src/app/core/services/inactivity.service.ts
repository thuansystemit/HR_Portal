import { Injectable, NgZone, inject, OnDestroy } from '@angular/core';
import { fromEvent, merge, Subscription } from 'rxjs';
import { throttleTime } from 'rxjs/operators';
import { AuthService } from '../../auth/services/auth';
import { DialogRef, DialogService } from '../../shared/components/dialog/dialog.service';
import { SessionWarningComponent } from '../components/session-warning/session-warning.component';

const TIMEOUT_MS     = 15 * 60 * 1000;  // 15 minutes total
const WARN_BEFORE_MS =  1 * 60 * 1000;  // warn 1 minute before logout
const WARN_SECONDS   = WARN_BEFORE_MS / 1000;

const ACTIVITY_EVENTS = ['mousemove', 'mousedown', 'keydown', 'scroll', 'touchstart', 'click'];

@Injectable({ providedIn: 'root' })
export class InactivityService implements OnDestroy {
  private readonly auth   = inject(AuthService);
  private readonly dialog = inject(DialogService);
  private readonly zone   = inject(NgZone);

  private warnHandle?:    ReturnType<typeof setTimeout>;
  private logoutHandle?:  ReturnType<typeof setTimeout>;
  private warnDialogRef?: DialogRef;
  private actSub?:        Subscription;

  start(): void {
    this.zone.runOutsideAngular(() => {
      const activity$ = merge(
        ...ACTIVITY_EVENTS.map(e => fromEvent(document, e)),
      ).pipe(throttleTime(500));

      this.actSub = activity$.subscribe(() => this.zone.run(() => this.onActivity()));
    });

    this.schedule();
  }

  stop(): void {
    this.warnDialogRef?.dismiss();
    this.warnDialogRef = undefined;
    this.clearTimers();
    this.actSub?.unsubscribe();
    this.actSub = undefined;
  }

  ngOnDestroy(): void { this.stop(); }

  private onActivity(): void {
    if (this.warnDialogRef) return;   // don't reset while warning is visible
    this.schedule();
  }

  private schedule(): void {
    this.clearTimers();

    this.warnHandle = setTimeout(() => {
      this.zone.run(() => this.showWarning());
    }, TIMEOUT_MS - WARN_BEFORE_MS);

    this.logoutHandle = setTimeout(() => {
      this.zone.run(() => {
        // Dismiss the warning dialog before navigating away so it doesn't
        // remain visible on top of the login page.
        this.warnDialogRef?.dismiss();
        this.warnDialogRef = undefined;
        this.auth.logout();
      });
    }, TIMEOUT_MS);
  }

  private clearTimers(): void {
    clearTimeout(this.warnHandle);
    clearTimeout(this.logoutHandle);
    this.warnHandle   = undefined;
    this.logoutHandle = undefined;
  }

  private showWarning(): void {
    if (this.warnDialogRef) return;

    this.warnDialogRef = this.dialog.openForm(
      SessionWarningComponent,
      {
        title:         'Session Expiring Soon',
        type:          'warning',
        confirmLabel:  'Stay Logged In',
        cancelLabel:   'Log Out',
      },
      { secondsLeft: WARN_SECONDS },
    );

    this.warnDialogRef.result.then(stayIn => {
      this.warnDialogRef = undefined;
      if (stayIn) {
        // User chose to stay — reset the full 15-minute timer.
        this.schedule();
      } else {
        // User chose to log out immediately.
        this.clearTimers();
        this.auth.logout();
      }
    });
  }
}
