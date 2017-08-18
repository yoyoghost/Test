package test.service;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.util.JSONUtils;

import eh.base.service.DoctorService;
import eh.entity.base.Doctor;

import java.util.Map;

public class DoctorServiceTester extends TestCase{

	private static ClassPathXmlApplicationContext appContext;
	private static DoctorService service;
	
	static
	{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		service =appContext.getBean("doctorService", DoctorService.class);
	}
	public void testGetByUID(){
		Doctor dr=service.getByUID(1047);
		System.out.println(JSONUtils.toString(dr));
	}

	public void testdoctorsRecommendedForScroll(){
		try {
			Map<String, Object> map = service.doctorsRecommendedForScroll("330100", null, null);
			System.out.println(JSONUtils.toString(map));
		}catch (Exception e){
			System.out.println(e);
		}
	}
}
