package test.dao.base.service;

import ctd.util.JSONUtils;
import eh.base.service.BankService;
import eh.base.service.PatientFeedbackService;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class BankServiceTester extends TestCase{
	private static ClassPathXmlApplicationContext appContext;
	private static BankService service;
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		service = appContext.getBean("bankService", BankService.class);
	}

	public void testfindEffBanks(){
		System.out.println(JSONUtils.toString(service.findEffBanks()));
	}
}
