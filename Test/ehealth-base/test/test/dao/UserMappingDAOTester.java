package test.dao;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.util.JSONUtils;

import eh.base.dao.UserMappingDAO;
import eh.entity.base.UserMapping;

public class UserMappingDAOTester extends TestCase {

	private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	private static UserMappingDAO dao = appContext.getBean("userMappingDAO",UserMappingDAO.class);

	/**
	 * 获取有效记录
	 * @author zhangx
	 * @date 2015-12-2 下午9:24:12
	 */
	public void testGetByEffectiveUserMapping(){
		UserMapping mapping=dao.getByEffectiveUserMapping("wechat", 1);
		System.out.println(JSONUtils.toString(mapping));
	}
}
