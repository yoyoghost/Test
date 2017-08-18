package test.opservice;

import eh.op.service.AuditDoctorOpService;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Created by houxr on 2016/5/31.
 */
public class AuditDoctorOpServiceTester extends TestCase {

    private static ClassPathXmlApplicationContext appContext;
    private static AuditDoctorOpService service;

    static {
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
        service = appContext.getBean("auditDoctorOpService", AuditDoctorOpService.class);
    }

    public void testCountDoctorByOrganId(){
        //System.out.println("count========="+service.getCountDoctorByOrganId(1000024));
    }
}
