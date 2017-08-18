package test.opservice;

import eh.bus.service.UnLoginSevice;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Created by houxr on 2016/8/2.
 */
public class UnLoginServiceTester extends TestCase {
    private static ClassPathXmlApplicationContext appContext;
    private static UnLoginSevice unLoginSevice;

    static {
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
        unLoginSevice = appContext.getBean("unLoginSevice", UnLoginSevice.class);
    }

    public void testRegisteredDoctorAccountByOtherOrgan(){
        unLoginSevice.RegisteredDoctorAccount("15809429943", "赵青青", "620524201512236653", 0, "02", "99", 1234,"天水四院");
    }
}
