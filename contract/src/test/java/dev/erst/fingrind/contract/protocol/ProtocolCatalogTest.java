package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for the core-owned protocol catalog. */
class ProtocolCatalogTest {
  @Test
  void operations_followCanonicalPublicOrder() {
    List<String> operationNames =
        ProtocolCatalog.operations().stream().map(operation -> operation.id().wireName()).toList();

    assertEquals(
        Arrays.stream(OperationId.values()).map(OperationId::wireName).toList(), operationNames);
    assertEquals("open-book", ProtocolCatalog.operationName(OperationId.OPEN_BOOK));
    assertEquals("Help", ProtocolCatalog.operation(OperationId.HELP).displayLabel());
    assertEquals(
        "raw-json",
        ProtocolCatalog.operation(OperationId.PRINT_REQUEST_TEMPLATE).executionMode().wireValue());
  }

  @Test
  void operationLookup_acceptsCanonicalNamesAndAliases() {
    assertSame(
        ProtocolCatalog.operation(OperationId.HELP),
        ProtocolCatalog.findByToken(OperationId.HELP.wireName()).orElseThrow());
    assertSame(
        ProtocolCatalog.operation(OperationId.HELP),
        ProtocolCatalog.findByToken("--help").orElseThrow());
    assertSame(
        ProtocolCatalog.operation(OperationId.VERSION),
        ProtocolCatalog.findByToken("--version").orElseThrow());
    assertSame(
        ProtocolCatalog.operation(OperationId.PRINT_REQUEST_TEMPLATE),
        ProtocolCatalog.findByToken("--print-request-template").orElseThrow());
    assertTrue(ProtocolCatalog.findByToken("unknown").isEmpty());
  }

  @Test
  void operationGroups_areDerivedFromCatalogCategories() {
    assertEquals(
        List.of("help", "version", "capabilities", "print-request-template", "print-plan-template"),
        ProtocolCatalog.operationNames(OperationCategory.DISCOVERY));
    assertEquals(
        List.of("generate-book-key-file", "open-book", "rekey-book", "declare-account"),
        ProtocolCatalog.operationNames(OperationCategory.ADMINISTRATION));
    assertEquals(
        List.of("inspect-book", "list-accounts", "get-posting", "list-postings", "account-balance"),
        ProtocolCatalog.operationNames(OperationCategory.QUERY));
    assertEquals(
        List.of("execute-plan", "preflight-entry", "post-entry"),
        ProtocolCatalog.operationNames(OperationCategory.WRITE));
  }

  @Test
  void operationDescriptors_renderLimitsOptionsUsageAndExamplesFromProtocolFacts() {
    ProtocolOperation listAccounts = ProtocolCatalog.operation(OperationId.LIST_ACCOUNTS);

    assertEquals("[--limit <1-200>]", ProtocolOptions.optionalLimitSyntax());
    assertEquals("[--offset <0+>]", ProtocolOptions.optionalOffsetSyntax());
    assertEquals(
        List.of("--book-key-file", "--book-passphrase-stdin", "--book-passphrase-prompt"),
        ProtocolOptions.bookPassphraseOptions());
    assertEquals(50, ProtocolLimits.DEFAULT_PAGE_LIMIT);
    assertEquals(200, ProtocolLimits.PAGE_LIMIT_MAX);
    assertTrue(listAccounts.options().contains("[--limit <1-200>]"));
    assertTrue(listAccounts.usage().contains("[--book-key-file <path> | --book-passphrase-stdin"));
    assertTrue(listAccounts.examples().getFirst().contains("--limit 50"));
  }

  @Test
  void globalFacts_publishTheCurrentBookModelAndRuntimeContract() {
    assertEquals(List.of("sqlite"), ProtocolCatalog.storageEngines());
    assertEquals(
        List.of("ok", "preflight-accepted", "committed"), ProtocolCatalog.successStatuses());
    assertEquals("single-currency-per-entry", ProtocolCatalog.bookModel().currencyScope());
    assertEquals("not-supported", ProtocolCatalog.currency().multiCurrencyStatus());
    assertEquals("advisory", ProtocolCatalog.preflight().semantics());
    assertFalse(ProtocolCatalog.preflight().commitGuarantee());
    assertEquals(
        List.of("macos-aarch64", "macos-x86_64", "linux-x86_64", "linux-aarch64", "windows-x86_64"),
        ProtocolCatalog.supportedPublicCliBundleTargets());
    assertEquals(List.of(), ProtocolCatalog.unsupportedPublicCliOperatingSystems());
  }
}
