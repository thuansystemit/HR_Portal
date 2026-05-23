from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class WorkExperience(BaseModel):
    company: str | None = None
    title: str | None = None
    startDate: str | None = None
    startDatePrecision: Literal["YEAR", "MONTH"] | None = None
    endDate: str | None = None
    isCurrent: bool = False
    location: str | None = None
    isRemote: bool | None = None
    responsibilities: list[str] = Field(default_factory=list)
    achievements: list[str] = Field(default_factory=list)
    technologies: list[str] = Field(default_factory=list)


class Education(BaseModel):
    institution: str | None = None
    degree: str | None = None
    fieldOfStudy: str | None = None
    startYear: int | None = None
    endYear: int | None = None
    gpa: float | None = None
    honors: list[str] | str | None = None


class Language(BaseModel):
    language: str | None = None
    proficiency: str | None = None


class Certification(BaseModel):
    name: str | None = None
    issuer: str | None = None
    issuedDate: str | None = None
    expiryDate: str | None = None
    credentialId: str | None = None


class Project(BaseModel):
    name: str | None = None
    description: str | None = None
    technologies: list[str] = Field(default_factory=list)
    url: str | None = None


class Publication(BaseModel):
    title: str | None = None
    journal: str | None = None
    year: int | None = None
    url: str | None = None


class CvExtraction(BaseModel):
    model_config = ConfigDict(extra="ignore")

    fullName: str | None = None
    email: str | None = None
    phone: str | None = None
    city: str | None = None
    country: str | None = None
    linkedinUrl: str | None = None
    githubUrl: str | None = None
    portfolioUrl: str | None = None
    summary: str | None = None
    toolsAndFrameworks: list[str] = Field(default_factory=list)
    softSkills: list[str] = Field(default_factory=list)
    technicalSkills: list[str] = Field(default_factory=list)
    projects: list[Project] = Field(default_factory=list)
    publications: list[Publication] = Field(default_factory=list)
    workExperiences: list[WorkExperience] = Field(default_factory=list)
    educations: list[Education] = Field(default_factory=list)
    languages: list[Language] = Field(default_factory=list)
    certifications: list[Certification] = Field(default_factory=list)
    rawLanguage: str | None = None
    confidenceOverall: Literal["HIGH", "MEDIUM", "LOW"] | None = None
    lowConfidenceFields: list[str] = Field(default_factory=list)
    missingFields: list[str] = Field(default_factory=list)
