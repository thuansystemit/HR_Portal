import { Injectable, inject, signal } from '@angular/core';
import { forkJoin } from 'rxjs';
import { ReportApi } from '../services/report.api';
import {
  CategoryCountEntry, RoleDistributionEntry, StorageEntry, UploadTrendEntry,
} from '../models/report.model';

@Injectable({ providedIn: 'root' })
export class ReportStore {
  private readonly api = inject(ReportApi);

  private readonly _uploadTrend      = signal<UploadTrendEntry[]>([]);
  private readonly _categoryCount    = signal<CategoryCountEntry[]>([]);
  private readonly _storage          = signal<StorageEntry[]>([]);
  private readonly _roleDistribution = signal<RoleDistributionEntry[]>([]);
  private readonly _loading          = signal(false);
  private readonly _error            = signal<string | null>(null);

  readonly uploadTrend      = this._uploadTrend.asReadonly();
  readonly categoryCount    = this._categoryCount.asReadonly();
  readonly storage          = this._storage.asReadonly();
  readonly roleDistribution = this._roleDistribution.asReadonly();
  readonly loading          = this._loading.asReadonly();
  readonly error            = this._error.asReadonly();

  loadAll(): void {
    this._loading.set(true);
    this._error.set(null);
    forkJoin({
      uploadTrend:      this.api.uploadTrend(),
      categoryCount:    this.api.categoryCount(),
      storage:          this.api.storage(),
      roleDistribution: this.api.roleDistribution(),
    }).subscribe({
      next: d => {
        this._uploadTrend.set(d.uploadTrend);
        this._categoryCount.set(d.categoryCount);
        this._storage.set(d.storage);
        this._roleDistribution.set(d.roleDistribution);
        this._loading.set(false);
      },
      error: err => { this._error.set(err.message); this._loading.set(false); },
    });
  }
}
