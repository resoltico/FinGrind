package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.core.CryptographicPrimitives;
import dev.erst.fingrind.jazzer.support.JazzerHarness;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Owns committed-seed content hashing and duplicate-content detection. */
final class RegressionSeedDigests {
  private RegressionSeedDigests() {}

  static List<RegressionSeedDuplicateContent> duplicateContentGroups(Path projectDirectory)
      throws IOException {
    Objects.requireNonNull(projectDirectory, "projectDirectory must not be null");
    List<DigestedInput> digestedInputs = new ArrayList<>();
    for (Path inputPath : RegressionSeedPaths.allInputPaths(projectDirectory)) {
      digestedInputs.add(
          new DigestedInput(
              sha256Hex(Files.readAllBytes(inputPath)), inputPath.toAbsolutePath().normalize()));
    }
    return duplicateContentGroups(digestedInputs);
  }

  static List<RegressionSeedDuplicateContent> duplicateContentGroupsForHarnesses(
      Path projectDirectory, List<JazzerHarness> harnesses) throws IOException {
    List<DigestedInput> digestedInputs = new ArrayList<>();
    for (JazzerHarness harness : harnesses) {
      for (Path metadataPath : RegressionSeedPaths.metadataPaths(projectDirectory, harness)) {
        RegressionSeedMetadataInspection inspection =
            RegressionSeedMetadataInspector.inspectMetadataPath(
                projectDirectory, harness, metadataPath);
        if (inspection.entry() == null) {
          continue;
        }
        digestedInputs.add(
            new DigestedInput(inspection.entry().sha256(), inspection.entry().inputPath()));
      }
    }
    return duplicateContentGroups(digestedInputs);
  }

  static String sha256Hex(byte[] bytes) {
    return CryptographicPrimitives.sha256Hex(bytes);
  }

  private static List<RegressionSeedDuplicateContent> duplicateContentGroups(
      List<DigestedInput> digestedInputs) {
    List<DigestedInput> sortedInputs = new ArrayList<>(digestedInputs);
    sortedInputs.sort(
        Comparator.comparing(DigestedInput::sha256).thenComparing(DigestedInput::inputPath));
    List<RegressionSeedDuplicateContent> duplicates = new ArrayList<>();
    String currentDigest = null;
    List<Path> currentPaths = new ArrayList<>();
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

  private static List<Path> newPathAccumulator() {
    return new ArrayList<>();
  }

  private record DigestedInput(String sha256, Path inputPath) {}
}
