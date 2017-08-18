package test.dao;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.account.UserRoleToken;
import ctd.account.user.User;
import ctd.account.user.UserController;
import ctd.persistence.DAOFactory;
import ctd.persistence.support.impl.user.UserDAO;
import ctd.persistence.support.impl.user.UserRoleTokenDAO;
import ctd.util.JSONUtils;

public class URDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	private static UserDAO dao;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		dao =appContext.getBean("userDAO", UserDAO.class);
	}
	
	
	
	public void testGet() throws Exception{
		User user = UserController.instance().get("13858043673");
		System.out.println(JSONUtils.toString(user));
	}
	public void testGetUserRole(){
		UserRoleTokenDAO tokenDao=DAOFactory.getDAO(UserRoleTokenDAO.class);
		UserRoleToken urt=tokenDao.get(335);
		System.out.println(JSONUtils.toString(urt));
	}

}
