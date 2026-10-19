package com.example.fitapp.sync;

/** What happened to a pushed row. STALE means the server already holds a newer edit. */
public enum SyncResult {
    ACCEPTED,
    STALE
}
