package test.message;

import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.spring.AppDomainContext;
import eh.msg.service.SmsValidateSevice;

public class SmsValidateSeviceTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	static
	{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		
	}
  public void testSend(){
	  //SmsValidateSevice sms=new SmsValidateSevice();
	  SmsValidateSevice sms=AppDomainContext.getBean("smsValidateSevice", SmsValidateSevice.class);
	  sms.sendValidateSmsOld("13735891715");

  }
  
  /**
	 * 患者端短信验证服务测试
	 */
	public void testSendValidateCodeToPatient(){
		String mobile="13777575850";
		String roleId="patient";
		SmsValidateSevice dao = appContext.getBean("smsValidateSevice", SmsValidateSevice.class);
		String code=dao.sendValidateSms(mobile, roleId);
		System.out.println(code);
		//将线程睡眠2秒，否则短信发送不成功
				try {
					TimeUnit.SECONDS.sleep(2);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
	}
	/**
	 * 医生端短信验证服务测试
	 */
	public void testSendValidateCodeToDoctor(){
		String mobile="13777575850";
		String roleId="doctor";
		SmsValidateSevice dao = appContext.getBean("smsValidateSevice", SmsValidateSevice.class);
		String code=dao.sendValidateSms(mobile, roleId);
		System.out.println(code);
		//将线程睡眠2秒，否则短信发送不成功
		try {
			TimeUnit.SECONDS.sleep(2);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	public void testsendValidateCodeByRole(){
		SmsValidateSevice dao = appContext.getBean("smsValidateSevice", SmsValidateSevice.class);
		dao.sendValidateCodeByRole("18767167504", "123", "doctor");
	}
	
}
