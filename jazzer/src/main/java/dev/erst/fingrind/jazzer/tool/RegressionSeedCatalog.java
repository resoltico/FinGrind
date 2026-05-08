package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.jazzer.support.JazzerHarness;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

/** Reads the committed regression-seed metadata that drives deterministic replay. */
public final class RegressionSeedCatalog {
  private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

  private RegressionSeedCatalog() {}

  /** Returns the metadata directory for one harness. */
  public static Path metadataDirectory(Path projectDirectory, JazzerHarness harness) {
    Objects.requireNonNull(projectDirectory, "projectDirectory must not be null");
    Objects.requireNonNull(harness, "harness must not be null");
    return harness.regressionMetadataDirectory(projectDirectory.toAbsolutePath().normalize());
  }

  /** Returns the committed metadata paths for one harness. */
  public static List<Path> metadataPaths(Path projectDirectory, JazzerHarness harness)
      throws IOException {
    Path metadataDirectory = metadataDirectory(projectDirectory, harness);
    if (!Files.isDirectory(metadataDirectory)) {
      return List.of();
    }
    try (Stream<Path> stream = Files.walk(metadataDirectory)) {
      return stream
          .filter(path -> path.getFileName().toString().endsWith(".json"))
          .sorted()
          .toList();
    }
  }

  /** Returns the committed input files for one harness. */
  public static List<Path> inputPaths(Path projectDirectory, JazzerHarness harness)
      throws IOException {
    Objects.requireNonNull(projectDirectory, "projectDirectory must not be null");
    Objects.requireNonNull(harness, "harness must not be null");
    Path inputDirectory = harness.inputDirectory(projectDirectory.toAbsolutePath().normalize());
    if (!Files.isDirectory(inputDirectory)) {
      return List.of();
    }
    try (Stream<Path> stream = Files.list(inputDirectory)) {
      return stream.filter(Files::isRegularFile).sorted().toList();
    }
  }

  /** Returns committed metadata entries for one harness, including input digests and intent. */
  public static List<RegressionSeedCatalogEntry> entries(
      Path projectDirectory, JazzerHarness harness) throws IOException {
    Objects.requireNonNull(projectDirectory, "projectDirectory must not be null");
    Objects.requireNonNull(harness, "harness must not be null");
    List<RegressionSeedCatalogEntry> entries = new ArrayList<>();
    for (Path metadataPath : metadataPaths(projectDirectory, harness)) {
      MetadataInspection inspection = inspectMetadataPath(projectDirectory, harness, metadataPath);
      if (inspection.problem() != null) {
        throw new IllegalStateException(inspection.problem().message());
      }
      entries.add(inspection.entry());
    }
    entries.sort(Comparator.comparing(RegressionSeedCatalogEntry::inputPath));
    return entries;
  }

  /** Returns every committed metadata entry across every replayable harness. */
  public static List<RegressionSeedCatalogEntry> entries(Path projectDirectory) throws IOException {
    Objects.requireNonNull(projectDirectory, "projectDirectory must not be null");
    List<RegressionSeedCatalogEntry> entries = new ArrayList<>();
    for (JazzerHarness harness : JazzerHarness.values()) {
      entries.addAll(entries(projectDirectory, harness));
    }
    entries.sort(
        Comparator.comparing(RegressionSeedCatalogEntry::targetKey)
            .thenComparing(RegressionSeedCatalogEntry::inputPath));
    return entries;
  }

  /** Returns committed seed groups that share identical raw input bytes. */
  public static List<RegressionSeedDuplicateContent> duplicateContentGroups(Path projectDirectory)
      throws IOException {
    Objects.requireNonNull(projectDirectory, "projectDirectory must not be null");
    List<DigestedInput> digestedInputs = new ArrayList<>();
    for (Path inputPath : allInputPaths(projectDirectory)) {
      digestedInputs.add(
          new DigestedInput(
              sha256Hex(Files.readAllBytes(inputPath)), inputPath.toAbsolutePath().normalize()));
    }
    return duplicateContentGroups(digestedInputs);
  }

  /** Returns a summary of the committed regression floor for one or every replayable harness. */
  public static RegressionSeedAuditReport audit(Path projectDirectory) throws IOException {
    Objects.requireNonNull(projectDirectory, "projectDirectory must not be null");
    return audit(projectDirectory, List.of(JazzerHarness.values()));
  }

  /** Returns a summary of one harness's committed regression floor. */
  public static RegressionSeedAuditReport audit(Path projectDirectory, JazzerHarness harness)
      throws IOException {
    Objects.requireNonNull(projectDirectory, "projectDirectory must not be null");
    Objects.requireNonNull(harness, "harness must not be null");
    return audit(projectDirectory, List.of(harness));
  }

