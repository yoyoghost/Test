package test.service;

import ctd.util.JSONUtils;
import eh.wxpay.service.SubscribeService;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SubscribeServiceTester extends TestCase{

	private static ClassPathXmlApplicationContext appContext;
	private static SubscribeService service;
	
	static
	{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		service =appContext.getBean("subscribeService", SubscribeService.class);
	}

	public void testfindSubscribeDoctors(){
		String openId="o6k7dshVsoLZ02Zm3Nqupmb1Qf5Q";
		System.out.println(JSONUtils.toString(service.findSubscribeDoctors()));
	}

}
