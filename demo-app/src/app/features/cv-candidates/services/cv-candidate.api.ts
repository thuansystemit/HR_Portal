import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { CandidateHiringStatus, CvCandidate } from '../models/cv-candidate.model';
import { CvSearchParams, CvSearchResult, PageResponse } from '../models/cv-search.model';
import { Application } from '../../recruitment/models/recruitment.model';

@Injectable({ providedIn: 'root' })
export class CvCandidateApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/cv-candidates`;

  listByCategory(categoryId: string): Observable<CvCandidate[]> {
    return this.http.get<CvCandidate[]>(`${this.base}/by-category/${categoryId}`);
  }

  getByDocument(documentId: string): Observable<CvCandidate> {
    return this.http.get<CvCandidate>(`${this.base}/by-document/${documentId}`);
  }

  getById(id: string): Observable<CvCandidate> {
    return this.http.get<CvCandidate>(`${this.base}/${id}`);
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  getApplications(id: string): Observable<Application[]> {
    return this.http.get<Application[]>(`${this.base}/${id}/applications`);
  }

  updateHiringStatus(id: string, status: CandidateHiringStatus): Observable<CvCandidate> {
    return this.http.patch<CvCandidate>(`${this.base}/${id}/hiring-status`, { hiringStatus: status });
  }

  search(params: CvSearchParams): Observable<PageResponse<CvSearchResult>> {
    let httpParams = new HttpParams();
    if (params.skills)               httpParams = httpParams.set('skills', params.skills);
    if (params.title)                httpParams = httpParams.set('title', params.title);
    if (params.location)             httpParams = httpParams.set('location', params.location);
    if (params.minYearsExperience != null) httpParams = httpParams.set('minYearsExperience', params.minYearsExperience.toString());
    if (params.keyword)              httpParams = httpParams.set('keyword', params.keyword);
    if (params.page != null)         httpParams = httpParams.set('page', params.page.toString());
    if (params.size != null)         httpParams = httpParams.set('size', params.size.toString());
    if (params.sortBy)               httpParams = httpParams.set('sortBy', params.sortBy);

    return this.http.get<PageResponse<CvSearchResult>>(`${this.base}/search`, { params: httpParams });
  }
}
