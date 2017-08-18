package test.opservice;

import eh.op.service.ScheduleOpService;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Created by houxr on 2016/5/25.
 * 运营平台排班相关服务测试
 */
public class SchedeleOpServiceTester extends TestCase {
    private static ClassPathXmlApplicationContext appContext;
    private static ScheduleOpService service;

    static {
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
        service = appContext.getBean("scheduleOpService", ScheduleOpService.class);
    }
}
