# Changelog

Notable changes to this project are documented in this file. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] - 2026-04-09

### Added
- Initial release.

### Changed
- GitHub CI now mirrors GridGrind’s lighter release stance and runs only `Check` plus `Docker
  smoke`; deterministic Jazzer support tests and committed-seed regression replay remain enforced
  locally through `./check.sh`.

### Fixed
- The standalone Jazzer verification build now owns its support-test pulse listener directly in
  `jazzer/build.gradle.kts`, so clean clones and GitHub CI no longer depend on ignored nested
  `buildSrc` artifacts to run `jazzerRegression`.

[Unreleased]: https://github.com/resoltico/FinGrind/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.1.0
