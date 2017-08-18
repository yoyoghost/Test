package test.dao;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.util.JSONUtils;

import eh.base.dao.ServerPriceDAO;
import eh.entity.base.ServerPrice;

public class ServerPriceDAOTester extends TestCase
{
	private static ClassPathXmlApplicationContext appContext;
	private static ServerPriceDAO dao;
	
	static
	{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		dao =appContext.getBean("serverPriceDAO", ServerPriceDAO.class);
	}
	
	/**
	 * 根据主键查询服务价格
	 * @author ZX
	 * @date 2015-4-26  下午5:07:18
	 */
	public void testGetByServerId(){
		int serverId=1;
		ServerPrice server = dao.getByServerId(serverId);
		
		System.out.println(JSONUtils.toString(server));
	}
	
}
