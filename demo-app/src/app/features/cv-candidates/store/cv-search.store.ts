import { Injectable, inject, signal, computed } from '@angular/core';
import { CvCandidateApi } from '../services/cv-candidate.api';
import { CvSearchParams, CvSearchResult } from '../models/cv-search.model';

@Injectable({ providedIn: 'root' })
export class CvSearchStore {
  private readonly api = inject(CvCandidateApi);

  // -- State signals --
  private readonly _results       = signal<CvSearchResult[]>([]);
  private readonly _loading       = signal(false);
  private readonly _error         = signal<string | null>(null);
  private readonly _totalElements = signal(0);
  private readonly _totalPages    = signal(0);
  private readonly _currentPage   = signal(0);
  private readonly _pageSize      = signal(20);
  private readonly _searchParams  = signal<CvSearchParams>({});

  // -- Public readonly signals --
  readonly results       = this._results.asReadonly();
  readonly loading       = this._loading.asReadonly();
  readonly error         = this._error.asReadonly();
  readonly totalElements = this._totalElements.asReadonly();
  readonly totalPages    = this._totalPages.asReadonly();
  readonly currentPage   = this._currentPage.asReadonly();
  readonly pageSize      = this._pageSize.asReadonly();
  readonly searchParams  = this._searchParams.asReadonly();

  readonly hasResults = computed(() => this._results().length > 0);
  readonly isEmpty    = computed(() => !this._loading() && this._results().length === 0);

  /**
   * Execute a search with the given parameters.
   * Resets to page 0 unless page is explicitly provided.
   */
  search(params: CvSearchParams): void {
    const merged: CvSearchParams = {
      ...params,
      page: params.page ?? 0,
      size: params.size ?? this._pageSize(),
    };
    this._searchParams.set(merged);
    this._loading.set(true);
    this._error.set(null);

    this.api.search(merged).subscribe({
      next: page => {
        this._results.set(page.content);
        this._totalElements.set(page.totalElements);
        this._totalPages.set(page.totalPages);
        this._currentPage.set(page.number);
        this._pageSize.set(page.size);
        this._loading.set(false);
      },
      error: err => {
        this._error.set(err.error?.message || err.message || 'Search failed');
        this._loading.set(false);
      },
    });
  }

  /**
   * Navigate to a specific page using the current search parameters.
   */
  goToPage(page: number): void {
    const current = this._searchParams();
    this.search({ ...current, page });
  }

  /**
   * Clear all search state.
   */
  clear(): void {
    this._results.set([]);
    this._totalElements.set(0);
    this._totalPages.set(0);
    this._currentPage.set(0);
    this._error.set(null);
    this._searchParams.set({});
  }
}
