export type Impression = 'INTERESTED' | 'NOT_INTERESTED' | 'REVIEW_LATER';

export interface CvShare {
  id:                      string;
  hiringRequestId:         string;
  hiringRequestTitle:      string | null;
  cvCandidateId:           string;
  candidateFullName:       string | null;
  candidateHiringStatus:   string | null;
  sharedBy:            string;
  sharedByName:        string | null;
  sharedWith:          string;
  sharedWithName:      string | null;
  impression:          Impression | null;
  comment:             string | null;
  sharedAt:            string;
  reviewedAt:          string | null;
}

export interface SubmitImpressionDto {
  impression: Impression;
  comment:    string;
}
