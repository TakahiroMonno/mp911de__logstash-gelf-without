package biz.paluch.logging.gelf.log4j2;

import java.io.IOException;
import java.io.Serializable;
import java.net.SocketException;
import java.net.UnknownHostException;

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

import biz.paluch.logging.gelf.LogMessageField;
import biz.paluch.logging.gelf.MdcMessageField;
import biz.paluch.logging.gelf.StaticMessageField;
import biz.paluch.logging.gelf.intern.GelfMessage;
import biz.paluch.logging.gelf.intern.GelfSender;
import biz.paluch.logging.gelf.intern.GelfSenderFactory;
import biz.paluch.logging.gelf.log4j.MdcGelfMessageAssembler;

/**
 * Logging-Appender for GELF (Graylog Extended Logging Format). This Log4j 2 Appender creates GELF Messages and posts them using UDP
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
 * <li>filter (Optional): nested Log4j 2 Filter, e.g. ThresholdFilter for log level filtering</li>
 * <li>additionalFields (Optional): Post additional fields. Eg. additionalFields=fieldName=Value,field2=value2</li>
 * <li>mdcFields (Optional): Post additional fields, pull Values from the Log4j 2 ThreadContext. Name of the Fields are comma-separated
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
@Plugin(name = "GelfLogAppender", category = "Core", elementType = "appender", printObject = true)
public class GelfLogAppender extends AbstractAppender {

    protected GelfSender gelfSender;
    protected MdcGelfMessageAssembler gelfMessageAssembler;

    protected GelfLogAppender(String name, Filter filter, Layout<? extends Serializable> layout, boolean ignoreExceptions) {
        super(name, filter, layout, ignoreExceptions, Property.EMPTY_ARRAY);
        gelfMessageAssembler = new MdcGelfMessageAssembler();
        gelfMessageAssembler.addFields(LogMessageField.getDefaultMapping());
    }

    @PluginFactory
    public static GelfLogAppender createAppender(@PluginAttribute("name") String name, @PluginAttribute("host") String host,
            @PluginAttribute(value = "port", defaultInt = 12201) int port, @PluginAttribute("originHost") String originHost,
            @PluginAttribute(value = "extractStackTrace", defaultBoolean = false) boolean extractStackTrace,
            @PluginAttribute(value = "filterStackTrace", defaultBoolean = false) boolean filterStackTrace,
            @PluginAttribute(value = "mdcProfiling", defaultBoolean = false) boolean mdcProfiling,
            @PluginAttribute("facility") String facility, @PluginAttribute("additionalFields") String additionalFields,
            @PluginAttribute("mdcFields") String mdcFields, @PluginAttribute("timestampPattern") String timestampPattern,
            @PluginAttribute(value = "maximumMessageSize", defaultInt = 8192) int maximumMessageSize,
            @PluginAttribute("testSenderClass") String testSenderClass,
            @PluginAttribute(value = "ignoreExceptions", defaultBoolean = true) boolean ignoreExceptions,
            @PluginElement("Filter") Filter filter) {

        GelfLogAppender appender = new GelfLogAppender(name, filter, null, ignoreExceptions);

        if (host != null) {
            appender.setHost(host);
        }
        appender.setPort(port);

        if (originHost != null) {
            appender.setOriginHost(originHost);
        }

        appender.setExtractStackTrace(extractStackTrace);
        appender.setFilterStackTrace(filterStackTrace);
        appender.setMdcProfiling(mdcProfiling);

        if (facility != null) {
            appender.setFacility(facility);
        }

        if (additionalFields != null) {
            appender.setAdditionalFields(additionalFields);
        }

        if (mdcFields != null) {
            appender.setMdcFields(mdcFields);
        }

        if (timestampPattern != null) {
            appender.setTimestampPattern(timestampPattern);
        }

        appender.setMaximumMessageSize(maximumMessageSize);

        if (testSenderClass != null) {
            appender.setTestSenderClass(testSenderClass);
        }

        return appender;
    }

    @Override
    public void append(org.apache.logging.log4j.core.LogEvent event) {
        if (event == null) {
            return;
        }

        if (null == gelfSender) {
            if (gelfMessageAssembler.getHost() == null) {
                error("Graylog2 hostname is empty!");
            } else {
                try {
                    this.gelfSender = GelfSenderFactory.createSender(gelfMessageAssembler.getHost(), gelfMessageAssembler.getPort());
                } catch (UnknownHostException e) {
                    error("Unknown Graylog2 hostname:" + gelfMessageAssembler.getHost(), e);
                } catch (SocketException e) {
                    error("Socket exception", e);
                } catch (IOException e) {
                    error("IO exception", e);
                }
            }
        }

        try {
            GelfMessage message = createGelfMessage(event);
            if (!message.isValid()) {
                error("GELF Message is invalid: " + message.toJson());
            }

            if (null == gelfSender || !gelfSender.sendMessage(message)) {
                error("Could not send GELF message");
            }
        } catch (Exception e) {
            error("Could not send GELF message", event, e);
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

    protected GelfMessage createGelfMessage(final org.apache.logging.log4j.core.LogEvent loggingEvent) {
        return gelfMessageAssembler.createGelfMessage(new Log4jLogEvent(loggingEvent));
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
                final Class<?> clazz = Class.forName(testSender);
                gelfSender = (GelfSender) clazz.newInstance();
            }
        } catch (final Exception e) {
            // ignore
        }
    }
}
