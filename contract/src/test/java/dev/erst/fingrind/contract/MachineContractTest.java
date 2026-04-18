package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
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
    ContractDiscovery.CapabilitiesDescriptor capabilities =
        MachineContract.capabilities(
            new ContractDiscovery.ApplicationIdentity("FinGrind", "0.9.0", "desc"),
            new ContractDiscovery.EnvironmentDescriptor(
                "self-contained-bundle",
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
            .map(ContractResponse.RejectionDescriptor::code)
            .toList();
    assertTrue(rejectionCodes.contains("administration-book-not-initialized"));
    assertTrue(rejectionCodes.contains("query-book-not-initialized"));
    assertTrue(rejectionCodes.contains("posting-book-not-initialized"));
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
    ContractDiscovery.ApplicationIdentity identity =
        new ContractDiscovery.ApplicationIdentity("FinGrind", "0.9.0", "desc");
    ContractDiscovery.EnvironmentDescriptor environment =
        new ContractDiscovery.EnvironmentDescriptor(
            "self-contained-bundle",
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
            true,
            "3.53.0",
            "2.3.3",
            "ready",
            "3.53.0",
            "2.3.3",
            null);

    ContractDiscovery.HelpDescriptor help = MachineContract.help(identity, environment);
    ContractDiscovery.VersionDescriptor version = MachineContract.version(identity);
    ContractTemplates.PostingRequestTemplateDescriptor template =
        MachineContract.requestTemplate(
            Clock.fixed(Instant.parse("2026-04-13T12:00:00Z"), java.time.ZoneOffset.UTC));
    ContractTemplates.ReversalTemplateDescriptor reversalTemplate =
        new ContractTemplates.ReversalTemplateDescriptor("posting-1", "operator reversal");

    assertEquals("FinGrind", help.application());
    assertEquals("single-currency-per-entry", help.bookModel().currencyScope());
    assertEquals(17, help.commands().size());
    assertEquals("generate-book-key-file", help.commands().get(5).name());
    assertEquals("open-book", help.commands().get(6).name());
    assertEquals("rekey-book", help.commands().get(7).name());
    assertEquals("inspect-book", help.commands().get(9).name());
    assertEquals("account-balance", help.commands().get(13).name());
    assertTrue(help.commands().get(7).options().get(2).contains("--new-book-passphrase-prompt"));
    assertEquals(4, help.exitCodes().size());
    assertEquals("advisory", help.preflight().semantics());
    assertEquals(environment, help.environment());

    assertEquals("0.9.0", version.version());
    assertEquals(
        environment.supportedPublicCliBundleTargets(),
        ProtocolCatalog.supportedPublicCliBundleTargets());
    assertEquals(List.of(), ProtocolCatalog.unsupportedPublicCliOperatingSystems());
    assertEquals("2026-04-13", template.effectiveDate());
    assertEquals("1000", template.lines().get(0).accountCode());
    assertEquals("USER", template.provenance().actorType());
    assertEquals("posting-1", reversalTemplate.priorPostingId());
  }

  private static List<String> enumValues(Enum<?>[] values) {
    return Arrays.stream(values).map(Enum::name).toList();
  }

  private static List<String> vocabularyValues(
      List<ContractRequestShapes.EnumVocabularyDescriptor> vocabularies, String name) {
    return vocabularies.stream()
        .filter(vocabulary -> vocabulary.name().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing vocabulary: " + name))
        .values();
  }
}
