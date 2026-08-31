package biz.paluch.logging.gelf.log4j;

import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;

import biz.paluch.logging.gelf.LogMessageField;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;

import biz.paluch.logging.gelf.MdcMessageField;
import biz.paluch.logging.gelf.StaticMessageField;
import biz.paluch.logging.gelf.intern.GelfMessage;
import biz.paluch.logging.gelf.intern.GelfSender;
import biz.paluch.logging.gelf.intern.GelfSenderFactory;

/**
 * Logging-Handler for GELF (Graylog Extended Logging Format). This Java-Util-Logging Handler creates GELF Messages and posts them using UDP
 * (default) or TCP. Following parameters are supported/needed:
 * <p/>
 * <ul>
 * <li>host (Mandatory): Hostname/IP-Address of the Logstash Host
 * <ul>
 * <li>tcp:(the host) for TCP, e.g. tcp:127.0.0.1 or tcp:some.host.com</li>
 * <li>udp:(the host) for UDP, e.g. udp:127.0.0.1 or udp:some.host.com</li>
 * <li>(the host) for UDP, e.g. 127.0.0.1 or some.host.com</li>
 * </ul>
 * </li>
 * <li>port (Optional): Port, default 12201</li>
 * <li>originHost (Optional): Originating Hostname, default FQDN Hostname</li>
 * <li>extractStackTrace (Optional): Post Stack-Trace to StackTrace field, default false</li>
 * <li>filterStackTrace (Optional): Perform Stack-Trace filtering (true/false), default false</li>
 * <li>mdcProfiling (Optional): Perform Profiling (Call-Duration) based on MDC Data. See <a href="#mdcProfiling">MDC Profiling</a>, default
 * false</li>
 * <li>facility (Optional): Name of the Facility, default gelf-java</li>
 * <li>threshold (Optional): Log-Level, default INFO</li>
 * <li>filter (Optional): Class-Name of a Log-Filter, default none</li>
 * <li>additionalFields(number) (Optional): Post additional fields. Eg. .GelfLogHandler.additionalFields=fieldName=Value,field2=value2</li>
 * <li>mdcFields (Optional): Post additional fields, pull Values from MDC. Name of the Fields are comma-separated
 * mdcFields=Application,Version,SomeOtherFieldName</li>
 * </ul>
 * <p/>
 * <a name="mdcProfiling"></a>
 * <h2>MDC Profiling</h2>
 * <p>
 * MDC Profiling allows to calculate the runtime from request start up to the time until the log message was generated. You must set one
 * value in the MDC:
 * <ul>
 * <li>profiling.requestStart.millis: Time Millis of the Request-Start (Long or String)</li>
 * </ul>
 * <p/>
 * Two values are set by the Log Appender:
 * <ul>
 * <li>profiling.requestEnd: End-Time of the Request-End in Date.toString-representation</li>
 * <li>profiling.requestDuration: Duration of the request (e.g. 205ms, 16sec)</li>
 * </ul>
 * <p/>
 * </p>
 */
@Plugin(name = "GelfLogAppender", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE, printObject = true)
public class GelfLogAppender extends AbstractAppender {

	protected GelfSender gelfSender;
	protected MdcGelfMessageAssembler gelfMessageAssembler;

	public GelfLogAppender(String name, Filter filter) {
		super(name, filter, PatternLayout.createDefaultLayout(), true);
		this.gelfMessageAssembler = new MdcGelfMessageAssembler();
		this.gelfMessageAssembler.addFields(LogMessageField.getDefaultMapping());
	}

	@Override
	public void append(LogEvent event) {
		if (event == null) {
			return;
		}

		if (null == gelfSender) {
			if (gelfMessageAssembler.getHost() == null) {
				LOGGER.error("Graylog2 hostname is empty!");
			} else {
				try {
					this.gelfSender = GelfSenderFactory.createSender(gelfMessageAssembler.getHost(), gelfMessageAssembler.getPort());
				} catch (UnknownHostException e) {
					LOGGER.error("Unknown Graylog2 hostname:" + gelfMessageAssembler.getHost(), e);
				} catch (SocketException e) {
					LOGGER.error("Socket exception", e);
				} catch (IOException e) {
					LOGGER.error("IO exception", e);
				}
			}
		}

		try {
			GelfMessage message = createGelfMessage(event);
			if (!message.isValid()) {
				LOGGER.error("GELF Message is invalid: " + message.toJson());
			}

			if (null == gelfSender || !gelfSender.sendMessage(message)) {
				LOGGER.error("Could not send GELF message");
			}
		} catch (Exception e) {
			LOGGER.error("Could not send GELF message", e);
		}
	}

	@Override
	public void stop() {
		super.stop();
		if (null != gelfSender) {
			gelfSender.close();
			gelfSender = null;
		}
	}

	protected GelfMessage createGelfMessage(final LogEvent loggingEvent) {
		return gelfMessageAssembler.createGelfMessage(new Log4jLogEvent(loggingEvent));
	}

