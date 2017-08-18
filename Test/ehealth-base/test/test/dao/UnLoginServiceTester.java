package test.dao;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.dictionary.DictionaryItem;
import ctd.util.JSONUtils;
import ctd.util.converter.support.StringToDate;
import eh.bus.service.UnLoginSevice;
import eh.entity.base.Doctor;
import eh.entity.base.Organ;

public class UnLoginServiceTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	private static UnLoginSevice service ;
	static {
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		service= appContext.getBean("unLoginSevice",UnLoginSevice.class);
	}

	public void testGetStaticNum() {

		String dateString = "2015-04-17";
		Date requestTime = new StringToDate().convert(dateString);
		System.out.println("时间=" + dateString);

		UnLoginSevice unLogin = appContext.getBean("unLoginSevice",
				UnLoginSevice.class);
		HashMap<String, Long> list = unLogin.getStaticNum(requestTime,
				requestTime);
		System.out.println(JSONUtils.toString(list));
	}

	/**
	 * 医院专科类别查询服务--未登录时调用
	 * 
	 * @author hyj
	 */
	public void testFindValidProfessionInUnLogin() {
		Integer organId = 1;
		int deptType = 1;
		List<Object> result = new ArrayList<Object>();
		UnLoginSevice unLogin = appContext.getBean("unLoginSevice",
				UnLoginSevice.class);
		result = unLogin.findValidProfessionInUnLogin(organId, deptType);
		System.out.println(result.size());
		for (Object o : result) {
			System.out.println(JSONUtils.toString(o));
		}
	}

	/**
	 * 供前端调取医院列表--未登陆时调用
	 * 
	 * @author hyj
	 */
	public void testFindByAddrAreaLikeInUnLogin() {
		try {
			UnLoginSevice unLogin = appContext.getBean("unLoginSevice",
					UnLoginSevice.class);
			System.out.println(JSONUtils.toString(unLogin.doctorsRecommendedNew("330100", 0, "")));
		}catch (Exception e){

		}
	}

	/**
	 * 搜索医生优化（专科、区域、擅长疾病、姓名、是否在线、是否有号）服务测试
	 */
	public void testFindDoctorByCondition() {
		String profession = "04";
		String addrArea = "";
		String domain = "";
		String name = "";
		Integer onLineStatus = null;
		Integer haveAppoint = null;


		List<Doctor> list = service.searchDoctorInUnLogin(profession, addrArea,
				domain, name, onLineStatus, haveAppoint, 0);
		System.out.println(list.size());
		for (Doctor d : list) {
			System.out.println(JSONUtils.toString(d));
		}

	}

	/**
	 * 获取专科代码字典服务--未登陆时调用
	 * 
	 * @author hyj
	 */
	public void testGetProfession() {
		UnLoginSevice unLogin = appContext.getBean("unLoginSevice",
				UnLoginSevice.class);
		String parentKey = "";
		List<DictionaryItem> list = unLogin.getAddrArea(parentKey,0);
		for (DictionaryItem d : list) {
			System.out.println(JSONUtils.toString(d));
		}

	}

	public void testHasOtherUser() {
		String userId = "18768177768";
		String role = "doctor";
		UnLoginSevice unLogin = appContext.getBean("unLoginSevice",
				UnLoginSevice.class);
		boolean b = unLogin.hasOtherUser(userId, role);
		System.out.println(b);
	}

	public void testGetDoctorInfoUnloginForHealth() {
		int doctorId = 1180;
		UnLoginSevice unLogin = appContext.getBean("unLoginSevice",
				UnLoginSevice.class);
		Doctor doc = unLogin.getDoctorInfoUnloginForHealth(doctorId);
		System.out.println(JSONUtils.toString(doc));
	}

	public void testgetDoctorInfoForHealth(){
		Integer docId=1183;
		String mpi="";
		String openId="o6k7dshVsoLZ02Zm3Nqupmb1Qf5Q";
		System.out.println(JSONUtils.toString(service.getDoctorInfoForHealth(docId,mpi)));
	}
}
