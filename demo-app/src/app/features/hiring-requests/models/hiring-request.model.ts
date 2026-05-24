export type RoleType = 'FRONTEND' | 'BACKEND' | 'FULLSTACK';
export type Urgency = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type RequestStatus =
  | 'PENDING'
  | 'IN_PROGRESS'
  | 'CANDIDATE_FOUND'
  | 'HIRED'
  | 'CLOSED'
  | 'REJECTED';

export interface HiringRequest {
  id: string;
  title: string;
  description: string | null;
  roleType: RoleType;
  department: string;
  urgency: Urgency;
  status: RequestStatus;
  requesterName: string;
  jobPostingId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateHiringRequestDto {
  title: string;
  description?: string;
  roleType: RoleType;
  department: string;
  urgency: Urgency;
}