  private static RegressionSeedAuditReport audit(
      Path projectDirectory, List<JazzerHarness> harnesses) throws IOException {
    List<RegressionSeedCatalogEntry> allEntries = new ArrayList<>();
    List<Path> orphanedInputPaths = new ArrayList<>();
    List<RegressionSeedCatalogEntry> unexpectedFailureSeeds = new ArrayList<>();
    List<RegressionSeedIntegrityProblem> integrityProblems = new ArrayList<>();
    List<RegressionSeedTargetAudit> targets = new ArrayList<>();
    Set<Path> scopedSeedInputPaths = new HashSet<>();
    for (JazzerHarness harness : harnesses) {
      List<RegressionSeedCatalogEntry> harnessEntries = newCatalogEntryAccumulator();
      List<RegressionSeedIntegrityProblem> harnessIntegrityProblems =
          newIntegrityProblemAccumulator();
      Set<Path> referencedInputs = newReferencedInputAccumulator();
      Path normalizedHarnessInputDirectory =
          harness.inputDirectory(projectDirectory).toAbsolutePath().normalize();
      for (Path metadataPath : metadataPaths(projectDirectory, harness)) {
        MetadataInspection inspection =
            inspectMetadataPath(projectDirectory, harness, metadataPath);
        if (inspection.entry() != null) {
          harnessEntries.add(inspection.entry());
          referencedInputs.add(inspection.entry().inputPath());
          continue;
        }
        RegressionSeedIntegrityProblem integrityProblem =
            Objects.requireNonNull(inspection.problem(), "problem must not be null");
        harnessIntegrityProblems.add(integrityProblem);
        if (integrityProblem.inputPath() != null
            && integrityProblem.inputPath().startsWith(normalizedHarnessInputDirectory)) {
          referencedInputs.add(integrityProblem.inputPath());
        }
      }
      List<Path> harnessOrphans =
          inputPaths(projectDirectory, harness).stream()
              .map(path -> path.toAbsolutePath().normalize())
              .filter(path -> !referencedInputs.contains(path))
              .sorted()
              .toList();
      List<RegressionSeedCatalogEntry> harnessUnexpectedFailureSeeds =
          harnessEntries.stream()
              .filter(
                  entry ->
                      entry.expectation().outcomeKind() == ReplayOutcomeKind.UNEXPECTED_FAILURE)
              .toList();
      if (harnessEntries.isEmpty()
          && harnessOrphans.isEmpty()
          && harnessIntegrityProblems.isEmpty()) {
        continue;
      }
      allEntries.addAll(harnessEntries);
      scopedSeedInputPaths.addAll(
          harnessEntries.stream().map(RegressionSeedCatalogEntry::inputPath).toList());
      orphanedInputPaths.addAll(harnessOrphans);
      unexpectedFailureSeeds.addAll(harnessUnexpectedFailureSeeds);
      integrityProblems.addAll(harnessIntegrityProblems);
      targets.add(
          new RegressionSeedTargetAudit(
              harness.key(),
              harnessEntries.size(),
              harnessEntries,
              harnessOrphans,
              harnessUnexpectedFailureSeeds,
              harnessIntegrityProblems));
    }
    List<RegressionSeedDuplicateContent> duplicateContentGroups =
        duplicateContentGroupsForHarnesses(projectDirectory, List.of(JazzerHarness.values()))
            .stream()
            .filter(
                duplicateGroup ->
                    duplicateGroup.inputPaths().stream().anyMatch(scopedSeedInputPaths::contains))
            .toList();
    return new RegressionSeedAuditReport(
        allEntries.size(),
        allEntries.stream()
            .map(RegressionSeedCatalogEntry::sha256)
            .collect(java.util.stream.Collectors.toSet())
            .size(),
        orphanedInputPaths.size(),
        unexpectedFailureSeeds.size(),
        integrityProblems.size(),
        targets,
        duplicateContentGroups,
        orphanedInputPaths,
        unexpectedFailureSeeds,
        integrityProblems);
  }

  /** Returns every committed raw input file across every harness, including orphaned inputs. */
  public static List<Path> allInputPaths(Path projectDirectory) throws IOException {
    Objects.requireNonNull(projectDirectory, "projectDirectory must not be null");
    List<Path> inputPaths = new ArrayList<>();
    for (JazzerHarness harness : JazzerHarness.values()) {
      inputPaths.addAll(inputPaths(projectDirectory, harness));
    }
    return inputPaths.stream().map(path -> path.toAbsolutePath().normalize()).sorted().toList();
  }

