package biz.paluch.logging.gelf.log4j2;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.ThreadContext;

import biz.paluch.logging.gelf.GelfUtil;
import biz.paluch.logging.gelf.LogEvent;
import biz.paluch.logging.gelf.LogMessageField;
import biz.paluch.logging.gelf.MdcMessageField;
import biz.paluch.logging.gelf.MessageField;

/**
 * @author <a href="mailto:mpaluch@paluch.biz">Mark Paluch</a>
 */
public class Log4jLogEvent implements LogEvent {

    private org.apache.logging.log4j.core.LogEvent loggingEvent;

    public Log4jLogEvent(org.apache.logging.log4j.core.LogEvent loggingEvent) {
        this.loggingEvent = loggingEvent;
    }

    @Override
    public String getMessage() {
        return loggingEvent.getMessage().getFormattedMessage();
    }

    @Override
    public Object[] getParameters() {
        Object[] parameters = loggingEvent.getMessage().getParameters();
        return parameters == null ? new Object[0] : parameters;
    }

    @Override
    public Throwable getThrowable() {
        return loggingEvent.getThrown();
    }

    @Override
    public long getLogTimestamp() {
        return loggingEvent.getTimeMillis();
    }

    @Override
    public String getSyslogLevel() {
        return "" + levelToSyslogLevel(loggingEvent.getLevel());
    }

    private int levelToSyslogLevel(final Level level) {

        if (level.equals(Level.FATAL)) {
            return 2;
        }
        if (level.equals(Level.ERROR)) {
            return 3;
        }
        if (level.equals(Level.WARN)) {
            return 4;
        }
        if (level.equals(Level.INFO)) {
            return 6;
        }

        return 7;
    }

    @Override
    public String getValue(MessageField field) {
        if (field instanceof LogMessageField) {
            return getValue((LogMessageField) field);
        }

        if (field instanceof MdcMessageField) {
            return getValue((MdcMessageField) field);
        }

        throw new UnsupportedOperationException("Cannot provide value for " + field);
    }

    public String getValue(LogMessageField field) {

        StackTraceElement source = loggingEvent.getSource();

        switch (field.getNamedLogField()) {
        case Severity:
            return loggingEvent.getLevel().toString();
        case ThreadName:
            return loggingEvent.getThreadName();
        case SourceClassName:
            return source == null ? null : source.getClassName();
        case SourceMethodName:
            return source == null ? null : source.getMethodName();
        case SourceSimpleClassName:
            return source == null ? null : GelfUtil.getSimpleClassName(source.getClassName());
        }

        throw new UnsupportedOperationException("Cannot provide value for " + field);
    }

    private String getValue(MdcMessageField field) {

        Object value = loggingEvent.getContextData().getValue(field.getMdcName());
        if (value == null) {
            value = ThreadContext.get(field.getMdcName());
        }

        if (value != null) {
            return value.toString();
        }

        return null;
    }
}
