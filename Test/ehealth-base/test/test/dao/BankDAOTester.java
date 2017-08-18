package test.dao;

import ctd.util.JSONUtils;
import eh.base.dao.BankDAO;
import eh.entity.msg.Ad;
import eh.msg.dao.AdDAO;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class BankDAOTester extends TestCase {

	private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	private static BankDAO dao = appContext.getBean("bankDAO",BankDAO.class);
	
	public void testfindEffBankNames(){
		System.out.println(JSONUtils.toString(dao.findEffBanks()));
	}
}
