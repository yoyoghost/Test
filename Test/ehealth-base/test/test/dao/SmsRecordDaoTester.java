package test.dao;

import java.util.Date;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.persistence.DAOFactory;
import eh.base.constant.SmsConstant;
import eh.bus.dao.SmsRecordDAO;
import eh.entity.bus.SmsRecord;
import eh.entity.msg.SmsContent;
import eh.util.SendTemplateSMS;

public class SmsRecordDaoTester extends TestCase {
private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	public void testSaveCallRecord(){
		SmsRecordDAO dao =DAOFactory.getDAO(SmsRecordDAO.class);
		SmsRecord sr=new SmsRecord();
		sr.setMobile("13735891715");
		sr.setContent("test");
		sr.setResult("sss");
		sr.setCreateTime(new Date());
		sr.setAppid("8a48b5514df54891014df58c1353004f");
		dao.save(sr);
	}
	public void testSendSms(){
		SmsContent sc=new SmsContent();
		sc.setMobile("13735891715");
		sc.setParameter(new String[]{"22221","5"});
		sc.setTemplateId(SmsConstant.SECURITY_CODE);
		sc.setType(SmsContent.PATIENT);
		SendTemplateSMS.sendMesToPatient(sc);
	}
}
