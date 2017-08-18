package test.dao;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.util.JSONUtils;
import eh.msg.dao.SmsGroupDAO;

public class SmsGroupDAOTest  extends TestCase{
	private static ClassPathXmlApplicationContext appContext;
	private static SmsGroupDAO dao;
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		dao= appContext.getBean("smsGroupDAO",SmsGroupDAO.class);
	}
	
	
	public void testFindAll() {
		System.out.println(JSONUtils.toString(dao.findAllSmsGroups()));
	}
	
	public void testSendSmsToGroups(){
		dao.sendSmsToGroups();
	}

}
