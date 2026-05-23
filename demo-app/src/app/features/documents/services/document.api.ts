import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { AppDocument, AppDocumentDto, ExtractionStatus } from '../models/document.model';
import { environment } from '../../../../environments/environment';

interface BackendDoc {
  id:               string;
  categoryId:       string;
  name:             string;
  mimeType:         string;
  sizeBytes:        number;
  uploadedBy:       string;
  uploadedByName:   string;
  uploadedAt:       string;
  extractionStatus: ExtractionStatus | null;
}

function mapDoc(b: BackendDoc): AppDocument {
  return {
    id:               b.id,
    categoryId:       b.categoryId,
    name:             b.name,
    mimeType:         b.mimeType,
    fileSize:         b.sizeBytes,
    uploadedBy:       b.uploadedBy,
    uploadedByName:   b.uploadedByName ?? b.uploadedBy,
    createdAt:        b.uploadedAt,
    extractionStatus: b.extractionStatus ?? null,
  };
}

@Injectable({ providedIn: 'root' })
export class DocumentApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/categories`;

  getByCategory(categoryId: string): Observable<AppDocument[]> {
    return this.http
      .get<{ content: BackendDoc[] }>(`${this.base}/${categoryId}/documents`)
      .pipe(map(res => res.content.map(mapDoc)));
  }

  create(dto: AppDocumentDto): Observable<AppDocument> {
    const form = new FormData();
    form.append('file', dto.file, dto.file.name);
    return this.http
      .post<BackendDoc>(`${this.base}/${dto.categoryId}/documents`, form)
      .pipe(map(mapDoc));
  }

  download(doc: AppDocument): void {
    window.open(
      `${this.base}/${doc.categoryId}/documents/${doc.id}/download`,
      '_blank',
    );
  }

  remove(categoryId: string, id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${categoryId}/documents/${id}`);
  }
}
