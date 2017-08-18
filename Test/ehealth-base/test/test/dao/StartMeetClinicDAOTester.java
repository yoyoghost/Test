package test.dao;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.controller.exception.ControllerException;
import ctd.persistence.exception.DAOException;

import eh.bus.dao.StartMeetClinicDAO;
import eh.entity.bus.MeetClinicResult;

public class StartMeetClinicDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	
	/*public void testStartMeetClinic() throws DAOException, ControllerException{
		int meetClinicId = 96;
		int meetClinicResultId = 140;
		int doctorId = 1;
		Integer exeOrgan = 1;
		Integer exeDepart = 1;
		StartMeetClinicDAO dao =appContext.getBean("startMeetClinicDAO", StartMeetClinicDAO.class);
		boolean start = dao.startMeetClinic(meetClinicId, meetClinicResultId, doctorId, exeOrgan, exeDepart);
		System.out.println(start);
	}*/
	
	public void testStartMeetClinic1() throws DAOException, ControllerException{
		int meetClinicId = 2066;
		int meetClinicResultId = 2371;
		int doctorId = 1182;
		Integer exeOrgan = 1;
		Integer exeDepart = 70;
		MeetClinicResult meetClinicResult = new MeetClinicResult();
		meetClinicResult.setMeetClinicId(meetClinicId);
		meetClinicResult.setMeetClinicResultId(meetClinicResultId);
		meetClinicResult.setExeDoctor(doctorId);
		meetClinicResult.setExeOrgan(exeOrgan);
		meetClinicResult.setExeDepart(exeDepart);
		StartMeetClinicDAO dao =appContext.getBean("startMeetClinicDAO", StartMeetClinicDAO.class);
		boolean start = dao.startMeetClinicNew(meetClinicResult);
		System.out.println(start);
	}
}
