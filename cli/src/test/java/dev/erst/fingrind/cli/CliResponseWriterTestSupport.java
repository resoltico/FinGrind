package dev.erst.fingrind.cli;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.erst.fingrind.contract.BookAdministrationRejection;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.CurrencyBalance;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.EnvironmentDescriptor;
import dev.erst.fingrind.contract.EnvironmentDistributionDescriptor;
import dev.erst.fingrind.contract.EnvironmentSqliteDescriptor;
import dev.erst.fingrind.contract.EnvironmentStorageDescriptor;
import dev.erst.fingrind.contract.LedgerPlanId;
import dev.erst.fingrind.contract.LedgerStepId;
import dev.erst.fingrind.contract.OpenBookResult;
import dev.erst.fingrind.contract.PostEntryResult;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingLineage;
import dev.erst.fingrind.contract.PostingRejection;
import dev.erst.fingrind.contract.SqliteCompileOptionsVerificationStatus;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.RuntimeDistribution;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeStatus;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.sqlite.SqliteRuntime;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.NullUnmarked;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Shared payload fixtures and JSON helpers for split CLI response writer tests. */
@NullUnmarked
class CliResponseWriterTestSupport {
  protected CliResponseWriterTestSupport() {}

  static PostingFact postingFact() {
    return new PostingFact(
        new PostingId("posting-1"),
        new JournalEntry(
            LocalDate.parse("2026-04-07"),
            List.of(
                new JournalLine(
                    new AccountCode("1000"), JournalLine.EntrySide.DEBIT, money("EUR", "10.00")),
                new JournalLine(
                    new AccountCode("2000"), JournalLine.EntrySide.CREDIT, money("EUR", "10.00")))),
        PostingLineage.reversal(
            new ReversalReference(new PostingId("posting-0")), new ReversalReason("full reversal")),
        new CommittedProvenance(
            new RequestProvenance(
                new ActorId("actor-1"),
                ActorType.AGENT,
                new CommandId("command-1"),
                new IdempotencyKey("idem-1"),
                new CausationId("cause-1"),
                java.util.Optional.of(new CorrelationId("corr-1"))),
            Instant.parse("2026-04-07T10:15:30Z"),
            SourceChannel.CLI));
  }

  static DeclaredAccount declaredCashAccount() {
    return new DeclaredAccount(
        new AccountCode("1000"),
        new AccountName("Cash"),
        NormalBalance.DEBIT,
        true,
        Instant.parse("2026-04-07T10:15:30Z"));
  }

  static CurrencyBalance currencyBalance(
      String currencyCode,
      String debitTotal,
      String creditTotal,
      String netAmount,
      BalanceSide balanceSide) {
    return new CurrencyBalance(
        money(currencyCode, debitTotal),
        money(currencyCode, creditTotal),
        money(currencyCode, netAmount),
        balanceSide);
  }

  static Money money(String currencyCode, String amount) {
    return new Money(new CurrencyCode(currencyCode), new BigDecimal(amount));
  }

  static PrintStream utf8PrintStream(ByteArrayOutputStream outputStream) {
    return new PrintStream(outputStream, false, StandardCharsets.UTF_8);
  }

  static EnvironmentDescriptor environmentDescriptor(
      String runtimeDistribution,
      SqliteCompileOptionsVerificationStatus compileOptionsVerification,
      String state,
      String loadedSqliteVersion,
      String loadedSqlite3mcVersion,
      String diagnostics) {
    return new EnvironmentDescriptor(
        new EnvironmentDistributionDescriptor(
            RuntimeDistribution.fromWireValue(runtimeDistribution),
            ProtocolCatalog.publicCliDistribution(),
            ProtocolCatalog.supportedPublicCliBundleTargets(),
            ProtocolCatalog.unsupportedPublicCliOperatingSystems(),
            ProtocolCatalog.sourceCheckoutJava()),
        new EnvironmentStorageDescriptor(
            ProtocolCatalog.storageDriver(),
            ProtocolCatalog.storageEngine(),
            ProtocolCatalog.bookProtectionMode(),
            ProtocolCatalog.defaultBookCipher()),
        new EnvironmentSqliteDescriptor(
            ProtocolCatalog.sqliteLibraryMode(),
            ProtocolCatalog.sqliteLibraryEnvironmentVariable(),
            ProtocolCatalog.sqliteBundleHomeSystemProperty(),
            SqliteRuntime.REQUIRED_SQLITE_COMPILE_OPTIONS,
            compileOptionsVerification,
            SqliteRuntime.REQUIRED_MINIMUM_SQLITE_VERSION,
            SqliteRuntime.REQUIRED_SQLITE3MC_VERSION,
            SqliteRuntimeStatus.fromWireValue(state),
            loadedSqliteVersion,
            loadedSqlite3mcVersion,
            diagnostics));
  }

