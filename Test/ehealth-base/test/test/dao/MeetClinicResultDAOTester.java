package test.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.controller.exception.ControllerException;
import ctd.util.JSONUtils;

import eh.bus.dao.MeetClinicResultDAO;
import eh.entity.bus.MeetClinicReportTable;
import eh.entity.bus.MeetClinicResult;

public class MeetClinicResultDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	static {
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	MeetClinicResultDAO dao = appContext.getBean("meetClinicResultDAO",
			MeetClinicResultDAO.class);

	/**
	 * 我的会诊列表
	 * 
	 * @author luf
	 * @param doctorId
	 *            当前登陆医生内码
	 * @param flag
	 *            -0全部（未完成：待处理，处理中；已结束：已会诊，取消，拒绝），1未处理（待处理、处理中），2已完成（已会诊）
	 * @param mark
	 *            -0未完成，1已结束
	 * @param start
	 *            分页开始位置
	 * @param limit
	 *            每页限制条数
	 * @return HashMap<String, List<HashMap<String, Object>>>
	 */
	public void testFindMeetClinicResult() {
		int doctorId = 4616;
		int flag = 0;
		int mark = 0;
		int start = 0;
		int limit = 10;
		HashMap<String, List<HashMap<String, Object>>> map = dao
				.findMeetClinicResult(doctorId, flag, mark, start, limit);
		System.out.println(JSONUtils.toString(map));
		System.out.println(map.get("unfinished").size());
		System.out.println(map.get("completed").size());
	}

	/**
	 * 会诊意见列表
	 * 
	 * @author luf
	 * @param mrIds
	 *            会诊执行单序号列表
	 * @param doctorId
	 *            当前登录医生内码
	 * @param flag
	 *            标志--0全部1结束2拒绝
	 * @return List<Object>
	 *         --HashMap=>meetClinicResult:会诊执行单信息,doctor:医生信息,enable:是否可点赞
	 */
	public void testFindReportByList() {
		List<Integer> is = new ArrayList<Integer>();
		is.add(2347);
		is.add(2346);
		// is.add(1388);
		// is.add(1389);
		// is.add(1390);
		// is.add(1391);
		// is.add(1392);
		// is.add(1393);
		List<Object> os = dao.findReportByList(is, 40, 1);
		System.out.println(JSONUtils.toString(os));
	}
	
	/**
	 * 会诊开始服务
	 * 
	 * @author luf
	 * @param meetClinicResult
	 *            会诊执行单信息
	 * @return Boolean
	 */
	public void testStartMeetClinic() {
		MeetClinicResult meetClinicResult = new MeetClinicResult();
		meetClinicResult.setMeetClinicResultId(1398);
		meetClinicResult.setMeetClinicId(955);
		meetClinicResult.setExeDoctor(1919);
		meetClinicResult.setExeOrgan(1);
		meetClinicResult.setExeDepart(70);
		System.out.println(dao.startMeetClinic(meetClinicResult));
	}
	
	public void testGetMeetClinicIdByResultId() {
		int meetClinicResultId = 918;
		System.out.println(dao.getMeetClinicIdByResultId(meetClinicResultId));
	}
	
	public void testFindProgressList() {
		int meetClinicId = 953;
		List<MeetClinicReportTable> mrts;
		try {
			mrts = dao.findProgressList(meetClinicId);
			System.out.println(JSONUtils.toString(mrts));
			System.out.println(mrts.size());
		} catch (ControllerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
