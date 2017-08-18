package test.dao.bus.service;

import ctd.util.JSONUtils;
import ctd.util.converter.support.StringToDate;
import eh.bus.constant.CloudClinicSetConstant;
import eh.bus.dao.AppointSourceDAO;
import eh.bus.service.AppointSourceService;
import eh.bus.service.VideoService;
import eh.his.service.HisPatientService;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.Date;

/**
 * Created by zhangx on 2016/5/24.
 */
public class AppointSourceServiceTest extends TestCase {

    private static ClassPathXmlApplicationContext appContext;
//    private static AppointSourceService service;
//    static{
//        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
//        service = appContext.getBean("appointSourceService", AppointSourceService.class);
//    }

    private static final HisPatientService service1;

    static {
        appContext = new ClassPathXmlApplicationContext("spring.xml");
        service1 = appContext.getBean("hisPatientService", HisPatientService.class);
    }

    public void testHHJHJJJ(){
//		service1.getNewPatientByOrgan(1);
        service1.getModifyPatientByOrgan(1);
    }

  

}
