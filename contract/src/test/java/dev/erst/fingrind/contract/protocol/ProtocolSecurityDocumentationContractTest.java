package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Guards the canonical security-model reference against contract drift. */
class ProtocolSecurityDocumentationContractTest extends ProtocolContractLintSupport {
  @Test
  void developerSecurityReference_coversCanonicalSecurityFacts() throws IOException {
    String document = Files.readString(repositoryRoot().resolve("docs/DEVELOPER_SECURITY.md"));
    ProtectedBookFormatContract protectedBookFormat = ProtocolCatalog.protectedBookFormat();
    Set<String> requiredFragments =
        new LinkedHashSet<>(
            java.util.List.of(
                "protected-book-verification-failed",
                ProtocolCatalog.requiredMinimumSqliteVersion(),
                ProtocolCatalog.requiredSqlite3mcVersion(),
                protectedBookFormat.cipher().wireValue(),
                Integer.toString(protectedBookFormat.pageSize()),
                Integer.toString(protectedBookFormat.reservedBytes()),
                Integer.toString(protectedBookFormat.kdfIter()),
                Integer.toString(protectedBookFormat.plaintextHeaderSize()),
                SqliteRuntimeProvenance.BUNDLE_MANAGED.wireValue(),
                SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED.wireValue(),
                SqliteRuntimeProvenance.ENVIRONMENT_CONFIGURED.wireValue(),
                "plaintext CLI passphrase arguments",
                "environment-variable passphrase transport",
                "THREADSAFE=1",
                "OMIT_LOAD_EXTENSION",
                "TEMP_STORE=3",
                "SECURE_DELETE"));

    Set<String> violations = new LinkedHashSet<>();
    for (String fragment : requiredFragments) {
      if (!document.contains(fragment)) {
        violations.add("docs/DEVELOPER_SECURITY.md is missing `" + fragment + "`");
      }
    }

    assertTrue(violations.isEmpty(), () -> "Security documentation drift:\n" + sorted(violations));
  }
}
