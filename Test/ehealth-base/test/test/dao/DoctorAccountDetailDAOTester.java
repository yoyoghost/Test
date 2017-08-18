package test.dao;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.util.JSONUtils;
import ctd.util.converter.support.StringToDate;
import eh.base.dao.DoctorAccountDetailDAO;
import eh.entity.base.DoctorAccountDetail;
import eh.entity.base.DoctorAccountDetailAndDoctorAccount;
import eh.entity.base.DoctorAndAccountAndDetail;

public class DoctorAccountDetailDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	private static DoctorAccountDetailDAO dao;

	static {
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");

		dao = appContext.getBean("doctorAccountDetailDAO",
				DoctorAccountDetailDAO.class);

	}

	/**
	 * 按月份统计收入服务
	 * 
	 * @author LF
	 */
	public void testFindInTotalByMonth() {
		Date startDate = new StringToDate().convert("2015-04-19");
		Date endDate = new StringToDate().convert("2015-04-24");
		List<DoctorAccountDetail> accountDetails = dao.findInTotalByMonth(40,
				startDate, endDate);
		System.out.println(JSONUtils.toString(accountDetails));
	}

	/**
	 * 按月份统计支出服务
	 * 
	 * @author LF
	 */
	public void testFindOutTotalByMonth() {
		Date startDate = new StringToDate().convert("2015-04-19");
		Date endDate = new StringToDate().convert("2015-04-24");
		List<DoctorAccountDetail> accountDetails = dao.findOutTotalByMonth(40,
				startDate, endDate);
		System.out.println(JSONUtils.toString(accountDetails));
	}

	/**
	 * 按月份统计收入支出服务
	 * 
	 * @author LF
	 */
	public void testFindTotalByMonth() {
		Date startDate = new StringToDate().convert("2015-01-01");
		Date endDate = new StringToDate().convert("2016-01-01");
		List<DoctorAccountDetail> accountDetails = dao.findTotalByMonth(40, 1,
				startDate, endDate);
		System.out.println(JSONUtils.toString(accountDetails));
	}

	/**
	 * 医生帐户收入明细查询服务
	 * 
	 * @author hyj
	 */
	public void testFindInDetailByDoctorIdAndCreateDate() {
		int doctorId = 1177;
		String start = "2015-04-20 13:25:28";
		String end = "2015-05-30 13:25:28";
		DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Date startDate = new Date();
		Date endDate = new Date();
		try {
			startDate = sdf.parse(start);
			endDate = sdf.parse(end);
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		List<DoctorAccountDetail> list = dao
				.findInDetailByDoctorIdAndCreateDate(doctorId, startDate,
						endDate);
		for (DoctorAccountDetail d : list) {
			System.out.println(JSONUtils.toString(d));
		}

	}

	/**
	 * 医生帐户支出明细查询服务
	 * 
	 * @author hyj
	 */
	public void testFindOutDetailByDoctorIdAndCreateDate() {
		int doctorId = 40;
		String start = "2015-04-20 13:25:28";
		String end = "2015-04-30 13:25:28";
		DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Date startDate = new Date();
		Date endDate = new Date();
		try {
			startDate = sdf.parse(start);
			endDate = sdf.parse(end);
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		List<DoctorAccountDetail> list = dao
				.findOutDetailByDoctorIdAndCreateDate(doctorId, startDate,
						endDate);
		for (DoctorAccountDetail d : list) {
			System.out.println(JSONUtils.toString(d));
		}

	}

	/**
	 * 按日份统计收入支出服务
	 * 
	 * @author LF
	 */
	public void testFindTotalByDay() {
		Date startDate = new StringToDate().convert("2015-01-01");
		Date endDate = new StringToDate().convert("2016-01-01");
		List<DoctorAccountDetail> accountDetails = dao.findTotalByDay(40, 1,
				startDate, endDate);
		System.out.println(JSONUtils.toString(accountDetails));
	}

	/**
	 * 按日份统计收入服务
	 * 
	 * @author LF
	 */
	public void testFindInTotalByDay() {
		Date startDate = new StringToDate().convert("2015-04-19");
		Date endDate = new StringToDate().convert("2015-04-24");
		List<DoctorAccountDetail> accountDetails = dao.findInTotalByDay(40,
				startDate, endDate);
		System.out.println(JSONUtils.toString(accountDetails));
	}

	/**
	 * 按日份统计收入服务
	 * 
	 * @author LF
	 */
	public void testFindOutTotalByDay() {
		Date startDate = new StringToDate().convert("2015-04-19");
		Date endDate = new StringToDate().convert("2015-04-24");
		List<DoctorAccountDetail> accountDetails = dao.findOutTotalByDay(40,
				startDate, endDate);
		System.out.println(JSONUtils.toString(accountDetails));
	}

	/**
	 * 服务类别统计收入支出方法
	 * 
	 * @author ZX
	 * @date 2015-4-26 下午2:14:14
	 * @param doctorId
	 *            医生id
	 * @param startDate
	 *            统计开始时间
	 * @param endDate
	 *            统计结束时间
	 * @return
	 */
	public void testFindTotalByType() {
		Date startDate = new StringToDate().convert("2015-04-19");
		Date endDate = new StringToDate().convert("2015-04-24");
		List<Object> list = dao.findTotalByType(40, 1, startDate, endDate);
		System.out.println(JSONUtils.toString(list));
	}

	/**
	 * 服务类别统计收入方法
	 * 
	 * @author ZX
	 * @date 2015-4-26 下午2:14:14
	 * @param doctorId
	 *            医生id
	 * @param startDate
	 *            统计开始时间
	 * @param endDate
	 *            统计结束时间
	 * @return
	 */
	public void testFindInTotalByType() {
		Date startDate = new StringToDate().convert("2015-04-19");
		Date endDate = new StringToDate().convert("2015-04-24");
		List<Object> list = dao.findInTotalByType(40, startDate, endDate);
		System.out.println(JSONUtils.toString(list));
	}

	/**
	 * 服务类别统计支出方法
	 * 
	 * @author ZX
	 * @date 2015-4-26 下午2:14:14
	 * @param doctorId
	 *            医生id
	 * @param startDate
	 *            统计开始时间
	 * @param endDate
	 *            统计结束时间
	 * @return
	 */
	public void testFindOutTotalByType() {
		Date startDate = new StringToDate().convert("2015-04-19");
		Date endDate = new StringToDate().convert("2015-04-24");
		List<Object> list = dao.findOutTotalByType(40, startDate, endDate);
		System.out.println(JSONUtils.toString(list));
	}

	/**
	 * 按日统计当月
	 * 
	 * @author ZX
	 */
	public void testfindTotalByMonthNow() {
		List<DoctorAccountDetail> list = dao.findTotalByMonthNow(40, 1);
		System.out.println(JSONUtils.toString(list));
	}

	/**
	 * 申请提现服务
	 * 
	 * @author hyj
	 */
	public void testRequestPay() {
		DoctorAccountDetail d = new DoctorAccountDetail();
		d.setDoctorId(1178);
		d.setMoney(new BigDecimal(10));
		d.setPayMode("1");
		dao.requestPay(d);

	}

	public void testP() {
		for (int i = 0; i < 2; i++) {
			testRequestPay();
		}
	}

	/**
	 * 查询申请提现信息
	 * 
	 * @author hyj
	 */
	public void testFindByPayStatus() {
		List<DoctorAccountDetailAndDoctorAccount> list = dao.findByPayStatus();
		for (DoctorAccountDetailAndDoctorAccount d : list) {
			System.out.println(JSONUtils.toString(d));
		}
	}

	/**
	 * 医生申请提现信息查询服务
	 * 
	 * @author hyj
	 */
	public void testFindByDoctorIdAndPayStatus() {
		int doctorId = 1178;
		int payStatus = 0;
		List<DoctorAccountDetail> list = dao.findByDoctorIdAndPayStatus(
				doctorId, payStatus);
		for (DoctorAccountDetail d : list) {
			System.out.println(JSONUtils.toString(d));
		}

	}

	/**
	 * 医生提现记录查询服务
	 * 
	 * @author hyj
	 */
	public void testFindByDoctorIdAndInout() {
		int doctorId = 1178;
		List<DoctorAccountDetail> list = dao.findByDoctorIdAndInout(doctorId);
		for (DoctorAccountDetail d : list) {
			System.out.println(JSONUtils.toString(d));
		}
	}

	/**
	 * 医生提现记录查询服务，按提现日期倒序
	 * 
	 * @author hyj
	 */
	public void testfindByDoctorIdAndInoutOrder() {
		int doctorId = 1178;
		List<DoctorAccountDetail> list = dao.findByDoctorIdAndInoutOrder(doctorId);
		for (DoctorAccountDetail d : list) {
			System.out.println(JSONUtils.toString(d));
		}
	}
	
	public void testAddMoney() {
		int doctorId = 1178;
		BigDecimal money = dao.addMoney(doctorId);
		System.out.println(money);
	}

	public void testFindRecordByPayStatus() {
		int paystatus = 0;
		List<DoctorAndAccountAndDetail> result = (List<DoctorAndAccountAndDetail>) dao
				.findRecordByPayStatusPage(paystatus, 1);
		for (DoctorAndAccountAndDetail s : result) {
			System.out.println(JSONUtils.toString(s));
		}

	}

	public void testGetNumForDoctor() {
		int doctorId = 1180;
		int bussType = 2;
		int serverId = 3;
		int inout = 1;
		long num = dao.getNumForDoctor(doctorId, bussType, serverId, inout);
		System.out.println(num);
	}
	
	public void testUpdateBillId(){
		dao.updateBillId("1");
	}
	
	
	public void testFindByDate(){
		Date stratDate = StringToDate.toDate("2015-04-01");
		Date endDate = StringToDate.toDate("2015-09-01");
		List<DoctorAccountDetail> list = dao.findByDate(stratDate, endDate,0);
		System.out.println(">>>>"+list.size());
	}
	
	public void testGetByAccountDetailId(){
		System.out.println(JSONUtils.toString(dao.getByAccountDetailId(188)));
	}
	
	public void testGetAppointNumForDoctor(){
		int doctorId = 1177;
		int bussType = 4;
		int serverId = 7;
		int inout = 1;
		int road=6;
		long num = dao.getAppointNumForDoctor(doctorId, bussType, serverId, inout,road);
		System.out.println(num);
	}
	
	public void testGetByServerIdAndBussId(){
		Long detail = dao.getByServerIdAndBussId(5, 192, 40, 1);
		System.out.println(JSONUtils.toString(detail));
	}

	/**
	 * 获取最近提现方式
	 * 
	 * eh.base.dao
	 * 
	 * @author luf 2016-2-15
	 * 
	 * @param doctorId
	 * @return String
	 */
	public void testGetLastModeByDoctor() {
		System.out.println(dao.getLastModeByDoctor(1177));
	}
}
