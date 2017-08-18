package test.dao.mpi.service;

import ctd.persistence.DAOFactory;
import ctd.util.context.Context;
import eh.entity.mpi.SignRecord;
import eh.mpi.dao.SignRecordDAO;
import eh.mpi.service.sign.RequestSignRecordService;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.Date;

public class RequestSignRecordServiceTester extends TestCase {

	private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	private static RequestSignRecordService service = appContext.getBean("requestSignRecordService",RequestSignRecordService.class);
	
	public void testrequestSign(){
		SignRecord record=new SignRecord();
		record.setDoctor(40);
		record.setRequestMpiId("4028811454613685015461376f940000");
		record.setSignTime("1");
		record.setOrgan(1);
		record.setDepart(3024);
		Date tomorrow = Context.instance().get("date.getTomorrow", Date.class);
		record.setStartDate(tomorrow);

		Date nextYear = Context.instance().get("date.getDateOfNextYear", Date.class);
		record.setEndDate(nextYear);
//		service.requestSign(record);
	}

	public void testCheckOfHis(){
		SignRecordDAO signRecordDAO = DAOFactory.getDAO(SignRecordDAO.class);
		SignRecord record = signRecordDAO.get(275);
		String cardType = "2";
		String cardId = "234";
		service.checkOfHis(record, cardType, cardId);
	}

}
