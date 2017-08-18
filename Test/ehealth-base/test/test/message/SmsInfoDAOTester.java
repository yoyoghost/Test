package test.message;

import java.util.Date;

import junit.framework.TestCase;
import eh.entity.msg.SmsInfo;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import eh.msg.dao.SmsInfoDAO;

public class SmsInfoDAOTester extends TestCase{

private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		dao = appContext.getBean("smsInfoDAO",SmsInfoDAO.class);
	}
	private static SmsInfoDAO dao;
	public void testSave(){
		SmsInfo info = new SmsInfo();
		info.setCreateTime(new Date());
		info.setBusId(2);
		info.setBusType("appointrecord");
		info.setSmsType("appointfail");
		info.setStatus(0);
		dao.save(info);
	}
}
