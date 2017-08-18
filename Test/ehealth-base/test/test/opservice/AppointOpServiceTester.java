package test.opservice;

import ctd.util.JSONUtils;
import eh.op.service.AppointOpService;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Created by houxr on 2016/6/1.
 */
public class AppointOpServiceTester extends TestCase{
    private static ClassPathXmlApplicationContext appContext;
    private static AppointOpService service;

    static {
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
        service = appContext.getBean("appointOpService", AppointOpService.class);
    }

    public void testgetAppointRecordById(){
        System.out.println(JSONUtils.toString(service.queryAppointRecordByAppointRecordId(28390)));
    }
}
