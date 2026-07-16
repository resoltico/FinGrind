package dev.erst.fingrind.contract.protocol;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Locks the public accounting-scope ADR to the contract-owned capability catalog. */
class CapabilityCatalogAdrContractTest extends ProtocolContractRepositorySupport {
  @Test
  void partialCapabilities_haveOneOperativeBoundaryAndUniqueIdentifiers() {
    Set<String> identifiers = new HashSet<>();
    for (CapabilityCatalogEntry entry : CapabilityCatalog.entries()) {
      assertTrue(
          identifiers.add(entry.id()), "Capability identifiers must be unique: " + entry.id());
      if (entry.status() == CapabilityStatus.PARTIAL) {
        assertTrue(
            entry.operativeBoundary() != null && !entry.operativeBoundary().isBlank(),
            "Partial capability must declare its operative boundary: " + entry.id());
      } else {
        assertFalse(
            entry.operativeBoundary() != null,
            "Only partial capabilities may declare an operative boundary: " + entry.id());
      }
    }
  }

  @Test
  void capabilityEntries_rejectInvalidBoundaryCombinationsAndExposeTheDomainCatalog() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new CapabilityCatalogEntry(" ", "scope", CapabilityStatus.IMPLEMENTED, null));
    assertThrows(
        NullPointerException.class,
        () ->
            new CapabilityCatalogEntry(
                "tax", nullOf(String.class), CapabilityStatus.PARTIAL, "scope"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CapabilityCatalogEntry("tax", " ", CapabilityStatus.PARTIAL, "scope"));
    assertThrows(
        NullPointerException.class,
        () -> new CapabilityCatalogEntry("tax", "scope", nullOf(CapabilityStatus.class), null));
    assertThrows(
        NullPointerException.class,
        () -> new CapabilityCatalogEntry("tax", "scope", CapabilityStatus.PARTIAL, null));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CapabilityCatalogEntry("tax", "scope", CapabilityStatus.PARTIAL, " "));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CapabilityCatalogEntry(
                "tax", "scope", CapabilityStatus.IMPLEMENTED, "unsupported"));

    CapabilityCatalogEntry partial =
        new CapabilityCatalogEntry(
            " tax ", " published scope ", CapabilityStatus.PARTIAL, " scope boundary ");
    assertEquals("tax", partial.id());
    assertEquals("published scope", partial.scopeStatement());
    assertEquals("scope boundary", partial.operativeBoundary());
    assertEquals(CapabilityCatalog.entries(), ProtocolDomainCatalog.INSTANCE.capabilities());
  }

  @Test
  void latvianMonthlyPayrollCapability_publishesOnlyItsImplementedProfile() {
    Optional<CapabilityCatalogEntry> payroll =
        CapabilityCatalog.entries().stream()
            .filter(entry -> "latvian-monthly-payroll".equals(entry.id()))
            .findFirst();

    CapabilityCatalogEntry payrollEntry = payroll.orElseThrow();
    assertEquals(CapabilityStatus.PARTIAL, payrollEntry.status());
    assertTrue(payrollEntry.scopeStatement().contains("Latvian 2026"));
    assertTrue(
        Objects.requireNonNull(payrollEntry.operativeBoundary()).contains("other worker profiles"));
    assertFalse(
        CapabilityCatalog.entries().stream().anyMatch(entry -> "payroll".equals(entry.id())));
  }

  @Test
  void latvianMonthlyPayrollAdr_namesThePublishedCommandsAndDedicatedReport() throws IOException {
    String document =
        Files.readString(repositoryRoot().resolve("docs/ADR_LATVIAN_PAYROLL.md"))
            .replace("\r\n", "\n");

    assertTrue(document.contains("## Commands And Reports"));
    assertTrue(document.contains("`record-latvian-monthly-payroll`"));
    assertTrue(document.contains("`record-latvian-payroll-net-wage-settlement`"));
    assertTrue(document.contains("`record-latvian-payroll-state-remittance`"));
    assertTrue(document.contains("`latvian-payroll-register`"));
  }

  @Test
  void accountingKernelScopeAdr_rendersTheCanonicalCapabilityCatalogBlock() throws IOException {
    String document =
        Files.readString(repositoryRoot().resolve("docs/ADR_ACCOUNTING_KERNEL_SCOPE.md"))
            .replace("\r\n", "\n");

    assertEquals(
        document,
        CapabilityCatalogAdrRenderer.updatedDocument(document),
        "The accounting-kernel scope ADR capability block must be rendered from CapabilityCatalog.");
  }

  @Test
  void accountingKernelScopeAdr_rejectsScopeAndExclusionProseThatDriftsFromTheCatalog()
      throws IOException {
    String document =
        Files.readString(repositoryRoot().resolve("docs/ADR_ACCOUNTING_KERNEL_SCOPE.md"))
            .replace("\r\n", "\n");

    String inventoryScopeDrift =
        document.replace(
            "Inventory is available only for the owner-managed trading template.",
            "Inventory is available for every template.");
    assertNotEquals(
        inventoryScopeDrift, CapabilityCatalogAdrRenderer.updatedDocument(inventoryScopeDrift));

    String financingBoundaryDrift =
        document.replace(
            "The context records nominal principal and exact accrued interest only; leases, effective-interest amortization, fair-value measurement, covenants, tax withholding, and lender integrations are excluded.",
            "Financing has no operative boundary.");
    assertNotEquals(
        financingBoundaryDrift,
        CapabilityCatalogAdrRenderer.updatedDocument(financingBoundaryDrift));
  }

  @Test
  void accountingKernelScopeAdr_rejectsMissingMisorderedAndDuplicatedCapabilityMarkers() {
    assertInvalidCatalogDocument("No capability markers.");
    assertInvalidCatalogDocument(CapabilityCatalogAdrRenderer.CURRENT_SCOPE_BEGIN);
    assertInvalidCatalogDocument(
        CapabilityCatalogAdrRenderer.CURRENT_SCOPE_END
            + CapabilityCatalogAdrRenderer.CURRENT_SCOPE_BEGIN);
    assertInvalidCatalogDocument(
        CapabilityCatalogAdrRenderer.CURRENT_SCOPE_BEGIN
            + CapabilityCatalogAdrRenderer.CURRENT_SCOPE_END
            + CapabilityCatalogAdrRenderer.CURRENT_SCOPE_BEGIN);
    assertInvalidCatalogDocument(
        CapabilityCatalogAdrRenderer.CURRENT_SCOPE_BEGIN
            + CapabilityCatalogAdrRenderer.CURRENT_SCOPE_END
            + CapabilityCatalogAdrRenderer.CURRENT_SCOPE_END);
  }

  private static void assertInvalidCatalogDocument(String document) {
    assertThrows(
        IllegalArgumentException.class,
        () -> CapabilityCatalogAdrRenderer.updatedDocument(document));
  }
}
