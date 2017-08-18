package test.dao;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import eh.base.dao.UnitOpauthorizeDAO;
import eh.entity.base.UnitOpauthorize;

public class UnitOpauthorizeDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}

	/**
	 * 增加授权
	 * @author LF
	 * @return
	 */
	public void testAddUnitOpauthorize(){
		UnitOpauthorize unitOpauthorize = new UnitOpauthorize();
		unitOpauthorize.setOrganId(1000004);
		unitOpauthorize.setAccreditOrgan(1000004);
		unitOpauthorize.setAccreditBuess("1");
		UnitOpauthorizeDAO dao = appContext.getBean("unitOpauthorizeDAO",UnitOpauthorizeDAO.class);
		UnitOpauthorize uOpauthorize = dao.addUnitOpauthorize(unitOpauthorize);
		System.out.println(JSONUtils.toString(uOpauthorize));
	}
	
	/**
	 * 删除授权
	 * @author LF
	 */
	public void testDeleteUnitOpauthorizeByUauthorizeId() {
		UnitOpauthorizeDAO dao = appContext.getBean("unitOpauthorizeDAO",UnitOpauthorizeDAO.class);
		dao.deleteUnitOpauthorizeByUauthorizeId(12);
	}
	
	public void testFindByOrganId() {
		UnitOpauthorizeDAO dao = appContext.getBean("unitOpauthorizeDAO",UnitOpauthorizeDAO.class);
		System.out.println(JSONUtils.toString(dao.findByOrganId(1,0)));
	}
	public void testFindByAccreditOrgan() {
		UnitOpauthorizeDAO dao = appContext.getBean("unitOpauthorizeDAO",UnitOpauthorizeDAO.class);
		System.out.println(JSONUtils.toString(dao.findByAccreditOrgan(1,0)));
	}
	
	/**
	 * 查询机构授权记录（授权机构）
	 * @author LF
	 * @return
	 */
	public void testFindUnitOpauthorizeAndOrgans() {
		UnitOpauthorizeDAO dao = appContext.getBean("unitOpauthorizeDAO",UnitOpauthorizeDAO.class);
		System.out.println(JSONUtils.toString(dao.findUnitOpauthorizeAndOrgans(1,0)));
	}
	
	/**
	 * 查询机构被授权记录（被授权机构）
	 * @author LF
	 * @return
	 */
	public void testFindUnitOpauthorizeAndAccreditOrgans() {
		UnitOpauthorizeDAO dao = appContext.getBean("unitOpauthorizeDAO",UnitOpauthorizeDAO.class);
		System.out.println(JSONUtils.toString(dao.findUnitOpauthorizeAndAccreditOrgans(1,0)));
	}
	
	/**
	 * 更新授权机构
	 * 
	 * @author luf
	 * @param unitOpauthorize
	 *            授权机构信息
	 * @throws DAOException
	 */
	public void testUpdateUnitOpauthorizeWithoutThree() {
		UnitOpauthorize unitOpauthorize = new UnitOpauthorize();
		unitOpauthorize.setUauthorizeId(2);
		UnitOpauthorizeDAO dao = appContext.getBean("unitOpauthorizeDAO",UnitOpauthorizeDAO.class);
		dao.updateUnitOpauthorizeWithoutThree(unitOpauthorize);
	}
}
