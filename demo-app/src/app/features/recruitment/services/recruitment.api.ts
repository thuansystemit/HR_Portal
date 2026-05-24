import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  Application,
  BoardResponse,
  Feedback,
  Interview,
  JobPosting,
  JobPostingSummary,
  Page,
} from '../models/recruitment.model';

@Injectable({ providedIn: 'root' })
export class RecruitmentApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/recruitment`;

  // ── Job Postings ────────────────────────────────────────────────────────────

  listPostings(status?: string, page = 0, size = 20): Observable<Page<JobPostingSummary>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) params = params.set('status', status);
    return this.http.get<Page<JobPostingSummary>>(`${this.base}/job-postings`, { params });
  }

  getPosting(id: string): Observable<JobPosting> {
    return this.http.get<JobPosting>(`${this.base}/job-postings/${id}`);
  }

  createPosting(body: Partial<JobPosting>): Observable<JobPosting> {
    return this.http.post<JobPosting>(`${this.base}/job-postings`, body);
  }

  updatePosting(id: string, body: Partial<JobPosting>): Observable<JobPosting> {
    return this.http.put<JobPosting>(`${this.base}/job-postings/${id}`, body);
  }

  deletePosting(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/job-postings/${id}`);
  }

  // ── Applications ────────────────────────────────────────────────────────────

  listApplications(jobId: string): Observable<Application[]> {
    return this.http.get<Application[]>(`${this.base}/job-postings/${jobId}/applications`);
  }

  getBoard(jobId: string): Observable<BoardResponse> {
    return this.http.get<BoardResponse>(`${this.base}/job-postings/${jobId}/applications/board`);
  }

  applyCandidate(
    jobId: string,
    body: { cvCandidateId: string; notes?: string },
  ): Observable<Application> {
    return this.http.post<Application>(`${this.base}/job-postings/${jobId}/applications`, body);
  }

  moveStage(
    jobId: string,
    appId: string,
    body: { stage: string; notes?: string },
  ): Observable<Application> {
    return this.http.patch<Application>(
      `${this.base}/job-postings/${jobId}/applications/${appId}/stage`,
      body,
    );
  }

  // ── Interviews ──────────────────────────────────────────────────────────────

  listInterviews(appId: string): Observable<Interview[]> {
    return this.http.get<Interview[]>(`${this.base}/applications/${appId}/interviews`);
  }

  scheduleInterview(
    appId: string,
    body: { scheduledAt: string; meetingLink?: string; notes?: string },
  ): Observable<Interview> {
    return this.http.post<Interview>(`${this.base}/applications/${appId}/interviews`, body);
  }

  submitFeedback(
    interviewId: string,
    body: { rating: number; notes?: string; recommendation: string },
  ): Observable<Feedback> {
    return this.http.post<Feedback>(`${this.base}/interviews/${interviewId}/feedback`, body);
  }
}
