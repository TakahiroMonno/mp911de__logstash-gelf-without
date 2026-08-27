package biz.paluch.logging.gelf.log4j2;

import java.net.URI;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LoggerContext;
import org.junit.Before;

import biz.paluch.logging.gelf.log4j.GelfTestSender;

/**
 * @author <a href="mailto:mpaluch@paluch.biz">Mark Paluch</a>
 */
public class GelfLogAppenderXmlTest extends AbstractGelfLogAppenderTest {

    @Before
    public void before() throws Exception {
        GelfTestSender.getMessages().clear();
        ThreadContext.clearMap();

        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        URI uri = getClass().getResource("/log4j2-test.xml").toURI();
        context.setConfigLocation(uri);
    }

}
