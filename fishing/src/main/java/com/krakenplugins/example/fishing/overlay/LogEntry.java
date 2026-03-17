package com.krakenplugins.example.fishing.overlay;

import lombok.Getter;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Getter
public class LogEntry {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final String timestamp;
    private final String message;
    private final LogLevel level;

    public LogEntry(LogLevel level, String message) {
        this.timestamp = LocalTime.now().format(FORMATTER);
        this.message = message;
        this.level = level;
    }
}