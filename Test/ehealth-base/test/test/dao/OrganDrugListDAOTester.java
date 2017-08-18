package test.dao;

import eh.base.service.OrganDrugListService;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.util.JSONUtils;

import eh.base.dao.OrganDrugListDAO;

import junit.framework.TestCase;

public class OrganDrugListDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	static {
		appContext = new ClassPathXmlApplicationContext("/spring.xml");
	}
	private static OrganDrugListDAO dao = appContext.getBean(
			"organDrugListDAO", OrganDrugListDAO.class);

	private static OrganDrugListService service = appContext.getBean("organDrugListService", OrganDrugListService.class);


	public void testGet() {
		System.out.println(JSONUtils.toString(dao.get(1)));
	}

	public void testAddBatch(){
		service.addDrugListForBatch();
	}
}
