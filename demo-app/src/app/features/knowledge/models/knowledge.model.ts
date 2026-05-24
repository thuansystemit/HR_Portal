export type EntityType = 'Technology' | 'Concept' | 'Person' | 'Project' | 'Team';

export interface KnowledgeEntitySummary {
  id: string;
  documentId: string;
  entityType: EntityType;
  name: string;
  aliases: string[];
  createdAt: string;
}

export interface RelationshipSummary {
  id: string;
  otherEntityId: string;
  otherEntityName: string;
  direction: 'OUTGOING' | 'INCOMING';
  relationType: string;
  weight: number;
}

export interface SourceSummary {
  documentId: string;
  excerpt: string | null;
  pageNumber: number | null;
}

export interface KnowledgeEntity extends KnowledgeEntitySummary {
  properties: Record<string, unknown> | null;
  relationships: RelationshipSummary[];
  sources: SourceSummary[];
}

export interface KnowledgePage {
  content: KnowledgeEntitySummary[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
