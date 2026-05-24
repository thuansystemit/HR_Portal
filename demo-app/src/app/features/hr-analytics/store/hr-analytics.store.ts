import { Injectable, inject, signal } from '@angular/core';
import { forkJoin } from 'rxjs';
import { HrAnalyticsApi } from '../services/hr-analytics.api';
import {
  ApplicationTrendEntry, ConversionRateEntry, FunnelStageEntry,
  TimeToHireEntry, TopSkillEntry,
} from '../models/hr-analytics.model';

@Injectable({ providedIn: 'root' })
export class HrAnalyticsStore {
  private readonly api = inject(HrAnalyticsApi);

  private readonly _funnel          = signal<FunnelStageEntry[]>([]);
  private readonly _timeToHire      = signal<TimeToHireEntry[]>([]);
  private readonly _topSkills       = signal<TopSkillEntry[]>([]);
  private readonly _applicationTrend = signal<ApplicationTrendEntry[]>([]);
  private readonly _conversionRates = signal<ConversionRateEntry[]>([]);
  private readonly _loading         = signal(false);
  private readonly _error           = signal<string | null>(null);

  readonly funnel           = this._funnel.asReadonly();
  readonly timeToHire       = this._timeToHire.asReadonly();
  readonly topSkills        = this._topSkills.asReadonly();
  readonly applicationTrend = this._applicationTrend.asReadonly();
  readonly conversionRates  = this._conversionRates.asReadonly();
  readonly loading          = this._loading.asReadonly();
  readonly error            = this._error.asReadonly();

  loadAll(): void {
    this._loading.set(true);
    this._error.set(null);
    forkJoin({
      funnel:           this.api.funnel(),
      timeToHire:       this.api.timeToHire(),
      topSkills:        this.api.topSkills(),
      applicationTrend: this.api.applicationTrend(),
      conversionRates:  this.api.conversionRates(),
    }).subscribe({
      next: d => {
        this._funnel.set(d.funnel);
        this._timeToHire.set(d.timeToHire);
        this._topSkills.set(d.topSkills);
        this._applicationTrend.set(d.applicationTrend);
        this._conversionRates.set(d.conversionRates);
        this._loading.set(false);
      },
      error: err => { this._error.set(err.message); this._loading.set(false); },
    });
  }
}
