package test.dao;

import java.util.Date;
import java.util.List;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.controller.exception.ControllerException;
import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import ctd.util.converter.support.StringToDate;
import eh.bus.dao.EndMeetClinicDAO;
import eh.entity.bus.MeetClinicResult;

public class EndMeetClinicDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	
	/**
	 * 会诊结束服务
	 * @return
	 * @throws DAOException
	 * @throws ControllerException
	 */
	public void testEndMeetClinic() throws DAOException, ControllerException{
		int meetClinicId = 2066;
		int meetClinicResultId = 2372;
		EndMeetClinicDAO dao =appContext.getBean("endMeetClinicDAO", EndMeetClinicDAO.class);
		Boolean b = dao.endMeetClinic(meetClinicId, meetClinicResultId);
		System.out.println(b);
	}
	
	public void testDate(){
		Date startDate = new StringToDate().convert("2015-04-27 10:17:00");
		Date endDate = new StringToDate().convert("2015-04-27 10:47:59");
		Long long1 = endDate.getTime()-startDate.getTime();
		System.out.println(long1/60000.0);
	}
	
	public void test1(){
		Integer meetClinicId = 262;
		EndMeetClinicDAO dao =appContext.getBean("endMeetClinicDAO", EndMeetClinicDAO.class);
		List<MeetClinicResult> meetClinicResults = dao.findByMeetClinicId(meetClinicId);
		System.out.println(JSONUtils.toString(meetClinicResults));
	}
	
	/**
	 * 会诊单完成情况列表查询服务
	 * @author LF
	 * @return
	 */
	public void testFindListOfResult() {
		Integer meetClinicId = 366;
		EndMeetClinicDAO dao =appContext.getBean("endMeetClinicDAO", EndMeetClinicDAO.class);
		System.out.println(JSONUtils.toString(dao.findListOfResult(meetClinicId)));
	}
	
	/**
	 * 申请医生会诊强制结束服务
	 * @author LF
	 * @param meetClinicId
	 * @return
	 */
	/*public void testForcedEndMeetClinic() {
		Integer meetClinicId=284;
		EndMeetClinicDAO dao =appContext.getBean("endMeetClinicDAO", EndMeetClinicDAO.class);
		Boolean boolean1 = dao.forcedEndMeetClinic(meetClinicId);
		System.out.println(boolean1);
	}*/
	
	/**
	 * 申请医生会诊结束确认
	 * @author LF
	 * @param meetClinicId
	 * @return
	 */
	/*public void testEndByRequest() {
		Integer meetClinicId = 343;
		EndMeetClinicDAO dao = appContext.getBean("endMeetClinicDAO", EndMeetClinicDAO.class);
		System.out.println(dao.endByRequest(meetClinicId));
	}*/

	/**
	 * 申请医生会诊结束服务
	 * @author LF
	 * @param meetClinicId
	 * @param cancelCause
	 * @return
	 */
	public void testEndMeetClinicByRequest() {
		Integer meetClinicId = 1897;
		String cancelCause = "申请医生会诊结束测试";
		EndMeetClinicDAO dao = appContext.getBean("endMeetClinicDAO", EndMeetClinicDAO.class);
		System.out.println(dao.endMeetClinicByRequest(meetClinicId, cancelCause));
	}
	
	public void testFindTimeAndNum(){
		Integer meetClinicId = 621;
		EndMeetClinicDAO dao = appContext.getBean("endMeetClinicDAO", EndMeetClinicDAO.class);
		List<Long> l = dao.findNumByTime(meetClinicId);
		System.out.println(JSONUtils.toString(l));
		
		
	}
}