  /** Returns committed inputs with no corresponding regression metadata entry. */
  public static List<Path> orphanedInputs(Path projectDirectory, JazzerHarness harness)
      throws IOException {
    List<Path> inputs = inputPaths(projectDirectory, harness);
    if (inputs.isEmpty()) {
      return List.of();
    }
    Set<Path> recordedInputs = new HashSet<>();
    for (Path metadataPath : metadataPaths(projectDirectory, harness)) {
      try {
        RegressionSeedMetadata metadata =
            JazzerJson.read(metadataPath, RegressionSeedMetadata.class);
        recordedInputs.add(metadata.inputPath(projectDirectory).toAbsolutePath().normalize());
      } catch (IOException | RuntimeException exception) {
        throw new IllegalStateException(
            "Committed regression metadata is invalid: "
                + metadataPath.toAbsolutePath().normalize()
                + " -> "
                + exception.getMessage(),
            exception);
      }
    }
    return inputs.stream()
        .map(path -> path.toAbsolutePath().normalize())
        .filter(path -> !recordedInputs.contains(path))
        .sorted()
        .toList();
  }

  private static MetadataInspection inspectMetadataPath(
      Path projectDirectory, JazzerHarness harness, Path metadataPath) throws IOException {
    Path normalizedMetadataPath = metadataPath.toAbsolutePath().normalize();
    RegressionSeedMetadata metadata;
    try {
      metadata = JazzerJson.read(normalizedMetadataPath, RegressionSeedMetadata.class);
    } catch (IOException | RuntimeException exception) {
      return MetadataInspection.problem(
          new RegressionSeedIntegrityProblem(
              harness.key(),
              normalizedMetadataPath,
              null,
              "metadata-read-failure",
              "Committed regression metadata is unreadable: "
                  + normalizedMetadataPath
                  + " -> "
                  + exception.getMessage()));
    }
    Path normalizedInputPath = metadata.inputPath(projectDirectory).toAbsolutePath().normalize();
    if (!metadata.targetKey().equals(harness.key())) {
      return MetadataInspection.problem(
          new RegressionSeedIntegrityProblem(
              harness.key(),
              normalizedMetadataPath,
              normalizedInputPath,
              "target-mismatch",
              "Committed regression metadata target does not match its owning harness directory: "
                  + normalizedMetadataPath
                  + " declares "
                  + metadata.targetKey()
                  + " but lives under "
                  + harness.key()
                  + "."));
    }

    Path normalizedHarnessInputDirectory =
        harness.inputDirectory(projectDirectory).toAbsolutePath().normalize();
    if (!normalizedInputPath.startsWith(normalizedHarnessInputDirectory)) {
      return MetadataInspection.problem(
          new RegressionSeedIntegrityProblem(
              harness.key(),
              normalizedMetadataPath,
              normalizedInputPath,
              "input-outside-harness",
              "Committed regression metadata points outside the owning harness input directory: "
                  + normalizedMetadataPath
                  + " -> "
                  + normalizedInputPath));
    }
    if (!Files.exists(normalizedInputPath)) {
      return MetadataInspection.problem(
          new RegressionSeedIntegrityProblem(
              harness.key(),
              normalizedMetadataPath,
              normalizedInputPath,
              "input-missing",
              "Committed regression metadata points to a missing raw input: "
                  + normalizedMetadataPath
                  + " -> "
                  + normalizedInputPath));
    }
    if (!Files.isRegularFile(normalizedInputPath)) {
      return MetadataInspection.problem(
          new RegressionSeedIntegrityProblem(
              harness.key(),
              normalizedMetadataPath,
              normalizedInputPath,
              "input-not-regular-file",
              "Committed regression metadata points to a non-file raw input: "
                  + normalizedMetadataPath
                  + " -> "
                  + normalizedInputPath));
    }
    if (normalizedInputPath.getFileName().toString().endsWith(".json")) {
      try {
        JSON_MAPPER.readTree(Files.readString(normalizedInputPath));
      } catch (IOException exception) {
        return MetadataInspection.problem(
            new RegressionSeedIntegrityProblem(
                harness.key(),
                normalizedMetadataPath,
                normalizedInputPath,
                "input-read-failure",
                "Committed regression input could not be read: "
                    + normalizedInputPath
                    + " -> "
                    + exception.getMessage()));
      } catch (RuntimeException exception) {
        return MetadataInspection.problem(
            new RegressionSeedIntegrityProblem(
                harness.key(),
                normalizedMetadataPath,
                normalizedInputPath,
                "input-json-malformed",
                "Committed JSON regression input is malformed: "
                    + normalizedInputPath
                    + " -> "
                    + exception.getMessage()));
      }
    }

    try {
      return MetadataInspection.entry(
          new RegressionSeedCatalogEntry(
              metadata.targetKey(),
              normalizedMetadataPath,
              normalizedInputPath,
              metadata.coverageIntent(),
              metadata.expectation(),
              sha256Hex(Files.readAllBytes(normalizedInputPath))));
    } catch (IOException exception) {
      return MetadataInspection.problem(
          new RegressionSeedIntegrityProblem(
              harness.key(),
              normalizedMetadataPath,
              normalizedInputPath,
              "input-read-failure",
              "Committed regression input could not be read: "
                  + normalizedInputPath
                  + " -> "
                  + exception.getMessage()));
    }
  }

