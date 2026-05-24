import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { CvShare, SubmitImpressionDto } from '../models/cv-share.model';

@Injectable({ providedIn: 'root' })
export class CvShareApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/cv-shares`;

  inbox(): Observable<CvShare[]> {
    return this.http.get<CvShare[]>(`${this.base}/inbox`);
  }

  getById(shareId: string): Observable<CvShare> {
    return this.http.get<CvShare>(`${this.base}/${shareId}`);
  }

  listByRequest(requestId: string): Observable<CvShare[]> {
    return this.http.get<CvShare[]>(
      `${environment.apiUrl}/hiring-requests/${requestId}/cv-shares`,
    );
  }

  share(requestId: string, body: { cvCandidateId: string; sharedWith: string; comment?: string }): Observable<CvShare> {
    return this.http.post<CvShare>(
      `${environment.apiUrl}/hiring-requests/${requestId}/cv-shares`,
      body,
    );
  }

  submitImpression(requestId: string, shareId: string, dto: SubmitImpressionDto): Observable<CvShare> {
    return this.http.patch<CvShare>(
      `${environment.apiUrl}/hiring-requests/${requestId}/cv-shares/${shareId}/impression`,
      dto,
    );
  }
}
