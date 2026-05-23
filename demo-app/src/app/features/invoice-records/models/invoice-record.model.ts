export type ConfidenceLevel = 'HIGH' | 'MEDIUM' | 'LOW';

export interface InvoiceParty {
  name: string | null;
  address: string | null;
  taxId: string | null;
  email: string | null;
  phone: string | null;
}

export interface InvoiceLineItem {
  description: string;
  quantity: number | null;
  unitPrice: number | null;
  amount: number | null;
  taxRate: number | null;
}

export interface InvoiceRecord {
  id: string;
  documentId: string;
  documentCategoryId: string;
  invoiceNumber: string | null;
  invoiceDate: string | null;
  dueDate: string | null;
  currency: string | null;
  vendor: InvoiceParty | null;
  buyer: InvoiceParty | null;
  lineItems: InvoiceLineItem[];
  subtotal: number | null;
  taxAmount: number | null;
  total: number | null;
  notes: string | null;
  paymentTerms: string | null;
  confidenceOverall: ConfidenceLevel;
  lowConfidenceFields: string[];
  missingFields: string[];
  extractedAt: string;
  createdAt: string;
}
