package test.dao;

import ctd.mvc.weixin.WXUser;
import ctd.mvc.weixin.WXUser;
import ctd.util.JSONUtils;
import ctd.util.converter.support.StringToDate;
import eh.base.user.WXUserService;
import eh.entity.mpi.Patient;
import eh.mpi.dao.PatientDAO;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.util.JSONUtils;
import ctd.util.converter.support.StringToDate;
import eh.base.user.WXUserService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class WXUserServiceTester extends TestCase {

	private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("spring.xml");
	}
	private static WXUserService dao = appContext.getBean("wxUserService",WXUserService.class);
	private static PatientDAO patientDAO = appContext.getBean("patientDAO",PatientDAO.class);

	
	public void testCreateWXUserAndLogin(){
		HashMap<String,String> gender=new HashMap<String,String>();
		gender.put("key","1");
		WXUser wxUser=new WXUser();
		wxUser.setMobile("187681777687");
		wxUser.setGender(gender);
		wxUser.setBirthday(new StringToDate().convert("1948-02-07"));
		wxUser.setName("黯然2");
		wxUser.setHomeArea("331080");
		wxUser.setValidateCode("9620");
		String appId="wx870abf50c6bc6da3";
		String openId="oeSjtwweWFj2wJt4_g7or9kOiiYY";

		System.out.println(JSONUtils.toString(dao.createWXUserAndLogin2(wxUser,appId,openId)));
	}

	//测试微信端患者完善信息服务
	public void testPerfectUserInfoForRecipeConsult() throws Exception{
		Patient p = patientDAO.getByMpiId("40288937594da14a01594e0ec7500000");
		p.setIdcard("230421199502141813");
		System.out.println(JSONUtils.toString(dao.perfectUserInfoExt(p,null,2088)));
	}

	//测试用户是否完善信息
	public void testJudgePerfectUserInfo(){
		dao.judgePerfectUserInfo(2088,"40288937594da14a01594e0ec7500000");
	}
}
