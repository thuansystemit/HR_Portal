import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  CategoryCountEntry, RoleDistributionEntry, StorageEntry, UploadTrendEntry,
} from '../models/report.model';

@Injectable({ providedIn: 'root' })
export class ReportApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/reports`;

  uploadTrend():      Observable<UploadTrendEntry[]>      { return this.http.get<UploadTrendEntry[]>(`${this.base}/upload-trend`); }
  categoryCount():    Observable<CategoryCountEntry[]>    { return this.http.get<CategoryCountEntry[]>(`${this.base}/category-count`); }
  storage():          Observable<StorageEntry[]>          { return this.http.get<StorageEntry[]>(`${this.base}/storage`); }
  roleDistribution(): Observable<RoleDistributionEntry[]> { return this.http.get<RoleDistributionEntry[]>(`${this.base}/role-distribution`); }
}
