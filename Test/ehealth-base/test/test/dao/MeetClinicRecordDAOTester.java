package test.dao;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import eh.bus.dao.MeetClinicRecordDAO;
import eh.entity.bus.MeetClinicAndResult;

public class MeetClinicRecordDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;

	static {
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}

	@SuppressWarnings("rawtypes")
	public void testGetMeetClinicRecord() {
		int doctorId = 40;
		List list = null;
		MeetClinicRecordDAO dao = appContext.getBean("meetClinicRecordDAO",
				MeetClinicRecordDAO.class);
		list = dao.getMeetClinicRecord(doctorId);
		// if(list!=null && list.size()>0){
		System.out.println(JSONUtils.toString(list));
		// }
	}

	/**
	 * 查询会诊申请单列表服务(分页)
	 * 
	 * @author LF
	 * @throws DAOException
	 */
	public void testFindMeetClinicRecordStart() {
		int doctorId = 40;
		MeetClinicRecordDAO dao = appContext.getBean("meetClinicRecordDAO",
				MeetClinicRecordDAO.class);
		List<MeetClinicAndResult> os = dao.findMeetClinicRecordStart(doctorId, 10);
		System.out.println(JSONUtils.toString(os));
		System.out.println(os.size());
	}

	/**
	 * 导出会诊业务数据
	 * 
	 * @author LF
	 */
	public void testExportExcelMeet() {
		MeetClinicRecordDAO dao = appContext.getBean("meetClinicRecordDAO",
				MeetClinicRecordDAO.class);
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		Date startTime = null;
		Date endTime = null;
		try {
			startTime = sdf.parse("2015-08-01");
			endTime = sdf.parse("2015-08-24");
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println(JSONUtils.toString(dao.exportExcelMeet(startTime,
				endTime)));
	}

	public void testFindMeetClinicRecordStartAndLimit() {
		MeetClinicRecordDAO dao = appContext.getBean("meetClinicRecordDAO",
				MeetClinicRecordDAO.class);
		System.out.println(JSONUtils.toString(dao.findMeetClinicRecordStartAndLimit(40,0,10)));
	}
}
