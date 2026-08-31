package biz.paluch.logging.gelf.log4j;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.ThreadContext;

import biz.paluch.logging.gelf.GelfUtil;
import biz.paluch.logging.gelf.LogEvent;
import biz.paluch.logging.gelf.LogMessageField;
import biz.paluch.logging.gelf.MdcMessageField;
import biz.paluch.logging.gelf.MessageField;

/**
 * @author <a href="mailto:mpaluch@paluch.biz">Mark Paluch</a>
 * @since 26.09.13 15:37
 */
public class Log4jLogEvent implements LogEvent {

	private org.apache.logging.log4j.core.LogEvent loggingEvent;

	public Log4jLogEvent(org.apache.logging.log4j.core.LogEvent loggingEvent) {
		this.loggingEvent = loggingEvent;
	}

	public String getMessage() {
		return loggingEvent.getMessage().getFormattedMessage();
	}

	public Object[] getParameters() {
		return new Object[0];
	}

	public Throwable getThrowable() {
		return loggingEvent.getThrown();
	}

	public long getLogTimestamp() {
		return loggingEvent.getTimeMillis();
	}

	public String getSyslogLevel() {
		return "" + levelToSyslogLevel(loggingEvent.getLevel());
	}

	private int levelToSyslogLevel(final Level level) {
		final int syslogLevel;

		switch (level.getStandardLevel()) {
		case FATAL:
			return 2;
		case ERROR:
			return 3;
		case WARN:
			return 4;
		case INFO:
			return 6;
		default:
			return 7;
		}
	}

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

		switch (field.getNamedLogField()) {
		case Severity:
			return loggingEvent.getLevel().toString();
		case ThreadName:
			return loggingEvent.getThreadName();
		case SourceClassName:
			StackTraceElement source = loggingEvent.getSource();
			if (source != null) {
				return source.getClassName();
			}
			return null;
		case SourceMethodName:
			source = loggingEvent.getSource();
			if (source != null) {
				return source.getMethodName();
			}
			return null;
		case SourceSimpleClassName:
			source = loggingEvent.getSource();
			if (source != null) {
				return GelfUtil.getSimpleClassName(source.getClassName());
			}
			return null;
		}

		throw new UnsupportedOperationException("Cannot provide value for " + field);
	}

	private String getValue(MdcMessageField field) {

		Object value = ThreadContext.get(field.getMdcName());
		if (value != null) {
			return value.toString();
		}

		return null;
	}
}
