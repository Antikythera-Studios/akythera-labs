package com.yesman.akytheralabs.editor.debug;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.jspecify.annotations.NonNull;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/// The game's log records mirrored into memory, so the editor console can show them without opening
/// `latest.log`.
///
/// Fed by an appender on the root logger at [#CAPTURE_LEVEL]: every namespace's records land here, not
/// only Akythera Labs'. The oldest line drops once the buffer holds [#capacity] of them.
public final class EditorLogBuffer {
    /// One rendered log line. An event that spans several lines — a message with newlines, a stack
    /// trace — becomes one [Line] per line, all carrying the event's level.
    ///
    /// `id` rises with every line pushed, and is how the console addresses a caret into a line that
    /// may since have moved or dropped.
    ///
    /// `prefixLength` covers the leading `[time] [thread/LEVEL] ` stamp, which the console tints by
    /// level. Continuation lines carry no stamp and leave it `0`.
    public record Line(long id, Level level, String text, int prefixLength) {}

    /// The lowest level the console keeps — the same floor `debug.log` is written at. Below it the
    /// filter row offers no checkbox, so capturing would only cost buffer room.
    private static final Level CAPTURE_LEVEL = Level.DEBUG;

    private static final int DEFAULT_CAPACITY = 5000;
    private static final String APPENDER_NAME = "AkytheraLabsConsole";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private static final EditorLogBuffer INSTANCE = new EditorLogBuffer();

    public static EditorLogBuffer getInstance() {
        return INSTANCE;
    }

    private final Deque<Line> lines = new ArrayDeque<>();

    private int capacity = DEFAULT_CAPACITY;
    private long nextId;

    /// Bumped on every mutation. The console rebuilds its rows when it changes.
    private int version;

    private boolean installed;

    private EditorLogBuffer() {
    }

    /// Attaches the capture appender to the root logger. Called once from each platform's client init.
    public void install() {
        if (installed) {
            return;
        }

        if (!(LogManager.getContext(false) instanceof LoggerContext context)) {
            return;
        }

        installed = true;

        CaptureAppender appender = new CaptureAppender();
        appender.start();

        context.getConfiguration().addAppender(appender);
        context.getConfiguration().getRootLogger().addAppender(appender, CAPTURE_LEVEL, null);
        context.updateLoggers();
    }

    public synchronized List<Line> snapshot() {
        return new ArrayList<>(lines);
    }

    public synchronized int version() {
        return version;
    }

    public synchronized int getCapacity() {
        return capacity;
    }

    /// Resizes the buffer, dropping the oldest lines that no longer fit.
    public synchronized void setCapacity(final int capacity) {
        this.capacity = Math.max(1, capacity);

        trim();

        version++;
    }

    public synchronized void clear() {
        lines.clear();
        version++;
    }

    private synchronized void push(final Level level, final String text, final int prefixLength) {
        lines.addLast(new Line(nextId++, level, text, prefixLength));

        trim();

        version++;
    }

    private void trim() {
        while (lines.size() > capacity) {
            lines.removeFirst();
        }
    }

    /// Splits `event` into the lines it renders as: a `[time] [thread/LEVEL] ` stamp followed by the
    /// origin and the message, then the stack trace when the event carries a throwable.
    private void capture(final LogEvent event) {
        Level level = event.getLevel();
        String stamp = "[" + TIME.format(Instant.ofEpochMilli(event.getTimeMillis())) + "] ["
            + event.getThreadName() + "/" + level.name() + "] ";
        String origin = "[" + event.getLoggerName() + "]: ";

        String[] messageLines = event.getMessage().getFormattedMessage().split("\r?\n", -1);
        push(level, stamp + origin + messageLines[0], stamp.length());

        for (int i = 1; i < messageLines.length; i++) {
            push(level, messageLines[i], 0);
        }

        Throwable thrown = event.getThrown();

        if (thrown == null) {
            return;
        }

        StringWriter writer = new StringWriter();
        thrown.printStackTrace(new PrintWriter(writer));

        for (String line : writer.toString().split("\r?\n")) {
            push(level, line.replace("\t", "    "), 0);
        }
    }

    private final class CaptureAppender extends AbstractAppender {
        private CaptureAppender() {
            super(APPENDER_NAME, null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(final @NonNull LogEvent event) {
            capture(event);
        }
    }
}
