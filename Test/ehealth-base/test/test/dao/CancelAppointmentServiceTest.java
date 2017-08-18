package test.dao;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import eh.remote.IHisServiceInterface;

public class CancelAppointmentServiceTest extends TestCase{
	private static ClassPathXmlApplicationContext appContext;
	private static IHisServiceInterface service;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
//		service  = appContext.getBean("ehis.cancelAppointmentService",IHisServiceInterface.class);
	}
	
	/**
	 * ԤԼȡ�����
	 * 
	 */
	public void testCancelAppoint(){
		String HisServiceId="h1.cancelAppointmentService";
		service  = appContext.getBean(HisServiceId,IHisServiceInterface.class);
		//boolean result=service.cancelAppoint("1", "ȡ��ԭ��", "36725");
		//System.out.println(result);
	}
}
