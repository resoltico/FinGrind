package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.NormalBalance;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link MachineContract}. */
class MachineContractTest {
  @Test
  void capabilities_areDerivedFromLiveEnumsAndRejectionCatalogs() {
    MachineContract.CapabilitiesDescriptor capabilities =
        MachineContract.capabilities(
            new MachineContract.ApplicationIdentity("FinGrind", "0.9.0", "desc"),
            new MachineContract.EnvironmentDescriptor(
                "self-contained-bundle",
                "self-contained-bundle",
                List.of(
                    "macos-aarch64",
                    "macos-x86_64",
                    "linux-x86_64",
                    "linux-aarch64",
                    "windows-x86_64"),
                List.of(),
                "26+",
                "sqlite-ffm-sqlite3mc",
                "sqlite",
                "required",
                "chacha20",
                "managed-only",
                "FINGRIND_SQLITE_LIBRARY",
                "fingrind.bundle.home",
                List.of("THREADSAFE=1", "OMIT_LOAD_EXTENSION", "TEMP_STORE=3", "SECURE_DELETE"),
                true,
                "3.53.0",
                "2.3.3",
                "ready",
                "3.53.0",
                "2.3.3",
                null),
            Instant.parse("2026-04-13T12:00:00Z"));

    assertEquals("advisory", capabilities.preflightSemantics());
    assertFalse(capabilities.preflight().isCommitGuarantee());
    assertEquals("single-currency-per-entry", capabilities.currencyModel().scope());
    assertEquals("not-supported", capabilities.currencyModel().multiCurrencyStatus());
    assertEquals(
        List.of("--book-key-file", "--book-passphrase-stdin", "--book-passphrase-prompt"),
        capabilities.requestInput().bookPassphraseOptions());
    assertTrue(
        capabilities
            .requestInput()
            .requestDocumentSemantics()
            .contains("duplicate JSON object keys are rejected"));

    assertEquals(
        enumValues(JournalLine.EntrySide.values()),
        vocabularyValues(capabilities.requestShapes().postEntry().enumVocabularies(), "lineSide"));
    assertEquals(
        enumValues(ActorType.values()),
        vocabularyValues(capabilities.requestShapes().postEntry().enumVocabularies(), "actorType"));
    assertEquals(
        enumValues(NormalBalance.values()),
        vocabularyValues(
            capabilities.requestShapes().declareAccount().enumVocabularies(), "normalBalance"));

    List<String> rejectionCodes =
        capabilities.responseModel().rejections().stream()
            .map(MachineContract.RejectionDescriptor::code)
            .toList();
    assertTrue(rejectionCodes.contains("book-not-initialized"));
    assertTrue(rejectionCodes.contains("account-normal-balance-conflict"));
    assertTrue(rejectionCodes.contains("posting-not-found"));
    assertTrue(rejectionCodes.contains("reversal-does-not-negate-target"));
    assertEquals(rejectionCodes.size(), rejectionCodes.stream().distinct().count());
  }

  @Test
  void rejectionCatalogs_coverEveryPermittedSubtype() {
    assertEquals(
        BookAdministrationRejection.class.getPermittedSubclasses().length,
        BookAdministrationRejection.descriptors().size());
    assertEquals(
        PostingRejection.class.getPermittedSubclasses().length,
        PostingRejection.descriptors().size());
    assertEquals(
        BookQueryRejection.class.getPermittedSubclasses().length,
        BookQueryRejection.descriptors().size());
  }

  @Test
  void helpVersionAndRequestTemplate_publishCanonicalDiscoveryMetadata() {
    MachineContract.ApplicationIdentity identity =
        new MachineContract.ApplicationIdentity("FinGrind", "0.9.0", "desc");
    MachineContract.EnvironmentDescriptor environment =
        new MachineContract.EnvironmentDescriptor(
            "self-contained-bundle",
            "self-contained-bundle",
            List.of(
                "macos-aarch64", "macos-x86_64", "linux-x86_64", "linux-aarch64", "windows-x86_64"),
            List.of(),
            "26+",
            "sqlite-ffm-sqlite3mc",
            "sqlite",
            "required",
            "chacha20",
            "managed-only",
            "FINGRIND_SQLITE_LIBRARY",
            "fingrind.bundle.home",
            List.of("THREADSAFE=1", "OMIT_LOAD_EXTENSION", "TEMP_STORE=3", "SECURE_DELETE"),
            true,
            "3.53.0",
            "2.3.3",
            "ready",
            "3.53.0",
            "2.3.3",
            null);

    MachineContract.HelpDescriptor help = MachineContract.help(identity, environment);
    MachineContract.VersionDescriptor version = MachineContract.version(identity);
    MachineContract.PostingRequestTemplateDescriptor template =
        MachineContract.requestTemplate(
            Clock.fixed(Instant.parse("2026-04-13T12:00:00Z"), java.time.ZoneOffset.UTC));
    MachineContract.ReversalTemplateDescriptor reversalTemplate =
        new MachineContract.ReversalTemplateDescriptor("posting-1");

    assertEquals("FinGrind", help.application());
    assertEquals("single-currency-per-entry", help.bookModel().currencyScope());
    assertEquals(17, help.commands().size());
    assertEquals("generate-book-key-file", help.commands().get(5).name());
    assertEquals("open-book", help.commands().get(6).name());
    assertEquals("rekey-book", help.commands().get(7).name());
    assertEquals("inspect-book", help.commands().get(9).name());
    assertEquals("account-balance", help.commands().get(13).name());
    assertTrue(help.commands().get(7).options().get(2).contains("--new-book-passphrase-prompt"));
    assertEquals(3, help.exitCodes().size());
    assertEquals("advisory", help.preflight().semantics());
    assertEquals(environment, help.environment());

    assertEquals("0.9.0", version.version());
    assertEquals(
        List.of("macos-aarch64", "macos-x86_64", "linux-x86_64", "linux-aarch64", "windows-x86_64"),
        MachineContract.supportedPublicCliBundleTargets());
    assertEquals(List.of(), MachineContract.unsupportedPublicCliOperatingSystems());
    assertEquals("2026-04-13", template.effectiveDate());
    assertEquals("1000", template.lines().get(0).accountCode());
    assertEquals("USER", template.provenance().actorType());
    assertEquals("posting-1", reversalTemplate.priorPostingId());
  }

  private static List<String> enumValues(Enum<?>[] values) {
    return Arrays.stream(values).map(Enum::name).toList();
  }

  private static List<String> vocabularyValues(
      List<MachineContract.EnumVocabularyDescriptor> vocabularies, String name) {
    return vocabularies.stream()
        .filter(vocabulary -> vocabulary.name().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing vocabulary: " + name))
        .values();
  }
}
