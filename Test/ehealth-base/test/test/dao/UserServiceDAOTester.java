package test.dao;

import java.util.ArrayList;
import java.util.List;

import ctd.controller.exception.ControllerException;
import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.util.JSONUtils;

import eh.base.user.UserSevice;
import eh.entity.base.DoctorOrPatientAndUrt;

public class UserServiceDAOTester extends TestCase {
	
	private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	
	public void testGetUrtId(){
		UserSevice dao =appContext.getBean("userSevice", UserSevice.class);
		
		List<Object> list = new ArrayList<Object>();
		list.add("1178");
		list.add("1182");
		list.add("40");
		String roleId="doctor";
		List<DoctorOrPatientAndUrt> hash = dao.getUrtId(list,roleId);
		System.out.println(JSONUtils.toString(hash));
		
		List<Object> list2 = new ArrayList<Object>();
		list2.add("2c9081824cc3552a014cc3a9a0120002");
		list2.add("2c9081814cd4ca2d014cd4ddd6c90000");
		list2.add("8a287a564d26f753014d281d6c530000");
		String roleId2="patient";
		List<DoctorOrPatientAndUrt> hash2 = dao.getUrtId(list2,roleId2);
		System.out.println(JSONUtils.toString(hash2));
	}

	
	public void testResertUid() throws ControllerException {
		String oldmobile="18768177769";
		String newMobile="18768177768";
		UserSevice dao =appContext.getBean("userSevice", UserSevice.class);
		dao.resertUid(oldmobile, newMobile);
	}
	
	public void testResertPassword(){
		UserSevice dao =appContext.getBean("userSevice", UserSevice.class);
		dao.resertPassword("187681777682", "888888");
	}

	public void testhasUser(){
		UserSevice dao =appContext.getBean("userSevice", UserSevice.class);
		System.out.println(dao.hasUser("18768177769"));
	}
}
