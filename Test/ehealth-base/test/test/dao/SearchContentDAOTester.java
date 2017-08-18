package test.dao;

import eh.bus.dao.SearchContentDAO;
import eh.entity.bus.SearchContent;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Created by luf on 2016/10/8.
 */

public class SearchContentDAOTester extends TestCase {

    private static ClassPathXmlApplicationContext appContext;
    private static SearchContentDAO dao;

    static {
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
        dao = appContext.getBean("searchContentDAO", SearchContentDAO.class);
    }

    public void testAddSearchContent() {
        for (int i = 0; i < 5; i++) {
            SearchContent searchContent = new SearchContent();
            searchContent.setMpiId("2c9081814d689a20014d6b6c4ad80001");
            searchContent.setContent("邵逸夫医院1");
            dao.addSearchContent(searchContent,0);
            searchContent.setContent("邵逸夫1");
            searchContent.setBussType(1);
            dao.addSearchContent(searchContent,0);
        }
    }

    public void testFindContentByMpiId() {
        System.out.println(dao.findContentByMpiId("2c9081814d689a20014d6b6c4ad80001"));
    }

    public void testFindContentsByMpiIdWithBuss() {
        System.out.println(dao.findContentsByMpiIdWithBuss("2c9081814d689a20014d6b6c4ad80001",1));
    }

}
