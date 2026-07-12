# AI assistance disclosure

**Built with help from Claude.ai.**

Parts of ACMEmailtrap — application code, the React frontend, tests, documentation,
and this repository's tooling — were produced with the assistance of Anthropic's
Claude (via claude.ai / Claude Code). This note documents that assistance for
transparency and traceability.

## Scope of AI assistance

- Implementation of features (SMTP/IMAP servers, storage, forwarding plugins,
  search, authentication, web UI) from human-authored requirements and design.
- Refactoring, test authoring, documentation, and build/CI configuration.
- Suggestions and drafts that were reviewed before being accepted.

## Human oversight and responsibility

- Every change was reviewed and approved by the project maintainers before it was
  committed, and the project builds and tests must pass in CI.
- The maintainers retain full responsibility and accountability for the code,
  its behaviour, and its release. AI output was treated as assistance, not as an
  authority: it was verified, corrected, and accepted or rejected by humans.
- Requirements, architectural decisions, and the acceptance of results are human
  decisions.

## Relation to ISO/IEC 42001

This disclosure supports the transparency and record-keeping expectations of an AI
management system (ISO/IEC 42001): the use of an AI tool in the software development
lifecycle is documented, human oversight is defined, and accountability remains with
the organisation. It is a factual record of tool usage, not a claim of certification.

## About this directory

The `.claude/` directory holds local development tooling configuration for Claude
Code (for example `launch.json`). It contains no secrets and is not required to build
or run ACMEmailtrap.

---

Maintainer: ACMEsoftware. License: Apache-2.0 (see [../LICENSE](../LICENSE)).
