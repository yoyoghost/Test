package test.dao;

import eh.bus.dao.PayBusinessDAO;

import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Created by hwg on 2016/11/2.
 */
public class PayBusinessDAOTester extends TestCase{
    private static ClassPathXmlApplicationContext appContext;
    private static PayBusinessDAO dao;
    static {
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
        dao = appContext.getBean("payBusinessDAO", PayBusinessDAO.class);
    }


    public void testUpdateHisbackCode(){
        String hisbackcode = "1111111";
        String outTradeNo ="pre20160914160033359147";
        dao.updateHisbackCode(hisbackcode,outTradeNo);
    }
}
