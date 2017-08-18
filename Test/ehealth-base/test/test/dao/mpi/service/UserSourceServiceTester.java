package test.dao.mpi.service;

import ctd.persistence.DAOFactory;
import eh.entity.mpi.SignRecord;
import eh.mpi.dao.SignRecordDAO;
import eh.mpi.service.sign.AcceptSignService;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class UserSourceServiceTester extends TestCase {

	private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	private static AcceptSignService service = appContext.getBean("acceptSignService",AcceptSignService.class);
	


}
