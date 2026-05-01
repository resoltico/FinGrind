package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.AccountPage;
import dev.erst.fingrind.contract.ContractDecision;
import dev.erst.fingrind.contract.DeclareAccountResult;
import dev.erst.fingrind.contract.ListAccountsResult;
import dev.erst.fingrind.contract.OpenBookResult;
import dev.erst.fingrind.contract.PostEntryResult.PreflightAccepted;
import dev.erst.fingrind.contract.PostEntryResult.PreflightRejected;
import dev.erst.fingrind.contract.PostingRejection;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.IdempotencyKey;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SqliteRoundTripWorkflowRenderingAssertionsTest {
  @Test
  void rendering_helpers_cover_blank_csv_json_and_fragment_guards() {
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowRenderingAssertions.assertRenderedDocument(
                "   ", OutputMode.HUMAN, null));
    assertThrows(
        tools.jackson.core.JacksonException.class,
        () ->
            SqliteRoundTripWorkflowRenderingAssertions.assertRenderedDocument(
                "{", OutputMode.JSON, null));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowRenderingAssertions.assertRenderedDocument(
                "header\nvalue", OutputMode.CSV, null));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowRenderingAssertions.assertRenderedDocument(
                "{\"status\":\"ok\"}", OutputMode.JSON, "missing-fragment"));
    assertDoesNotThrow(
        () ->
            SqliteRoundTripWorkflowRenderingAssertions.assertRenderedDocument(
                "{\"status\":\"ok\"}", OutputMode.JSON, "\"status\""));
    assertDoesNotThrow(
        () ->
            SqliteRoundTripWorkflowRenderingAssertions.assertRenderedDocument(
                "[]", OutputMode.JSON, null));
    assertDoesNotThrow(
        () ->
            SqliteRoundTripWorkflowRenderingAssertions.assertRenderedDecision(
                () ->
                    ContractDecision.accepted(
                        new ListAccountsResult.Listed(
                            new AccountPage(List.of(), 50, Optional.empty()))),
                OutputMode.JSON,
                SqliteRoundTripWorkflowRenderingAssertions::writeListAccountsJson,
                null));
  }

  @Test
  void decision_rendering_helpers_cover_rejected_and_runtime_paths() {
    assertDoesNotThrow(
        () ->
            SqliteRoundTripWorkflowRenderingAssertions.assertRenderedDecision(
                () -> ContractDecision.accepted("accepted value"),
                OutputMode.JSON,
                (writer, value) -> writer.writeJson(java.util.Map.of("value", value)),
                "accepted value"));

    assertDoesNotThrow(
        () ->
            SqliteRoundTripWorkflowRenderingAssertions.assertRenderedDecision(
                () ->
                    ContractDecision.rejected(
                        SqliteRoundTripWorkflowTestSupport.contractFailure("rendered rejection")),
                OutputMode.JSON,
                (writer, value) -> writer.writeJson(java.util.Map.of("value", value)),
                "rendered rejection"));

    assertDoesNotThrow(
        () ->
            SqliteRoundTripWorkflowRenderingAssertions.assertRenderedDecision(
                () -> {
                  throw new IllegalStateException("runtime render failure");
                },
                OutputMode.JSON,
                (writer, value) -> writer.writeJson(java.util.Map.of("value", value)),
                "runtime-failure"));

    assertDoesNotThrow(
        () ->
            SqliteRoundTripWorkflowRenderingAssertions.assertRenderedRuntimeFailure(
                new IllegalStateException("boom"), OutputMode.JSON, "runtime-failure"));
  }

  @Test
  void accepted_result_renderers_and_lifecycle_guards_cover_wrong_shapes() {
    Path bookPath = Path.of("entity.sqlite");

    IllegalStateException opened =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteRoundTripWorkflowRenderingAssertions.assertOpened(
                    ContractDecision.accepted(
                        new OpenBookResult.Rejected(
                            new dev.erst.fingrind.contract.BookAdministrationRejection
                                .BookAlreadyInitialized())),
                    bookPath,
                    OutputMode.JSON,
                    "unused"));
    SqliteRoundTripWorkflowTestSupport.assertMessageContains(opened, "open successfully");

    IllegalStateException declared =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteRoundTripWorkflowRenderingAssertions.assertDeclared(
                    ContractDecision.accepted(
                        new DeclareAccountResult.Rejected(
                            new dev.erst.fingrind.contract.BookAdministrationRejection
                                .BookNotInitialized())),
                    OutputMode.JSON,
                    "unused"));
    SqliteRoundTripWorkflowTestSupport.assertMessageContains(declared, "declaration to succeed");

    assertInstanceOf(
        PreflightAccepted.class,
        SqliteRoundTripWorkflowLifecycleAssertions.requirePreflightAccepted(
            ContractDecision.accepted(
                new PreflightAccepted(
                    new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")))));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowLifecycleAssertions.requirePreflightAccepted(
                ContractDecision.accepted(
                    new PreflightRejected(
                        new IdempotencyKey("idem-1"), new PostingRejection.BookNotInitialized()))));

    assertEquals(
        "posting-1",
        SqliteRoundTripWorkflowLifecycleAssertions.requireCommitted(
                ContractDecision.accepted(
                    SqliteRoundTripWorkflowTestSupport.committed("posting-1")))
            .postingId()
            .value());
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowLifecycleAssertions.requireCommitted(
                ContractDecision.accepted(
                    SqliteRoundTripWorkflowTestSupport.commitRejected(
                        new PostingRejection.DuplicateIdempotencyKey()))));

    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowLifecycleAssertions.requiredCommitRejected(
                SqliteRoundTripWorkflowTestSupport.committed("posting-2")));

    assertInstanceOf(
        DeclareAccountResult.Declared.class,
        SqliteRoundTripWorkflowLifecycleAssertions.requireDeclared(
            ContractDecision.accepted(
                new DeclareAccountResult.Declared(
                    SqliteRoundTripWorkflowTestSupport.declaredAccount(
                        new AccountCode("1000"), true)))));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowLifecycleAssertions.requireDeclared(
                ContractDecision.accepted(
                    new DeclareAccountResult.Rejected(
                        new dev.erst.fingrind.contract.BookAdministrationRejection
                            .BookNotInitialized()))));
  }
}
