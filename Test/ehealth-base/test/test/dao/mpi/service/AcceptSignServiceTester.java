package test.dao.mpi.service;

import ctd.controller.exception.ControllerException;
import ctd.persistence.DAOFactory;
import ctd.util.JSONUtils;
import eh.entity.mpi.SignRecord;
import eh.mpi.dao.SignRecordDAO;
import eh.mpi.service.sign.AcceptSignService;
import eh.mpi.service.sign.SignRecordService;
import junit.framework.TestCase;
import org.mvel2.ast.Sign;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.HashMap;
import java.util.List;

public class AcceptSignServiceTester extends TestCase {

	private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	private static AcceptSignService service = appContext.getBean("acceptSignService",AcceptSignService.class);
	
	public void testacceptSignRecord(){
		System.out.println(service.acceptSignRecord(4,40));
	}

//	public void testpushWxMsgToPatWithAcceptSignRecord(){
//		SignRecordDAO signDao= DAOFactory.getDAO(SignRecordDAO.class);
//		SignRecord record=signDao.get(3);
//		service.pushWxMsgToPatWithAcceptSignRecord(record);
//	}

	/**
	 * 测试医生同意签约
	 */
	public void testAcceptSignRecord(){
		Integer signRecordId = 272;
		Integer doctorId = 1182;
		System.out.println(service.acceptSignRecord(signRecordId, doctorId));
	}
	/**
	 * 测试将居民类型作为医生给患者打的标签
	 */
	public void testSavePatientLabel() throws ControllerException {
		SignRecordDAO signRecordDAO = DAOFactory.getDAO(SignRecordDAO.class);
		SignRecord signRecord = signRecordDAO.get(272);
		System.out.println(service.savePatientLabel(signRecord));
	}

}
