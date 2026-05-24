/**
 * Query parameters for the CV candidate search API.
 */
export interface CvSearchParams {
  skills?: string;
  title?: string;
  location?: string;
  minYearsExperience?: number;
  keyword?: string;
  page?: number;
  size?: number;
  sortBy?: 'relevanceScore' | 'fullName' | 'experienceYears';
}

/**
 * Single result row from the search endpoint.
 */
export interface CvSearchResult {
  candidateId: string;
  fullName: string;
  email: string | null;
  city: string | null;
  country: string | null;
  currentTitle: string | null;
  totalExperienceYears: number;
  topSkills: string[];
  relevanceScore: number;
  documentId: string;
  documentCategoryId: string;
}

/**
 * Spring Boot Page<T> response shape.
 */
export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}
