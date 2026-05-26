import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
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

  private params(jobPostingId?: string | null): { params?: HttpParams } {
    if (!jobPostingId) return {};
    return { params: new HttpParams().set('jobPostingId', jobPostingId) };
  }

  funnel(jobPostingId?: string | null):           Observable<FunnelStageEntry[]>       { return this.http.get<FunnelStageEntry[]>(`${this.base}/funnel`, this.params(jobPostingId)); }
  timeToHire(jobPostingId?: string | null):       Observable<TimeToHireEntry[]>        { return this.http.get<TimeToHireEntry[]>(`${this.base}/time-to-hire`, this.params(jobPostingId)); }
  topSkills(jobPostingId?: string | null):        Observable<TopSkillEntry[]>          { return this.http.get<TopSkillEntry[]>(`${this.base}/top-skills`, this.params(jobPostingId)); }
  applicationTrend(jobPostingId?: string | null): Observable<ApplicationTrendEntry[]>  { return this.http.get<ApplicationTrendEntry[]>(`${this.base}/application-trend`, this.params(jobPostingId)); }
  conversionRates(jobPostingId?: string | null):  Observable<ConversionRateEntry[]>    { return this.http.get<ConversionRateEntry[]>(`${this.base}/conversion-rates`, this.params(jobPostingId)); }
}
