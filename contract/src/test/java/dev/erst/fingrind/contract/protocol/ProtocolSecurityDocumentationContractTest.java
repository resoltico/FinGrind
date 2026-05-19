package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.discovery.ApplicationIdentity;
import dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.runtime.EnvironmentDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentDistributionDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentSqliteDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentStorageDescriptor;
import dev.erst.fingrind.contract.runtime.SqliteCompileOptionsVerificationStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Guards the canonical security-model reference against contract drift. */
class ProtocolSecurityDocumentationContractTest extends ProtocolContractLintSupport {
  @Test
  void developerSecurityReference_coversCanonicalSecurityFacts() throws IOException {
    String document = Files.readString(repositoryRoot().resolve("docs/DEVELOPER_SECURITY.md"));
    CapabilitiesDescriptor capabilities = capabilitiesDescriptor();
    EnvironmentDescriptor environment = readyEnvironmentDescriptor();
    ProtectedBookFormatContract protectedBookFormat = ProtocolCatalog.protectedBookFormat();

    Set<String> violations = new LinkedHashSet<>();
    requireContains(
        document,
        violations,
        "protected-book-verification-failed",
        "the public protected-book verification failure contract");
    requireContains(
        document,
        violations,
        sharedPassphraseByteLimit(capabilities) + " bytes",
        "the shared passphrase byte limit");
    requireContains(
        document,
        violations,
        "owner-only parent directory",
        "the key-file parent-directory security requirement");
    requireContains(
        document, violations, "memory_security=fill", "the required memory-hardening pragma");
    requireContains(
        document,
        violations,
        "durable session-scoped passphrase copy",
        "the true session-secret lifetime");
    requireContains(
        document,
        violations,
        "best-effort overwritten",
        "the heap-overwrite caveat for passphrase handling");
    requireContains(
        document,
        violations,
        "heap-resident secret copies the JVM GC",
        "the JVM heap caveat for passphrase handling");
    requireContains(document, violations, ".sha256", "the sibling digest sidecar rule");
    requireContains(
        document, violations, ".rekey-rollback-", "the stale rekey rollback artifact disclosure");
    requireContains(
        document, violations, "GitHub artifact attestation", "the release-attestation contract");
    requireContains(
        document,
        violations,
        "GitHub private vulnerability reporting",
        "the disclosure-channel contract");
    requireContains(
        document,
        violations,
        "./scripts/verify-security-policy-surface.sh",
        "the live GitHub security-policy verifier");
    requireContains(
        document,
        violations,
        "SqliteManagedLibraryIdentityTest",
        "the runtime-identity evidence owner");

    for (String passphraseOption : capabilities.requestInput().bookPassphraseOptions()) {
      requireContains(
          document,
          violations,
          passphraseOption,
          "documented supported passphrase route " + passphraseOption);
    }
    for (String compileOption : environment.sqlite().requiredCompileOptions()) {
      requireContains(
          document,
          violations,
          compileOption,
          "documented required SQLite compile option " + compileOption);
    }
    for (String compileOption : environment.sqlite().forbiddenCompileOptions()) {
      requireContains(
          document,
          violations,
          compileOption,
          "documented forbidden SQLite compile option " + compileOption);
    }
    if (environment.sqlite().requiresSecureMemorySupport()) {
      requireContains(
          document,
          violations,
          "SQLITE3MC_SECURE_MEMORY=1",
          "the managed SQLite secure-memory compile requirement");
    }
    for (String runtimeFact :
        java.util.List.of(
            environment.sqlite().requiredMinimumSqliteVersion(),
            environment.sqlite().requiredSqlite3mcVersion(),
            protectedBookFormat.cipher().wireValue(),
            Integer.toString(protectedBookFormat.pageSize()),
            Integer.toString(protectedBookFormat.reservedBytes()),
            Integer.toString(protectedBookFormat.kdfIter()),
            Integer.toString(protectedBookFormat.plaintextHeaderSize()))) {
      requireContains(
          document,
          violations,
          runtimeFact,
          "documented managed-runtime/security fact " + runtimeFact);
    }
    for (SqliteRuntimeProvenance provenance : SqliteRuntimeProvenance.values()) {
      requireContains(
          document,
          violations,
          provenance.wireValue(),
          "documented runtime provenance " + provenance.wireValue());
      requireContains(
          document,
          violations,
          SqliteRuntimeTrustBasis.fromProvenance(provenance).wireValue(),
          "documented runtime trust basis for " + provenance.wireValue());
    }

    assertTrue(violations.isEmpty(), () -> "Security documentation drift:\n" + sorted(violations));
  }

