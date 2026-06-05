package dev.erst.fingrind.cli;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.RuntimeDistribution;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeProvenance;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeStatus;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeTrustBasis;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.contract.runtime.EnvironmentDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentPublicationDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentRuntimeDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentSqliteDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentStorageDescriptor;
import dev.erst.fingrind.contract.runtime.SqliteCompileOptionsVerificationStatus;
import dev.erst.fingrind.contract.workflow.LedgerPlanId;
import dev.erst.fingrind.contract.workflow.LedgerStepId;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.sqlite.SqliteRuntime;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Shared payload fixtures and JSON helpers for split CLI response writer tests. */
class CliResponseWriterTestSupport extends CliIoFixtureSupport {
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
        PostingKind.STANDARD,
        dev.erst.fingrind.core.PostingOriginKind.REVERSAL_ADJUSTMENT,
        CliFixtureSupport.accountingEvidence("idem-1"),
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

  static PostingFact postingFactWithApproval() {
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
        PostingKind.STANDARD,
        dev.erst.fingrind.core.PostingOriginKind.REVERSAL_ADJUSTMENT,
        CliFixtureSupport.accountingEvidenceWithApproval("idem-1"),
        new CommittedProvenance(
            new RequestProvenance(
                new ActorId("actor-1"),
                ActorType.AGENT,
                new CommandId("command-1"),
                new IdempotencyKey("idem-1"),
                new CausationId("cause-1"),
                Optional.of(new CorrelationId("corr-1"))),
            Instant.parse("2026-04-07T10:15:30Z"),
            SourceChannel.CLI));
  }

  static DeclaredAccount declaredCashAccount() {
    return CliIoFixtureSupport.declaredAccount(
        "1000",
        "Cash",
        dev.erst.fingrind.core.AccountType.ASSET,
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
    CurrencyBalance balance =
        CurrencyBalance.ofTotals(money(currencyCode, debitTotal), money(currencyCode, creditTotal));
    if (!balance.netAmount().equals(money(currencyCode, netAmount))
        || balance.balanceSide() != balanceSide) {
      throw new IllegalArgumentException("Test fixture balance does not match derived totals.");
    }
    return balance;
  }

  static Money money(String currencyCode, String amount) {
    return Money.parse(currencyCode, amount);
  }

  protected static PrintStream utf8PrintStream(ByteArrayOutputStream outputStream) {
    return new PrintStream(outputStream, false, StandardCharsets.UTF_8);
  }

  protected static CliOutputChannel outputChannel(ByteArrayOutputStream outputStream) {
    return new CliOutputChannel(utf8PrintStream(outputStream));
  }

  protected static CliFailureResponseWriter failureWriter(ByteArrayOutputStream outputStream) {
    return new CliFailureResponseWriter(outputChannel(outputStream));
  }

  protected static CliDiscoveryResponseWriter discoveryWriter(ByteArrayOutputStream outputStream) {
    return new CliDiscoveryResponseWriter(outputChannel(outputStream));
  }

  protected static CliMutationResponseWriter mutationWriter(ByteArrayOutputStream outputStream) {
    return new CliMutationResponseWriter(outputChannel(outputStream));
  }

  protected static CliBookReadResponseWriter bookReadWriter(ByteArrayOutputStream outputStream) {
    return new CliBookReadResponseWriter(outputChannel(outputStream));
  }

  protected static CliReportResponseWriter reportWriter(ByteArrayOutputStream outputStream) {
    return new CliReportResponseWriter(outputChannel(outputStream));
  }

  protected static CliPlanResponseWriter planWriter(ByteArrayOutputStream outputStream) {
    return new CliPlanResponseWriter(outputChannel(outputStream));
  }

  static EnvironmentDescriptor environmentDescriptor(
      String runtimeDistribution,
      SqliteCompileOptionsVerificationStatus compileOptionsVerification,
      String state,
      @Nullable String loadedSqliteVersion,
      @Nullable String loadedSqlite3mcVersion,
      @Nullable String diagnostics) {
    SqliteRuntimeStatus runtimeStatus = SqliteRuntimeStatus.fromWireValue(state);
    SqliteRuntimeProvenance runtimeProvenance =
        runtimeStatus == SqliteRuntimeStatus.UNAVAILABLE
            ? null
            : SqliteRuntimeProvenance.BUNDLE_MANAGED;
    SqliteRuntimeTrustBasis runtimeTrustBasis =
        runtimeProvenance == null
            ? null
            : SqliteRuntimeTrustBasis.fromProvenance(runtimeProvenance);
    String loadedLibraryPath =
        runtimeStatus == SqliteRuntimeStatus.UNAVAILABLE ? null : "<redacted>/libsqlite3.dylib";
    String loadedSqliteSourceId =
        runtimeStatus == SqliteRuntimeStatus.UNAVAILABLE
            ? null
            : SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID;
    return new EnvironmentDescriptor(
        new EnvironmentRuntimeDescriptor(RuntimeDistribution.fromWireValue(runtimeDistribution)),
        new EnvironmentPublicationDescriptor(
            ProtocolCatalog.distribution().publicCliDistribution(),
            ProtocolCatalog.distribution().supportedPublicCliBundleTargets(),
            ProtocolCatalog.distribution().unsupportedPublicCliBundleTargets(),
            ProtocolCatalog.distribution().sourceCheckoutJava()),
        new EnvironmentStorageDescriptor(
            ProtocolCatalog.runtime().storageDriver(),
            ProtocolCatalog.runtime().storageEngine(),
            ProtocolCatalog.runtime().bookProtectionMode(),
            ProtocolCatalog.runtime().protectedBookFormat()),
        new EnvironmentSqliteDescriptor(
            ProtocolCatalog.runtime().sqliteLibraryMode(),
            ProtocolCatalog.runtime().sqliteBundleHomeSystemProperty(),
            SqliteRuntime.REQUIRED_SQLITE_COMPILE_OPTIONS,
            SqliteRuntime.FORBIDDEN_SQLITE_COMPILE_OPTIONS,
            SqliteRuntime.REQUIRES_SECURE_MEMORY_SUPPORT,
            SqliteRuntime.REQUIRED_MINIMUM_SQLITE_VERSION,
            SqliteRuntime.REQUIRED_SQLITE3MC_VERSION,
            SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
            EnvironmentSqliteDescriptor.runtime(
                compileOptionsVerification,
                runtimeStatus,
                runtimeProvenance,
                runtimeTrustBasis,
                loadedLibraryPath,
                loadedSqliteVersion,
                loadedSqlite3mcVersion,
                loadedSqliteSourceId,
                diagnostics),
            null));
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

  protected static List<String> readTextArray(JsonNode node) {
    List<String> values = new java.util.ArrayList<>();
    node.forEach(element -> values.add(element.stringValue()));
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
    @org.jspecify.annotations.NullUnmarked
    public String wireValue() {
      return null;
    }
  }

  /** Value whose accessor throws so unrelated serializer failures are not rewritten. */
  static final class ExplodingGetter {
    final boolean explode;
    final @Nullable String failureMessage;

    ExplodingGetter() {
      this(true, "boom");
    }

    ExplodingGetter(boolean explode, @Nullable String failureMessage) {
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
