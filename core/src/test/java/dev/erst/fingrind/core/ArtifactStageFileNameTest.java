package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Verifies that fresh stage leaves remain safe to create on both POSIX and Windows filesystems. */
class ArtifactStageFileNameTest {
  private static final UUID STAGE_IDENTIFIER =
      UUID.fromString("12345678-1234-5678-9abc-def012345678");

  @Test
  void compose_buildsTheExpectedPortableUuidBasedStageLeaf() {
    String actual = ArtifactStageFileName.compose(".receipt-", STAGE_IDENTIFIER, ".fgar");

    assertEquals(".receipt-12345678-1234-5678-9abc-def012345678.fgar", actual);
  }

  @Test
  void compose_validatesTheFullLeafRatherThanRejectingAReservedWordInsideIt() {
    String actual = ArtifactStageFileName.compose(".con-", STAGE_IDENTIFIER, ".fgar");

    assertEquals(".con-12345678-1234-5678-9abc-def012345678.fgar", actual);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidLeaves")
  void requireValidLeaf_rejectsNamesWindowsOrPortableFilesystemsCannotCreate(
      String description, String leaf) {
    assertThrows(
        IllegalArgumentException.class,
        () -> ArtifactStageFileName.requireValidLeaf(leaf),
        description);
  }

  @Test
  void compose_preservesTheExistingNonemptyPrefixAndSuffixContract() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ArtifactStageFileName.compose("", STAGE_IDENTIFIER, ".fgar"));
    assertThrows(
        IllegalArgumentException.class,
        () -> ArtifactStageFileName.compose(".receipt-", STAGE_IDENTIFIER, ""));
  }

  @Test
  void compose_requiresAllInputs() {
    assertThrows(
        NullPointerException.class,
        () -> ArtifactStageFileName.compose(nullOf(), STAGE_IDENTIFIER, ".fgar"));
    assertThrows(
        NullPointerException.class,
        () -> ArtifactStageFileName.compose(".receipt-", nullOf(), ".fgar"));
    assertThrows(
        NullPointerException.class,
        () -> ArtifactStageFileName.compose(".receipt-", STAGE_IDENTIFIER, nullOf()));
    assertThrows(
        NullPointerException.class, () -> ArtifactStageFileName.requireValidLeaf(nullOf()));
  }

  private static Stream<Arguments> invalidLeaves() {
    return Stream.of(
        Arguments.of("empty", ""),
        Arguments.of("forward path separator", ".receipt-/nested.fgar"),
        Arguments.of("backward path separator", ".receipt-\\nested.fgar"),
        Arguments.of("Windows-reserved less-than sign", ".receipt<stage.fgar"),
        Arguments.of("Windows-reserved greater-than sign", ".receipt>stage.fgar"),
        Arguments.of("Windows-reserved colon", ".receipt:stage.fgar"),
        Arguments.of("Windows-reserved double quote", ".receipt\"stage.fgar"),
        Arguments.of("Windows-reserved vertical bar", ".receipt|stage.fgar"),
        Arguments.of("Windows-reserved question mark", ".receipt?stage.fgar"),
        Arguments.of("Windows-reserved asterisk", ".receipt*stage.fgar"),
        Arguments.of("NUL", ".receipt-" + (char) 0 + ".fgar"),
        Arguments.of("C1 control", ".receipt-" + (char) 0x85 + ".fgar"),
        Arguments.of("unpaired surrogate", ".receipt-" + Character.MIN_SURROGATE + ".fgar"),
        Arguments.of("trailing period", ".receipt-stage."),
        Arguments.of("trailing space", ".receipt-stage "),
        Arguments.of("reserved device name", "CON"),
        Arguments.of("reserved device name with extension", "nUl.tar.gz"),
        Arguments.of("reserved serial device with extension", "COM9.txt"),
        Arguments.of("reserved superscript serial device", "LPT\u00B3.backup"));
  }
}
