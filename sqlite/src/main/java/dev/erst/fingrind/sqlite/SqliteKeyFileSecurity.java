package dev.erst.fingrind.sqlite;

/** Platform-native security descriptor supported by the book-key checker. */
sealed interface SqliteKeyFileSecurity
    permits SqlitePosixKeyFileSecurity, SqliteAclKeyFileSecurity {}
