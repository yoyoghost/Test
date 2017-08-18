package test.dao;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.util.JSONUtils;
import eh.base.dao.DoctorAccountDAO;
import eh.base.service.DoctorAccountService;
import eh.entity.base.DoctorAccount;

public class DoctorAccountDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	private static DoctorAccountDAO dao;

	static {
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		dao = appContext.getBean("doctorAccountDAO", DoctorAccountDAO.class);
	}

	/**
	 * 根据主键查询账户信息
	 * 
	 * @author ZX
	 * @date 2015-4-26 下午6:05:41
	 */
	public void testGetByDoctorId() {
		Integer doctorId = 304;
		DoctorAccount doctorAccount = dao.getByDoctorId(doctorId);
		System.out.println(JSONUtils.toString(doctorAccount));
	}

	/**
	 * 根据主键更新收入
	 * 
	 * @author ZX
	 * @date 2015-4-26 下午6:05:31
	 */
	public void testUpdateInComeByDoctorId() {
		BigDecimal inCome = new BigDecimal(20d);
		Integer doctorId = 40;
		dao.updateInComeByDoctorId(inCome, doctorId);
	}

	/**
	 * 新增医生账户
	 * 
	 * @author ZX
	 * @date 2015-4-26 下午6:06:45
	 */
	public void testSaveNewDoctorAccount() {
		Integer doctorId = 1182;
		dao.saveNewDoctorAccount(doctorId);
	}

	/**
	 * 增加账户收入
	 * 
	 * @author ZX
	 * @date 2015-4-26 下午7:21:51
	 */
	public void testAddDoctorIncome() {
		Integer doctorId = 1178;
		Integer serverId = 7;
		int bussId = 262;
		int addFlag = 0;
		// doctoraccountdao.addDoctorIncome(Integer.parseInt(a.getAppointUser()),
		// 7, a.getAppointRecordId(), 0);
		dao.addDoctorIncome(doctorId, serverId, bussId, addFlag);
	}
	
	/**
	 * 增加患者付费的，医生账户收入
	 * 
	 * @author zhangx
	 * @date 2016-1-18 下午3:05:28
	 */
	public void testAddDoctorRevenue() {
		dao.addDoctorRevenue(1182, 22, 906, 200d);
	}
	
	/**
	 * 推荐奖励
	 * @author zhangx
	 * @date 2016-2-16 上午11:29:51
	 * @throws InterruptedException
	 */
	public void testRecommendReward() throws InterruptedException{
		dao.recommendReward(11822);
	}

	/**
	 * 新增账户设置服务
	 */
	public void testAddOrUpdateDoctorAccount() {
		DoctorAccount d = new DoctorAccount();
		d.setDoctorId(1193);
		d.setPayMobile("15157151057");
		d.setAlipayId("15157151057");
		d.setCardNo("66298704221749837389");
		d.setCardName("周宇");
		d.setBankCode("工商银行");
		d.setSubBank("江南大道网点");
		dao.addOrUpdateDoctorAccount(d);
	}

	public void testUpdatePayOut() {
		BigDecimal payOut = new BigDecimal(10d);
		int doctorId = 1178;
		dao.updatePayOut(payOut, doctorId, payOut, new BigDecimal(10d));
	}

	public void testCheckMoney() {
		List<DoctorAccount> list = dao.checkMoney();
		for (DoctorAccount d : list) {
			System.out.println(JSONUtils.toString(d));
		}
	}

	public void testCreateCashBills() {
		dao.createCashBills();
	}

	public void testisFirstMeetClinicDoctor() {
//		Boolean b = new DoctorAccountService().isFirstMeetClinicDoctor(1292, 1904);
//		System.out.println(b);
	}

	public void testIsSamePerson() {
		// Boolean b =dao.isSamePerson(850);
//		Boolean b1 = new DoctorAccountService().isSamePersonForTransfer(850);
		// System.out.println("isSamePerson="+b);
//		System.out.println("isSamePersonForTransfer=" + b1);
	}

}
