package dev.erst.fingrind.sqlite;

import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.Arrays;

/** In-memory channel for filesystem operations on the test ACL filesystem. */
final class AclFixtureSeekableByteChannel implements SeekableByteChannel {
  private final AclFixturePath path;
  private long position;
  private boolean open = true;

  AclFixtureSeekableByteChannel(AclFixturePath path) {
    this.path = java.util.Objects.requireNonNull(path, "path");
  }

  @Override
  public int read(ByteBuffer dst) {
    if (path.consumeZeroProgressRead()) {
      return 0;
    }
    byte[] content = path.content();
    if (position >= content.length) {
      return -1;
    }
    int length = Math.min(dst.remaining(), content.length - Math.toIntExact(position));
    dst.put(content, Math.toIntExact(position), length);
    position += length;
    return length;
  }

  @Override
  public int write(ByteBuffer src) throws java.io.IOException {
    java.io.IOException writeFailure = path.writeFailure();
    if (writeFailure != null) {
      throw writeFailure;
    }
    if (path.consumeZeroProgressWrite()) {
      return 0;
    }
    int remaining = src.remaining();
    int start = Math.toIntExact(position);
    byte[] content = path.content();
    byte[] expanded = Arrays.copyOf(content, Math.max(content.length, start + remaining));
    src.get(expanded, start, remaining);
    path.replaceContent(expanded);
    position += remaining;
    return remaining;
  }

  @Override
  public long position() {
    return position;
  }

  @Override
  public SeekableByteChannel position(long newPosition) {
    if (newPosition < 0) {
      throw new IllegalArgumentException("newPosition must be non-negative");
    }
    position = newPosition;
    return this;
  }

  @Override
  public long size() {
    return path.content().length;
  }

  @Override
  public SeekableByteChannel truncate(long size) {
    if (size < 0) {
      throw new IllegalArgumentException("size must be non-negative");
    }
    byte[] content = path.content();
    if (size < content.length) {
      path.replaceContent(Arrays.copyOf(content, Math.toIntExact(size)));
    }
    position = Math.min(position, size);
    return this;
  }

  @Override
  public boolean isOpen() {
    return open;
  }

  @Override
  public void close() {
    open = false;
  }
}
