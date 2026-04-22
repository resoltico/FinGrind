package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.ApplicationIdentity;
import dev.erst.fingrind.contract.CommandDescriptor;
import dev.erst.fingrind.contract.ContractResponse;
import dev.erst.fingrind.contract.EnvironmentDescriptor;
import dev.erst.fingrind.contract.EnvironmentDistributionDescriptor;
import dev.erst.fingrind.contract.EnvironmentSqliteDescriptor;
import dev.erst.fingrind.contract.EnvironmentStorageDescriptor;
import dev.erst.fingrind.contract.ExitCodeDescriptor;
import dev.erst.fingrind.contract.HelpDescriptor;
import dev.erst.fingrind.contract.MachineContract;
import dev.erst.fingrind.contract.SqliteCompileOptionsVerificationStatus;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliDiscoveryOutputRenderer}. */
class CliDiscoveryOutputRendererTest {
  @Test
  void renderHelpHuman_rendersImplicitJsonOutputsAndEmptyQuickStart() {
    String rendered =
        CliDiscoveryOutputRenderer.renderHelpHuman(
            new HelpDescriptor(
                "FinGrind",
                "0.22.0",
                "desc",
                List.of("fingrind help"),
                new ContractResponse.BookModelDescriptor(
                    "single-sqlite-file",
                    "entity-book",
                    "local-path",
                    "key-file",
                    "explicit-open-book",
                    "declared-accounts",
                    "hard-break-only",
                    "single-currency-entry"),
                List.of(
                    new CommandDescriptor(
                        "help",
                        List.of(),
                        List.of(),
                        "single-shot",
                        List.of(),
                        List.of(),
                        "Show help")),
                List.of(),
                List.of(new ExitCodeDescriptor(0, "ok")),
                new ContractResponse.PreflightDescriptor(
                    "advisory", ContractResponse.CommitGuarantee.NOT_GUARANTEED, "desc"),
                new ContractResponse.CurrencyDescriptor("per-entry", "single-entry", "desc"),
                environment()));

    assertTrue(rendered.contains("FinGrind Help"));
    assertTrue(rendered.contains("help"));
    assertTrue(rendered.contains("(json)"));
    assertTrue(rendered.contains("Quick Start"));
    assertTrue(rendered.contains("(none)"));
  }

  @Test
  void renderCapabilitiesHuman_rendersCommandGroupsAndReadSurface() {
    String rendered =
        CliDiscoveryOutputRenderer.renderCapabilitiesHuman(
            MachineContract.capabilities(
                identity(), environment(), Instant.parse("2026-04-19T08:00:00Z")));

    assertTrue(rendered.contains("FinGrind Capabilities"));
    assertTrue(rendered.contains("Command Groups"));
    assertTrue(rendered.contains("Read Surface"));
    assertTrue(rendered.contains("Discovery"));
    assertTrue(rendered.contains("Query/report stdout"));
  }

  @Test
  void renderVersionHuman_rendersTitleAndKeyValues() {
    String rendered =
        CliDiscoveryOutputRenderer.renderVersionHuman(MachineContract.version(identity()));

    assertTrue(rendered.contains("FinGrind"));
    assertTrue(rendered.contains("Version"));
    assertTrue(rendered.contains("0.22.0"));
  }

  private static ApplicationIdentity identity() {
    return new ApplicationIdentity(
        "FinGrind",
        "0.22.0",
        "Finance-grade bookkeeping kernel with an agent-first CLI and SQLite-first persistence");
  }

  private static EnvironmentDescriptor environment() {
    return new EnvironmentDescriptor(
        new EnvironmentDistributionDescriptor(
            "self-contained-bundle",
            "self-contained-bundle",
            List.of("macos-aarch64", "windows-x86_64"),
            List.of(),
            ProtocolCatalog.sourceCheckoutJava()),
        new EnvironmentStorageDescriptor("sqlite-ffm-sqlite3mc", "sqlite", "required", "chacha20"),
        new EnvironmentSqliteDescriptor(
            "managed-only",
            "FINGRIND_SQLITE_LIBRARY",
            "fingrind.bundle.home",
            List.of("THREADSAFE=1"),
            SqliteCompileOptionsVerificationStatus.VERIFIED,
            "3.53.0",
            "2.3.3",
            "loaded",
            "3.53.0",
            "2.3.3",
            null));
  }
}
