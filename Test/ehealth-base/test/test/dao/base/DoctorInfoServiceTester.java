package test.dao.base;

import ctd.util.JSONUtils;
import eh.base.service.DoctorInfoService;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import java.util.HashMap;
public class DoctorInfoServiceTester extends TestCase{

	private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("spring.xml");
	}

	/**
	 * 获取医生信息(个人/团队)
	 */
	public void testgetDoctorInfoForHealth(){
		//Integer docId=5;//团队
		Integer docId=7954;//个人
		String mpi="2c9081814cc5cb8a014cd483068d0001";
		HashMap<String, Object> map = new DoctorInfoService().getDoctorInfoForHealth(docId,mpi);
		System.out.println(JSONUtils.toString(map));
	}
}
