package test.dao;

import java.util.List;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import eh.bus.dao.QueryMeetClinicDAO;
import eh.entity.bus.MeetClinicAndResult;

public class QueryMeetClinicDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	
	/*public void testQueryMeetClinicDAO(){
		int doctorId = 1177;
//		boolean groupFlag = true;
		boolean groupFlag = false;
		QueryMeetClinicDAO dao = appContext.getBean("queryMeetClinicDAO",QueryMeetClinicDAO.class);
		Map<String, List<MeetClinicAndResult>> map = dao.queryMeetClinic(doctorId,groupFlag);
		System.out.println(JSONUtils.toString(map));
	}*/
	
	public void testQueryMeetClinicDAO1(){
		int doctorId = 40;
//		boolean groupFlag = true;
		boolean groupFlag = false;
		QueryMeetClinicDAO dao = appContext.getBean("queryMeetClinicDAO",QueryMeetClinicDAO.class);
		List<MeetClinicAndResult> map = dao.queryMeetClinicNew(doctorId,groupFlag);
		System.out.println(JSONUtils.toString(map));
	}
	
	/**
	 * 查询待处理会诊单服务(添加分页)
	 * @author LF
	 * @return
	 * @throws DAOException
	 */
	public void testQueryMeetClinic() {
		int doctorId = 40;
//		boolean groupFlag = true;
		boolean groupFlag = false;
		QueryMeetClinicDAO dao = appContext.getBean("queryMeetClinicDAO",QueryMeetClinicDAO.class);
		List<MeetClinicAndResult> map = dao.queryMeetClinic(doctorId,groupFlag,10);
		System.out.println(JSONUtils.toString(map));
	}

	public void testQueryMeetClinicStartAndLimit() {
		QueryMeetClinicDAO dao = appContext.getBean("queryMeetClinicDAO",QueryMeetClinicDAO.class);
		System.out.println(JSONUtils.toString(dao.queryMeetClinicStartAndLimit(3848,false,0,10)));
	}
}
