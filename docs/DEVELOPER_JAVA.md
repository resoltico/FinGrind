---
afad: "3.5"
version: "0.3.1"
domain: DEVELOPER_JAVA
updated: "2026-04-10"
route:
  keywords: [fingrind, java, java26, jdk, jdk.java.net, local-dev, zsh, java-home, brew]
  questions: ["how is java 26 set up for fingrind", "why does fingrind use jdk.java.net instead of brew java", "how do i configure local java 26 for fingrind"]
---

# Java 26 Developer Setup

**Purpose**: Document the local Java 26 setup used for FinGrind development, why it is configured this way, and how to maintain it safely.
**Prerequisites**: macOS with zsh.

## Current Stance

FinGrind targets Java 26.

That target is enforced in project configuration and GitHub Actions, so local developer shells
should also resolve to Java 26 by default.

The current local setup intentionally uses:
- the official Java 26 release from [jdk.java.net/26](https://jdk.java.net/26/)
- a user-local installation under `~/Library/Java/JavaVirtualMachines/openjdk-26.jdk`
- explicit `JAVA_HOME` and `PATH` wiring in zsh startup files
- no Homebrew-managed JDK on the machine

## Why This Setup

Reasons:
- Java 26 reached GA, but Homebrew lagged behind the release for local runtime availability.
- FinGrind's packaged CLI uses the ambient `java` process, not only Gradle toolchains.
- FinGrind's SQLite adapter uses Java 26 FFM, so the packaged CLI and local Gradle runs must both
  run on a JDK that supports stable native access.
- CI already uses Java 26 explicitly, so local shells should match that contract.
- `jdk.java.net` gives a clear, upstream, version-specific source instead of waiting on a package
  manager mirror cadence.
- Keeping the active JDK outside Homebrew reduces accidental version drift during unrelated Brew
  upgrades.

## Installed Layout

Current local bundle:
- `~/Library/Java/JavaVirtualMachines/openjdk-26.jdk`

Current upstream artifact used for this setup:
- archive: `openjdk-26_macos-aarch64_bin.tar.gz`
- source page: [jdk.java.net/26](https://jdk.java.net/26/)
- published SHA-256 at time of install:
  `254586bcd1bf6dcd125ad667ac32562cb1e2ab1abf3a61fb117b6fabb571e765`

Current shell resolution:
- `JAVA_HOME=/Users/erst/Library/Java/JavaVirtualMachines/openjdk-26.jdk/Contents/Home`
- `java` resolves to `${JAVA_HOME}/bin/java`
- `javac` resolves to `${JAVA_HOME}/bin/javac`

The old Homebrew-linked symlink was intentionally removed:
- `~/Library/Java/JavaVirtualMachines/openjdk.jdk`

## Shell Configuration

The current shell setup is intentionally duplicated across login and interactive zsh startup.

In [~/.zprofile](/Users/erst/.zprofile):

```zsh
eval "$(/opt/homebrew/bin/brew shellenv zsh)"

if /usr/libexec/java_home -v 26 >/dev/null 2>&1; then
    export JAVA_HOME="$("/usr/libexec/java_home" -v 26)"
    case ":${PATH}:" in
        *":${JAVA_HOME}/bin:"*) ;;
        *) export PATH="${JAVA_HOME}/bin:${PATH}" ;;
    esac
fi
```

In [~/.zshrc](/Users/erst/.zshrc):

```zsh
if [[ -z "${JAVA_HOME:-}" ]] && /usr/libexec/java_home -v 26 >/dev/null 2>&1; then
  export JAVA_HOME="$("/usr/libexec/java_home" -v 26)"
fi
if [[ -n "${JAVA_HOME:-}" ]]; then
  case ":${PATH}:" in
    *":${JAVA_HOME}/bin:"*) ;;
    *) export PATH="${JAVA_HOME}/bin:${PATH}" ;;
  esac
fi
```

Why both files:
- `~/.zprofile` covers login shells
- `~/.zshrc` covers interactive non-login shells
- this avoids surprises where one terminal path sees Java 26 and another silently falls back to a
  different JDK

## Installation Procedure

This is the exact local procedure used for FinGrind.

1. Download the official macOS AArch64 Java 26 archive from [jdk.java.net/26](https://jdk.java.net/26/).
2. Verify the archive checksum against the value published by the OpenJDK site.
3. Extract the bundle into `~/Library/Java/JavaVirtualMachines/openjdk-26.jdk`.
4. Add the zsh configuration shown above so `JAVA_HOME` and `PATH` prefer Java 26.
5. Remove stale JDK symlinks that still point to Homebrew-managed Java, especially
   `~/Library/Java/JavaVirtualMachines/openjdk.jdk`.
6. Verify both shell modes:
   `zsh -ic 'echo $JAVA_HOME; command -v java; command -v javac'`
   and
   `zsh -lic 'echo $JAVA_HOME; command -v java; command -v javac'`

## Homebrew Cleanup

The old Homebrew JDK was intentionally removed.

Important detail:
- Homebrew refused the uninstall initially because the `gradle` formula depends on `openjdk`
- the removal was completed with `brew uninstall --ignore-dependencies openjdk`

Why that was acceptable:
- FinGrind uses the project Gradle wrapper as the canonical entrypoint
- the shell now resolves Java 26 directly through `JAVA_HOME`
- the Homebrew JDK was redundant and was the main source of local version ambiguity

## Pitfalls

Known pitfalls:
- Homebrew `gradle` still declares a dependency on `openjdk`.
  If you rely on the Brew `gradle` formula instead of `./gradlew`, Homebrew may consider the setup
  incomplete or may try to reinstall `openjdk` later.
- `java -jar` does not use Gradle toolchains.
  That is why shell-level Java 26 setup matters even though Gradle can provision toolchains for
  compilation.
- FinGrind enables native access explicitly for Gradle `Test` and `JavaExec` tasks, and the
  packaged CLI JAR declares `Enable-Native-Access: ALL-UNNAMED`.
  A stale or incompatible JDK is more likely to surface as a runtime storage failure now than in
  the old shell-out design.
- Kotlin build logic still emits JVM 25 bytecode for compatibility, but it now does so using the
  Java 26 toolchain.
  That applies to the shared included build under `gradle/build-logic`, so a separate local Java
  25 installation is not part of the supported setup.
- `~/.zprofile` alone is not enough.
  Some terminal launches use interactive non-login shells and would otherwise miss the Java 26
  override.
- Stale symlinks under `~/Library/Java/JavaVirtualMachines/` can silently point back to removed or
  outdated JDKs.
- If `brew shellenv` is evaluated after Java path setup, Homebrew paths can regain precedence.
  That is why Java path insertion happens after Brew shellenv in `~/.zprofile`.
- Some automation environments start non-interactive shells and do not load zsh startup files.
  In those cases, always inspect `command -v java` before trusting the runtime.

## Verification Commands

Useful checks:

```bash
/usr/libexec/java_home -V
command -v java
command -v javac
java --version
zsh -ic 'echo $JAVA_HOME; command -v java; command -v javac'
zsh -lic 'echo $JAVA_HOME; command -v java; command -v javac'
sed -n '1,20p' "$(/usr/libexec/java_home -v 26)/release"
```

Expected outcomes:
- Java 26 appears in `/usr/libexec/java_home -V`
- both zsh modes resolve `java` and `javac`, and both commands report version 26
- shell resolution may point either directly into `openjdk-26.jdk` or through the macOS
  `/usr/bin/java` and `/usr/bin/javac` launcher stubs, as long as the launched runtime is 26
- the `release` file reports `JAVA_VERSION="26"`

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
- non-interactive wrappers may skip `~/.zprofile` and `~/.zshrc`
- macOS still ships legacy `/usr/bin/java` and `/usr/bin/javac` launcher stubs, so version output
  is more trustworthy than path shape alone

## Maintenance Guidance

When Java 27 or later becomes the project target:
- update the shell config to use the new major version
- install the new upstream JDK beside the old one first
- verify both shell modes before removing the previous JDK
- update FinGrind and companion repos together so local, CI, and release surfaces stay aligned
