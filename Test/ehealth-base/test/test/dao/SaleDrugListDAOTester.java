package test.dao;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.util.JSONUtils;

import eh.base.dao.SaleDrugListDAO;

import junit.framework.TestCase;

public class SaleDrugListDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	static {
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	private static SaleDrugListDAO dao = appContext.getBean("saleDrugListDAO",
			SaleDrugListDAO.class);

	public void testGet() {
		System.out.println(JSONUtils.toString(dao.get(1)));
	}
}
