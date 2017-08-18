package test.dao;

import eh.entity.msg.ComLan;
import eh.msg.dao.ComLanDAO;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.Date;

public class ComLanDAOTester extends TestCase {

	private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	private static ComLanDAO dao = appContext.getBean("comLanDAO",ComLanDAO.class);

	public void testGet() {
		ComLan cm = new ComLan();
		cm.setDoctorId(40);
		cm.setBussType(1);
		cm.setCreateTime(new Date());
		cm.setLastModifyTime(new Date());
		cm.setWords("啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊" +
				"啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊" +
				"啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊" +
				"啊啊啊啊啊啊啊啊啊啊");
		dao.save(cm);
		System.out.println(dao.get(1));
	}
}
