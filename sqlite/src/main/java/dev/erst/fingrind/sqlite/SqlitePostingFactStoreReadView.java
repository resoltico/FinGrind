package dev.erst.fingrind.sqlite;

/** Read/query surface over one SQLite posting-fact store. */
interface SqlitePostingFactStoreReadView
    extends SqlitePostingFactStorePostingHistoryView,
        SqlitePostingFactStoreAccountCatalogView,
        SqlitePostingFactStorePostingLookupView,
        SqlitePostingFactStoreReportingView {}
