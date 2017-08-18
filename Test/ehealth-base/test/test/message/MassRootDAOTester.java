package test.message;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import eh.msg.dao.MassRootDAO;

public class MassRootDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	static MassRootDAO dao=null;
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		dao=appContext.getBean("massRootDAO", MassRootDAO.class);
	}
	
	public void testSendMassMsg(){
		ArrayList<String> unCheckList = new ArrayList<>();
		unCheckList.add("8a287a564d286405014d289ac6c00000");
		unCheckList.add("2c9081814cd4ca2d014cd4ddd6c90000");
		unCheckList.add("2c9081814cc3ad35014cc3e0361f0000");
		dao.sendMassMsg(40, "群发测试，打扰了，谢谢！", true, 0, "", null, unCheckList);
		
		try {
			TimeUnit.SECONDS.sleep(60*4);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
