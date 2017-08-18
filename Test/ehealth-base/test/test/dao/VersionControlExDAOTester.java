package test.dao;

import com.google.gson.Gson;
import eh.base.dao.VersionControlExDAO;
import eh.entity.base.VersionControlBean;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * company: ngarihealth
 * author: 0184/yu_yun
 * date:2016/4/15.
 */

public class VersionControlExDAOTester extends TestCase {

    private static ClassPathXmlApplicationContext appContext;

    static{
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
    }


    public void testCheckVersion(){
        VersionControlExDAO dao = appContext.getBean("versionControlExDAO",VersionControlExDAO.class);
        //Assert.assertNull(dao);
        VersionControlBean bean = dao.checkVersion(2,1,1);
        //Assert.assertNull(bean);

        System.out.println(new Gson().toJson(bean));

    }

    public void testCheckVersionServer(){
        VersionControlExDAO dao = appContext.getBean("versionControlExDAO",VersionControlExDAO.class);
        dao.checkVersionForServer(2,4,1);
    }
}
