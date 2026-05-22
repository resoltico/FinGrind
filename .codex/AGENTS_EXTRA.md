# FinGrind Directives

## Synchronization rule

`AGENTS_EXTRA.md` is part of the repository's operational contract. It must describe either:

1. the system that exists now; or
2. the system being intentionally created in the same change set.

If the file disagrees with code, schema, build wiring, tests, runtime behavior, public docs, or
accepted architecture, do not leave it stale. Either refactor the system to match the file or
refactor the file to match the system. Remove superseded language instead of layering exceptions.

## Preflight

Do not read `AGENTS_KOTLIN24_GRADLE.md` for FinGrind application work. FinGrind is a Java
application. Kotlin in this repository is Gradle build tooling, not an application-language or
domain-modeling authority.

## Truth-ownership doctrine

Use this ownership model when judging architecture, naming, storage, and public contracts:

1. Economic reality and retained evidence own the truth of what happened.
2. FinGrind owns accounting meaning. It decides what commands mean, which requests are admissible,
   which invariants hold, which postings are derived, and which reports are legitimate.
3. The protected book owns the durable accepted accounting facts for one accounting entity.
4. SQLite owns persistence mechanics for the protected book: file format, locking, atomicity,
   encryption, recovery, and low-level integrity enforcement.
5. CLI, bundle, container, and future transport surfaces own interaction and projection only. They
   do not own bookkeeping doctrine.

Corollaries:

1. SQLite must not become the hidden owner of accounting meaning. Triggers and constraints may
   defend durability boundaries, but they do not replace the owning domain model.
2. FinGrind is not merely an interaction shell. If a rule expresses accounting meaning, it belongs
   in FinGrind's domain language before it reaches adapters.
3. Public DTOs are not the working domain model. Translate at boundaries.
4. Adjacent future domains such as source evidence, approvals, tax, FX, subledgers, disclosures,
   and group accounting must own their own commands, state, storage, and tests before they are
   published as real seams.

## Best-in-class accounting target doctrine

FinGrind is not finished when the bookkeeping kernel is elegant. A best-in-class accounting product line
for this repository means:

1. the current bookkeeping kernel remains exact, durable, and policy-owned;
2. source-document and approval evidence are first-class;
3. typed business-event commands become the primary write surface above raw journal mechanics;
4. cash-flow, disclosure, tax, and FX foundations become first-class adjacent contexts;
5. operator and agent surfaces stay truthful about what exists and what does not.

Do not counterfeit completion by publishing fake seams, abstract extension points with no owned
state, or docs that imply richer accounting coverage than the code actually implements.

## Hard-break posture

FinGrind prefers clean breaks over compatibility scaffolding.

1. Do not add migration shims, transitional APIs, legacy aliases, compatibility modes, or
   duplicate public vocabularies unless the user explicitly demands them and the repository proves
   a real consumer needs them.
2. When one better model is known, replace the weaker one decisively across code, schema, tests,
   docs, examples, and release surfaces.
3. When a public contract is below the intended doctrine, the fix is to hard-refactor the owning
   contract, not to preserve the obsolete one indefinitely.
