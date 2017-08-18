package test.service;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import eh.wxpay.service.WxApiService;
import junit.framework.TestCase;

public class WxApiServiceTester extends TestCase{
	private static ClassPathXmlApplicationContext appContext;
	private static WxApiService service;
	static {
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		service = appContext.getBean("wxApiService", WxApiService.class);
	}
	public void testGetAccessToken(){
		System.out.println(service.getAccessToken());
		System.out.println(service.getJsapiTicket());
		System.out.println(service.getWxConfig("http://weixin.ngarihealth.com"));
	}
}
