package test.dao.his;

import eh.bus.service.AppointSourceService;
import eh.entity.his.push.callNum.HisCallNumReqMsg;
import eh.entity.his.push.callNum.PushRequestModel;
import eh.push.NgariService;
import eh.utils.DateConversion;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class NgariServiceTest  extends TestCase {
    private static ClassPathXmlApplicationContext appContext;
    private static NgariService service;
    static{
        appContext = new ClassPathXmlApplicationContext("spring.xml");
        service = appContext.getBean("ngariService", NgariService.class);
    }
  public  void testReaciveMsg(){
      NgariService  ngariService = new NgariService();
      PushRequestModel  req= new PushRequestModel();
      req.setServiceId("PushNumInfo");
      HisCallNumReqMsg r = new HisCallNumReqMsg();
      r.setAppointId("");
      r.setCallNum(8);
      r.setOrderNum(10);
      r.setDoctorID(0);
      r.setDoctorName("医生姓名");
      r.setDepartCode("asd");
      r.setDepartName("挂号科室");
      r.setIdCard("330481198909072211");
      r.setPatientName("张宪强");
      r.setMobile("18868744478");
      r.setOrganID(1000423);
      r.setStartTime(DateConversion.getCurrentDate("2016-11-01 12:00:00","yyyy-MM-dd HH:mm:ss"));
      req.setData(r);
      ngariService.reciveHisMsg(req);
  }
}
