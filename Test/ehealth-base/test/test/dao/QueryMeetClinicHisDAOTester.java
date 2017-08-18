package test.dao;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import ctd.persistence.bean.QueryResult;
import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import ctd.util.converter.support.StringToDate;
import eh.bus.dao.QueryMeetClinicHisDAO;
import eh.entity.bus.MeetClinic;
import eh.entity.bus.MeetClinicAndResult;
import eh.entity.bus.MeetClinicResult;

public class QueryMeetClinicHisDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;

	static {
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}

	/**
	 * 历史会诊单查询服务
	 * 
	 * @author LF
	 * @return
	 */
	public void testQueryMeetClinicHis() throws DAOException {
		QueryMeetClinicHisDAO dao = appContext.getBean("queryMeetClinicHisDAO",
				QueryMeetClinicHisDAO.class);
		int doctorId = 1177;
		// Integer doctorId = null;
		String mpiId = null;
		// String mpiId = "402881834b6d0cfc014b6d0d04f10000";
		Date startTime = new StringToDate().convert("2015-05-06");
		Date endTime = new StringToDate().convert("2015-06-07 16:15:00");
		List<MeetClinicAndResult> list = dao.queryMeetClinicHis(startTime,
				endTime, doctorId, mpiId);
		// if (list != null && list.size() > 0) {
		System.out.println(JSONUtils.toString(list));
		// }
		// List<MeetClinicAndResult> list2 =
		// dao.queryMeetClinicHisLastMonth(doctorId, mpiId);
		// System.out.println(JSONUtils.toString(list2));
	}

	/**
	 * 历史会诊单查询服务(添加分页)
	 * 
	 * @author LF
	 * @return
	 */
	public void testQueryMeetClinicHisStart() {
		QueryMeetClinicHisDAO dao = appContext.getBean("queryMeetClinicHisDAO",
				QueryMeetClinicHisDAO.class);
		int doctorId = 40;
		// Integer doctorId = null;
		String mpiId = null;
		// String mpiId = "2c9081814cc3ad35014cc3e0361f0000";
		Date startTime = new StringToDate().convert("2015-01-01");
		Date endTime = new StringToDate().convert("2015-12-30");
		List<MeetClinicAndResult> list = dao.queryMeetClinicHisStart(startTime,
				endTime, doctorId, mpiId, 20);
		// if (list != null && list.size() > 0) {
		System.out.println(JSONUtils.toString(list));
		// }
		List<MeetClinicAndResult> list2 = dao.queryMeetClinicHisLastMonthStart(
				doctorId, mpiId, 0);
		System.out.println(JSONUtils.toString(list2));
		System.out.println(list2.size());
	}

	/*
	 * @SuppressWarnings("rawtypes") public void testQueryMeetClinicHis1()
	 * throws DAOException{ QueryMeetClinicHisDAO dao
	 * =appContext.getBean("queryMeetClinicHisDAO",
	 * QueryMeetClinicHisDAO.class); int doctorId = 1177; // Integer doctorId =
	 * null; String mpiId = null; // String mpiId =
	 * "402881834b6d0cfc014b6d0d04f10000"; Date startTime = new
	 * StringToDate().convert("2015-01-01"); Date endTime = new
	 * StringToDate().convert("2015-12-30"); List list =
	 * dao.queryMeetClinicHisOld(startTime,endTime,doctorId,mpiId);
	 * if(list!=null && list.size()>0){
	 * System.out.println(JSONUtils.toString(list)); } }
	 */

	/**
	 * 统计查询
	 * 
	 * @author ZX
	 * @date 2015-5-12 下午4:58:00
	 */
	public void testFindMeetClinicAndResultWithStatic() {

		QueryMeetClinicHisDAO dao = appContext.getBean("queryMeetClinicHisDAO",
				QueryMeetClinicHisDAO.class);

		Date startTime = new StringToDate().convert("2015-05-07");
		Date endTime = new StringToDate().convert("2015-05-07");

		MeetClinic mc = new MeetClinic();
		// mc.setRequestOrgan(1);
		// mc.setRequestDoctor(1182);

		MeetClinicResult mr = new MeetClinicResult();
		// mr.setTargetOrgan(1);
		// mr.setTargetDoctor(1195);
		// mr.setExeStatus(0);

		int start = 0;
		List<MeetClinicAndResult> list = dao.findMeetClinicAndResultWithStatic(
				startTime, endTime, mc, mr, start);

		System.out.println(JSONUtils.toString(list));
	}

	public void testGetMeetClinicNumWithStatic() {

		QueryMeetClinicHisDAO dao = appContext.getBean("queryMeetClinicHisDAO",
				QueryMeetClinicHisDAO.class);

		Date startTime = new StringToDate().convert("2015-05-06");
		Date endTime = new StringToDate().convert("2015-05-17");

		MeetClinic mc = new MeetClinic();
		// mc.setRequestOrgan(1);
		// mc.setRequestDoctor(1182);
		//
		MeetClinicResult mr = new MeetClinicResult();
		// mr.setTargetOrgan(1);
		// mr.setTargetDoctor(1195);
		// mr.setExeStatus(0);

		long list = dao.getNumWithStatic(startTime, endTime, mc, mr);

		System.out.println(JSONUtils.toString(list));
	}

	/**
	 * 申请方昨日会诊总数统计
	 * 
	 * @author ZX
	 * @date 2015-6-2 下午6:36:25
	 */
	public void testGetRequestNumForYestoday() {
		String manageUnit = "eh";
		QueryMeetClinicHisDAO dao = appContext.getBean("queryMeetClinicHisDAO",
				QueryMeetClinicHisDAO.class);
		long list = dao.getRequestNumForYesterday(manageUnit);
		System.out.println(JSONUtils.toString(list));
	}

	/**
	 * 申请方今日会诊总数统计
	 * 
	 * @author ZX
	 * @date 2015-6-2 下午6:36:25
	 */
	public void testGetRequestNumForToday() {
		String manageUnit = "eh";
		QueryMeetClinicHisDAO dao = appContext.getBean("queryMeetClinicHisDAO",
				QueryMeetClinicHisDAO.class);
		long list = dao.getRequestNumForToday(manageUnit);
		System.out.println(JSONUtils.toString(list));
	}

	/**
	 * 申请方总会诊数
	 * 
	 * @author ZX
	 * @date @date 2015-6-2 下午6:36:25
	 */
	public void testGetRequestNum() {
		String manageUnit = "eh";
		QueryMeetClinicHisDAO dao = appContext.getBean("queryMeetClinicHisDAO",
				QueryMeetClinicHisDAO.class);
		long list = dao.getRequestNum(manageUnit);
		System.out.println(JSONUtils.toString(list));
	}

	/**
	 * 接收方昨日会诊总数统计
	 * 
	 * @author ZX
	 * @date 2015-6-2 下午6:36:25
	 */
	public void testGetTargetNumForYestoday() {
		String manageUnit = "eh";
		QueryMeetClinicHisDAO dao = appContext.getBean("queryMeetClinicHisDAO",
				QueryMeetClinicHisDAO.class);
		long list = dao.getTargetNumForYesterday(manageUnit);
		System.out.println(JSONUtils.toString(list));
	}

	/**
	 * 接收方今日会诊总数统计
	 * 
	 * @author ZX
	 * @date 2015-6-2 下午6:36:25
	 */
	public void testGetTargetNumForToday() {
		String manageUnit = "eh";
		QueryMeetClinicHisDAO dao = appContext.getBean("queryMeetClinicHisDAO",
				QueryMeetClinicHisDAO.class);
		long list = dao.getTargetNumForToday(manageUnit);
		System.out.println(JSONUtils.toString(list));
	}

	/**
	 * 接收方总会诊数
	 * 
	 * @author ZX
	 * @date 2015-6-2 下午6:36:25
	 */
	public void testGetTargetNum() {
		String manageUnit = "eh";
		QueryMeetClinicHisDAO dao = appContext.getBean("queryMeetClinicHisDAO",
				QueryMeetClinicHisDAO.class);
		long list = dao.getTargetNum(manageUnit);
		System.out.println(JSONUtils.toString(list));
	}

	public void testQueryMeetClinicHisWithPage() {
		Integer doctorId = 40;
//		Integer doctorId = null;
//		String mpiId = "2c9081824cc3552a014cc3a9a0120002";
		String mpiId = "";
		int start = 0;
		int limit = 10;
		List<MeetClinicAndResult> mcars = appContext.getBean(
				"queryMeetClinicHisDAO", QueryMeetClinicHisDAO.class)
				.queryMeetClinicHisWithPage(doctorId, mpiId, start, limit);
		System.out.println(JSONUtils.toString(mcars));
		System.out.println(mcars.size());
	}

	public void testFindMeetClinicAndResultByStatic(){
		QueryMeetClinicHisDAO dao = appContext.getBean("queryMeetClinicHisDAO",
				QueryMeetClinicHisDAO.class);
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
		MeetClinic mc = new MeetClinic();
		mc.setMeetClinicType(1);
		MeetClinicResult mr = new MeetClinicResult();
		Date startDate = null;
		Date endDate = null;
		try {
			startDate = format.parse("2016-03-01");
			endDate= format.parse("2016-03-05");
		} catch (ParseException e) {
			e.printStackTrace();
		}
		QueryResult<MeetClinicAndResult> qr = dao.findMeetClinicAndResultByStatic(startDate,endDate,mc,mr,0,null,null,null);
		System.out.println(JSONUtils.toString(qr));

	}
}
