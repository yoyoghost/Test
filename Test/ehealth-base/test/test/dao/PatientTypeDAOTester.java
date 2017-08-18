package test.dao;

import ctd.util.JSONUtils;
import eh.entity.mpi.PatientType;
import eh.mpi.dao.PatientTypeDAO;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.List;

/**
 * Created by luf on 2016/5/5.
 */

public class PatientTypeDAOTester extends TestCase {
    private static ClassPathXmlApplicationContext appContext;
    private static PatientTypeDAO dao;
    static {
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
        dao = appContext.getBean("patientTypeDAO", PatientTypeDAO.class);
    }

    public void testFindTypeByAddr() {
        String addr = "4";
        List<PatientType> types = dao.findTypeByAddr(addr);
        System.out.println(JSONUtils.toString(types));
    }
}
