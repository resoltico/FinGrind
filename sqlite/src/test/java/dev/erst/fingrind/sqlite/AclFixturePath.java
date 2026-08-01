package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Minimal path implementation for the test ACL filesystem. */
public class AclFixturePath extends AclFixtureAbstractPath {
  public boolean exists;
  public boolean regularFile;
  public @Nullable AclFixtureView aclView;
  public @Nullable AclFileAttributeView overrideAclView;
  public Set<PosixFilePermission> posixPermissions = Set.of();
  private final AclFixturePathIdentityPlan identityPlan = new AclFixturePathIdentityPlan();
  private final AclFixturePathChannelPlan channelPlan = new AclFixturePathChannelPlan();
  private final AclFixturePathMutationPlan mutationPlan = new AclFixturePathMutationPlan();

  AclFixturePath(AclFixtureFileSystem fileSystem, String value) {
    super(fileSystem, value);
    this.aclView = new AclFixtureView(fileSystem.owner);
  }

  public boolean existsValue() {
    return exists;
  }

  public boolean regularFileValue() {
    return regularFile;
  }

  public @Nullable AclFixtureView aclViewValue() {
    return aclView;
  }

  byte[] content() {
    return channelPlan.content();
  }

  void replaceContent(byte[] replacement) {
    channelPlan.replaceContent(replacement);
  }

  AclFixturePath failDeleteIfExistsWith(IOException exception) {
    mutationPlan.failDeleteIfExistsWith(exception);
    return this;
  }

  AclFixturePath failReadAttributesWith(IOException exception) {
    mutationPlan.failReadAttributesWith(exception);
    return this;
  }

  AclFixturePath failToRealPathWith(IOException exception) {
    identityPlan.failToRealPathWith(exception);
    return this;
  }

  AclFixturePath failToRealPathAfterSuccessfulCallsWith(
      int successfulCallsBeforeFailure, IOException exception) {
    identityPlan.failToRealPathAfterSuccessfulCallsWith(successfulCallsBeforeFailure, exception);
    return this;
  }

  AclFixturePath returnRealPath(Path path) {
    identityPlan.returnRealPath(path);
    return this;
  }

  @Override
  public Path toRealPath(LinkOption... options) throws IOException {
    return identityPlan.resolveRealPath(() -> super.toRealPath(options));
  }

  AclFixturePath failPosixReadAttributesWith(IOException exception) {
    mutationPlan.failPosixReadAttributesWith(exception);
    return this;
  }

  AclFixturePath failSameFileWith(IOException exception) {
    identityPlan.failSameFileWith(exception);
    return this;
  }

  AclFixturePath failSameFileAgainst(Path otherPath, IOException exception) {
    identityPlan.failSameFileAgainst(otherPath, exception);
    return this;
  }

  AclFixturePath preserveExistingEntryOnDeleteIfExists() {
    mutationPlan.preserveExistingEntryOnDeleteIfExists();
    return this;
  }

  AclFixturePath failNewByteChannelWith(IOException exception) {
    channelPlan.failNewByteChannelWith(exception);
    return this;
  }

  AclFixturePath failNewFileChannelWithUnsupportedOperation(
      UnsupportedOperationException exception) {
    channelPlan.failNewFileChannelWithUnsupportedOperation(exception);
    return this;
  }

  AclFixturePath failNewByteChannelWithUnsupportedOperation(
      UnsupportedOperationException exception) {
    channelPlan.failNewByteChannelWithUnsupportedOperation(exception);
    return this;
  }

  AclFixturePath failTryLockWith(IOException exception) {
    channelPlan.failTryLockWith(exception);
    return this;
  }

  AclFixturePath failCloseWith(IOException exception) {
    channelPlan.failCloseWith(exception);
    return this;
  }

  AclFixturePath reportSizeAs(long size) {
    channelPlan.reportSizeAs(size);
    return this;
  }

  AclFixturePath failCreateDirectoryWith(IOException exception) {
    mutationPlan.failCreateDirectoryWith(exception);
    return this;
  }

  AclFixturePath failCreateDirectoryWithUnsupportedOperation(
      UnsupportedOperationException exception) {
    mutationPlan.failCreateDirectoryWithUnsupportedOperation(exception);
    return this;
  }

  AclFixturePath failNewByteChannelAfter(int successfulCalls, IOException exception) {
    channelPlan.failNewByteChannelAfter(successfulCalls, exception);
    return this;
  }

  AclFixturePath failWriteWith(IOException exception) {
    channelPlan.failWriteWith(exception);
    return this;
  }

  AclFixturePath returnZeroProgressFromNextWrite() {
    channelPlan.returnZeroProgressFromNextWrite();
    return this;
  }

  AclFixturePath returnZeroProgressFromNextRead() {
    channelPlan.returnZeroProgressFromNextRead();
    return this;
  }

  AclFixturePath failNewDirectoryStreamWith(IOException exception) {
    mutationPlan.failNewDirectoryStreamWith(exception);
    return this;
  }

  AclFixturePath failNewDirectoryStreamAfterSuccessfulCallsWith(
      int successfulCallsBeforeFailure, IOException exception) {
    mutationPlan.failNewDirectoryStreamAfterSuccessfulCallsWith(
        successfulCallsBeforeFailure, exception);
    return this;
  }

  AclFixturePath failDirectoryStreamCloseWith(IOException exception) {
    mutationPlan.failDirectoryStreamCloseWith(exception);
    return this;
  }

  AclFixturePath failDirectoryStreamCloseAfterSuccessfulCallsWith(
      int successfulCallsBeforeFailure, IOException exception) {
    mutationPlan.failDirectoryStreamCloseAfterSuccessfulCallsWith(
        successfulCallsBeforeFailure, exception);
    return this;
  }

  AclFixturePath failMoveWith(IOException exception) {
    mutationPlan.failMoveWith(exception);
    return this;
  }

  AclFixturePathIdentityPlan identityPlan() {
    return identityPlan;
  }

  AclFixturePathChannelPlan channelPlan() {
    return channelPlan;
  }

  AclFixturePathMutationPlan mutationPlan() {
    return mutationPlan;
  }
}
