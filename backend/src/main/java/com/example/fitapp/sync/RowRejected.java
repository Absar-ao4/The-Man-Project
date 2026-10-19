package com.example.fitapp.sync;

/** A pushed row that can never be accepted as sent (malformed, invalid, or not the caller's). */
public class RowRejected extends RuntimeException {

    public RowRejected(String reason) {
        super(reason);
    }
}
