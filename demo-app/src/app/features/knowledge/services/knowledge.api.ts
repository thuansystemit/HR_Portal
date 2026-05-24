import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { KnowledgeEntity, KnowledgePage } from '../models/knowledge.model';

@Injectable({ providedIn: 'root' })
export class KnowledgeApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/knowledge`;

  search(params: { q?: string; type?: string; page?: number; size?: number }): Observable<KnowledgePage> {
    let httpParams = new HttpParams();
    if (params.q)    httpParams = httpParams.set('q',    params.q);
    if (params.type) httpParams = httpParams.set('type', params.type);
    if (params.page !== undefined) httpParams = httpParams.set('page', String(params.page));
    if (params.size !== undefined) httpParams = httpParams.set('size', String(params.size));
    return this.http.get<KnowledgePage>(`${this.base}/entities`, { params: httpParams });
  }

  getById(id: string): Observable<KnowledgeEntity> {
    return this.http.get<KnowledgeEntity>(`${this.base}/entities/${id}`);
  }
}
