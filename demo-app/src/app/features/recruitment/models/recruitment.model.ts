export type JobStatus = 'DRAFT' | 'OPEN' | 'CLOSED';
export type AppStage = 'APPLIED' | 'SCREENING' | 'INTERVIEW' | 'OFFER' | 'HIRED' | 'REJECTED';
export type Recommendation = 'PASS' | 'HOLD' | 'REJECT';

export interface JobPostingSummary {
  id: string;
  title: string;
  department: string;
  location: string;
  status: JobStatus;
  deadline: string;
  applicationCount: number;
}

export interface JobPosting extends JobPostingSummary {
  description: string;
  requirements: string;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface Application {
  id: string;
  jobPostingId: string;
  jobTitle: string | null;
  cvCandidateId: string;
  documentCategoryId: string | null;
  candidateFullName: string;
  candidateEmail: string;
  stage: AppStage;
  notes: string;
  appliedAt: string;
  updatedAt: string;
  fitScore: number | null;
}

export interface BoardResponse {
  columns: Record<AppStage, Application[]>;
}

export interface Interview {
  id: string;
  applicationId: string;
  scheduledAt: string;
  meetingLink: string;
  notes: string;
  createdBy: string;
  createdAt: string;
  feedback: Feedback[];
}

export interface Feedback {
  id: string;
  rating: number;
  notes: string;
  recommendation: Recommendation;
  submittedAt: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface JobPostingSkill {
  skillName: string;
  isRequired: boolean;
}
