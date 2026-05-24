import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  ApplicationTrendEntry, ConversionRateEntry, FunnelStageEntry,
  TimeToHireEntry, TopSkillEntry,
} from '../models/hr-analytics.model';

@Injectable({ providedIn: 'root' })
export class HrAnalyticsApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/reports/hr`;

  funnel():           Observable<FunnelStageEntry[]>       { return this.http.get<FunnelStageEntry[]>(`${this.base}/funnel`); }
  timeToHire():       Observable<TimeToHireEntry[]>        { return this.http.get<TimeToHireEntry[]>(`${this.base}/time-to-hire`); }
  topSkills():        Observable<TopSkillEntry[]>          { return this.http.get<TopSkillEntry[]>(`${this.base}/top-skills`); }
  applicationTrend(): Observable<ApplicationTrendEntry[]>  { return this.http.get<ApplicationTrendEntry[]>(`${this.base}/application-trend`); }
  conversionRates():  Observable<ConversionRateEntry[]>    { return this.http.get<ConversionRateEntry[]>(`${this.base}/conversion-rates`); }
}
