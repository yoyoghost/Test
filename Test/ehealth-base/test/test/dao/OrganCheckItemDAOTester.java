package test.dao;

import java.util.List;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.util.JSONUtils;

import eh.base.dao.OrganCheckItemDAO;
import eh.entity.base.OrganCheckItem;
import junit.framework.TestCase;

public class OrganCheckItemDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	static {
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	private OrganCheckItemDAO dao = appContext.getBean("organCheckItemDAO",
			OrganCheckItemDAO.class);
	
	public void testFindByCheckItemId() {
		int checkItemId = 2;
		List<OrganCheckItem> items = dao.findByCheckItemId(checkItemId);
		System.out.println(JSONUtils.toString(items));
	}
	
	public void testCheckClass() {
		try {
			String checkClass = DictionaryController.instance()
			        .get( "eh.base.dictionary.CheckClass")
			        .getText("001");
			System.out.println(checkClass);
		} catch (ControllerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
