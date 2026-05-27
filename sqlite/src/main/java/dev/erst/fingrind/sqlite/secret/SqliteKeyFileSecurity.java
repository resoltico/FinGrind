package dev.erst.fingrind.sqlite.secret;

/** Platform-native security descriptor supported by the book-key checker. */
sealed interface SqliteKeyFileSecurity
    permits SqlitePosixKeyFileSecurity, SqliteAclKeyFileSecurity {}
