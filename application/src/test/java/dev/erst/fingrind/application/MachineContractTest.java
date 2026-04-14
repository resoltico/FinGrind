package dev.erst.fingrind.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.NormalBalance;
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
                "26+",
                "sqlite-ffm-sqlite3mc",
                "sqlite",
                "required",
                "chacha20",
                "managed",
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
  }

  @Test
  void helpVersionAndRequestTemplate_publishCanonicalDiscoveryMetadata() {
    MachineContract.ApplicationIdentity identity =
        new MachineContract.ApplicationIdentity("FinGrind", "0.9.0", "desc");
    MachineContract.EnvironmentDescriptor environment =
        new MachineContract.EnvironmentDescriptor(
            "26+",
            "sqlite-ffm-sqlite3mc",
            "sqlite",
            "required",
            "chacha20",
            "managed",
            "3.53.0",
            "2.3.3",
            "ready",
            "3.53.0",
            "2.3.3",
            null);

    MachineContract.HelpDescriptor help = MachineContract.help(identity, environment);
    MachineContract.VersionDescriptor version = MachineContract.version(identity);
    MachineContract.PostingRequestTemplateDescriptor template = MachineContract.requestTemplate();
    MachineContract.ReversalTemplateDescriptor reversalTemplate =
        new MachineContract.ReversalTemplateDescriptor("posting-1");

    assertEquals("FinGrind", help.application());
    assertEquals("single-currency-per-entry", help.bookModel().currencyScope());
    assertEquals(9, help.commands().size());
    assertEquals("open-book", help.commands().get(4).name());
    assertTrue(help.commands().get(4).options().get(1).contains("--book-passphrase-prompt"));
    assertEquals(3, help.exitCodes().size());
    assertEquals("advisory", help.preflight().semantics());
    assertEquals(environment, help.environment());

    assertEquals("0.9.0", version.version());
    assertEquals("2026-04-08", template.effectiveDate());
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
