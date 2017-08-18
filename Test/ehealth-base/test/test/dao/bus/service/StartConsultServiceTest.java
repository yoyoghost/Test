package test.dao.bus.service;

import ctd.persistence.DAOFactory;
import ctd.util.JSONUtils;
import eh.bus.dao.WxTemplateDAO;
import eh.bus.service.consult.RequestConsultService;
import eh.bus.service.consult.StartConsultService;
import eh.entity.bus.Consult;
import eh.util.SameUserMatching;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.HashMap;

/**
 * Created by zhangx on 2016/5/24.
 */
public class StartConsultServiceTest extends TestCase {

    private static ClassPathXmlApplicationContext appContext;
    private static StartConsultService service;

    static{
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
        service=appContext.getBean("startConsultService",StartConsultService.class);
    }


    public void testcanAcceptConsultInfo(){
        System.out.println(JSONUtils.toString(service.canAcceptConsultInfo(561,1482)));
    }

}
