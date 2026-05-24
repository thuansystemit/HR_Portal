export type ConfidenceLevel = 'HIGH' | 'MEDIUM' | 'LOW';

export type CandidateHiringStatus =
  | 'AVAILABLE'
  | 'IN_PROCESS'
  | 'OFFERED'
  | 'HIRED'
  | 'REJECTED'
  | 'WITHDRAWN';

export interface CvWorkExperience {
  id: string;
  sortOrder: number;
  company: string;
  title: string;
  startDate: string | null;
  startDatePrecision: string | null;
  endDate: string | null;
  isCurrent: boolean;
  location: string | null;
  isRemote: boolean | null;
  responsibilities: string[];
  achievements: string[];
  technologies: string[];
}

export interface CvEducation {
  id: string;
  sortOrder: number;
  institution: string;
  degree: string;
  fieldOfStudy: string | null;
  startYear: number | null;
  endYear: number | null;
  gpa: number | null;
  honors: string | null;
}

export interface CvLanguage {
  id: string;
  language: string;
  proficiency: string;
}

export interface CvCertification {
  id: string;
  name: string;
  issuer: string | null;
  issuedDate: string | null;
  expiryDate: string | null;
  credentialId: string | null;
}

export interface CvProject {
  name: string;
  description: string | null;
  technologies: string[];
  url: string | null;
}

export interface CvPublication {
  title: string;
  journal: string | null;
  year: number | null;
  url: string | null;
}

export interface CvCandidate {
  id: string;
  documentId: string;
  documentCategoryId: string;
  fullName: string;
  email: string | null;
  phone: string | null;
  city: string | null;
  country: string | null;
  linkedinUrl: string | null;
  githubUrl: string | null;
  portfolioUrl: string | null;
  summary: string | null;
  toolsAndFrameworks: string[];
  softSkills: string[];
  projects: CvProject[];
  publications: CvPublication[];
  confidenceOverall: ConfidenceLevel;
  lowConfidenceFields: string[];
  missingFields: string[];
  rawLanguage: string | null;
  extractedAt: string;
  createdAt: string;
  hiringStatus: CandidateHiringStatus;
  workExperiences: CvWorkExperience[];
  educations: CvEducation[];
  technicalSkills: string[];
  languages: CvLanguage[];
  certifications: CvCertification[];
}
