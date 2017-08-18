package test.opservice;

import eh.op.service.SourceOpService;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Created by houxr on 2016/5/25.
 */
public class SourceOpServiceTest extends TestCase {
    private static ClassPathXmlApplicationContext appContext;
    private static SourceOpService service;

    static {
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
        service = appContext.getBean("sourceOpService", SourceOpService.class);
    }
}