  @Test
  void developerSecurityReference_matchesMachineReadableRuntimeTrustSurface() throws IOException {
    String document = Files.readString(repositoryRoot().resolve("docs/DEVELOPER_SECURITY.md"));
    EnvironmentSqliteDescriptor sqlite = readyEnvironmentDescriptor().sqlite();

    assertTrue(
        document.contains("`bundle-managed` is publisher-authenticated"),
        "docs/DEVELOPER_SECURITY.md must describe the trusted managed-runtime identity boundary explicitly.");
    assertTrue(
        document.contains("`source-checkout-managed` is source-verified-local-build"),
        "docs/DEVELOPER_SECURITY.md must describe source-checkout-managed as one local-build trust class rather than publisher-authenticated release identity.");
    assertTrue(
        document.contains("`environment-configured` is an unsafe-local-override escape hatch"),
        "docs/DEVELOPER_SECURITY.md must describe environment-configured as one explicit unsafe local override.");
    assertTrue(
        document.contains("`runtimeTrustBasis`"),
        "docs/DEVELOPER_SECURITY.md must describe the machine-readable runtimeTrustBasis field.");
    assertTrue(
        document.contains(
            "environment.sqlite.runtime.runtimeTrustBasis distinguishes publisher-authenticated bundle runtimes, source-verified local-build runtimes, and unsafe local overrides"),
        "docs/DEVELOPER_SECURITY.md must explain how machine consumers distinguish runtime trust classes.");
    EnvironmentSqliteDescriptor.ReadyRuntime readyRuntime =
        (EnvironmentSqliteDescriptor.ReadyRuntime) sqlite.runtime();
    assertTrue(
        document.contains(readyRuntime.runtimeTrustBasis().wireValue()),
        "docs/DEVELOPER_SECURITY.md must include the ready managed-runtime trust-basis wire value.");
    assertTrue(
        document.contains("public quick-start and example docs keep key files under a separate"),
        "docs/DEVELOPER_SECURITY.md must explain the separate book-versus-secret example layout.");
    assertTrue(
        document.contains("stale `*.rekey-rollback-*.sqlite` artifacts"),
        "docs/DEVELOPER_SECURITY.md must disclose crash-persisted rekey rollback artifacts.");
  }

  @Test
  void repositorySecurityPolicy_existsAndDocumentsPrivateReporting() throws IOException {
    java.nio.file.Path securityPolicy = repositoryRoot().resolve("SECURITY.md");
    java.nio.file.Path liveVerifier =
        repositoryRoot().resolve("scripts/verify-security-policy-surface.sh");
    String document = Files.readString(securityPolicy);
    String verifierScript = Files.readString(liveVerifier);
    Set<String> requiredFragments =
        new LinkedHashSet<>(
            java.util.List.of(
                "Security Policy",
                "Supported Versions",
                "Report a vulnerability",
                "Do not open a public issue",
                "GitHub private vulnerability reporting",
                "./scripts/verify-security-policy-surface.sh",
                "5 business days",
                "10 business days"));

    Set<String> violations = new LinkedHashSet<>();
    for (String fragment : requiredFragments) {
      if (!document.contains(fragment)) {
        violations.add("SECURITY.md is missing `" + fragment + "`");
      }
    }

    assertTrue(
        violations.isEmpty(), () -> "Repository security-policy drift:\n" + sorted(violations));
    assertTrue(
        verifierScript.contains("/private-vulnerability-reporting"),
        "scripts/verify-security-policy-surface.sh must query the live GitHub private vulnerability reporting surface.");
    assertTrue(
        verifierScript.contains("GitHub private vulnerability reporting is disabled"),
        "scripts/verify-security-policy-surface.sh must fail explicitly when private reporting is disabled.");
  }

