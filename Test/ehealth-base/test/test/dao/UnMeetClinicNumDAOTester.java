package test.dao;

import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import eh.bus.dao.UnMeetClinicNumDAO;

public class UnMeetClinicNumDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	
	public void testGetUnMeetClinicNum(){
		int doctorId = 2;
//		boolean groupFlag = false;
		boolean groupFlag = true;
		UnMeetClinicNumDAO dao =appContext.getBean("unMeetClinicNumDAO", UnMeetClinicNumDAO.class);
		long count = dao.getUnMeetClinicNum(doctorId,groupFlag);
		System.out.println(count);
	}
}
