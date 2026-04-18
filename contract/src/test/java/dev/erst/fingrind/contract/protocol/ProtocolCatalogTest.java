package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
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
  void catalogIndexesRejectDuplicateIdsAndTokens() {
    ProtocolOperation help = operation(OperationId.HELP, List.of("--same"));
    ProtocolOperation duplicateHelp = operation(OperationId.HELP, List.of("--other"));
    ProtocolOperation version = operation(OperationId.VERSION, List.of("--same"));

    assertThrows(
        IllegalStateException.class, () -> ProtocolCatalog.indexById(List.of(help, duplicateHelp)));
    assertThrows(
        IllegalStateException.class, () -> ProtocolCatalog.indexByToken(List.of(help, version)));
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

  private static ProtocolOperation operation(OperationId id, List<String> aliases) {
    return new ProtocolOperation(
        id,
        OperationCategory.DISCOVERY,
        id.wireName(),
        aliases,
        List.of(),
        ExecutionMode.JSON_ENVELOPE,
        "fingrind " + id.wireName(),
        "summary",
        List.of());
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
    assertEquals(100, ProtocolLimits.LEDGER_PLAN_STEP_MAX);
    assertTrue(listAccounts.options().contains("[--limit <1-200>]"));
    assertTrue(listAccounts.usage().contains("[--book-key-file <path> | --book-passphrase-stdin"));
    assertTrue(listAccounts.examples().getFirst().contains("--limit 50"));
  }

  @Test
  void globalFacts_publishTheCurrentBookModelAndRuntimeContract() {
    assertEquals(List.of("sqlite"), ProtocolCatalog.storageEngines());
    assertEquals(
        List.of(
            ProtocolStatuses.OK,
            ProtocolStatuses.PREFLIGHT_ACCEPTED,
            ProtocolStatuses.COMMITTED,
            ProtocolStatuses.PLAN_COMMITTED),
        ProtocolCatalog.successStatuses());
    assertEquals(
        List.of(
            ProtocolStatuses.REJECTED,
            ProtocolStatuses.PLAN_REJECTED,
            ProtocolStatuses.PLAN_ASSERTION_FAILED),
        ProtocolCatalog.rejectionStatuses());
    assertEquals("single-currency-per-entry", ProtocolCatalog.bookModel().currencyScope());
    assertEquals("not-supported", ProtocolCatalog.currency().multiCurrencyStatus());
    assertEquals("advisory", ProtocolCatalog.preflight().semantics());
    assertFalse(ProtocolCatalog.preflight().commitGuarantee());
    assertEquals("atomic", ProtocolCatalog.planExecution().transactionMode());
    assertEquals("halt-on-first-failure", ProtocolCatalog.planExecution().failurePolicy());
    assertTrue(ProtocolCatalog.planExecution().journal().contains("per-step journal"));
    assertTrue(
        ProtocolCatalog.planExecution().hardLimitations().stream()
            .anyMatch(limitation -> limitation.contains("open-book")));
    assertTrue(
        ProtocolCatalog.planExecution().hardLimitations().stream()
            .anyMatch(limitation -> limitation.contains("100 steps")));
    assertEquals(
        PublicDistributionContract.current().supportedPublicCliBundleTargets(),
        ProtocolCatalog.supportedPublicCliBundleTargets());
    assertEquals(
        PublicDistributionContract.current().unsupportedPublicCliOperatingSystems(),
        ProtocolCatalog.unsupportedPublicCliOperatingSystems());
  }

  @Test
  void planExecutionFactsAndPlanFieldConstantsValidateTheirShape() {
    PlanExecutionFacts facts =
        new PlanExecutionFacts(
            "atomic", "halt-on-first-failure", "complete journal", List.of("limit"));

    assertEquals(List.of("limit"), facts.hardLimitations());
    assertEquals(List.of("planId", "steps"), ProtocolLedgerPlanFields.planFields());
    assertEquals(
        List.of("stepId", "kind", "posting", "declareAccount", "query", "assertion", "postingId"),
        ProtocolLedgerPlanFields.stepFields());
    assertEquals(
        List.of("accountCode", "effectiveDateFrom", "effectiveDateTo", "limit", "cursor", "offset"),
        ProtocolLedgerPlanFields.queryFields());
    assertEquals(
        List.of(
            "kind",
            "accountCode",
            "postingId",
            "effectiveDateFrom",
            "effectiveDateTo",
            "currencyCode",
            "netAmount",
            "balanceSide"),
        ProtocolLedgerPlanFields.assertionFields());
    assertEquals("planId", ProtocolLedgerPlanFields.Plan.PLAN_ID);
    assertEquals("steps", ProtocolLedgerPlanFields.Plan.STEPS);
    assertEquals("stepId", ProtocolLedgerPlanFields.Step.STEP_ID);
    assertEquals("kind", ProtocolLedgerPlanFields.Step.KIND);
    assertEquals("posting", ProtocolLedgerPlanFields.Step.POSTING);
    assertEquals("declareAccount", ProtocolLedgerPlanFields.Step.DECLARE_ACCOUNT);
    assertEquals("query", ProtocolLedgerPlanFields.Step.QUERY);
    assertEquals("assertion", ProtocolLedgerPlanFields.Step.ASSERTION);
    assertEquals("postingId", ProtocolLedgerPlanFields.Step.POSTING_ID);
    assertEquals("accountCode", ProtocolLedgerPlanFields.Query.ACCOUNT_CODE);
    assertEquals("effectiveDateFrom", ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_FROM);
    assertEquals("effectiveDateTo", ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_TO);
    assertEquals("limit", ProtocolLedgerPlanFields.Query.LIMIT);
    assertEquals("offset", ProtocolLedgerPlanFields.Query.OFFSET);
    assertEquals("kind", ProtocolLedgerPlanFields.Assertion.KIND);
    assertEquals("accountCode", ProtocolLedgerPlanFields.Assertion.ACCOUNT_CODE);
    assertEquals("postingId", ProtocolLedgerPlanFields.Assertion.POSTING_ID);
    assertEquals("effectiveDateFrom", ProtocolLedgerPlanFields.Assertion.EFFECTIVE_DATE_FROM);
    assertEquals("effectiveDateTo", ProtocolLedgerPlanFields.Assertion.EFFECTIVE_DATE_TO);
    assertEquals("currencyCode", ProtocolLedgerPlanFields.Assertion.CURRENCY_CODE);
    assertEquals("netAmount", ProtocolLedgerPlanFields.Assertion.NET_AMOUNT);
    assertEquals("balanceSide", ProtocolLedgerPlanFields.Assertion.BALANCE_SIDE);
    assertEquals(
        List.of("effectiveDate", "lines", "provenance", "reversal"),
        ProtocolPostEntryFields.topLevelFields());
    assertEquals(
        List.of("accountCode", "side", "currencyCode", "amount"),
        ProtocolPostEntryFields.journalLineFields());
    assertEquals(
        List.of(
            "actorId", "actorType", "commandId", "idempotencyKey", "causationId", "correlationId"),
        ProtocolPostEntryFields.provenanceFields());
    assertEquals(List.of("priorPostingId", "reason"), ProtocolPostEntryFields.reversalFields());

    assertThrows(
        IllegalArgumentException.class,
        () -> new PlanExecutionFacts(" ", "halt-on-first-failure", "journal", List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PlanExecutionFacts("atomic", " ", "journal", List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PlanExecutionFacts("atomic", "halt-on-first-failure", " ", List.of()));
  }

  @Test
  void publicDistributionContractLoaderValidatesResourceAndNormalizationEdges() {
    PublicDistributionContract loaded =
        PublicDistributionContract.loadFromResource(
            new ByteArrayInputStream(
                """
                supportedPublicCliBundleTargets=macos-aarch64, linux-x86_64
                unsupportedPublicCliOperatingSystems=windows-arm64
                """
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            "test-resource");

    assertEquals(
        List.of("macos-aarch64", "linux-x86_64"), loaded.supportedPublicCliBundleTargets());
    assertEquals(List.of("windows-arm64"), loaded.unsupportedPublicCliOperatingSystems());
    assertEquals(
        List.of(),
        PublicDistributionContract.loadFromResource(
                new ByteArrayInputStream(new byte[0]), "blank-resource")
            .supportedPublicCliBundleTargets());
    assertThrows(
        IllegalStateException.class,
        () -> PublicDistributionContract.loadFromResource(null, "missing-resource"));
    assertThrows(
        UncheckedIOException.class,
        () -> PublicDistributionContract.loadFromResource(failingInputStream(), "bad-resource"));
    assertThrows(NullPointerException.class, () -> new PublicDistributionContract(null, List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PublicDistributionContract(List.of("macos-aarch64", " "), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PublicDistributionContract(List.of("linux-x86_64", "linux-x86_64"), List.of()));
  }

  private static InputStream failingInputStream() {
    return new InputStream() {
      @Override
      public int read() throws IOException {
        throw new IOException("boom");
      }

      @Override
      public int read(byte[] buffer, int offset, int length) throws IOException {
        throw new IOException("boom");
      }
    };
  }
}
