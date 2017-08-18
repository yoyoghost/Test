package test.service.base;

import com.alibaba.fastjson.JSON;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import eh.base.service.ScratchableService;
import eh.base.service.doctor.QueryDoctorListService;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class QueryDoctorListServiceTester extends TestCase{
	private static ClassPathXmlApplicationContext appContext;
	private static QueryDoctorListService service;
	static {
		appContext = new ClassPathXmlApplicationContext("spring.xml");
		service=appContext.getBean("queryDoctorListService", QueryDoctorListService.class);
	}


	public void testqueryDoctorListForAppointConsult(){
		final int doctorId=1182;
		final String addrArea="330104";
		System.out.println(JSONUtils.toString(service.queryDoctorListForAppointConsult(doctorId,addrArea)));
	}


	/**
	 * 测试专家解读列表功能中获取医生的列表
	 * @author cuill
	 * Date: 2017年2月15日
	 */
	public void testQueryDoctorListForProfessorConsult(){

		System.out.println(JSONUtils.toString(
				service.queryDoctorListForProfessorConsult("02",0,10)));
	}

	/**
	 * 测试专家解读列表科室的排序
	 * @author cuill
	 * Date: 2017年2月15日
	 */
	public void testFindDepartmentList(){
		System.out.println(JSONUtils.toString(service.findProfessionList()));
	}
}
