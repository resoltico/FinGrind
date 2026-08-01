package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Retained POSIX channel admitted through {@link PrivateOutputFilePlatformOperations}. */
final class PrivateOutputFilePosixOpenedFile implements PrivateOutputFile.OpenedFile {
  private final FileChannel channel;
  private final boolean created;
  private final @Nullable Path file;

  PrivateOutputFilePosixOpenedFile(FileChannel channel, boolean created, @Nullable Path file) {
    this.channel = Objects.requireNonNull(channel, "channel");
    this.created = created;
    this.file = file;
  }

  @Override
  public boolean created() {
    return created;
  }

  @Override
  public int read(ByteBuffer destination) throws IOException {
    return channel.read(Objects.requireNonNull(destination, "destination"));
  }

  @Override
  public int write(ByteBuffer source) throws IOException {
    return channel.write(Objects.requireNonNull(source, "source"));
  }

  @Override
  public boolean isOpen() {
    return channel.isOpen();
  }

  @Override
  public long size() throws IOException {
    return channel.size();
  }

  @Override
  public void truncate(long size) throws IOException {
    channel.truncate(size);
  }

  @Override
  public void position(long position) throws IOException {
    channel.position(position);
  }

  @Override
  public void force() throws IOException {
    channel.force(true);
  }

  @Override
  public PrivateOutputFile.@Nullable HeldLock tryExclusiveLock(long position, long size)
      throws IOException {
    PrivateOutputFile.requireLockRange(position, size);
    try {
      return heldLock(channel.tryLock(position, size, false));
    } catch (OverlappingFileLockException unavailable) {
      return null;
    }
  }

  /** Converts one NIO lock result into the retained capability without inventing an absent lock. */
  static PrivateOutputFile.@Nullable HeldLock heldLock(@Nullable FileLock lock) {
    return lock == null ? null : lock::release;
  }

  @Override
  public String physicalObjectIdentity() throws IOException {
    if (file == null) {
      throw new IOException(
          "The retained test-only private-output channel has no physical artifact path.");
    }
    return physicalObjectIdentity(
        Files.readAttributes(file, "unix:dev,ino", LinkOption.NOFOLLOW_LINKS));
  }

  /** Validates and renders the explicit POSIX device-and-inode identity fact. */
  static String physicalObjectIdentity(Map<String, Object> attributes) throws IOException {
    Map<String, Object> checkedAttributes = Objects.requireNonNull(attributes, "attributes");
    Object device = checkedAttributes.get("dev");
    Object inode = checkedAttributes.get("ino");
    if (!(device instanceof Number deviceNumber) || !(inode instanceof Number inodeNumber)) {
      throw new IOException(
          "The selected private-output filesystem did not expose explicit device/inode identity.");
    }
    return "posix-v1:dev="
        + Long.toUnsignedString(deviceNumber.longValue())
        + ":ino="
        + Long.toUnsignedString(inodeNumber.longValue());
  }

  @Override
  public void close() throws IOException {
    channel.close();
  }
}
