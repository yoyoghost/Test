/**
 * 
 */
package test.dao;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.google.common.eventbus.Subscribe;

import ctd.persistence.event.support.AbstractDAOEventLisenter;
import ctd.persistence.event.support.CreateDAOEvent;
import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import eh.base.dao.DictProfessionDAO;
import eh.entity.base.DictProfession;

import junit.framework.TestCase;

/**
 * @author Eric
 * 
 */
public class DictProfessionDAOTester extends TestCase {

	private static ClassPathXmlApplicationContext appContext;

	static {
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}

	public void testCreate() throws DAOException {
		DictProfessionDAO dao = appContext.getBean("dictProfessionDAO",
				DictProfessionDAO.class);

		dao.addEventListener(new AbstractDAOEventLisenter() {
			@Override
			@Subscribe
			public void onCreate(CreateDAOEvent e) {
				System.out.println(e.getTarget() + "," + e.getTargetId());
			}
		});

		int nmr = ThreadLocalRandom.current().nextInt(10000, 99999);

		DictProfession dictPro = new DictProfession();
		dictPro.setProfessiontId("15" + nmr);
		dictPro.setProfessiontName("内科");
		dictPro.setPreProfession(null);
		dao.save(dictPro);
		System.out.println("save done");
	}

	public void test() {
		int nmr = ThreadLocalRandom.current().nextInt(10000, 99999);
		System.out.println(nmr);
	}

	public List<DictProfession> testFindAllDictProfession() {
		DictProfessionDAO dao = appContext.getBean("dictProfessionDAO",
				DictProfessionDAO.class);
		List<DictProfession> pros = dao.findAllDictProfession();
		System.out.println(JSONUtils.toString(pros));
		return pros;
	}
	
	public List<DictProfession> testFindChildrenByParentId(){
		DictProfessionDAO dao = appContext.getBean("dictProfessionDAO",
				DictProfessionDAO.class);
		List<DictProfession> pros = dao.findChildrenByParentId("150");
		System.out.println(JSONUtils.toString(pros));
		return pros;
	}

	public DictProfession testGetByProfessiontId() throws DAOException {
		DictProfessionDAO dao = appContext.getBean("dictProfessionDAO",
				DictProfessionDAO.class);
		DictProfession pro = dao.getByProfessiontId("150");
		System.out.println(JSONUtils.toString(pro));
		return pro;
	}
}