  static String rejectedJson(PostingRejection rejection) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    responseWriter.writePostEntryResult(
        new PostEntryResult.CommitRejected(new IdempotencyKey("idem-1"), rejection));

    return outputStream.toString(StandardCharsets.UTF_8);
  }

  static String openBookRejectedJson(BookAdministrationRejection rejection) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    responseWriter.writeOpenBookResult(
        Path.of("book.sqlite"), new OpenBookResult.Rejected(rejection));

    return outputStream.toString(StandardCharsets.UTF_8);
  }

  protected final JsonNode readJson(ByteArrayOutputStream outputStream) throws IOException {
    return new ObjectMapper().readTree(outputStream.toString(StandardCharsets.UTF_8));
  }

  static LedgerPlanId planId(String value) {
    return new LedgerPlanId(value);
  }

  static LedgerStepId stepId(String value) {
    return new LedgerStepId(value);
  }

  protected final JsonNode writeInspection(BookInspection inspection) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));
    responseWriter.writeBookInspection(Path.of("book.sqlite"), inspection);
    return readJson(outputStream).path("payload");
  }

  static List<String> readTextArray(JsonNode node) {
    List<String> values = new java.util.ArrayList<>();
    node.forEach(element -> values.add(element.asText()));
    return List.copyOf(values);
  }

  static boolean causeChainContains(Throwable exception, String text) {
    Throwable current = exception;
    while (current != null) {
      String message = current.getMessage();
      if (message != null && message.contains(text)) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  /** Mixed project-enum payload used to verify stable JSON wire values. */
  record EnumPayload(OutputMode outputMode, NormalBalance normalBalance) {}

  /** Non-project enum payload used to verify fallback enum serialization. */
  record ExternalEnumPayload(Thread.State state) {}

  /** Payload whose enum omits wireValue() to verify explicit enforcement. */
  record MissingWireValuePayload(MissingWireValue value) {}

  /** Payload whose enum returns blank wire text to verify validation. */
  record BlankWireValuePayload(CliBlankWireValueFixture value) {}

  /** Payload whose enum returns null wire text to verify validation. */
  record NullWireValuePayload(NullWireValue value) {}

  /** Payload whose enum throws during wireValue() resolution. */
  record ExplodingWireValuePayload(CliExplodingWireValueFixture value) {}

  /** Test-only enum that intentionally omits wireValue() to verify CLI JSON enforcement. */
  enum MissingWireValue {
    UNSAFE
  }

  /** Test-only enum that intentionally returns null to verify CLI JSON validation. */
  enum NullWireValue implements dev.erst.fingrind.core.WireValue {
    UNSAFE;

    @Override
    public String wireValue() {
      return null;
    }
  }

  /** Value whose accessor throws so unrelated serializer failures are not rewritten. */
  static final class ExplodingGetter {
    final boolean explode;
    final String failureMessage;

    ExplodingGetter() {
      this(true, "boom");
    }

    ExplodingGetter(boolean explode, String failureMessage) {
      this.explode = explode;
      this.failureMessage = failureMessage;
    }

    @JsonProperty("value")
    String value() {
      if (explode) {
        if (failureMessage == null) {
          throw new IllegalStateException();
        }
        throw new IllegalStateException(failureMessage);
      }
      return "safe";
    }
  }

  /** Deliberately self-referential value used to force a serializer failure. */
  static final class SelfReferentialValue {
    @JsonProperty("self")
    Object self() {
      return this;
    }
  }
}
