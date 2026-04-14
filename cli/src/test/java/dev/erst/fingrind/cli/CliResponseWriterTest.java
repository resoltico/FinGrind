package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.application.BookAdministrationRejection;
import dev.erst.fingrind.application.DeclareAccountResult;
import dev.erst.fingrind.application.DeclaredAccount;
import dev.erst.fingrind.application.ListAccountsResult;
import dev.erst.fingrind.application.MachineContract;
import dev.erst.fingrind.application.OpenBookResult;
import dev.erst.fingrind.application.PostEntryResult;
import dev.erst.fingrind.application.PostingRejection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.sqlite.RekeyBookResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Unit tests for {@link CliResponseWriter}. */
class CliResponseWriterTest {
  @Test
  void writeVersion_writesOkEnvelope() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    responseWriter.writeVersion(
        new MachineContract.VersionDescriptor(
            "FinGrind",
            "0.9.0",
            "Finance-grade bookkeeping kernel with an agent-first CLI and SQLite-first persistence"));

    JsonNode json = readJson(outputStream);
    assertEquals("ok", json.path("status").asString());
    assertEquals("0.9.0", json.path("payload").path("version").asString());
  }

  @Test
  void writeCapabilities_omitsNullEnvironmentFields() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    responseWriter.writeCapabilities(
        MachineContract.capabilities(
            new MachineContract.ApplicationIdentity(
                "FinGrind",
                "0.9.0",
                "Finance-grade bookkeeping kernel with an agent-first CLI and SQLite-first persistence"),
            new MachineContract.EnvironmentDescriptor(
                "self-contained-bundle",
                "26+",
                "sqlite-ffm-sqlite3mc",
                "sqlite",
                "required",
                "chacha20",
                "managed-only",
                "FINGRIND_SQLITE_LIBRARY",
                "fingrind.bundle.home",
                List.of("THREADSAFE=1", "OMIT_LOAD_EXTENSION", "TEMP_STORE=3", "SECURE_DELETE"),
                false,
                "3.53.0",
                "2.3.3",
                "unavailable",
                null,
                null,
                "system sqlite unavailable"),
            Instant.parse("2026-04-13T12:00:00Z")));

    JsonNode json = readJson(outputStream);
    JsonNode payload = json.path("payload");
    JsonNode environment = payload.path("environment");

    assertTrue(payload.has("preflightSemantics"));
    assertTrue(payload.has("currencyModel"));
    assertEquals("required", environment.path("bookProtectionMode").asString());
    assertEquals("self-contained-bundle", environment.path("publicCliDistribution").asString());
    assertFalse(environment.has("loadedSqliteVersion"));
    assertFalse(environment.has("loadedSqlite3mcVersion"));
  }

  @Test
  void writeGenerateBookKeyFileResult_writesNonSecretMetadataEnvelope() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    responseWriter.writeGenerateBookKeyFileResult(
        new dev.erst.fingrind.sqlite.SqliteBookKeyFileGenerator.GeneratedKeyFile(
            Path.of("secrets").resolve("entity.book-key"), "base64url-no-padding", 256, "0600"));

    JsonNode json = readJson(outputStream);
    assertEquals("ok", json.path("status").asString());
    assertEquals(
        Path.of("secrets").resolve("entity.book-key").toAbsolutePath().normalize().toString(),
        json.path("payload").path("bookKeyFile").asString());
    assertEquals("base64url-no-padding", json.path("payload").path("encoding").asString());
    assertEquals(256, json.path("payload").path("entropyBits").asInt());
    assertEquals("0600", json.path("payload").path("permissions").asString());
  }

  @Test
  void writeFailure_writesErrorEnvelope() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    responseWriter.writeFailure("invalid-request", "bad request");

    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("\"status\":\"error\""));
  }

  @Test
  void writePostEntryResult_writesPreflightEnvelope() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    responseWriter.writePostEntryResult(
        new PostEntryResult.PreflightAccepted(
            new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")));

    assertTrue(
        outputStream
            .toString(StandardCharsets.UTF_8)
            .contains("\"status\":\"preflight-accepted\""));
  }

  @Test
  void writePostEntryResult_writesCommittedEnvelope() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    responseWriter.writePostEntryResult(
        new PostEntryResult.Committed(
            new PostingId("posting-1"),
            new IdempotencyKey("idem-1"),
            LocalDate.parse("2026-04-07"),
            Instant.parse("2026-04-07T10:15:30Z")));

    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("\"status\":\"committed\""));
  }

  @Test
  void writePostEntryResult_writesRejectedEnvelopeWithStructuredDetails() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    responseWriter.writePostEntryResult(
        new PostEntryResult.Rejected(
            new IdempotencyKey("idem-1"),
            new PostingRejection.ReversalTargetNotFound(new PostingId("posting-1"))));

    String json = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(json.contains("\"status\":\"rejected\""));
    assertTrue(json.contains("\"code\":\"reversal-target-not-found\""));
    assertTrue(json.contains("\"priorPostingId\":\"posting-1\""));
  }

  @Test
  void writePostEntryResult_writesDuplicateIdempotencyRejectionWithoutDetails() {
    String json = rejectedJson(new PostingRejection.DuplicateIdempotencyKey());

    assertTrue(json.contains("\"code\":\"duplicate-idempotency-key\""));
    assertTrue(json.contains("same idempotency key"));
    assertFalse(json.contains("\"details\""));
  }

  @Test
  void writePostEntryResult_writesReversalReasonRequiredRejection() {
    String json = rejectedJson(new PostingRejection.ReversalReasonRequired());

    assertTrue(json.contains("\"code\":\"reversal-reason-required\""));
    assertTrue(json.contains("must include a human-readable reason"));
  }

  @Test
  void writePostEntryResult_writesReversalReasonForbiddenRejection() {
    String json = rejectedJson(new PostingRejection.ReversalReasonForbidden());

    assertTrue(json.contains("\"code\":\"reversal-reason-forbidden\""));
    assertTrue(json.contains("only permitted when a reversal target is present"));
  }

  @Test
  void writePostEntryResult_writesReversalAlreadyExistsRejection() {
    String json =
        rejectedJson(new PostingRejection.ReversalAlreadyExists(new PostingId("posting-1")));

    assertTrue(json.contains("\"code\":\"reversal-already-exists\""));
    assertTrue(json.contains("already has a full reversal"));
    assertTrue(json.contains("\"priorPostingId\":\"posting-1\""));
  }

  @Test
  void writePostEntryResult_writesReversalDoesNotNegateTargetRejection() {
    String json =
        rejectedJson(new PostingRejection.ReversalDoesNotNegateTarget(new PostingId("posting-1")));

    assertTrue(json.contains("\"code\":\"reversal-does-not-negate-target\""));
    assertTrue(json.contains("does not negate posting"));
    assertTrue(json.contains("\"priorPostingId\":\"posting-1\""));
  }

  @Test
  void writePostEntryResult_writesBookInitializationAndAccountRejections() {
    String bookJson = rejectedJson(new PostingRejection.BookNotInitialized());
    String unknownJson = rejectedJson(new PostingRejection.UnknownAccount(new AccountCode("1000")));
    String inactiveJson =
        rejectedJson(new PostingRejection.InactiveAccount(new AccountCode("1000")));

    assertTrue(bookJson.contains("\"code\":\"book-not-initialized\""));
    assertTrue(unknownJson.contains("\"accountCode\":\"1000\""));
    assertTrue(inactiveJson.contains("\"code\":\"inactive-account\""));
  }

  @Test
  void writeOpenBookResult_writesSuccessEnvelope() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    responseWriter.writeOpenBookResult(
        Path.of("book.sqlite"), new OpenBookResult.Opened(Instant.parse("2026-04-07T10:15:30Z")));

    String json = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(json.contains("\"status\":\"ok\""));
    assertTrue(json.contains("\"bookFile\""));
    assertTrue(json.contains("\"initializedAt\":\"2026-04-07T10:15:30Z\""));
  }

  @Test
  void writeOpenBookResult_writesAlreadyInitializedAndSchemaConflictRejections() {
    String alreadyInitializedJson =
        openBookRejectedJson(new BookAdministrationRejection.BookAlreadyInitialized());
    String schemaConflictJson =
        openBookRejectedJson(new BookAdministrationRejection.BookContainsSchema());

    assertTrue(alreadyInitializedJson.contains("\"code\":\"book-already-initialized\""));
    assertTrue(alreadyInitializedJson.contains("already initialized"));
    assertTrue(schemaConflictJson.contains("\"code\":\"book-contains-schema\""));
    assertTrue(schemaConflictJson.contains("already contains schema objects"));
  }

  @Test
  void writeRekeyBookResult_writesSuccessAndRejectionEnvelopes() {
    ByteArrayOutputStream successOutput = new ByteArrayOutputStream();
    CliResponseWriter successWriter = new CliResponseWriter(utf8PrintStream(successOutput));

    successWriter.writeRekeyBookResult(
        new RekeyBookResult.Rekeyed(Path.of("books").resolve("entity.sqlite")));

    String successJson = successOutput.toString(StandardCharsets.UTF_8);
    assertTrue(successJson.contains("\"status\":\"ok\""));
    assertTrue(successJson.contains("\"bookFile\""));

    ByteArrayOutputStream rejectionOutput = new ByteArrayOutputStream();
    CliResponseWriter rejectionWriter = new CliResponseWriter(utf8PrintStream(rejectionOutput));

    rejectionWriter.writeRekeyBookResult(
        new RekeyBookResult.Rejected(new BookAdministrationRejection.BookNotInitialized()));

    String rejectionJson = rejectionOutput.toString(StandardCharsets.UTF_8);
    assertTrue(rejectionJson.contains("\"status\":\"rejected\""));
    assertTrue(rejectionJson.contains("\"code\":\"book-not-initialized\""));
  }

  @Test
  void writeDeclareAccountAndListAccountsResults_writeSuccessAndRejectionEnvelopes() {
    ByteArrayOutputStream successOutput = new ByteArrayOutputStream();
    CliResponseWriter successWriter = new CliResponseWriter(utf8PrintStream(successOutput));
    DeclaredAccount declaredAccount =
        new DeclaredAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            NormalBalance.DEBIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));

    successWriter.writeDeclareAccountResult(new DeclareAccountResult.Declared(declaredAccount));
    successWriter.writeListAccountsResult(
        new ListAccountsResult.Listed(java.util.List.of(declaredAccount)));

    String successJson = successOutput.toString(StandardCharsets.UTF_8);
    assertTrue(successJson.contains("\"accountName\":\"Cash\""));
    assertTrue(successJson.contains("\"declaredAt\":\"2026-04-07T10:15:30Z\""));

    ByteArrayOutputStream declareRejectionOutput = new ByteArrayOutputStream();
    CliResponseWriter declareRejectionWriter =
        new CliResponseWriter(utf8PrintStream(declareRejectionOutput));
    declareRejectionWriter.writeDeclareAccountResult(
        new DeclareAccountResult.Rejected(new BookAdministrationRejection.BookNotInitialized()));

    ByteArrayOutputStream listRejectionOutput = new ByteArrayOutputStream();
    CliResponseWriter listRejectionWriter =
        new CliResponseWriter(utf8PrintStream(listRejectionOutput));
    listRejectionWriter.writeListAccountsResult(
        new ListAccountsResult.Rejected(
            new BookAdministrationRejection.NormalBalanceConflict(
                new AccountCode("1000"), NormalBalance.DEBIT, NormalBalance.CREDIT)));

    assertTrue(
        declareRejectionOutput
            .toString(StandardCharsets.UTF_8)
            .contains("\"code\":\"book-not-initialized\""));
    assertTrue(
        listRejectionOutput
            .toString(StandardCharsets.UTF_8)
            .contains("\"code\":\"account-normal-balance-conflict\""));
  }

  @Test
  void withoutNulls_handlesJsonNullsObjectNullFieldsAndNullArrayEntries()
      throws NoSuchMethodException, IllegalAccessException {
    MethodHandle withoutNulls =
        MethodHandles.privateLookupIn(CliResponseWriter.class, MethodHandles.lookup())
            .findStatic(
                CliResponseWriter.class,
                "withoutNulls",
                MethodType.methodType(JsonNode.class, JsonNode.class));
    ObjectMapper objectMapper = new ObjectMapper();

    assertTrue(invokeWithoutNulls(withoutNulls, objectMapper.valueToTree(null)).isNull());

    ObjectNode objectNode = objectMapper.createObjectNode();
    objectNode.put("kept", "value");
    objectNode.putNull("jsonNull");
    ObjectNode nestedObject = objectMapper.createObjectNode();
    nestedObject.put("nestedKept", "nested-value");
    nestedObject.putNull("nestedNull");
    objectNode.set("nested", nestedObject);
    ArrayNode arrayNode = objectMapper.createArrayNode();
    arrayNode.add("entry");
    arrayNode.addNull();
    objectNode.set("array", arrayNode);

    JsonNode sanitized = invokeWithoutNulls(withoutNulls, objectNode);
    assertEquals("value", sanitized.path("kept").asString());
    assertFalse(sanitized.has("jsonNull"));
    assertFalse(sanitized.path("nested").has("nestedNull"));
    assertEquals("nested-value", sanitized.path("nested").path("nestedKept").asString());
    assertEquals("entry", sanitized.path("array").get(0).asString());
    assertTrue(sanitized.path("array").get(1).isNull());
  }

  private static PrintStream utf8PrintStream(ByteArrayOutputStream outputStream) {
    return new PrintStream(outputStream, false, StandardCharsets.UTF_8);
  }

  private static String rejectedJson(PostingRejection rejection) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    responseWriter.writePostEntryResult(
        new PostEntryResult.Rejected(new IdempotencyKey("idem-1"), rejection));

    return outputStream.toString(StandardCharsets.UTF_8);
  }

  private static String openBookRejectedJson(BookAdministrationRejection rejection) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    responseWriter.writeOpenBookResult(
        Path.of("book.sqlite"), new OpenBookResult.Rejected(rejection));

    return outputStream.toString(StandardCharsets.UTF_8);
  }

  private static JsonNode readJson(ByteArrayOutputStream outputStream) throws IOException {
    return new ObjectMapper().readTree(outputStream.toString(StandardCharsets.UTF_8));
  }

  private static JsonNode invokeWithoutNulls(MethodHandle withoutNulls, JsonNode node) {
    try {
      return (JsonNode) withoutNulls.invoke(node);
    } catch (Throwable throwable) {
      throw new AssertionError("withoutNulls invocation failed", throwable);
    }
  }
}
