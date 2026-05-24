import { Injectable, inject, signal } from '@angular/core';
import { RecruitmentApi } from '../services/recruitment.api';
import { BoardResponse, JobPosting, JobPostingSummary } from '../models/recruitment.model';

@Injectable({ providedIn: 'root' })
export class RecruitmentStore {
  private readonly api = inject(RecruitmentApi);

  // -- State signals --
  private readonly _postings        = signal<JobPostingSummary[]>([]);
  private readonly _currentPosting  = signal<JobPosting | null>(null);
  private readonly _board           = signal<BoardResponse | null>(null);
  private readonly _loading         = signal(false);
  private readonly _error           = signal<string | null>(null);

  // -- Public readonly signals --
  readonly postings       = this._postings.asReadonly();
  readonly currentPosting = this._currentPosting.asReadonly();
  readonly board          = this._board.asReadonly();
  readonly loading        = this._loading.asReadonly();
  readonly error          = this._error.asReadonly();

  loadPostings(status?: string): void {
    this._loading.set(true);
    this._error.set(null);
    this.api.listPostings(status).subscribe({
      next: page => {
        this._postings.set(page.content);
        this._loading.set(false);
      },
      error: err => {
        this._error.set(err.error?.message || err.message || 'Failed to load job postings');
        this._loading.set(false);
      },
    });
  }

  loadPosting(id: string): void {
    this._loading.set(true);
    this._error.set(null);
    this.api.getPosting(id).subscribe({
      next: posting => {
        this._currentPosting.set(posting);
        this._loading.set(false);
      },
      error: err => {
        this._error.set(err.error?.message || err.message || 'Failed to load job posting');
        this._loading.set(false);
      },
    });
  }

  loadBoard(jobId: string): void {
    this._loading.set(true);
    this._error.set(null);
    this.api.getBoard(jobId).subscribe({
      next: board => {
        this._board.set(board);
        this._loading.set(false);
      },
      error: err => {
        this._error.set(err.error?.message || err.message || 'Failed to load board');
        this._loading.set(false);
      },
    });
  }

  createPosting(body: Partial<JobPosting>): void {
    this._loading.set(true);
    this._error.set(null);
    this.api.createPosting(body).subscribe({
      next: () => {
        this._loading.set(false);
        this.loadPostings();
      },
      error: err => {
        this._error.set(err.error?.message || err.message || 'Failed to create job posting');
        this._loading.set(false);
      },
    });
  }

  updatePosting(id: string, body: Partial<JobPosting>): void {
    this._loading.set(true);
    this._error.set(null);
    this.api.updatePosting(id, body).subscribe({
      next: posting => {
        this._currentPosting.set(posting);
        this._loading.set(false);
      },
      error: err => {
        this._error.set(err.error?.message || err.message || 'Failed to update job posting');
        this._loading.set(false);
      },
    });
  }

  deletePosting(id: string): void {
    this.api.deletePosting(id).subscribe({
      next: () => this.loadPostings(),
      error: err => this._error.set(err.error?.message || err.message || 'Failed to delete posting'),
    });
  }

  clearCurrentPosting(): void {
    this._currentPosting.set(null);
  }

  clearError(): void {
    this._error.set(null);
  }
}
