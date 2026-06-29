package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.CommitEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Focused coverage for record-entry command validation and commit routing. */
class CliMutationCommandExecutorTest extends CliResponseWriterTestSupport {
  @Test
  void runRecordEntryCommand_rejectsMismatchedEntryKindWithExactFieldAndValue() throws IOException {
    Path requestFile = writeRequest(CliRequestReaderTestSupport.validRequestJson(false));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliMutationCommandExecutor executor =
        new CliMutationCommandExecutor(
            new CliRequestReader(new ByteArrayInputStream(new byte[0])),
            mutationWriter(outputStream),
            planWriter(outputStream),
            failureWriter(outputStream),
            new CliWorkflowDoubleSupport.ExplodingWorkflow(
                new IllegalStateException("workflow should not run")));

    int exitCode =
        executor.runRecordEntryCommand(
            bookAccess(), requestFile, OutputMode.JSON, OperationId.RECORD_EXPENSE);

    JsonNode envelope = readJson(outputStream);
    assertEquals(1, exitCode);
    assertEquals("error", envelope.path("status").stringValue());
    assertEquals("invalid-request", envelope.path("code").stringValue());
    assertEquals("entryKind", envelope.path("argument").stringValue());
    assertTrue(envelope.path("message").stringValue().contains("entryKind"));
    assertTrue(envelope.path("message").stringValue().contains("EXPENSE"));
    assertTrue(envelope.path("message").stringValue().contains("SALE"));
    assertTrue(
        envelope.path("hint").stringValue().contains("print-request-template record-expense"));
  }

  @Test
  void runPostEntryCommand_rejectsTypedEntryKindWithRawStarterHint() throws IOException {
    Path requestFile = writeRequest(CliRequestReaderTestSupport.validRequestJson(false));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliMutationCommandExecutor executor =
        new CliMutationCommandExecutor(
            new CliRequestReader(new ByteArrayInputStream(new byte[0])),
            mutationWriter(outputStream),
            planWriter(outputStream),
            failureWriter(outputStream),
            new CliWorkflowDoubleSupport.ExplodingWorkflow(
                new IllegalStateException("workflow should not run")));

    int exitCode = executor.runPostEntryCommand(bookAccess(), requestFile, OutputMode.JSON);

    JsonNode envelope = readJson(outputStream);
    assertEquals(1, exitCode);
    assertEquals("error", envelope.path("status").stringValue());
    assertEquals("invalid-request", envelope.path("code").stringValue());
    assertEquals("entryKind", envelope.path("argument").stringValue());
    assertTrue(envelope.path("message").stringValue().contains("DIRECT_JOURNAL"));
    assertTrue(envelope.path("message").stringValue().contains("SALE"));
    assertTrue(envelope.path("hint").stringValue().contains("print-request-template post-entry"));
  }

  @Test
  void runRecordEntryCommand_commitsMatchingEntryKind() throws IOException {
    Path requestFile = writeRequest(CliRequestReaderTestSupport.validRequestJson(false));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    AtomicReference<PostEntryCommand> capturedCommand = new AtomicReference<>();
    CliBookMutationWorkflow workflow =
        new CliBookWorkflowAdapter() {
          @Override
          public ContractDecision<CommitEntryResult> commit(
              BookAccess bookAccess, PostEntryCommand command) {
            capturedCommand.set(command);
            return ContractDecision.accepted(
                new PostEntryResult.Committed(
                    new PostingId("posting-1"),
                    new IdempotencyKey("idem-1"),
                    LocalDate.parse("2026-04-07"),
                    Instant.parse("2026-04-07T10:15:30Z"),
                    false));
          }
        };
    CliMutationCommandExecutor executor =
        new CliMutationCommandExecutor(
            new CliRequestReader(new ByteArrayInputStream(new byte[0])),
            mutationWriter(outputStream),
            planWriter(outputStream),
            failureWriter(outputStream),
            workflow);

    int exitCode =
        executor.runRecordEntryCommand(
            bookAccess(), requestFile, OutputMode.JSON, OperationId.RECORD_SALE);

    JsonNode envelope = readJson(outputStream);
    assertEquals(0, exitCode);
    assertEquals("ok", envelope.path("status").stringValue());
    assertEquals("posting-1", envelope.path("payload").path("postingId").stringValue());
    assertNotNull(capturedCommand.get());
    assertEquals(
        dev.erst.fingrind.core.BookkeepingEntryKind.SALE,
        capturedCommand.get().entry().entryKind());
  }

  private BookAccess bookAccess() {
    Path bookFile = tempDirectory.resolve("book.sqlite");
    Path bookKeyFile = writeBookKey(bookFile);
    return new BookAccess(bookFile, new BookAccess.PassphraseSource.KeyFile(bookKeyFile));
  }
}
