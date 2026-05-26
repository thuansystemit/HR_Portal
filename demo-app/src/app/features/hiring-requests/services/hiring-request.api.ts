import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { HiringRequest, CreateHiringRequestDto } from '../models/hiring-request.model';

@Injectable({ providedIn: 'root' })
export class HiringRequestApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/hiring-requests`;

  listAll(): Observable<HiringRequest[]> {
    return this.http.get<HiringRequest[]>(this.base);
  }

  listMine(): Observable<HiringRequest[]> {
    return this.http.get<HiringRequest[]>(`${this.base}/my`);
  }

  getById(id: string): Observable<HiringRequest> {
    return this.http.get<HiringRequest>(`${this.base}/${id}`);
  }

  create(body: CreateHiringRequestDto): Observable<HiringRequest> {
    return this.http.post<HiringRequest>(this.base, body);
  }

  updateStatus(id: string, status: string, jobPostingId?: string): Observable<HiringRequest> {
    const body: Record<string, string> = { status };
    if (jobPostingId) body['jobPostingId'] = jobPostingId;
    return this.http.patch<HiringRequest>(`${this.base}/${id}/status`, body);
  }

  linkJobPosting(requestId: string, jobPostingId: string): Observable<HiringRequest> {
    return this.http.patch<HiringRequest>(`${this.base}/${requestId}/link-posting`, null, {
      params: new HttpParams().set('jobPostingId', jobPostingId),
    });
  }
}
