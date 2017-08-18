package test.dao;

import ctd.controller.exception.ControllerException;
import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import ctd.util.converter.support.StringToDate;
import eh.bus.dao.MeetClinicDAO;
import eh.entity.bus.MeetClinic;
import eh.entity.bus.MeetClinicAndResult;
import eh.entity.bus.MeetClinicResult;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class MeetClinicDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;

	static {
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}

	MeetClinicDAO dao = appContext
			.getBean("meetClinicDAO", MeetClinicDAO.class);

	public void testGet() {
		dao.get(1895);
	}

	/*
	 * public void testGetMeetClinic() throws DAOException{ MeetClinicDAO dao =
	 * appContext.getBean("meetClinicDAO", MeetClinicDAO.class); int
	 * meetClinicId = 85; List<MeetClinicAndResult> list =
	 * dao.getMeetClinic(meetClinicId);
	 * System.out.println(JSONUtils.toString(list)); }
	 */

	public void testGetMeetClinicAndCdrOtherdoc() throws DAOException {
		int meetClinicId = 597;
		List<MeetClinicAndResult> list = dao
				.getMeetClinicAndCdrOtherdoc(meetClinicId);
		System.out.println(JSONUtils.toString(list));
	}

	public void testFindByMeetClinicId() throws DAOException {
		int meetClinicId = 1;
		List<MeetClinicResult> list = dao.findByMeetClinicId(meetClinicId);
		System.out.println(JSONUtils.toString(list));
	}

	public void testGetByMeetClinicId() throws DAOException {
		int meetClinicId = 93;
		MeetClinic mc = dao.getByMeetClinicId(meetClinicId);
		System.out.println(JSONUtils.toString(mc));
	}

	public void testGetNowMeetClinicNum() {
		Date requestTime = new StringToDate().convert("2015-04-20");
		Long num = dao.getNowMeetClinicNum(requestTime);
		System.out.println(num);
	}

	public void testGetAverageNum() {
		Date requestTime = new StringToDate().convert("2015-04-20");
		Double num = dao.getAverageNum(requestTime);
		System.out.println(num);
	}

	/**
	 * 会诊医生拒绝服务
	 * 
	 * @author LF
	 * @param meetClinicResultId
	 */
	public void testUpdateStatusToRefused() {
		Integer meetClinicResultId = 473;
		System.out.println(dao.updateStatusToRefused(meetClinicResultId,
				"测试会诊拒绝"));
	}

	/**
	 * 会诊医生拒绝理由列表服务
	 * 
	 * @author LF
	 * @return
	 */
	public void testFindRBymeetClinicId() {
		Integer meetClinicId = 454;
		System.out.println(JSONUtils.toString(dao
				.findRBymeetClinicId(meetClinicId)));
	}

	/**
	 * 我的会诊申请列表
	 * 
	 * @author luf
	 * @param doctorId
	 *            当前登陆医生内码
	 * @param flag
	 *            -0全部（未完成：待处理，会诊中；已结束：已完成，取消，拒绝），1未处理（待处理），2会诊中（会诊中），3已完成（已完成）
	 * @param mark
	 *            -0未完成，1已结束
	 * @param start
	 *            分页开始位置
	 * @param limit
	 *            每页限制条数
	 * @return List<HashMap<String, Object>>
	 */
	public void testFindMeetClinicRequest() {
		int doctorId = 40;
		int flag = 0;
		int mark = 0;
		int start = 0;
		int limit = 10;
		System.out.println(JSONUtils.toString(dao.findMeetClinicRequest(
				doctorId, flag, mark, start, limit)));
	}

	public void testEndByRequest() {
		int meetClinicId = 2221;
		String cancelCause = "测试endMeetClinicByRequest取消部分发短信";
		dao.endByRequest(meetClinicId, cancelCause);
	}

	// public void testGroup() {
	// ObjectNode node = JsonNodeFactory.instance.objectNode();
	// ObjectNode group = JsonNodeFactory.instance.objectNode();
	// // group.put("groupId", "219684831215646");
	// node.put("action", "post");
	// node.put("application", "fc9a5e20-08eb-11e5-984f-61b55773769e");
	// node.put("uri", "https://a1.easemob.com/easygroup/nagri");
	// node.put("data", group);
	// if(node==null) {
	// System.out.println("null");
	// }
	// if(node.get("data")==null) {
	// System.out.println("data null");
	// }
	// if(node.get("data").get("groupid")==null) {
	// System.out.println("groupid null");
	// }
	// }

	/**
	 * 会诊单详情
	 * 
	 * @author luf
	 * @param meetClinicId
	 *            会诊单号
	 * @param doctorId
	 *            当前登录医生内码
	 * @return HashMap<String, Object>
	 *         --meetClinic:会诊单信息,cdrOtherdocs:图片资料列表,patient：患者信息(全部),
	 *         meetClinicResultIds:执行单Id列表,news:最新答复信息,newsPhoto:最新答复头像,
	 *         inDoctors:参与医生内码(剔除拒绝),inNames:参与会诊医生姓名(剔除拒绝),
	 *         targetPhones:目标医生姓名及电话列表,status:详情单状态,statusText:状态名
	 * @throws ControllerException
	 */
	public void testGetDetailByMeetClinicId() {
		int meetClinicId = 2039;
		int doctorId = 40;
		HashMap<String, Object> map = new HashMap<String, Object>();
		try {
			map = dao.getDetailByMeetClinicId(meetClinicId, doctorId);
		} catch (ControllerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println(JSONUtils.toString(map));
	}

	public void testGetMeetClinicStatusById() {
		System.out.println(dao.getMeetClinicStatusById(955));
	}

	public void testRefuseMeetClinic() {
		int meetClinicId = 2065;
		String cause = "hjjjj";
		int meetClinicResultId = 2368;
		dao.refuseMeetClinic(meetClinicId, cause, meetClinicResultId);
	}

	public void testGroupEnable() {
		int meetClinicId = 953;
		System.out.println(dao.groupEnable(meetClinicId));
	}

	public void testGetMeetClinicDetail() {
		Integer meetClinicId = null;
		Integer meetClinicResultId = null;
		int doctorId = 40;
		try {
			System.out.println(JSONUtils.toString(dao.getMeetClinicDetail(meetClinicId,meetClinicResultId,doctorId)));
		} catch (ControllerException e) {
			e.printStackTrace();
		}
	}

	public void testGetMeetClinicAndCdrOtherdocPC(){
		Integer meetClinicId=17572;
		Integer preDoctorId=1425;
		Integer meetClinicResultId=20972;
		try {
			System.out.println(JSONUtils.toString(dao.getMeetClinicAndCdrOtherdocPC(meetClinicId, preDoctorId,
					meetClinicResultId)));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
