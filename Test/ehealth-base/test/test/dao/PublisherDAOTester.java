package test.dao;

import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import eh.msg.dao.PublisherDAO;

public class PublisherDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	
	public void testCreateSysTalk(){
		Integer publisherId = 1;
		Integer memberType = 1;
		Integer memberId = 1;
		PublisherDAO dao =appContext.getBean("publisherDAO", PublisherDAO.class);
		Integer sessionId = dao.createSysTalk(publisherId, memberType, memberId);
		System.out.println(sessionId);
	}
}
