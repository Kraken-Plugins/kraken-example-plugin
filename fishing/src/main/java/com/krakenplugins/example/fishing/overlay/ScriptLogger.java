package com.krakenplugins.example.fishing.overlay;


import com.google.inject.Singleton;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

@Singleton
public class ScriptLogger {

    private static final int MAX_ENTRIES = 6;

    // ArrayDeque is not thread-safe — synchronize all access since
    // the game loop thread writes and the EDT reads for rendering.
    private final Deque<LogEntry> entries = new ArrayDeque<>();

    public synchronized void log(String message) {
        push(new LogEntry(LogLevel.INFO, message));
    }

    public synchronized void warn(String message) {
        push(new LogEntry(LogLevel.WARN, message));
    }

    public synchronized void error(String message) {
        push(new LogEntry(LogLevel.ERROR, message));
    }

    /** Returns a snapshot of current entries, oldest first. */
    public synchronized List<LogEntry> getEntries() {
        return new ArrayList<>(entries);
    }

    public synchronized void clear() {
        entries.clear();
    }

    private void push(LogEntry entry) {
        if (entries.size() >= MAX_ENTRIES) {
            entries.pollFirst(); // evict oldest
        }
        entries.addLast(entry);
    }
}
