from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class Party(BaseModel):
    name: str | None = None
    address: str | None = None
    taxId: str | None = None
    email: str | None = None
    phone: str | None = None


class LineItem(BaseModel):
    description: str
    quantity: float | None = None
    unitPrice: float | None = None
    amount: float | None = None
    taxRate: float | None = None


class InvoiceExtraction(BaseModel):
    model_config = ConfigDict(extra="ignore")

    invoiceNumber: str | None = None
    invoiceDate: str | None = None
    dueDate: str | None = None
    currency: str | None = None
    vendor: Party | None = None
    buyer: Party | None = None
    lineItems: list[LineItem] = Field(default_factory=list)
    subtotal: float | None = None
    taxAmount: float | None = None
    total: float | None = None
    notes: str | None = None
    paymentTerms: str | None = None
    confidenceOverall: Literal["HIGH", "MEDIUM", "LOW"] | None = None
    lowConfidenceFields: list[str] = Field(default_factory=list)
    missingFields: list[str] = Field(default_factory=list)
