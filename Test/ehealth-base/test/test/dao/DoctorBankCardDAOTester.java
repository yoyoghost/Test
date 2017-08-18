package test.dao;

import ctd.util.JSONUtils;
import eh.base.dao.DoctorBankCardDAO;
import eh.entity.msg.Ad;
import eh.msg.dao.AdDAO;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class DoctorBankCardDAOTester extends TestCase {

	private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	private static DoctorBankCardDAO dao = appContext.getBean("doctorBankCardDAO",DoctorBankCardDAO.class);

	public void testGet(){
		System.out.println(JSONUtils.toString(dao.get(null)));
	}

}
