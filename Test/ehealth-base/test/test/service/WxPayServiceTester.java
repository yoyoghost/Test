package test.service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import ctd.persistence.DAOFactory;
import eh.bus.dao.TransferDAO;
import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.util.JSONUtils;
import eh.wxpay.service.WxPayService;

public class WxPayServiceTester extends TestCase{

	private static ClassPathXmlApplicationContext appContext;
	private static WxPayService service;
	
	static
	{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		service =appContext.getBean("wxPayService", WxPayService.class);
	}
	@SuppressWarnings("rawtypes")
	public void testGetPayReqMap(){
		Map map = service.payApply("40", "transfer", "1148");
		System.out.println(JSONUtils.toString(map));
	}
	@SuppressWarnings("rawtypes")
	public void testPayQuery(){
		Map map = service.payQuery("1154", "transfer");
		System.out.println(JSONUtils.toString(map));
	}

}
