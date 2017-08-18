package test.dao.mpi.service;

import ctd.util.JSONUtils;
import eh.mpi.service.sign.SignRecordService;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.HashMap;
import java.util.List;

public class SignRecordServiceTester extends TestCase {

	private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	private static SignRecordService service = appContext.getBean("signRecordService",SignRecordService.class);
	
	public void testgetSignRecordByDoctor(){
		List<HashMap<String,Object>> list= service.getSignRecordByDoctor(40,0);
		System.out.println(JSONUtils.toString(list));
	}

	/**
	 * 测试根据signRecordId获取患者标签
	 */
	public void testGetSignPatientLabel(){
		Integer signRecordId = 275;
		List<String> list =  service.getSignPatientLabel(signRecordId);
		if (list != null){
			System.out.println(JSONUtils.toString(list));
		} else {
			System.out.println("list为空！");
		}
	}

}
