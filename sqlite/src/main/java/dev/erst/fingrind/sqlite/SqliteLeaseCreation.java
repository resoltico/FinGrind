package dev.erst.fingrind.sqlite;

/** Result of attempting to claim a cooperative maintenance lease file for one artifact path. */
sealed interface SqliteLeaseCreation permits SqliteCreatedLease, SqliteExistingLease {}
