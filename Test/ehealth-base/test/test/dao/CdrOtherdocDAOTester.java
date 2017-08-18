package test.dao;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.controller.exception.ControllerException;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import eh.cdr.dao.CdrOtherdocDAO;
import eh.entity.cdr.Otherdoc;

public class CdrOtherdocDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	 
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	private CdrOtherdocDAO dao = appContext.getBean("cdrOtherdocDAO",CdrOtherdocDAO.class);
	
	public void testSaveCdrOtherdoc() throws DAOException, ControllerException{
		Otherdoc cdrOtherdoc = new Otherdoc();
		cdrOtherdoc.setClinicId(131);
		cdrOtherdoc.setClinicType(2);
		cdrOtherdoc.setMpiid("402881834b6d0cfc014b6d0d04f10000");
		cdrOtherdoc.setDocType("9");
		cdrOtherdoc.setDocName("王谨.jpg");
		cdrOtherdoc.setDocFormat("13");
		cdrOtherdoc.setDocContent(53);
		
		DAOFactory.getDAO(CdrOtherdocDAO.class).save(cdrOtherdoc);
	}
	
	/**
	 * 根据图片ID获取图片类型
	 * @author LF
	 * @return
	 */
	public void testFindByDocContent() {
		Integer docContent = 362;
		System.out.println(JSONUtils.toString(dao.findByDocContent(docContent)));
	}
}
