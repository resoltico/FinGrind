---
afad: "3.5"
version: "0.12.0"
domain: DEVELOPER_JAVA
updated: "2026-04-13"
route:
  keywords: [fingrind, java26, gradle-wrapper, global-gradle, brew, openjdk.org, workstation, shell, java-home, macos]
  questions: ["what is the best-practice java and gradle setup for fingrind", "why should fingrind use ./gradlew instead of brew gradle", "how do i configure a fresh macos machine for java 26 and the gradle wrapper", "when is a global gradle install acceptable", "why is shell-level java still required for fingrind"]
---

# Java 26 And Gradle Workstation Setup

**Purpose**: Document the best-practice macOS workstation setup for FinGrind contributors, including how Java and Gradle should be sourced and why.
**Prerequisites**: macOS with zsh.

Supported workstation shape:
- the repository lives on the Mac's local filesystem
- external, removable, or network-mounted volumes are outside the documented FinGrind setup standard
  because full Gradle and JaCoCo verification require file-locking semantics that mounted volumes
  can fail on macOS

## Overview

FinGrind targets Java 26 and uses the repository Gradle wrapper pinned in
[gradle/wrapper/gradle-wrapper.properties](../gradle/wrapper/gradle-wrapper.properties).

The supported setup is intentionally simple:
- the machine provides Java 26
- the repository checkout lives on the local Mac filesystem
- the repository provides Gradle through `./gradlew`
- no global `gradle` install is required for FinGrind
- no Homebrew-managed JDK is part of the supported FinGrind setup

For wrapped Gradle projects, this is the best-practice stance:
- Java is machine-level state
- Gradle versioning is project-level state
- the wrapper is the authoritative Gradle entrypoint

## Canonical Stance

