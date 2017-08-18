package test.dao;

import ctd.persistence.exception.DAOException;
import eh.bus.dao.ReportsGuideControlDAO;
import eh.entity.bus.RepGuideControl;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Created by Chuwei on 2016/8/19.
 */
public class ReportsGuideControlDAOTest extends TestCase {
    private static ClassPathXmlApplicationContext appContext;
    private static ReportsGuideControlDAO dao;

    static{
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
        dao =appContext.getBean("reportsGuideControlDAO", ReportsGuideControlDAO.class);
    }
    public void testGetByOpenId() throws DAOException {
        String openId = "abc";
        RepGuideControl rep = dao.getByOpenId(openId);
        if (rep == null) {
            System.out.println("true");
        } else{
            System.out.println("false");
        }
    }

}
