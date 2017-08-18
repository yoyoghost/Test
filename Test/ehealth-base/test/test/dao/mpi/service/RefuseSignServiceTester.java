package test.dao.mpi.service;

import ctd.persistence.DAOFactory;
import eh.entity.mpi.SignRecord;
import eh.mpi.dao.SignRecordDAO;
import eh.mpi.service.sign.AcceptSignService;
import eh.mpi.service.sign.RefuseSignService;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class RefuseSignServiceTester extends TestCase {

	private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	private static RefuseSignService service = appContext.getBean("refuseSignService",RefuseSignService.class);
	
	public void testacceptSignRecord(){
		System.out.println(service.refuseSignRecordWithNoCause(5));
	}

//	public void testpushWxMsgToPatWithAcceptSignRecord(){
//		SignRecordDAO signDao= DAOFactory.getDAO(SignRecordDAO.class);
//		SignRecord record=signDao.get(5);
//		service.pushWxMsgToPatWithRefuseSignRecord(record);
//	}


}