	@PluginFactory
	public static GelfLogAppender createAppender(
			@PluginAttribute("name") String name,
			@PluginElement("Filter") Filter filter,
			@PluginAttribute("GraylogHost") String graylogHost,
			@PluginAttribute("GraylogPort") int graylogPort,
			@PluginAttribute("Host") String host,
			@PluginAttribute("Port") int port,
			@PluginAttribute("Facility") String facility,
			@PluginAttribute("OriginHost") String originHost,
			@PluginAttribute("ExtractStackTrace") boolean extractStackTrace,
			@PluginAttribute("FilterStackTrace") boolean filterStackTrace,
			@PluginAttribute("MdcProfiling") boolean mdcProfiling,
			@PluginAttribute("TimestampPattern") String timestampPattern,
			@PluginAttribute("MaximumMessageSize") int maximumMessageSize,
			@PluginAttribute("AdditionalFields") String additionalFields,
			@PluginAttribute("MdcFields") String mdcFields,
			@PluginAttribute("TestSenderClass") String testSenderClass) {

		GelfLogAppender appender = new GelfLogAppender(name, filter);

		// Use GraylogHost/GraylogPort if specified, otherwise use Host/Port
		String hostToUse = graylogHost != null ? graylogHost : host;
		int portToUse = graylogPort > 0 ? graylogPort : (port > 0 ? port : 12201);

		if (hostToUse != null) {
			appender.setGraylogHost(hostToUse);
		}
		if (portToUse > 0) {
			appender.setGraylogPort(portToUse);
		}
		if (facility != null) {
			appender.setFacility(facility);
		}
		if (originHost != null) {
			appender.setOriginHost(originHost);
		}
		appender.setExtractStackTrace(extractStackTrace);
		appender.setFilterStackTrace(filterStackTrace);
		appender.setMdcProfiling(mdcProfiling);
		if (timestampPattern != null) {
			appender.setTimestampPattern(timestampPattern);
		}
		if (maximumMessageSize > 0) {
			appender.setMaximumMessageSize(maximumMessageSize);
		}
		if (additionalFields != null) {
			appender.setAdditionalFields(additionalFields);
		}
		if (mdcFields != null) {
			appender.setMdcFields(mdcFields);
		}
		if (testSenderClass != null) {
			appender.setTestSenderClass(testSenderClass);
		}

		return appender;
	}

	public void setAdditionalFields(String fieldSpec) {

		String[] properties = fieldSpec.split(",");

		for (String field : properties) {
			final int index = field.indexOf('=');
			if (-1 != index) {
				gelfMessageAssembler.addField(new StaticMessageField(field.substring(0, index), field.substring(index + 1)));
			}
		}
	}

	public void setMdcFields(String fieldSpec) {
		String[] fields = fieldSpec.split(",");

		for (String field : fields) {
			gelfMessageAssembler.addField(new MdcMessageField(field.trim(), field.trim()));
		}
	}

	public String getGraylogHost() {
		return gelfMessageAssembler.getHost();
	}

	public void setGraylogHost(String graylogHost) {
		gelfMessageAssembler.setHost(graylogHost);
	}

	public int getGraylogPort() {
		return gelfMessageAssembler.getPort();
	}

	public void setGraylogPort(int graylogPort) {
		gelfMessageAssembler.setPort(graylogPort);
	}

	public String getHost() {
		return gelfMessageAssembler.getHost();
	}

	public void setHost(String host) {
		gelfMessageAssembler.setHost(host);
	}

	public int getPort() {
		return gelfMessageAssembler.getPort();
	}

	public void setPort(int port) {
		gelfMessageAssembler.setPort(port);
	}

	public String getOriginHost() {
		return gelfMessageAssembler.getOriginHost();
	}

	public void setOriginHost(String originHost) {
		gelfMessageAssembler.setOriginHost(originHost);
	}

	public String getFacility() {
		return gelfMessageAssembler.getFacility();
	}

	public void setFacility(String facility) {
		gelfMessageAssembler.setFacility(facility);
	}

	public boolean isExtractStackTrace() {
		return gelfMessageAssembler.isExtractStackTrace();
	}

	public void setExtractStackTrace(boolean extractStacktrace) {
		gelfMessageAssembler.setExtractStackTrace(extractStacktrace);
	}

	public boolean isFilterStackTrace() {
		return gelfMessageAssembler.isFilterStackTrace();
	}

	public void setFilterStackTrace(boolean filterStackTrace) {
		gelfMessageAssembler.setFilterStackTrace(filterStackTrace);
	}

	public boolean isMdcProfiling() {
		return gelfMessageAssembler.isMdcProfiling();
	}

	public void setMdcProfiling(boolean mdcProfiling) {
		gelfMessageAssembler.setMdcProfiling(mdcProfiling);
	}

	public String getTimestampPattern() {
		return gelfMessageAssembler.getTimestampPattern();
	}

	public void setTimestampPattern(String timestampPattern) {
		gelfMessageAssembler.setTimestampPattern(timestampPattern);
	}

	public int getMaximumMessageSize() {
		return gelfMessageAssembler.getMaximumMessageSize();
	}

	public void setMaximumMessageSize(int maximumMessageSize) {
		gelfMessageAssembler.setMaximumMessageSize(maximumMessageSize);
	}

	public void setTestSenderClass(String testSender) {
		// This only used for testing
		try {
			if (null != testSender) {
				final Class clazz = Class.forName(testSender);
				gelfSender = (GelfSender) clazz.newInstance();
			}
		} catch (final Exception e) {
			// ignore
		}
	}
}
