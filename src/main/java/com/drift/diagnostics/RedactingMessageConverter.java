package com.drift.diagnostics;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.drift.history.Redactor;

/** Logback conversion word %redact: the log message with secrets and user names removed. */
public class RedactingMessageConverter extends ClassicConverter {
    @Override
    public String convert(ILoggingEvent event) {
        return Redactor.redact(event.getFormattedMessage());
    }
}
