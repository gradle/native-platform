package net.rubygrapefruit.platform.testfixture;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class JulLogging extends TestWatcher {

    private final Logger logger;
    private final Level level;
    private RecordingHandler recorder;
    private Level oldLevel;

    public JulLogging(Class<?> clazz, Level level) {
        this(Logger.getLogger(clazz.getName()), level);
    }

    private JulLogging(Logger logger, Level level) {
        this.logger = logger;
        this.level = level;
    }

    @Override
    protected void starting(Description description) {
        recorder = new RecordingHandler();
        logger.addHandler(recorder);

        oldLevel = logger.getLevel();
        logger.setLevel(level);
    }

    @Override
    protected void finished(Description description) {
        logger.removeHandler(recorder);
        logger.setLevel(oldLevel);
    }

    public Map<String, Level> getMessages() {
        return recorder.messages;
    }

    private static class RecordingHandler extends Handler {
        private final Map<String, Level> messages = new LinkedHashMap<String, Level>();

        @Override
        public void publish(LogRecord record) {
            this.messages.put(record.getMessage(), record.getLevel());
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
    }
}
