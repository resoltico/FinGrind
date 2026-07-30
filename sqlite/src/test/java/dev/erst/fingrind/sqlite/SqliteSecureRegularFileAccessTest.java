package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.PrivateOutputFile;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Behavioral tests for external nofollow reads and exact owner-only recovery records. */
class SqliteSecureRegularFileAccessTest extends SqliteNativeBridgeTestSupport {
  @Test
  void readsOwnerOnlyMetadataThroughBoundedByteAndLineContracts() throws Exception {
    Path record = tempDirectory.resolve("secure-access/record.control");
    Path parent = record.getParent();
    if (parent == null) {
      throw new AssertionError("Recovery-record fixture requires one parent directory.");
    }
    Files.createDirectories(parent);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(parent);
    try (PrivateOutputFile.OpenedFile channel = SqliteOwnedRegularFileAccess.openNewWrite(record)) {
      channel.write(
          ByteBuffer.wrap("first\nsecond\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    assertEquals(
        List.of("first", "second"),
        SqliteOwnedRegularFileAccess.readOwnedUtf8LinesBounded(record, 32, 2));
    IOException tooManyLines =
        assertThrows(
            IOException.class,
            () -> SqliteOwnedRegularFileAccess.readOwnedUtf8LinesBounded(record, 32, 1));
    assertTrue(
        java.util.Objects.requireNonNull(tooManyLines.getMessage(), "line-bound message")
            .contains("too many lines"));
    IOException tooManyBytes =
        assertThrows(
            IOException.class, () -> SqliteSecureRegularFileAccess.readAllBytesBounded(record, 2));
    assertTrue(
        java.util.Objects.requireNonNull(tooManyBytes.getMessage(), "byte-bound message")
            .contains("maximum allowed size"));
    assertThrows(
        IllegalArgumentException.class,
        () -> SqliteSecureRegularFileAccess.readAllBytesBounded(record, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> SqliteSecureRegularFileAccess.readAllBytesBounded(record, Integer.MAX_VALUE));
    assertThrows(
        IllegalArgumentException.class,
        () -> SqliteOwnedRegularFileAccess.readOwnedUtf8LinesBounded(record, Integer.MAX_VALUE, 2));
    assertThrows(
        IllegalArgumentException.class,
        () -> SqliteOwnedRegularFileAccess.readOwnedUtf8LinesBounded(record, 0, 2));
    assertThrows(
        IllegalArgumentException.class,
        () -> SqliteOwnedRegularFileAccess.readOwnedUtf8LinesBounded(record, 8, Integer.MAX_VALUE));
  }

  @Test
  void writeAndForceOperationsRequireOneExactRegularFile() throws Exception {
    Path record = tempDirectory.resolve("secure-write/record.control");
    Path parent = record.getParent();
    if (parent == null) {
      throw new AssertionError("Secure-write fixture requires one parent directory.");
    }
    Files.createDirectories(parent);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(parent);
    try (PrivateOutputFile.OpenedFile channel = SqliteOwnedRegularFileAccess.openNewWrite(record)) {
      channel.write(ByteBuffer.wrap(new byte[] {1, 2, 3}));
    }
    SqliteOwnedRegularFileAccess.forceFile(record);
    try (PrivateOutputFile.OpenedFile channel =
        SqliteOwnedRegularFileAccess.openTruncatingWrite(record)) {
      channel.write(ByteBuffer.wrap(new byte[] {9}));
    }
    assertEquals(1L, Files.size(record));
    assertEquals(9, SqliteOwnedRegularFileAccess.readOwnedAllBytes(record)[0]);

    Path directory = Files.createDirectory(parent.resolve("not-a-regular-file"));
    IOException refusal =
        assertThrows(IOException.class, () -> SqliteSecureRegularFileAccess.openRead(directory));
    assertTrue(
        java.util.Objects.requireNonNull(refusal.getMessage(), "non-regular refusal message")
            .contains("regular non-symlink"));
  }

  @Test
  void externalNofollowProviderRefusalsAndNonemptyNewStagesFailClosed() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath readPath = fileSystem.path("\\evidence\\read.control");
      readPath.exists = true;
      readPath.regularFile = true;
      UnsupportedOperationException readRefusal =
          new UnsupportedOperationException("injected nofollow input refusal");
      readPath.failNewByteChannelWithUnsupportedOperation(readRefusal);

      IOException readFailure =
          assertThrows(IOException.class, () -> SqliteSecureRegularFileAccess.openRead(readPath));
      assertEquals(readRefusal, readFailure.getCause());
    }

    Path nonempty = tempDirectory.resolve("nonempty-new-stage.test");
    Files.write(nonempty, new byte[] {1});
    IOException stageFailure =
        assertThrows(
            IOException.class,
            () ->
                SqliteOwnedRegularFileAccess.createNewEmptyFile(
                    tempDirectory.resolve("reported-new-stage.test"),
                    ignored ->
                        SqliteTestPrivateOutputFile.wrap(
                            FileChannel.open(nonempty, java.nio.file.StandardOpenOption.READ))));
    assertTrue(
        java.util.Objects.requireNonNull(stageFailure.getMessage(), "new-stage refusal message")
            .contains("was not empty"));
  }

  @Test
  void retainedOwnedChannelsCloseOnConstructionAndTruncationFailures() throws Exception {
    RuntimeException streamConstructionFailure =
        new RuntimeException("stream construction failure");
    IOException streamCloseFailure = new IOException("stream close failure");
    try (ControlledOpenedFile streamChannel = new ControlledOpenedFile(null, streamCloseFailure);
        ControlledOpenedFile truncationChannel =
            new ControlledOpenedFile(
                new IOException("truncate failure"), new IOException("truncate close failure"));
        ControlledOpenedFile successfullyClosedStream = new ControlledOpenedFile(null, null);
        ControlledOpenedFile successfullyClosedTruncation =
            new ControlledOpenedFile(
                new IOException("successful truncation cleanup failure"), null)) {

      assertSame(
          streamConstructionFailure,
          assertThrows(
              RuntimeException.class,
              () ->
                  SqliteOwnedRegularFileAccess.openOwnedRead(
                      tempDirectory.resolve("stream.control"),
                      (ignoredPath, ignoredAccess) -> streamChannel,
                      ignored -> {
                        throw streamConstructionFailure;
                      })));
      assertEquals(1, streamChannel.closeCount);
      assertEquals(
          List.of(streamCloseFailure),
          java.util.List.of(streamConstructionFailure.getSuppressed()));

      IOException truncationFailure = truncationChannel.truncateFailure();
      IOException truncationCloseFailure = truncationChannel.closeFailure();
      assertSame(
          truncationFailure,
          assertThrows(
              IOException.class,
              () ->
                  SqliteOwnedRegularFileAccess.openTruncatingWrite(
                      tempDirectory.resolve("truncate.control"),
                      (ignoredPath, ignoredAccess) -> truncationChannel)));
      assertEquals(1, truncationChannel.closeCount);
      assertEquals(
          List.of(truncationCloseFailure), java.util.List.of(truncationFailure.getSuppressed()));

      RuntimeException successfulStreamCleanupFailure =
          new RuntimeException("successful stream cleanup failure");
      assertSame(
          successfulStreamCleanupFailure,
          assertThrows(
              RuntimeException.class,
              () ->
                  SqliteOwnedRegularFileAccess.openOwnedRead(
                      tempDirectory.resolve("successfully-closed-stream.control"),
                      (ignoredPath, ignoredAccess) -> successfullyClosedStream,
                      ignored -> {
                        throw successfulStreamCleanupFailure;
                      })));
      assertEquals(1, successfullyClosedStream.closeCount);

      IOException successfulTruncationCleanupFailure =
          successfullyClosedTruncation.truncateFailure();
      assertSame(
          successfulTruncationCleanupFailure,
          assertThrows(
              IOException.class,
              () ->
                  SqliteOwnedRegularFileAccess.openTruncatingWrite(
                      tempDirectory.resolve("successfully-closed-truncate.control"),
                      (ignoredPath, ignoredAccess) -> successfullyClosedTruncation)));
      assertEquals(1, successfullyClosedTruncation.closeCount);

      SqliteCallerPathContractException missingParentFailure =
          assertThrows(
              SqliteCallerPathContractException.class,
              () ->
                  SqliteOwnedRegularFileAccess.openWrite(tempDirectory.resolve("missing/record")));
      assertEquals(
          SqliteCallerPathFailure.MISSING_PARENT_DIRECTORY, missingParentFailure.pathFailure());
    }
  }

  @Test
  void retainedOwnedChannelsCloseWithoutSuppressingWhenCleanupSucceeds() throws Exception {
    RuntimeException streamConstructionFailure =
        new RuntimeException("stream construction failure");
    try (ControlledOpenedFile streamChannel = new ControlledOpenedFile(null, null);
        ControlledOpenedFile truncationChannel =
            new ControlledOpenedFile(new IOException("truncate failure"), null)) {

      assertSame(
          streamConstructionFailure,
          assertThrows(
              RuntimeException.class,
              () ->
                  SqliteOwnedRegularFileAccess.openOwnedRead(
                      tempDirectory.resolve("successful-stream-cleanup.control"),
                      new CapturingOwnedFileOpener(streamChannel),
                      new ThrowingOwnedInputStreamFactory(streamConstructionFailure))));
      assertEquals(1, streamChannel.closeCount);
      assertEquals(0, streamConstructionFailure.getSuppressed().length);

      IOException truncationFailure = truncationChannel.truncateFailure();
      assertSame(
          truncationFailure,
          assertThrows(
              IOException.class,
              () ->
                  SqliteOwnedRegularFileAccess.openTruncatingWrite(
                      tempDirectory.resolve("successful-truncation-cleanup.control"),
                      new CapturingOwnedFileOpener(truncationChannel))));
      assertEquals(1, truncationChannel.closeCount);
      assertEquals(0, truncationFailure.getSuppressed().length);
    }
  }

  @Test
  void retainedOwnedChannelsTransferTheirExactSuccessfulCapabilitiesToTheCaller() throws Exception {
    try (ControlledOpenedFile readChannel = new ControlledOpenedFile(null, null);
        ControlledOpenedFile truncationChannel = new ControlledOpenedFile(null, null)) {
      InputStream expectedInput = new ByteArrayInputStream(new byte[] {7});
      CapturingOwnedFileOpener readOpener = new CapturingOwnedFileOpener(readChannel);

      try (InputStream input =
          SqliteOwnedRegularFileAccess.openOwnedRead(
              tempDirectory.resolve("read.control"),
              readOpener,
              new FixedOwnedInputStreamFactory(expectedInput))) {
        assertSame(expectedInput, input);
        assertEquals(7, input.read());
      }
      assertEquals(PrivateOutputFile.Access.READ_ONLY, readOpener.access());
      assertEquals(0, readChannel.closeCount);
      readChannel.close();

      CapturingOwnedFileOpener truncationOpener = new CapturingOwnedFileOpener(truncationChannel);
      try (PrivateOutputFile.OpenedFile opened =
          SqliteOwnedRegularFileAccess.openTruncatingWrite(
              tempDirectory.resolve("truncate.control"), truncationOpener)) {
        assertSame(truncationChannel, opened);
        assertEquals(1, truncationChannel.truncateCount);
        assertEquals(0L, truncationChannel.position);
      }
      assertEquals(PrivateOutputFile.Access.READ_WRITE, truncationOpener.access());
      assertEquals(1, truncationChannel.closeCount);
    }
  }

  /** Captures the exact owner-only access requested by a production opening call. */
  private static final class CapturingOwnedFileOpener
      implements SqliteOwnedRegularFileAccess.OwnedFileOpener {
    private final PrivateOutputFile.OpenedFile opened;
    private PrivateOutputFile.@Nullable Access access;

    private CapturingOwnedFileOpener(PrivateOutputFile.OpenedFile opened) {
      this.opened = opened;
    }

    @Override
    public PrivateOutputFile.OpenedFile open(Path path, PrivateOutputFile.Access access) {
      this.access = access;
      return opened;
    }

    private PrivateOutputFile.Access access() {
      return java.util.Objects.requireNonNull(access, "captured access");
    }
  }

  /** Returns one fixed stream after production has transferred the opened-file capability. */
  private static final class FixedOwnedInputStreamFactory
      implements SqliteOwnedRegularFileAccess.OwnedInputStreamFactory {
    private final InputStream input;

    private FixedOwnedInputStreamFactory(InputStream input) {
      this.input = input;
    }

    @Override
    public InputStream open(PrivateOutputFile.OpenedFile opened) {
      return input;
    }
  }

  /** Fails stream construction after production has received the exact opened-file capability. */
  private static final class ThrowingOwnedInputStreamFactory
      implements SqliteOwnedRegularFileAccess.OwnedInputStreamFactory {
    private final RuntimeException failure;

    private ThrowingOwnedInputStreamFactory(RuntimeException failure) {
      this.failure = failure;
    }

    @Override
    public InputStream open(PrivateOutputFile.OpenedFile opened) {
      throw failure;
    }
  }

  /** Controlled owner-only opened-file double with idempotent close tracking. */
  private static final class ControlledOpenedFile implements PrivateOutputFile.OpenedFile {
    private final @Nullable IOException truncateFailure;
    private final @Nullable IOException closeFailure;
    private int closeCount;
    private int truncateCount;
    private long position = -1L;

    private ControlledOpenedFile(
        @Nullable IOException truncateFailure, @Nullable IOException closeFailure) {
      this.truncateFailure = truncateFailure;
      this.closeFailure = closeFailure;
    }

    private IOException truncateFailure() {
      return java.util.Objects.requireNonNull(truncateFailure, "truncateFailure");
    }

    private IOException closeFailure() {
      return java.util.Objects.requireNonNull(closeFailure, "closeFailure");
    }

    @Override
    public boolean created() {
      return false;
    }

    @Override
    public int read(ByteBuffer destination) {
      return -1;
    }

    @Override
    public int write(ByteBuffer source) {
      return source.remaining();
    }

    @Override
    public boolean isOpen() {
      return true;
    }

    @Override
    public long size() {
      return 0L;
    }

    @Override
    public void truncate(long size) throws IOException {
      truncateCount++;
      if (truncateFailure != null) {
        throw truncateFailure;
      }
    }

    @Override
    public void position(long position) {
      this.position = position;
    }

    @Override
    public void force() {}

    @Override
    public PrivateOutputFile.HeldLock tryExclusiveLock(long position, long size) {
      return () -> {};
    }

    @Override
    public String physicalObjectIdentity() {
      return "controlled";
    }

    @Override
    public void close() throws IOException {
      if (closeCount != 0) {
        return;
      }
      closeCount++;
      if (closeFailure != null) {
        throw closeFailure;
      }
    }
  }
}
