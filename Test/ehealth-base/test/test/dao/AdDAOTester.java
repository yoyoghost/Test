package test.dao;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.util.JSONUtils;
import eh.entity.msg.Ad;
import eh.msg.dao.AdDAO;

public class AdDAOTester extends TestCase {

	private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	private static AdDAO dao = appContext.getBean("adDAO",AdDAO.class);
	
	/**
	 * 保存ad记录
	 * @author LF
	 * @return
	 */
	public void testSaveAd() {
		Ad ad = new Ad();
		ad.setTitle("第二期天使计划");
		 System.out.println(dao.saveAd(ad));
	}

	
	/**
	 * 查询状态为1的所有ad
	 * @author LF
	 */
	public void testFindByStatus() {
		System.out.println(JSONUtils.toString(dao.findByStatus(1)));
	}
	
	/**
	 * 根据层级编码和机构内码查询有效广告列表
	 * @author LF
	 * @return
	 */
	public void testFindAdBymanageUnitAndOrganId() {
		String manageUnit = null;
//		Integer organId = 4;
		Integer organId = null;
		System.out.println(JSONUtils.toString(dao.findAdBymanageUnitAndOrganId(manageUnit, organId)));
	}
	
	/**
	 * 三条件查询有效广告列表
	 * @author LF
	 * @return
	 */
	public void testFindAdBymanageUnitAndOrganIdAndRoleId() {
		String manageUnit = null;
		Integer organId = null;
//		Integer organId = 4;
		Integer roleId = 1;
		System.out.println(JSONUtils.toString(dao.findAdBymanageUnitAndOrganIdAndRoleId(manageUnit, organId, roleId)));
	}
	
	public void testAddOrganAdOnlyOneBanner() {
		dao.addOrganAdOnlyOneBanner();
	}
	
	public void testAddOneAdForAllOrgan() {
		dao.addOneAdForAllOrgan();
	}
}
