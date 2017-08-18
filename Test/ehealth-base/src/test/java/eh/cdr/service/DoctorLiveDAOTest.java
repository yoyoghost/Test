package eh.cdr.service;

import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import eh.cdr.dao.DoctorLiveDAO;
import eh.entity.cdr.DoctorLive;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by liuya on 2017-7-5.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:spring-test.xml")
public class DoctorLiveDAOTest extends AbstractJUnit4SpringContextTests {
    @Test
    public void getLivingById() throws Exception {
        DoctorLiveDAO doctorLiveDAO = DAOFactory.getDAO(DoctorLiveDAO.class);
        DoctorLiveService service = AppContextHolder.getBean("doctorLiveService",DoctorLiveService.class);
//        System.out.println(JSONUtils.toString(doctorLiveDAO.getLivingById(9598, new Date())));
//        System.out.println(JSONUtils.toString(doctorLiveDAO.getByDoctorId(9598)));
//        doctorLiveDAO.updateURLByDoctorId(9598, "www.baidu.com");
        System.out.println(JSONUtils.toString(service.getLivingByMobile("13738049559")));
//        DoctorLive doctorLive = new DoctorLive();
//        doctorLive.setDoctorId(6563);
//        doctorLive.setEndDate(new SimpleDateFormat("yyyy-MM-dd hh:mm:ss").parse("2017-7-3 00:00:00"));
//        doctorLive.setStartDate(new SimpleDateFormat("yyyy-MM-dd hh:mm:ss").parse("2017-8-4 00:00:00"));
//        doctorLive.setURL("cccccccc");
//        System.out.println(service.saveDoctorLive(doctorLive));
//        System.out.println(JSONUtils.toString(doctorLiveDAO.getLivingById(1234, new Date())));
//        service.updateDoctorLive(doctorLive);
    }
}