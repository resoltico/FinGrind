package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Creates or admits the one private HMAC key that authenticates a user's transaction journals. */
final class PublicationTransactionOwnerKey {
  private static final String FILE_NAME = "owner-hmac-key-v1.bin";
  private static final int BYTES = 32;

  private PublicationTransactionOwnerKey() {}

  static byte[] loadOrCreate(
      Path storeRoot, PublicationTransactionDirectoryDurability directoryDurability)
      throws IOException {
    Path checkedStoreRoot = Objects.requireNonNull(storeRoot, "storeRoot");
    byte[] ownerKey = load(PrivateOutputFile.openOrCreate(path(checkedStoreRoot)));
    Objects.requireNonNull(directoryDurability, "directoryDurability").force(checkedStoreRoot);
    return ownerKey;
  }

  static byte[] load(PrivateOutputFile.OpenedFile opened) throws IOException {
    PrivateOutputFile.OpenedFile openedKey = Objects.requireNonNull(opened, "opened");
    final byte[] ownerKey;
    try {
      if (openedKey.created()) {
        byte[] generatedKey = CryptographicPrimitives.secureBytes(BYTES);
        PublicationTransactionJournalFileIO.writeExactlyAndForce(
            openedKey, generatedKey, "publication transaction owner key");
        ownerKey = generatedKey;
      } else {
        ownerKey =
            PublicationTransactionJournalFileIO.readExactLength(
                openedKey, BYTES, "publication transaction owner key");
      }
    } catch (IOException failure) {
      PublicationTransactionJournalFileIO.closeAfterFailure(openedKey, failure);
      throw failure;
    }
    openedKey.close();
    return ownerKey;
  }

  static Path path(Path storeRoot) {
    return Objects.requireNonNull(storeRoot, "storeRoot").resolve(FILE_NAME);
  }
}
