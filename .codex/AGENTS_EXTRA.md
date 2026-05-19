# FinGrind Directives

## Agent guide synchronization

`AGENTS_EXTRA.md` is part of the project’s operational contract. It must describe the project’s actual current reality, or the explicitly intended future reality being implemented in the same change set.

If an agent discovers that `AGENTS_EXTRA.md` disagrees with the implemented code, database schema, build wiring, tests, documentation, or accepted architectural direction, the guide must not be left stale. The agent must either:

1. refactor the project to restore agreement with the guide, if the guide is still the intended contract; or
2. refactor `AGENTS_EXTRA.md` to match the new project reality, if the implementation is being deliberately changed.

Do not preserve obsolete instructions for backward compatibility. A hard refactor is incomplete unless the code, schema, automation, tests, documentation, and agent-facing guidance all describe the same system.

When `AGENTS_EXTRA.md` is updated, remove superseded protocol language instead of layering exceptions on top of it. The file must remain a concise source of current project truth, not a historical record of abandoned implementation models.

## Preflight

Do not read `AGENTS_KOTLIN24_GRADLE.md`. That file is not applicable to FinGrind project work. FinGrind is a Java project. Kotlin is used only as part of Gradle build tooling. Do not treat Kotlin as an application language, implementation target, or architectural basis for this project.

---
