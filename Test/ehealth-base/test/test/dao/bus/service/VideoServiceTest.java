package test.dao.bus.service;

import eh.bus.constant.CloudClinicSetConstant;
import eh.bus.service.AppointService;
import eh.bus.service.HisRemindService;
import eh.bus.service.VideoService;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.concurrent.TimeUnit;

/**
 * Created by zhangx on 2016/5/24.
 */
public class VideoServiceTest extends TestCase {

    private static ClassPathXmlApplicationContext appContext;
    private static VideoService service;
    static{
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
        service = appContext.getBean("videoService", VideoService.class);
    }


    public void testgetAppointMeetingRoom(){
        String telClinicId="14741931868873948";
        String platform= CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_XIAOYU;
        System.out.println(service.getAppointMeetingRoom(telClinicId,platform));
    }

    public void testMeetingIsValid() {
        System.out.println(service.meetingIsValid(17714));
    }
}
