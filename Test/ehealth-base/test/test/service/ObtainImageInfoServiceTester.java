package test.service;

import eh.bus.service.AppointService;
import eh.bus.service.ObtainImageInfoService;
import eh.entity.bus.CheckRequest;
import eh.entity.mpi.Patient;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Created by Administrator on 2016/12/15.
 */
public class ObtainImageInfoServiceTester extends TestCase {

    private static ClassPathXmlApplicationContext appContext;
    private static ObtainImageInfoService service;

    static
    {
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
        service =appContext.getBean("obtainImageInfoService", ObtainImageInfoService.class);
    }

    public void testGetImageInfo(){
        Patient p = new Patient();
        p.setCardId("123456789123456789");
        p.setMpiId("2c9081824cc3ae4a014cc4ee8e2c0000");
        p.setPatientName("王超");
       service.getImageInfo();
    }
}