| Component | Best-practice source | Why |
|:----------|:---------------------|:----|
| JDK | official OpenJDK 26 build from [openjdk.org](https://openjdk.org) | upstream project source of truth for Java 26 status and the published macOS AArch64 binary |
| Gradle | repository `./gradlew` | pinned per project, downloads the official Gradle distribution declared by the repo |
| Global `gradle` | absent by default | avoids a second moving part and avoids Homebrew `openjdk` dependency churn |
| Brand-new Gradle repo bootstrap | temporary Gradle install only to generate and commit wrapper files | the wrapper becomes canonical immediately afterward |

FinGrind contributors should therefore treat:
- `./gradlew` as the only supported Gradle entrypoint in this repo
- a missing global `gradle` command as healthy
- Brew `gradle` as outside the supported setup
- building Gradle from source as unrelated to normal workstation setup

## Why This Is The Best Practice

Reasons:
- the wrapper pins FinGrind's actual Gradle version and downloads the official Gradle distribution
  declared by the repository
- `java -jar` and the packaged CLI use the ambient shell `java`, not Gradle toolchains
- FinGrind's SQLite adapter relies on Java 26 FFM, so shell Java and Gradle-launched Java must
  both meet the same baseline
- CI already runs on Java 26, so local shells should match that contract
- `https://openjdk.org` is the upstream project source of truth for Java 26 release status
- the Java 26 macOS AArch64 binary must be selected from the OpenJDK links published from
  `https://openjdk.org`, not from a third-party redistribution
- avoiding Brew `gradle` avoids Homebrew's `openjdk` dependency from silently becoming part of the
  repo's Java story again
- building Gradle from source adds bootstrap cost, maintenance burden, and version ambiguity without
  improving reproducibility for a wrapped application repo

## Current Baseline

Java 26 is GA. The OpenJDK JDK 26 project page lists `General Availability` on `2026-03-17`.

For FinGrind, the source of truth for Java 26 information and the place from which the binary
download must be chosen is explicitly `https://openjdk.org`.

Authoritative route:
- start at [`https://openjdk.org/projects/jdk/26/`](https://openjdk.org/projects/jdk/26/)
- use the JDK 26 page there as the authoritative release-status page
- follow the OpenJDK-published GA builds link from that page to choose the macOS AArch64 archive
- do not replace that route in repo docs with Adoptium, Azul, SDKMAN, Homebrew, or other third-party distributions

Current macOS AArch64 artifact and checksum used for this setup:
- archive: `openjdk-26_macos-aarch64_bin.tar.gz`
- published SHA-256:
  `254586bcd1bf6dcd125ad667ac32562cb1e2ab1abf3a61fb117b6fabb571e765`

Use `https://openjdk.org` as both:
- the release-status reference
- the authoritative place from which the Java 26 macOS AArch64 binary is obtained

In other words:
- OpenJDK owns the information source
- OpenJDK owns the binary-selection path
- third-party redistributions are outside the documented FinGrind workstation standard

## Installed Layout

The supported local layout is:
- JDK bundle: `~/Library/Java/JavaVirtualMachines/jdk-26.jdk`
- `JAVA_HOME`: `~/Library/Java/JavaVirtualMachines/jdk-26.jdk/Contents/Home`
- expected `java`: `${JAVA_HOME}/bin/java`
- expected `javac`: `${JAVA_HOME}/bin/javac`

Best-practice shell resolution is explicit JDK precedence. If `java --version` still reports 26
through the macOS `/usr/bin/java` launcher stub, the runtime is compatible, but the shell is not in
the preferred explicit state yet.

## Shell Hygiene

Optional toolchain setup files should never be sourced unconditionally. A missing optional file can
break every shell invocation before FinGrind commands even start.

In [~/.zshenv](/Users/erst/.zshenv), prefer:

```zsh
if [[ -f "$HOME/.cargo/env" ]]; then
  . "$HOME/.cargo/env"
fi
```

That keeps shell startup resilient on fresh machines where Rust or other optional tools have not
been installed yet.

## Shell Configuration

The current shell setup is intentionally duplicated across login and interactive zsh startup.

In [~/.zprofile](/Users/erst/.zprofile):

```zsh
if [[ -x /opt/homebrew/bin/brew ]]; then
  eval "$(/opt/homebrew/bin/brew shellenv zsh)"
fi

if /usr/libexec/java_home -v 26 >/dev/null 2>&1; then
  export JAVA_HOME="$("/usr/libexec/java_home" -v 26)"
  path=("${JAVA_HOME}/bin" ${path:#${JAVA_HOME}/bin})
fi
```

In [~/.zshrc](/Users/erst/.zshrc):

```zsh
if [[ -z "${JAVA_HOME:-}" ]] && /usr/libexec/java_home -v 26 >/dev/null 2>&1; then
  export JAVA_HOME="$("/usr/libexec/java_home" -v 26)"
fi
if [[ -n "${JAVA_HOME:-}" ]]; then
  path=("${JAVA_HOME}/bin" ${path:#${JAVA_HOME}/bin})
fi
```

Why both files:
- `~/.zprofile` covers login shells
- `~/.zshrc` covers interactive non-login shells
- Java path normalization happens after `brew shellenv`, so Homebrew cannot regain precedence later
  in shell startup
- the `path=(...)` form removes any inherited later copy of `${JAVA_HOME}/bin` before re-prepending
  it, which is what keeps Java truly first even when parent processes already injected it elsewhere

## Installation Procedure

This is the supported fresh-machine procedure for FinGrind.

1. Start at [openjdk.org](https://openjdk.org) and use the Java 26 page published there.
2. Download the macOS AArch64 Java 26 archive linked from OpenJDK.
3. Verify the archive checksum against the SHA-256 published through the OpenJDK download page.
4. Extract the bundle into `~/Library/Java/JavaVirtualMachines/jdk-26.jdk`.
5. Add the zsh configuration shown above so `JAVA_HOME` and `PATH` prefer Java 26.
6. Remove stale JDK symlinks that still point to Homebrew-managed Java, especially
   `~/Library/Java/JavaVirtualMachines/openjdk.jdk`.
7. Verify both shell modes and then run the repo checks.

Useful local commands after the manual download step:

```bash
archive="$HOME/Downloads/openjdk-26_macos-aarch64_bin.tar.gz"
root_dir="$(tar -tzf "$archive" | head -1 | cut -d/ -f1)"

shasum -a 256 "$archive"
mkdir -p "$HOME/Library/Java/JavaVirtualMachines"
rm -rf "$HOME/Library/Java/JavaVirtualMachines/jdk-26.jdk"
tar -xzf "$archive" -C "$HOME/Library/Java/JavaVirtualMachines"
mv "$HOME/Library/Java/JavaVirtualMachines/$root_dir" \
  "$HOME/Library/Java/JavaVirtualMachines/jdk-26.jdk"
rm -f "$HOME/Library/Java/JavaVirtualMachines/openjdk.jdk"
```

## Gradle Rule

For FinGrind, best practice is:
- use `./gradlew` for every build, test, packaging, and release command
- do not install Brew `gradle` as part of the FinGrind setup
- do not build Gradle from source as part of the FinGrind setup

The only narrow exception is bootstrapping a brand-new Gradle repository that does not yet contain
wrapper files. In that case, a temporary Gradle installation may be used once to generate and
commit the wrapper. After the wrapper exists, return immediately to the wrapper-only model.

FinGrind itself already has wrapper files committed, so that bootstrap exception does not apply to
normal repo work here.

## Build Logic Caveat

FinGrind's product modules and runtime baseline are Java 26.

There is one deliberate toolchain exception: the shared included build logic under
`gradle/build-logic` still emits JVM 25 bytecode because Kotlin `2.3.0` does not yet target JVM 26
directly. That build still compiles with the Java 26 toolchain, so a separate Java 25 install is
not part of the supported setup.

## Java 26 Feature Adoption

FinGrind's Java 26 stance is stable-first:
- use finalized Java 26 features as soon as they materially improve the codebase
- keep preview and incubator features disabled by default

Why:
- the official JDK 26 release surface still includes major preview / incubator items
- enabling preview features changes the repo-wide compiler, runtime, tooling, and release
  contract
- a bookkeeping kernel benefits more from stability and explicitness than from adopting preview
  syntax for its own sake

Current practical result:
- FinGrind already leans on modern stable Java such as records, sealed families, modern switch
  forms, `List.getFirst()`, and the finalized FFM API
- FinGrind intentionally does not codify `--enable-preview` into the build until a preview feature
  creates a clear architectural win worth the lifecycle cost

## Pitfalls

Known pitfalls:
- `java -jar` does not use Gradle toolchains
- Homebrew `gradle` declares a dependency on `openjdk`, which can reintroduce Java drift during
  unrelated Brew upgrades
- `~/.zprofile` alone is not enough for terminals that start interactive non-login shells
- stale symlinks under `~/Library/Java/JavaVirtualMachines/` can silently point back to removed or
  outdated JDKs
- optional shell setup files sourced unconditionally can break every shell command on a fresh
  machine
- non-interactive automation environments may skip zsh startup files, so `java --version` matters
  more than assumptions about `PATH`

## Verification Commands

Useful checks:

```bash
/usr/libexec/java_home -V
echo "$JAVA_HOME"
command -v java
command -v javac
java --version
javac --version
zsh -ic 'echo "$JAVA_HOME"; command -v java; command -v javac'
zsh -lic 'echo "$JAVA_HOME"; command -v java; command -v javac'
sed -n '1,20p' "$(/usr/libexec/java_home -v 26)/release"
./gradlew --version --console=plain
```

Expected outcomes:
- Java 26 appears in `/usr/libexec/java_home -V`
- `JAVA_HOME` resolves inside `jdk-26.jdk`
- both zsh modes resolve `java` and `javac`
- `java --version` and `javac --version` report version 26
- `./gradlew --version` reports Gradle `9.4.1`

## Full Toolchain Verification

After Java 26 is active in the shell, the canonical FinGrind verification sequence is:

```bash
java --version
./gradlew --version --console=plain
./gradlew check --no-daemon --console=plain
./gradlew -p jazzer test jazzerRegression --no-daemon --console=plain
./gradlew :cli:shadowJar --no-daemon --console=plain
./check.sh --console=plain
```

Run those commands from a real terminal session.

Why:
- local shell startup files are what make Java 26 the default `java`
- the packaged CLI depends on the ambient launcher runtime
- Gradle toolchains complement the shell JDK; they do not replace it

## Maintenance Guidance

When Java 27 or later becomes the project target:
- install the new upstream JDK beside the old one first
- update the shell config to use the new major version
- verify both shell modes before removing the previous JDK
- update FinGrind's shell docs, build scripts, CI, and release surfaces together
