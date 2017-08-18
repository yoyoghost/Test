package test.service;

import java.util.Map;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.util.JSONUtils;
import eh.wxpay.service.WxRefundService;

public class WxRefundServiceTester extends TestCase{

	private static ClassPathXmlApplicationContext appContext;
	private static WxRefundService service;
	
	static
	{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		service =appContext.getBean("wxRefundService", WxRefundService.class);
	}
	@SuppressWarnings("rawtypes")
	public void testGetPayReqMap(){
		Map map = service.refund(1154, "transfer");
		System.out.println(JSONUtils.toString(map));
	}
	@SuppressWarnings("rawtypes")
	public void testPayQuery(){
		Map map = service.refundQuery(1154, "transfer");
		System.out.println(JSONUtils.toString(map));
	}
}
