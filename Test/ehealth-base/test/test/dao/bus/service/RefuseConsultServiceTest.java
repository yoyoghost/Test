package test.dao.bus.service;

import eh.bus.dao.ConsultDAO;
import eh.bus.service.consult.RefuseConsultService;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.concurrent.TimeUnit;

/**
 * Created by zhangx on 2016/5/24.
 */
public class RefuseConsultServiceTest extends TestCase {

    private static ClassPathXmlApplicationContext appContext;
    private static RefuseConsultService service;

    static{
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
        service=new RefuseConsultService();
    }

    public void testsendSmsToPatWithRefundSucc(){
        try {
            int id=674;
            ConsultDAO dao=appContext.getBean("consultDAO", ConsultDAO.class);



            //将线程睡眠2秒，否则短信发送不成功
            TimeUnit.SECONDS.sleep(20);

        } catch (InterruptedException e1) {
            e1.printStackTrace();
        }

    }


}
