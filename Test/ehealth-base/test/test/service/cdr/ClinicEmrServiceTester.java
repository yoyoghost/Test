package test.service.cdr;

import ctd.persistence.exception.DAOException;
import eh.cdr.dao.ClinicEmrDAO;
import eh.cdr.service.ClinicEmrService;
import eh.entity.cdr.ClinicEmr;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Created by wnw on 2016/11/3.
 */
public class ClinicEmrServiceTester extends TestCase{
    private static ClassPathXmlApplicationContext appContext;

    static{
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
    }
    public void testGetEmrUrlByMpiId(){
        ClinicEmrService service = appContext.getBean("clinicEmrService",ClinicEmrService.class);
        System.out.println(service.getEmrUrlByMpiId("2c9081814cc3ad35014cc3e0361f0000"));
        System.out.println(service.getEmrUrlByMpiId("123"));
    }
    public void testGetPatientId(){
        ClinicEmrService service = appContext.getBean("clinicEmrService",ClinicEmrService.class);
        System.out.println(service.getPatientId("2c9081814cc3ad35014cc3e0361f0000"));
    }
}
