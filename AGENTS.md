## Project Context 🏢

### EdiSpark — Modern EDI SaaS for SMB to Enterprise

**What We Are**

EdiSpark is a modern, multi-tenant EDI SaaS product serving customers from SMB to Enterprise. It is built as part of a composable architecture, meaning each capability is exposed as an independent, API-first service that customers and partners can integrate into their own technology stacks. There is no monolithic integration platform — instead, bounded domains (parsing, validation, routing, document management, partner management) are independently deployable and composable.

The product targets businesses that exchange EDI documents with their trading partners and need a reliable, observable, and low-friction way to do so — without the complexity and cost of traditional EDI middleware.

**Product Positioning**

- **Target market**: SMB to Enterprise customers who send and receive EDI with retailers, suppliers, logistics providers, and other trading partners
- **Delivery model**: Multi-tenant SaaS with tenant-level data isolation, encryption, and configuration
- **Architecture style**: Composable, API-first, event-driven — customers integrate EdiSpark capabilities into their own workflows rather than adopting a closed platform
- **Deployment**: Cloud-native on AWS, serverless-first, with a control plane for tenant management and an application plane for business processing

**Problem Statement**

Trading partners exchange EDI messages (TRADACOMS and EDIFACT) that arrive as single or batched files. EdiSpark provides:

- **Inbound**: receive, validate, parse, split by transaction, enrich, route to downstream apps, and persist acknowledgments/errors
- **Outbound**: assemble transactions from internal data, build valid EDI (incl. envelopes/batches), validate, and deliver
- **Batches**: detect, split and/or (re)group transactions; support extraction of individual documents; maintain correlation to the original batch
- **Standards**: support TRADACOMS (e.g., ORDERS, INVOIC) and EDIFACT (e.g., ORDERS, INVOIC/INVOIC D96A+)
- **Observability & Control**: track everything with idempotency, retry, and audit trails
- **Composability**: all capabilities exposed via APIs so customers can embed EDI processing into their own systems

**Personas**

- **Integration Analyst (IA)**: configures trading partner profiles, maps, and routing rules within EdiSpark
- **Operations Engineer (OE)**: monitors flows, reprocesses failures, manages retries and SLA compliance
- **ERP/AP/AR User (ERP)**: consumes clean, structured orders and invoices delivered by EdiSpark into their ERP
- **Trading Partner (TP)**: sends and receives compliant EDI and expects timely acknowledgements
- **Security/Compliance (SC)**: needs audit trails, PII controls, data residency, and retention policies
- **Platform Developer (PD)**: integrates EdiSpark APIs and webhooks into customer-built applications and workflows

**Key Entities (Conceptual)**

- **Interchange/Envelope** (UNB/UNZ; TRADACOMS STX/END): sender, receiver, control refs, test/live
- **Functional Group** (UNG/UNE; TRADACOMS GNH/GNT)
- **Message/Transaction**: ORDERS, INVOIC etc. (UNH/UNT; TRADACOMS message blocks)
- **Batch**: a file containing many messages
- **Tenant**: an EdiSpark customer with isolated data, configuration, and encryption keys
- **Trading Partner Profile**: configuration for a specific sender or receiver, including EDI standard, version, and routing rules

## Response Guidelines 🎯

- When responding, use emojis and markdown formatting to improve readability ✨

## Code Style Guidelines 💻

- Follow established coding standards and style guides

## React Component Library Guidelines ⚛️

- Design components for external third-party use with customizable branding
- Implement accessibility-first approach with proper ARIA attributes
- Use TailwindCSS for styling with customizable design tokens
- Provide sensible and attractive defaults while allowing full customization
- Follow modern React/TypeScript patterns and best practices
- Ensure components are reusable across different brand requirements

## Security Best Practices 🔒

- Always prioritize security in all recommendations and implementations
- Validate and sanitize all inputs to prevent injection attacks
- Use parameterized queries for database operations
- Implement proper authentication and authorization mechanisms
- Follow principle of least privilege for access control
- Encrypt sensitive data at rest and in transit
- Use secure communication protocols (HTTPS, TLS)
- Implement proper session management and timeout policies
- Log security events for monitoring and auditing
- Keep dependencies updated to patch security vulnerabilities
- Use environment variables for sensitive configuration
- Never hardcode credentials, API keys, or secrets in code
- Implement rate limiting and throttling for APIs
- Use secure random number generation for tokens and IDs
- Validate file uploads and restrict file types
- Implement proper error handling without exposing sensitive information

## PII and Data Protection 🛡️

- Substitute Personally Identifiable Information (PII) with generic placeholders:
  - Use `[name]` instead of actual names
  - Use `[email]` instead of real email addresses
  - Use `[phone_number]` instead of actual phone numbers
  - Use `[address]` instead of real addresses
  - Use `[ssn]` instead of social security numbers
  - Use `[credit_card]` instead of actual card numbers
- Never include real PII in code examples, documentation, or test data
- Implement data masking and anonymization for non-production environments
- Follow data retention policies and implement secure data deletion
- Ensure compliance with privacy regulations (GDPR, CCPA, etc.)
- Use data classification to handle different sensitivity levels appropriately

## Development Principles 🏗️

- Apply SOLID principles (Single Responsibility, Open/Closed, Liskov Substitution, Interface Segregation, Dependency Inversion)
- Favor composition over inheritance
- Write clean, readable, and maintainable code
- Use meaningful names for classes, methods, and variables
- Keep methods small and focused on a single task (ideally 4-10 lines)
- Minimize dependencies and coupling between components
- Design for testability and mockability
- Follow DRY (Don't Repeat Yourself) principle
- Apply defensive programming practices
- Fail fast - validate inputs early and throw meaningful exceptions
- Use immutable objects where possible
- Prefer explicit over implicit behavior
- Write self-documenting code that explains the 'why', not just the 'what'
- Handle edge cases and null values appropriately
- Use appropriate data structures for the problem domain
- Optimize for readability first, performance second
- Follow the principle of least surprise
- Encapsulate complexity behind simple interfaces
- Use constants instead of magic numbers
- Prefer early returns to reduce nesting

## Testing Principles 🧪

- Test units of behavior, not individual classes or methods
- Focus on testing what the code does, not how it does it
- Write tests that describe the expected behavior from a user's perspective
- Test the public interface and observable outcomes
- Use descriptive test names that explain the scenario and expected result
- Arrange-Act-Assert pattern for clear test structure
