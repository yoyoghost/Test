package test.dao.base;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.util.JSONUtils;
import eh.base.dao.PcConfigDAO;
import eh.entity.base.PcConfig;

public class PcConfigDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	private static PcConfigDAO dao;
	static {
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		dao = appContext.getBean("pcConfigDAO", PcConfigDAO.class);
	}

	public void testFindByIpAndPort() {
		PcConfig pc = dao.getByOrganAndServiceUrl(1, "http://192.36.4.92:80/ehealth-base/");
		System.out.println(JSONUtils.toString(pc));
	}
}
