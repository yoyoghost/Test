package test.service;

import com.alibaba.druid.support.json.JSONUtils;
import eh.bus.service.payment.QueryOutpatient;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.HashMap;
import java.util.Map;

public class QueryOutpatientTester extends TestCase{

	private static ClassPathXmlApplicationContext appContext;
	private static QueryOutpatient service;
	
	static
	{
		appContext = new ClassPathXmlApplicationContext("spring.xml");
		service =appContext.getBean("queryOutpatient", QueryOutpatient.class);
	}
	public void testGetPrePayForLiveHospital1(){
		Map<String,String> map = new HashMap<String,String>();
		map.put("MpiID","2c90818959c974990159c9e155fa0000");
		map.put("OrganID","1");

		System.out.println("********************kaka**************");
		System.out.println(ctd.util.JSONUtils.toString(service.getPrePayForLiveHospital(map)));
	}
	public void testGetPayList(){
		Map<String,String> map = new HashMap<String,String>();
		map.put("MpiID","40288162565a0b3901565a0b84960000");
		map.put("OrganID","1");
		map.put("Flag","0");
		map.put("StartNo","1");
		map.put("Records","10");
		System.out.println("*****************kaka*****************");
		System.out.println(ctd.util.JSONUtils.toString(service.getPayList(map)));
	}

	public void testGetPayDetail(){
		Map<String,String> map = new HashMap<String,String>();
		map.put("MpiID","40288162565a0b3901565a0b84960000");
		map.put("OrganID","1");
		map.put("OrderID","234455");
		map.put("Flag","0");
		map.put("StartNo","1");
		map.put("Records","10");
		System.out.println("********************kaka**************");
		System.out.println(ctd.util.JSONUtils.toString(service.getPayDetail(map)));
	}

	public void testGetPrePayForLiveHospital(){
		Map<String,String> map = new HashMap<String,String>();
		map.put("MpiID","40288162565a0b3901565a0b84960000");
		map.put("OrganID","1");

		System.out.println("********************kaka**************");
		System.out.println(ctd.util.JSONUtils.toString(service.getPrePayForLiveHospital(map)));
	}

	public void testGetAlreadyGenerateTypeList(){
		Map<String,String> map = new HashMap<String,String>();
		map.put("MpiID","40288162565a0b3901565a0b84960000");
		map.put("OrganID","1");
		map.put("interid","23421");

		System.out.println("********************kaka**************");
		System.out.println(service.getAlreadyGenerateTypeList(map));
	}

	public void testGetAlreadyGenerateTypeDetail(){
		Map<String,String> map = new HashMap<String,String>();
		map.put("MpiID","40288162565a0b3901565a0b84960000");
		map.put("OrganID","1");
		map.put("interid","23421");
		map.put("itemtypecode","33");

		System.out.println("********************kaka**************");
		System.out.println(service.getAlreadyGenerateTypeDetail(map));
	}

	public void testGetAlreadyGenerateDayList(){
		Map<String,String> map = new HashMap<String,String>();
		map.put("MpiID","40288162565a0b3901565a0b84960000");
		map.put("OrganID","1");
		map.put("interid","23421");
		map.put("indate","2016-09-13 12:20");

		System.out.println("********************kaka**************");
		System.out.println(service.getAlreadyGenerateDayList(map));
	}

	public void testGetAlreadyPrepayList(){
		Map<String,String> map = new HashMap<String,String>();
		map.put("MpiID","40288162565a0b3901565a0b84960000");
		map.put("OrganID","1");

		System.out.println("********************kaka**************");
		System.out.println(ctd.util.JSONUtils.toString(service.getAlreadyPrepayList(map)));
	}
}
