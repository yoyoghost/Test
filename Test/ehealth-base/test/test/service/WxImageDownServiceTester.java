package test.service;
import java.util.Map;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.util.JSONUtils;

import eh.wxpay.service.WxImageDownService;
public class WxImageDownServiceTester extends TestCase{

	private static ClassPathXmlApplicationContext appContext;
	private static WxImageDownService service;
	
	static
	{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		service =appContext.getBean("wxImageDownService", WxImageDownService.class);
	}
	
	public void testGetPayReqMap(){
		String accessToken = "XOVPktTG05PWHI0ExbptPV7Cx9bnieXWn0h4MbHBB9SwrbbtylVc7nREBPvHsY9ZuesjFslTLqFWcV9_LvkIqSmMgLUjeXvOdjX_S3hag4MZJCfAIAVFY";
		String mediaId = "6OBc9hL_BJ43bq56cf9hBa-DWnISw4sbzENQnpAOcr3nG16nAOuz6bSp4ZKXickP";
		Map<String, Object> map = service.wxImageUpload(accessToken, mediaId);
		System.out.println("map="+JSONUtils.toString(map));
	}
}