  static String sha256Hex(byte[] bytes) {
    return sha256Hex(bytes, () -> MessageDigest.getInstance("SHA-256"));
  }

  static String sha256Hex(byte[] bytes, DigestFactory digestFactory) {
    Objects.requireNonNull(bytes, "bytes must not be null");
    Objects.requireNonNull(digestFactory, "digestFactory must not be null");
    try {
      return HexFormat.of().formatHex(digestFactory.create().digest(bytes));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 digest is unavailable in this JVM.", exception);
    }
  }

  private static List<Path> newPathAccumulator() {
    return new ArrayList<>();
  }

  private static List<RegressionSeedCatalogEntry> newCatalogEntryAccumulator() {
    return new ArrayList<>();
  }

  private static List<RegressionSeedIntegrityProblem> newIntegrityProblemAccumulator() {
    return new ArrayList<>();
  }

  private static Set<Path> newReferencedInputAccumulator() {
    return new HashSet<>();
  }

  /** Supplies the message-digest implementation used for committed-seed hashing. */
  @FunctionalInterface
  interface DigestFactory {
    /** Creates one digest instance for the configured hashing algorithm. */
    MessageDigest create() throws NoSuchAlgorithmException;
  }

  private static List<RegressionSeedDuplicateContent> duplicateContentGroupsForHarnesses(
      Path projectDirectory, List<JazzerHarness> harnesses) throws IOException {
    List<DigestedInput> digestedInputs = new ArrayList<>();
    for (JazzerHarness harness : harnesses) {
      for (Path metadataPath : metadataPaths(projectDirectory, harness)) {
        MetadataInspection inspection =
            inspectMetadataPath(projectDirectory, harness, metadataPath);
        if (inspection.entry() == null) {
          continue;
        }
        digestedInputs.add(
            new DigestedInput(inspection.entry().sha256(), inspection.entry().inputPath()));
      }
    }
    return duplicateContentGroups(digestedInputs);
  }

  private static List<RegressionSeedDuplicateContent> duplicateContentGroups(
      List<DigestedInput> digestedInputs) {
    List<DigestedInput> sortedInputs = new ArrayList<>(digestedInputs);
    sortedInputs.sort(
        Comparator.comparing(DigestedInput::sha256).thenComparing(DigestedInput::inputPath));
    List<RegressionSeedDuplicateContent> duplicates = new ArrayList<>();
    String currentDigest = null;
    List<Path> currentPaths = newPathAccumulator();
    for (DigestedInput digestedInput : sortedInputs) {
      if (!Objects.equals(currentDigest, digestedInput.sha256())) {
        appendDuplicateGroupIfNeeded(duplicates, currentDigest, currentPaths);
        currentDigest = digestedInput.sha256();
        currentPaths = newPathAccumulator();
      }
      currentPaths.add(digestedInput.inputPath());
    }
    appendDuplicateGroupIfNeeded(duplicates, currentDigest, currentPaths);
    return List.copyOf(duplicates);
  }

  private static void appendDuplicateGroupIfNeeded(
      List<RegressionSeedDuplicateContent> duplicates,
      @Nullable String digest,
      List<Path> currentPaths) {
    if (digest == null || currentPaths.size() <= 1) {
      return;
    }
    duplicates.add(new RegressionSeedDuplicateContent(digest, List.copyOf(currentPaths)));
  }

  private record MetadataInspection(
      @Nullable RegressionSeedCatalogEntry entry,
      @Nullable RegressionSeedIntegrityProblem problem) {
    private static MetadataInspection entry(RegressionSeedCatalogEntry entry) {
      return new MetadataInspection(Objects.requireNonNull(entry, "entry must not be null"), null);
    }

    private static MetadataInspection problem(RegressionSeedIntegrityProblem problem) {
      return new MetadataInspection(
          null, Objects.requireNonNull(problem, "problem must not be null"));
    }
  }

  private record DigestedInput(String sha256, Path inputPath) {}
}
