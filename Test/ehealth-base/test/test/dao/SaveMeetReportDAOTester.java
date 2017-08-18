package test.dao;

import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import ctd.controller.exception.ControllerException;
import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import eh.bus.dao.SaveMeetReportDAO;
import eh.entity.bus.MeetClinicResult;

public class SaveMeetReportDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	
	/*public void testSaveMeetReport() throws DAOException, ControllerException{
		int doctorId = 13;
		int meetClinicResultId = 3;
		Integer exeOrgan = 11;
		Integer exeDepart = 11;
		Integer exeDoctor = 13;
		String meetReport = "bbb";
		SaveMeetReportDAO dao =appContext.getBean("saveMeetReportDAO", SaveMeetReportDAO.class);
		dao.saveMeetReport(meetClinicResultId,doctorId,exeOrgan,exeDepart,exeDoctor,meetReport);
	}*/
	
	public void testSaveMeetReportNew() throws DAOException, ControllerException{
		int meetClinicResultId = 550;
		Integer exeOrgan = 1;
		Integer exeDepart = 70;
		Integer exeDoctor = 1178;
		String meetReport = "确认是胃溃疡、用养胃药即可、注意忌辛辣食物。平时可多喝水、多吃水果、多按摩胃部。早餐尽量吃稀饭等流质食物。<br /><br />如果病情加重可转诊到我院！";
		MeetClinicResult meetClinicResult = new MeetClinicResult();
		meetClinicResult.setMeetClinicResultId(meetClinicResultId);
		meetClinicResult.setExeOrgan(exeOrgan);
		meetClinicResult.setExeDoctor(exeDoctor);
		meetClinicResult.setExeDepart(exeDepart);
		meetClinicResult.setMeetReport(meetReport);
		SaveMeetReportDAO dao =appContext.getBean("saveMeetReportDAO", SaveMeetReportDAO.class);
		dao.saveMeetReportNew(meetClinicResult);
	}
	
	public void testGetMeetReport() throws DAOException, ControllerException{
		int meetClinicResultid = 1;
		SaveMeetReportDAO dao =appContext.getBean("saveMeetReportDAO", SaveMeetReportDAO.class);
		MeetClinicResult mc = dao.getByMeetClinicResultId(meetClinicResultid);
		System.out.println(JSONUtils.toString(mc));
	}
}