  private static void requireContains(
      String document, Set<String> violations, String expected, String explanation) {
    if (!document.contains(expected)) {
      violations.add(
          "docs/DEVELOPER_SECURITY.md is missing `" + expected + "` for " + explanation + ".");
    }
  }

  private static CapabilitiesDescriptor capabilitiesDescriptor() {
    return MachineContract.capabilities(new ApplicationIdentity("FinGrind", "0.41.0", "desc"));
  }

  private static EnvironmentDescriptor readyEnvironmentDescriptor() {
    return new EnvironmentDescriptor(
        new EnvironmentDistributionDescriptor(
            ProtocolCatalog.bundleRuntimeDistribution(),
            ProtocolCatalog.publicCliDistribution(),
            ProtocolCatalog.supportedPublicCliBundleTargets(),
            ProtocolCatalog.unsupportedPublicCliBundleTargets(),
            ProtocolCatalog.sourceCheckoutJava()),
        new EnvironmentStorageDescriptor(
            ProtocolCatalog.storageDriver(),
            ProtocolCatalog.storageEngine(),
            ProtocolCatalog.bookProtectionMode(),
            ProtocolCatalog.protectedBookFormat()),
        new EnvironmentSqliteDescriptor(
            ProtocolCatalog.sqliteLibraryMode(),
            ProtocolCatalog.sqliteLibraryEnvironmentVariable(),
            ProtocolCatalog.sqliteOperatorTrustSystemProperty(),
            ProtocolCatalog.sqliteBundleHomeSystemProperty(),
            ProtocolCatalog.requiredSqliteCompileOptions(),
            ProtocolCatalog.forbiddenSqliteCompileOptions(),
            ProtocolCatalog.requiresSecureMemorySupport(),
            ProtocolCatalog.requiredMinimumSqliteVersion(),
            ProtocolCatalog.requiredSqlite3mcVersion(),
            ProtocolCatalog.requiredSqliteSourceId(),
            EnvironmentSqliteDescriptor.runtime(
                SqliteCompileOptionsVerificationStatus.VERIFIED,
                SqliteRuntimeStatus.READY,
                SqliteRuntimeProvenance.BUNDLE_MANAGED,
                SqliteRuntimeTrustBasis.PUBLISHER_AUTHENTICATED,
                "/tmp/libsqlite3.dylib",
                ProtocolCatalog.requiredMinimumSqliteVersion(),
                ProtocolCatalog.requiredSqlite3mcVersion(),
                ProtocolCatalog.requiredSqliteSourceId(),
                null)));
  }

  private static String sharedPassphraseByteLimit(CapabilitiesDescriptor capabilities) {
    String semantics = String.join("\n", capabilities.requestInput().bookPassphraseSemantics());
    java.util.regex.Matcher utf8LimitMatcher =
        Pattern.compile("(\\d+)-byte UTF-8 limit").matcher(semantics);
    if (utf8LimitMatcher.find()) {
      return utf8LimitMatcher.group(1);
    }
    java.util.regex.Matcher byteMatcher = Pattern.compile("(\\d+) bytes").matcher(semantics);
    if (byteMatcher.find()) {
      return byteMatcher.group(1);
    }
    throw new IllegalStateException(
        "Could not derive the shared passphrase byte limit from MachineContract request input semantics.");
  }
}
