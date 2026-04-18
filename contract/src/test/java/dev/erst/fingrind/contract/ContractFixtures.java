package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Shared contract test fixtures. */
final class ContractFixtures {
  private ContractFixtures() {}

  static PostEntryCommand postEntryCommand(String idempotencyKey) {
    return new PostEntryCommand(
        new JournalEntry(
            LocalDate.parse("2026-04-07"),
            List.of(
                new JournalLine(
                    new AccountCode("1000"),
                    JournalLine.EntrySide.DEBIT,
                    new Money(new CurrencyCode("EUR"), new BigDecimal("10.00"))),
                new JournalLine(
                    new AccountCode("2000"),
                    JournalLine.EntrySide.CREDIT,
                    new Money(new CurrencyCode("EUR"), new BigDecimal("10.00"))))),
        PostingLineage.direct(),
        new RequestProvenance(
            new ActorId("actor-1"),
            ActorType.AGENT,
            new CommandId("command-1"),
            new IdempotencyKey(idempotencyKey),
            new CausationId("cause-1"),
            Optional.of(new CorrelationId("corr-1"))),
        SourceChannel.CLI);
  }

  static ContractDiscovery.EnvironmentDescriptor environmentDescriptor() {
    return new ContractDiscovery.EnvironmentDescriptor(
        "source-checkout",
        "self-contained-bundle",
        ProtocolCatalog.supportedPublicCliBundleTargets(),
        ProtocolCatalog.unsupportedPublicCliOperatingSystems(),
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
        "test fixture");
  }
}
