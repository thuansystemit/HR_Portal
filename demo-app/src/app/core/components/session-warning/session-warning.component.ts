import {
  Component, OnInit, OnDestroy,
  computed, input, signal,
} from '@angular/core';
import { interval, Subscription } from 'rxjs';

@Component({
  selector: 'app-session-warning',
  standalone: true,
  templateUrl: './session-warning.component.html',
})
export class SessionWarningComponent implements OnInit, OnDestroy {
  /** Initial countdown value in seconds (defaults to 60). */
  secondsLeft = input<number>(60);

  protected countdown = signal<number>(60);

  /** Percentage of time remaining, used to shrink the progress bar. */
  protected progressPct = computed(() => {
    const initial = this.secondsLeft();
    const current = this.countdown();
    return initial > 0 ? Math.round((current / initial) * 100) : 0;
  });

  /** Formatted MM:SS string, e.g. "01:00" or "00:42". */
  protected formatted = computed(() => {
    const total = Math.max(0, this.countdown());
    const mm = Math.floor(total / 60).toString().padStart(2, '0');
    const ss = (total % 60).toString().padStart(2, '0');
    return `${mm}:${ss}`;
  });

  private tickSub?: Subscription;

  ngOnInit(): void {
    this.countdown.set(this.secondsLeft());

    this.tickSub = interval(1000).subscribe(() => {
      this.countdown.update(n => Math.max(0, n - 1));
    });
  }

  ngOnDestroy(): void {
    this.tickSub?.unsubscribe();
  }
}
