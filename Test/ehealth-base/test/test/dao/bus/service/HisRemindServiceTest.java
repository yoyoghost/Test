package test.dao.bus.service;

import eh.bus.dao.ConsultDAO;
import eh.bus.service.AppointService;
import eh.bus.service.HisRemindService;
import eh.util.Easemob;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.concurrent.TimeUnit;

/**
 * Created by zhangx on 2016/5/24.
 */
public class HisRemindServiceTest extends TestCase {

    private static ClassPathXmlApplicationContext appContext;
    private static HisRemindService service;
    static{
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
        service = appContext.getBean("hisRemindService", HisRemindService.class);
    }

}
