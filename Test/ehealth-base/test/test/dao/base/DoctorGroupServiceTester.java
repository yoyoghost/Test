package test.dao.base;

import ctd.util.JSONUtils;
import eh.base.service.DoctorGroupService;
import eh.base.service.DoctorInfoService;
import eh.entity.base.Doctor;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.HashMap;
import java.util.List;

public class DoctorGroupServiceTester extends TestCase{

	private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}

	/**
	 * 分页获取团队成员信息
	 */
	public void testgetTeamMembersForHealthPages(){
		Integer docId=5;//团队
		List<Doctor> map = new DoctorGroupService().getTeamMembersForHealthPages(docId,0,2);
		System.out.println(JSONUtils.toString(map));
	}

	/**
	 * 获取全部团队成员信息
	 */
	public void testgetAllTeamMembersForHealth(){
		Integer docId=5;//团队
		List<Doctor> map = new DoctorGroupService().getAllTeamMembersForHealth(docId);
		System.out.println(JSONUtils.toString(map));
	}
}
