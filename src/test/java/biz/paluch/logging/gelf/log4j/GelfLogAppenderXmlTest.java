package biz.paluch.logging.gelf.log4j;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.Before;

/**
 * @author <a href="mailto:mpaluch@paluch.biz">Mark Paluch</a>
 * @since 27.09.13 07:47
 */
public class GelfLogAppenderXmlTest extends AbstractGelfLogAppenderTest {

    @Before
    public void before() throws Exception {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        context.stop();
        context.start();
        GelfTestSender.getMessages().clear();
        Configurator.initialize(null, "classpath:log4j2.xml");
        ThreadContext.remove("mdcField1");
    }

}
