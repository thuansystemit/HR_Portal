from __future__ import annotations

from pydantic import BaseModel, Field


class TechEntity(BaseModel):
    name: str
    version: str | None = None
    category: str | None = None
    aliases: list[str] = Field(default_factory=list)


class ConceptEntity(BaseModel):
    name: str
    definition: str | None = None
    relatedConcepts: list[str] = Field(default_factory=list)


class Relationship(BaseModel):
    source: str
    target: str
    relationType: str
    weight: float | None = None


class KnowledgeExtraction(BaseModel):
    title: str | None = None
    summary: str | None = None
    technologies: list[TechEntity] = Field(default_factory=list)
    concepts: list[ConceptEntity] = Field(default_factory=list)
    relationships: list[Relationship] = Field(default_factory=list)
