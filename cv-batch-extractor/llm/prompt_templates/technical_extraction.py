from __future__ import annotations

_PROMPT = """You are an expert technical knowledge extraction system. Your task is to extract structured knowledge entities and relationships from technical documents (architecture docs, design specs, RFCs, technical reports, etc.).

Analyse the document text below and extract:
- **Technologies**: programming languages, frameworks, libraries, databases, tools, protocols, cloud services
- **Concepts**: abstract ideas, design patterns, architectural concepts, domain terms, methodologies
- **Relationships**: directional connections between any two entities (depends_on, uses, implements, extends, replaces, part_of, related_to, defined_by, etc.)

Return ONLY a valid JSON object — no markdown, no commentary, no code fences.

Schema:
{
  "title": "<inferred document title or null>",
  "summary": "<1-2 sentence summary of what this document describes>",
  "technologies": [
    {
      "name": "<exact technology name>",
      "version": "<version string or null>",
      "category": "<one of: language, framework, library, database, tool, protocol, cloud_service, platform, other>",
      "aliases": ["<alternative names>"]
    }
  ],
  "concepts": [
    {
      "name": "<concept name>",
      "definition": "<concise 1-sentence definition>",
      "relatedConcepts": ["<other concept names mentioned in context>"]
    }
  ],
  "relationships": [
    {
      "source": "<entity name>",
      "target": "<entity name>",
      "relationType": "<depends_on|uses|implements|extends|replaces|part_of|related_to|defined_by|calls|stores_in|deployed_on|monitored_by|authenticated_by>",
      "weight": <0.0-1.0 confidence, default 1.0>
    }
  ]
}

Rules:
- Only include entities that are explicitly mentioned in the text
- Technology names must be exact (e.g. "PostgreSQL" not "postgres", "Spring Boot" not "spring")
- Every relationship source and target must appear as a technology or concept name
- If a field is unknown, use null for strings and [] for arrays
- Do not fabricate entities not present in the text

DOCUMENT TEXT:
{{TEXT}}"""


def build(text: str) -> str:
    return _PROMPT.replace("{{TEXT}}", text)
