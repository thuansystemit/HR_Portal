import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { CvCandidate } from '../models/cv-candidate.model';

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
}
