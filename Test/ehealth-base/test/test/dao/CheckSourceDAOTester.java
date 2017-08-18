package test.dao;

import java.util.Date;
import java.util.List;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.util.JSONUtils;

import eh.bus.dao.CheckSourceDAO;
import eh.entity.bus.CheckSource;
import eh.utils.DateConversion;

import junit.framework.TestCase;

public class CheckSourceDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	static {
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	private CheckSourceDAO dao = appContext.getBean("checkSourceDAO",
			CheckSourceDAO.class);

	public void testFindSourcesByCheckItemId() {
		int checkItemId = 2;
		String addrArea = "33010";
		List<Object> os = dao.findSourcesByCheckItemId(checkItemId, addrArea);
		System.out.println(JSONUtils.toString(os));
		System.out.println(os.size());
	}

	public void testFindByThree() {
		Date workDate = DateConversion.getCurrentDate("2016-01-07",
				"yyyy-MM-dd");
		Integer workType = 2;
		Integer checkAppointId = 1;
		List<CheckSource> list = dao.findByThree(workType, workDate,
				checkAppointId);
		System.out.println(JSONUtils.toString(list));
		System.out.println(list.size());
	}

	/**
	 * 添加号源锁
	 * 
	 *            检查号源序号
	 *            当前医生内码
	 * @return Boolean
	 */
	public void testLockCheckSource() {
		int chkSourceId = 1;
		int doctor = 40;
		System.out.println(dao.lockCheckSource(chkSourceId, doctor));
	}

	/**
	 * 解锁号源
	 * 
	 *            检查号源序号
	 *            当前医生内码
	 * @return Boolean
	 */
	public void testUnlockCheckSource() {
		int chkSourceId = 1;
		int doctor = 1182;
		System.out.println(dao.unlockCheckSource(chkSourceId, doctor));
	}

	/**
	 * 定时解锁超时号源
	 * 
	 * @return
	 * @throws Exception
	 */
	public void testUnlockOverTime() {
		try {
			System.out.println(dao.unlockOverTime());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * 号源服务--原生
	 * 
	 *            检查项目序号
	 * @return List<Object>
	 */
//	public void testFindSourcesByCheckItemId2() {
//		int checkItemId = 2;
//		String addrArea = "33010";
//		List<Object> os = dao.findSourcesByCheckItemId2(checkItemId, addrArea,1);
//		System.out.println(JSONUtils.toString(os));
//		System.out.println(os.size());
//	}
}
