package test.dao;

import java.util.List;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.util.JSONUtils;
import eh.base.dao.DiseasDAO;
import eh.entity.base.Diseas;

public class DiseasDAOTester extends TestCase{
	private static ClassPathXmlApplicationContext appContext;
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	private static DiseasDAO dao = appContext.getBean("diseasDAO",DiseasDAO.class);
	
	public void testFindByDiseasNameLike() {
		/*String diseasName = "肺炎";
		List<Diseas> diseas = dao.findByDiseasNameLike(1,diseasName,0,10);
		System.out.println(JSONUtils.toString(diseas));
		System.out.println(diseas.size());*/
	}
}
