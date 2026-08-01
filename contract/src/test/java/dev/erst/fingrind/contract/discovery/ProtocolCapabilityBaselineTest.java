package dev.erst.fingrind.contract.discovery;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static dev.erst.fingrind.contract.discovery.MachineContractDiscoveryTestSupport.IDENTITY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolOperation;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Regression coverage for the protocol-owned release-smoke capability snapshot. */
class ProtocolCapabilityBaselineTest {
  private static final String SNAPSHOT_DIRECTORY =
      "contract/src/main/resources/dev/erst/fingrind/contract/protocol/capability-baseline";

  @Test
  void snapshotProjectsEveryProtocolOperationThroughItsMachineContractDescriptor() {
    ProtocolCapabilityBaseline.CapabilityBaselineSnapshot snapshot =
        ProtocolCapabilityBaseline.snapshot();
    Map<OperationId, CommandDescriptor> descriptorsByOperation =
        MachineContract.capabilities(IDENTITY).commands().allCommands().stream()
            .collect(Collectors.toMap(CommandDescriptor::name, descriptor -> descriptor));
    List<List<ProtocolCapabilityBaseline.CapabilityBaselineArtifact>> expectedArtifactOutputs =
        ProtocolCatalog.operations().stream()
            .map(ProtocolCapabilityBaselineTest::expectedArtifactOutputs)
            .toList();

    assertEquals(ProtocolCapabilityBaseline.SCHEMA_VERSION, snapshot.schemaVersion());
    assertEquals(ProtocolCatalog.operations().size(), snapshot.commands().size());
    assertEquals(ProtocolCatalog.operations().size(), descriptorsByOperation.size());

    for (int index = 0; index < ProtocolCatalog.operations().size(); index++) {
      ProtocolOperation operation = ProtocolCatalog.operations().get(index);
      CommandDescriptor descriptor = descriptorsByOperation.get(operation.id());
      ProtocolCapabilityBaseline.CapabilityBaselineCommand actual = snapshot.commands().get(index);

      assertNotNull(descriptor);
      assertEquals(operation.category().wireValue(), actual.category());
      assertEquals(operation.id().wireName(), actual.name());
      assertEquals(operation.displayLabel(), actual.displayLabel());
      assertEquals(operation.aliases(), actual.aliases());
      assertEquals(operation.options(), actual.options());
      assertEquals(operation.executionMode().wireValue(), actual.executionMode());
      assertEquals(
          operation.outputModes().stream().map(outputMode -> outputMode.wireValue()).toList(),
          actual.outputModes());
      assertEquals(expectedArtifactOutputs.get(index), actual.artifactOutputs());
      assertEquals(operation.analysisSummary(), actual.summary());
      assertSelectableOutputDefaults(descriptor, actual);
    }
  }

  @Test
  void committedSnapshotsMatchTheCanonicalRenderings() throws IOException {
    Path snapshotDirectory = repositoryRoot().resolve(SNAPSHOT_DIRECTORY);
    Map<Path, String> documents = ProtocolCapabilityBaseline.renderedDocuments();

    assertTrue(Files.isDirectory(snapshotDirectory));
    try (var paths = Files.walk(snapshotDirectory)) {
      assertEquals(
          documents.keySet(),
          paths
              .filter(Files::isRegularFile)
              .map(snapshotDirectory::relativize)
              .collect(Collectors.toCollection(LinkedHashSet::new)));
    }
    for (Map.Entry<Path, String> document : documents.entrySet()) {
      assertEquals(
          document.getValue(), Files.readString(snapshotDirectory.resolve(document.getKey())));
    }
  }

  @Test
  void snapshotProjectionRejectsMissingExtraAndMismatchedDescriptors() throws Throwable {
    ProtocolOperation operation = ProtocolCatalog.operations().getFirst();
    Map<OperationId, CommandDescriptor> descriptorsByOperation = descriptorsByOperation();
    CommandDescriptor descriptor = requireDescriptor(descriptorsByOperation, operation.id());
    ProtocolOperation differentOperation =
        ProtocolCatalog.operations().stream()
            .filter(candidate -> candidate.id() != operation.id())
            .findFirst()
            .orElseThrow();
    CommandDescriptor differentDescriptor =
        requireDescriptor(descriptorsByOperation, differentOperation.id());
    MethodHandle projectSnapshot = projectSnapshotHandle();

    IllegalStateException missing =
        assertThrows(
            IllegalStateException.class,
            () -> projectSnapshot(projectSnapshot, List.of(operation), Map.of()));
    IllegalStateException extra =
        assertThrows(
            IllegalStateException.class,
            () -> projectSnapshot(projectSnapshot, List.of(), Map.of(operation.id(), descriptor)));
    IllegalStateException mismatched =
        assertThrows(
            IllegalStateException.class,
            () ->
                projectSnapshot(
                    projectSnapshot,
                    List.of(operation),
                    Map.of(operation.id(), differentDescriptor)));

    assertEquals(
        "Machine-contract command descriptor is missing canonical operation "
            + operation.id().wireName()
            + ".",
        missing.getMessage());
    assertEquals(
        "Machine-contract command descriptors do not cover the canonical protocol catalog.",
        extra.getMessage());
    assertEquals(
        "Machine-contract command descriptor does not match canonical operation "
            + operation.id().wireName()
            + ".",
        mismatched.getMessage());
  }

