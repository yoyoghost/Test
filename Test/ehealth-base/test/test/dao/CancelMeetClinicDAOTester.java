package test.dao;

import eh.bus.dao.CancelMeetClinicDAO;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CancelMeetClinicDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	 
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	
	/*public void testCancelMeetClinic() throws DAOException, ControllerException{
		int meetClinicId = 40;
		int cancelOrgan = 1;
		int cancelDepart = 1;
		int DoctorId = 1;
		String cancelCause = "已确诊";
		CancelMeetClinicDAO dao =appContext.getBean("cancelMeetClinicDAO", CancelMeetClinicDAO.class);
		dao.updateCancelMeetClinic(meetClinicId, cancelOrgan, cancelDepart, DoctorId, cancelCause);
	}*/
	
	/**
	 * 取消会诊医生服务（取消执行单）
	 * @author LF
	 * @param meetClinicId
	 * @param targetDoctor
	 */
	public void testUpdateTargetDoctor() {
		Integer meetClinicId = 278;
		Integer targetDoctor = 1178;
		CancelMeetClinicDAO dao =appContext.getBean("cancelMeetClinicDAO", CancelMeetClinicDAO.class);
		dao.updateTargetDoctor(meetClinicId, targetDoctor);
	}
}
