package test.dao;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.util.JSONUtils;

import eh.base.dao.DrugAliasDAO;

public class DrugAliasDAOTester extends TestCase {

	private static ClassPathXmlApplicationContext appContext;

	static {
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	private static DrugAliasDAO dao = appContext.getBean("drugAliasDAO",
			DrugAliasDAO.class);

	public void testGet() {
		System.out.println(JSONUtils.toString(dao.get(1)));
	}
}
