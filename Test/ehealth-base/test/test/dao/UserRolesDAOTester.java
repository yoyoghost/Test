package test.dao;

import java.util.List;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.util.JSONUtils;

import eh.base.dao.UserRolesDAO;
import eh.entity.base.UserRoles;

public class UserRolesDAOTester extends TestCase {

	private static ClassPathXmlApplicationContext appContext;
	private static UserRolesDAO dao;

	static {
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		dao = appContext.getBean("userRolesDAO", UserRolesDAO.class);
	}

	/**
	 * 根据机构层级编码获取用户角色（供 OrganDAO updateOrgan使用）
	 * 
	 * @author luf
	 */
	public void testFindByManageUnit() {
		String manageUnit = "eh330001";
		List<UserRoles> list = dao.findByManageUnit(manageUnit);
		System.out.println(JSONUtils.toString(list));
		System.out.println(list.size());
	}
}
