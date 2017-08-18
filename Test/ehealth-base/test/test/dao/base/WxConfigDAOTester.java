package test.dao.base;

import java.util.Date;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.util.JSONUtils;

import eh.base.dao.WxConfigDAO;
import eh.entity.base.WxConfig;
import junit.framework.TestCase;

public class WxConfigDAOTester extends TestCase{
	private static ClassPathXmlApplicationContext appContext;
	private static WxConfigDAO dao;
	static {
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		dao = appContext.getBean("wxConfigDAO", WxConfigDAO.class);
	}

	public void testSave(){
		WxConfig wc=new WxConfig();
		wc.setAccessToken("o3oVQxYWo8qXwULtAE0XdQ6Uz4mGLrUOGNRdKF6ckf57nxBe80qD3umURt1qUcRVoKkHTgKB03_0TP_2ZvvPFQQZ-sBE7ckBWpX8dk0eaOYIXUfADAODA");
		wc.setAppid("11111111111");
		wc.setJsapiTicket("sssssssssssssssssssss");
		wc.setTicketExpireIn(7000);
		wc.setTicketModifyDate(new Date());
		wc.setTicketExpireTime((int) ((System.currentTimeMillis() / 1000)+7000));
		wc.setTokenExpireIn(7000);
		wc.setTokenExpireTime((int) ((System.currentTimeMillis() / 1000)+7000));
		wc.setTokenModifyDate(new Date());
		dao.save(wc);
	}
	public void testGetByAppid() {
		
		System.out.println(JSONUtils.toString(dao.getByAppid("11111111111")));
	}
}