  @Test
  void baselineValueObjectsRejectInvalidShape() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new ProtocolCapabilityBaseline.CapabilityBaselineSnapshot(0, List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ProtocolCapabilityBaseline.CapabilityBaselineCommand(
                " ",
                "help",
                "Help",
                List.of(),
                List.of(),
                "raw-json",
                List.of(),
                null,
                List.of(),
                "Help."));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ProtocolCapabilityBaseline.CapabilityBaselineSelectableOutputDefaults(" ", "json"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ProtocolCapabilityBaseline.CapabilityBaselineArtifact(" ", "--out", "Output."));
  }

  @Test
  void synchronizationIsIdempotentAndRejectsInvalidDestinations(@TempDir Path tempDir)
      throws IOException {
    Path snapshot = tempDir.resolve("nested/capability-baseline");

    ProtocolCapabilityBaseline.sync(snapshot);
    Map<Path, String> documents = ProtocolCapabilityBaseline.renderedDocuments();
    Path firstDocument = documents.keySet().iterator().next();
    String firstRendering = Files.readString(snapshot.resolve(firstDocument));
    Files.writeString(snapshot.resolve(firstDocument), "not the canonical baseline");
    Path staleFragment = snapshot.resolve("commands/query/stale.json");
    Files.createDirectories(staleFragment.getParent());
    Files.writeString(staleFragment, "{}");
    ProtocolCapabilityBaseline.sync(snapshot);

    assertEquals(documents.get(firstDocument), firstRendering);
    assertTrue(Files.notExists(staleFragment));
    ProtocolCapabilityBaseline.sync(snapshot);
    assertEquals(firstRendering, Files.readString(snapshot.resolve(firstDocument)));
    assertThrows(NullPointerException.class, () -> ProtocolCapabilityBaseline.sync(nullOf()));
    assertThrows(
        IllegalArgumentException.class, () -> ProtocolCapabilityBaseline.sync(Path.of("/")));
  }

  @Test
  void baselineDocumentTargetsRemainContainedByTheirGeneratedDirectory(@TempDir Path tempDir)
      throws Throwable {
    Path targetDirectory = tempDir.resolve("capability-baseline").toAbsolutePath().normalize();

    assertEquals(
        targetDirectory.resolve("commands/query/help.json"),
        ProtocolCapabilityBaselineDirectory.targetPath(
            targetDirectory, Path.of("commands/query/help.json")));
    IllegalArgumentException escaped =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ProtocolCapabilityBaselineDirectory.targetPath(
                    targetDirectory, Path.of("../escaped.json")));

    assertEquals("Capability baseline document path escapes its directory.", escaped.getMessage());
  }

  private static void assertSelectableOutputDefaults(
      CommandDescriptor descriptor, ProtocolCapabilityBaseline.CapabilityBaselineCommand actual) {
    SelectableOutputDefaultsDescriptor expected = descriptor.selectableOutputDefaults();
    if (expected == null) {
      assertNull(actual.selectableOutputDefaults());
      return;
    }
    ProtocolCapabilityBaseline.CapabilityBaselineSelectableOutputDefaults actualDefaults =
        actual.selectableOutputDefaults();
    assertNotNull(actualDefaults);
    assertEquals(expected.interactiveTerminal().wireValue(), actualDefaults.interactiveTerminal());
    assertEquals(expected.redirectedStdout().wireValue(), actualDefaults.redirectedStdout());
  }

  private static List<ProtocolCapabilityBaseline.CapabilityBaselineArtifact>
      expectedArtifactOutputs(ProtocolOperation operation) {
    return operation.artifactOutputs().stream()
        .map(
            artifact ->
                new ProtocolCapabilityBaseline.CapabilityBaselineArtifact(
                    artifact.format(), artifact.option(), artifact.description()))
        .toList();
  }

  private static Map<OperationId, CommandDescriptor> descriptorsByOperation() {
    return MachineContract.capabilities(IDENTITY).commands().allCommands().stream()
        .collect(Collectors.toMap(CommandDescriptor::name, descriptor -> descriptor));
  }

  private static CommandDescriptor requireDescriptor(
      Map<OperationId, CommandDescriptor> descriptorsByOperation, OperationId operationId) {
    CommandDescriptor descriptor = descriptorsByOperation.get(operationId);
    assertNotNull(descriptor);
    return descriptor;
  }

  private static MethodHandle projectSnapshotHandle()
      throws NoSuchMethodException, IllegalAccessException {
    return MethodHandles.privateLookupIn(ProtocolCapabilityBaseline.class, MethodHandles.lookup())
        .findStatic(
            ProtocolCapabilityBaseline.class,
            "projectSnapshot",
            MethodType.methodType(
                ProtocolCapabilityBaseline.CapabilityBaselineSnapshot.class,
                List.class,
                Map.class));
  }

  private static ProtocolCapabilityBaseline.CapabilityBaselineSnapshot projectSnapshot(
      MethodHandle projectSnapshot,
      List<ProtocolOperation> operations,
      Map<OperationId, CommandDescriptor> descriptorsByOperation)
      throws Throwable {
    return (ProtocolCapabilityBaseline.CapabilityBaselineSnapshot)
        projectSnapshot.invokeExact(operations, descriptorsByOperation);
  }

  private static Path repositoryRoot() {
    String configuredRoot = System.getProperty("fingrind.repository.root");
    assertNotNull(configuredRoot);
    return Path.of(configuredRoot).toAbsolutePath().normalize();
  }
}
