# Domain Docs

How engineering skills should consume this repo's domain documentation when exploring the codebase.

## Before Exploring

Read `CONTEXT.md` at the repo root before making domain-language decisions. It defines the terms for this single-context repository.

Also read any relevant ADRs under `docs/adr/` if that directory exists and the work touches an architectural decision.

## File Structure

This repo currently follows a single-context layout:

```text
/
├── CONTEXT.md
├── docs/
│   └── adr/
└── src/
```

## Use the Glossary's Vocabulary

When output names a domain concept in an issue title, agent brief, refactor proposal, hypothesis, or test name, use the term as defined in `CONTEXT.md`. Do not drift to synonyms the glossary explicitly avoids.

If the needed concept is not in the glossary yet, surface that as a domain-language gap for a future `/grill-with-docs` session.

## Flag ADR Conflicts

If output contradicts an existing ADR, surface it explicitly instead of overriding it silently.
