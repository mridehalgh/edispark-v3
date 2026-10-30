# Domain Documentation Standards

Every **bounded context** MUST contain a `DOMAIN.md` file at its module root. This document combines three perspectives—Vision, Overview, and Narrative—into a single authoritative source of domain knowledge.

## Applicability

**Requires DOMAIN.md:** Domain modules with business logic, rules, and behavior.

**Does NOT require DOMAIN.md:** Support libraries, infrastructure modules, generated code, test utilities.

## The Three Perspectives

### Vision
| Purpose | Form | Audience | Best for |
|---------|------|----------|----------|
| Strategic alignment | 2-4 declarative sentences | Team, stakeholders, leadership | North star and decision filter |

Must answer: What is the core domain? Why does it matter? What makes it strategic? What must be protected from dilution?

### Overview
| Purpose | Form | Audience | Best for |
|---------|------|----------|----------|
| Orient the reader | 1-2 paragraph summary | Anyone needing fast context | Front door into deeper docs |

Must cover: What problems it solves. What it does NOT cover (boundaries). How it relates to other domains.

### Narrative
| Purpose | Form | Audience | Best for |
|---------|------|----------|----------|
| Tell the domain story | Prose, examples, scenarios | New devs, domain experts | Onboarding and shared understanding |

Must describe: How the business works over time. Real situations such as "When a customer places an order the system validates availability." Behaviour and rules rather than data structures. Edge cases that matter.

Write it the way domain experts explain their world.

## Document Template

```markdown
# {Domain Name}

## Vision

[2-4 sentences: What is this domain? Why does it matter to the business? 
What makes it strategic? What must be protected from dilution?]

## Overview

[1-2 paragraphs: What problems does this solve? What does it NOT cover? 
How does it relate to other domains?]

## Domain Narrative

[Prose and scenarios: How the business works over time. Real situations.
Behaviour and rules. Edge cases that matter.]

## Ubiquitous Language

| Term | Definition |
|------|------------|
| {Term} | {Business definition} |

## Key Aggregates

| Aggregate | Responsibility |
|-----------|----------------|
| {Name} | {What it manages} |

## Domain Events

| Event | Meaning |
|-------|---------|
| {EventName} | {Business significance} |

## Integration Points

| Direction | Domain | Mechanism |
|-----------|--------|-----------|
| Publishes to | {Domain} | {Event or API} |
| Consumes from | {Domain} | {Event or API} |
```

## Writing Guidelines

Write DOMAIN.md in narrative prose for Vision, Overview and Narrative sections. Use tables for structured reference data such as Ubiquitous Language, Aggregates, Events and Integration Points. Use British English throughout. Avoid em dashes. Do not use trailing commas in lists.

**Vision:** Use business language rather than technical jargon. Answer "why does this exist?" rather than "what does it do?" Keep this section stable as vision rarely changes. Protect it from dilution.

**Overview:** Orient the reader in under two minutes. Be explicit about what is out of scope. Reference other domains by name where relevant. Update when scope changes significantly.

**Narrative:** Write as a domain expert would explain their world to a new team member. Use concrete examples such as "When a warehouse receives a shipment the system validates..." Describe behaviour and rules rather than data structures. Include the business rationale behind rules. Cover the happy path first then address edge cases and exceptions.

**Ubiquitous Language:** Define terms as the business uses them rather than technical definitions. Keep entries alphabetised. This is a living glossary. Add terms as they emerge during implementation.

## Spec Integration

When creating a spec for a new bounded context:

1. **Requirements**: Include `Create DOMAIN.md with vision, overview, and ubiquitous language`
2. **Design**: Draft Vision, Overview, and initial Ubiquitous Language
3. **Tasks**: First task should create `DOMAIN.md`

Domain understanding must be captured BEFORE implementation begins.

## When to Update

- Adding new aggregates or significant entities
- Discovering business rules during implementation
- Clarifying boundaries with other domains
- Onboarding reveals gaps
- Business stakeholders correct understanding

## AI Instructions

**Working with existing domains:**
- Check DOMAIN.md before making changes
- Use ubiquitous language in code, tests, comments
- Respect boundaries in Overview
- Suggest updates when discovering undocumented rules

**Creating specs for new bounded contexts:**
- Identify if it needs DOMAIN.md (has business rules? → yes)
- Include DOMAIN.md in requirements
- Make it the first implementation task
- Do NOT create for support libraries
