package test.dao;

import eh.base.dao.ProfessionDAO;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Created by luf on 2016/10/6.
 */

public class ProfessionDAOTester extends TestCase {
    private static ClassPathXmlApplicationContext appContext;
    private static ProfessionDAO dao;

    static {
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
        dao = appContext.getBean("professionDAO", ProfessionDAO.class);
    }

    public void testFindByText() {
        System.out.println(dao.findKeysByText("%内科%"));
    }
}
