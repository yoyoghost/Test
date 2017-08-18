package test.dao;

import ctd.util.JSONUtils;
import eh.entity.wx.WxSubscribe;
import eh.wxpay.dao.WxSubscribeDAO;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.List;

public class WxSubscribeDAOTester extends TestCase {

	private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	private static WxSubscribeDAO dao = appContext.getBean("subscribeDAO",WxSubscribeDAO.class);
	
	public void testSaveOrNotTest(){
		WxSubscribe wxSubscribe = new WxSubscribe();
		wxSubscribe.setOpenId("ogG6Ut5YCpoelUy_ddYtQWKdcSAc");
		wxSubscribe.setDoctorId(1292);
		wxSubscribe.setUserId("18268034805");
		wxSubscribe.setAppId("wx6a80dd109228fd4b");
		System.out.println(dao.saveOrNot(wxSubscribe));
	}
	public void testGetSubscribeByOpenIdAndDoctor(){
		WxSubscribe sub = dao.getSubscribe("ogG6UtxsiwvLjsFORyNLtA3U_dKw",1182,null);
		System.out.println(JSONUtils.toString(sub));
	}

	public void testUpdateFlag(){
		dao.updateFlag("ogG6UtxsiwvLjsFORyNLtA3U_dKw",5,"");
	}

	public void testFindListByOpenId(){
		List<WxSubscribe> list = dao.findListByOpenIdAndUserId("ogG6UtxsiwvLjsFORyNLtA3U_dKw");
		System.out.println(JSONUtils.toString(list));
	}

	public void testSubscribeDoctor(){
		String result = dao.subscribeDoctorBase("wx6a80dd109228fd4b","ogG6Ut5YCpoelUy_ddYtQWKdcSAc","18268034805");
		System.out.println(result);
	}
	public void testRelationDoctor(){
		dao.relationDoctor(1292,"wx6a80dd109228fd4b","ogG6Ut5YCpoelUy_ddYtQWKdcSAc");
	}

	public void testfindSubscribesByOpenId(){
		String openId="o6k7dshVsoLZ02Zm3Nqupmb1Qf5Q";
		System.out.println(JSONUtils.toString(dao.findSubscribesByOpenId(openId,0,4)));
	}

	public void testfindByOpenId(){
		System.out.println(JSONUtils.toString(dao.findByOpenId("",0,1)));
	}

}
