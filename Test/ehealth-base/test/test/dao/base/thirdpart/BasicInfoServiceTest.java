package test.dao.base.thirdpart;

import eh.base.service.thirdparty.BasicInfoService;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.HashMap;


public class BasicInfoServiceTest {
    private static BasicInfoService dao;
    static {
        ClassPathXmlApplicationContext appContext = new ClassPathXmlApplicationContext("classpath:spring.xml");
        dao = appContext.getBean("basicInfoService", BasicInfoService.class);
    }
    @Test
    public void getDoctorInfoByDoctorId() throws Exception {

       HashMap<String,Object> map= dao.getDoctorInfoByMobile("13735891715");
       System.out.print(map);

    }
    @Test
    public  void testPushTeachMessage(){
        dao.pushTeachMessage("13735891715","hello","www.baidu.com");
    }


}