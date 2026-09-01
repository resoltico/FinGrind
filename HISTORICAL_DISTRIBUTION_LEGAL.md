# Historical Distribution Legal Status

This record covers FinGrind v0.63.0, published on 2026-08-21, and the matching ghcr.io/resoltico/fingrind:0.63.0 / then-current latest image.

Those immutable artifacts predate the corrected legal payload recorded under Unreleased. They do not contain SOURCE_OFFER.md, NOTICE-ZULU-26.32.203, the complete SQLite3MC embedded-code notices, the resolved-JAR legal-resource index, or the container Alpine notices/package lock now required by the source tree. Their bundled Apache and Noto license files also contain the defects described by the subsequent correction. Do not silently replace their bytes beneath the existing tag.

SOURCE_OFFER.md applies to every still-available historical FinGrind artifact, including v0.63.0. A request must identify the release asset or OCI digest so the historical dependency set is supplied rather than the current one.

## v0.63.0 linked Java input

The release bundles and container use Azul Zulu 26.32.13 / OpenJDK 26.0.2+10. Bundle runtime/release records Java 26.0.2 and the linked module closure.

The tagged container source pins:

- x86_64 input archive zulu26.32.13-ca-jdk26.0.2-linux_musl_x64.tar.gz, SHA-256 05f3533b40a244581a55b842d43bfe775f262c91986e85910b164e2582ea4140
- aarch64 input archive zulu26.32.13-ca-jdk26.0.2-linux_musl_aarch64.tar.gz, SHA-256 4c2b1e6fb622da78419a24d3825dc749679a7a316ab7f3972adbea07f46d8f31

The applicable Azul July 2026 licensing information is https://docs.azul.com/core/tpls/july-2026/zulu26_jdk_tpl.pdf. Historical corresponding source must match this build; Zulu 26.32.203 source is not a substitute.

## v0.63.0 container identity

- OCI index digest: sha256:1425382a3adc14fcd7fa86e627f6a41971c2d06c129a7287ff73b2a4cd6699b5
- linux/amd64 manifest: sha256:0cec7ebab1fef302c8c3ec616f6ede54f1116c3a01d14367b97d4fdba26ce09b
- linux/arm64 manifest: sha256:438aa6ac8dc1a0592e82b0bd8df80246e8eb88d878b60e6c3e1720b56f92a8fe
- pinned Alpine 3.24 image index: sha256:a2d49ea686c2adfe3c992e47dc3b5e7fa6e6b5055609400dc2acaeb241c829f4

The image installed libstdc++ from the Alpine 3.24 repositories at build time. Its exact installed package database and BuildKit SBOM/provenance, not the current Alpine lock, own the historical package/source mapping.
The inspected final package inventory is retained at [gradle/historical/v0.63.0-alpine-packages.tsv](gradle/historical/v0.63.0-alpine-packages.tsv).

## Publication action

Publish the correction as a new release, then deprecate the affected historical container tags or add a prominent source-offer/legal-correction pointer on their package and release pages after owner review. Asset immutability does not prohibit a truthful metadata correction or companion source publication, but external release/package changes require an explicit owner decision.

No statement here is an export-control classification, patent clearance, legal opinion, or representation that the historical payload was compliant.
