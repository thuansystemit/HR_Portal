export type DocumentType = 'CV' | 'INVOICE';

export const DOCUMENT_TYPE_OPTIONS: { value: DocumentType; label: string }[] = [
  { value: 'CV',      label: 'CV'      },
  { value: 'INVOICE', label: 'Invoice' },
];

export interface CategoryRolePermission {
  roleId:    string;
  roleName:  string;
  canView:   boolean;
  canUpload: boolean;
  canDelete: boolean;
}

export interface DocumentCategory {
  id:            string;
  name:          string;
  description:   string;
  documentType:  DocumentType;
  permissions:   CategoryRolePermission[];
  documentCount: number;
  createdAt:     string;
  updatedAt:     string;
  llmExtraction: boolean;
}

export interface DocumentCategoryDto {
  name:          string;
  description:   string;
  documentType:  DocumentType;
  permissions:   CategoryRolePermission[];
  llmExtraction: boolean;
}

export type ExtractionStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

export interface AppDocument {
  id:               string;
  categoryId:       string;
  name:             string;
  mimeType:         string;
  fileSize:         number;   // sizeBytes
  uploadedBy:       string;   // UUID
  uploadedByName:   string;
  createdAt:        string;   // uploadedAt
  extractionStatus: ExtractionStatus | null;
}

export interface AppDocumentDto {
  name:       string;
  categoryId: string;
  file:       File;
}

export const CATEGORY_PERM_COLS: { key: keyof CategoryRolePermission; label: string }[] = [
  { key: 'canView',   label: 'View'   },
  { key: 'canUpload', label: 'Upload' },
  { key: 'canDelete', label: 'Delete' },
];
