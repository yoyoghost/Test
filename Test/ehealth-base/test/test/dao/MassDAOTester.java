package test.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import eh.msg.dao.MassRootDAO;

public class MassDAOTester extends TestCase {

	private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	private static MassRootDAO dao = appContext.getBean("massRootDAO",MassRootDAO.class);
	
	public void testSendMassMsg(){
		int docId=1178;
		List<String> list=new ArrayList<String>();
		list.add("2c9081814cd4ca2d014cd4ddd6c90000");
		String msg="测试";
		
		dao.sendMassMsg(docId, msg, false, 0, "", list, null);
		
		try {
			TimeUnit.SECONDS.sleep(60*4);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		//dao.sendMassMsg(docId, list, msg);
	}
}
