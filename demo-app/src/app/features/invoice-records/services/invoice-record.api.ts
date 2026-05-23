import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { InvoiceRecord } from '../models/invoice-record.model';

@Injectable({ providedIn: 'root' })
export class InvoiceRecordApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/invoice-records`;

  listByCategory(categoryId: string): Observable<InvoiceRecord[]> {
    return this.http.get<InvoiceRecord[]>(`${this.base}/by-category/${categoryId}`);
  }

  getByDocument(documentId: string): Observable<InvoiceRecord> {
    return this.http.get<InvoiceRecord>(`${this.base}/by-document/${documentId}`);
  }

  getById(id: string): Observable<InvoiceRecord> {
    return this.http.get<InvoiceRecord>(`${this.base}/${id}`);
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
