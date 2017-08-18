package test.dao;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.persistence.exception.DAOException;
import eh.cdr.dao.DiagnosisDAO;
import eh.entity.cdr.Diagnosis;

import junit.framework.TestCase;

public class DiagnosisDAOTester extends TestCase  {
private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	public void testCreate() throws DAOException{
		DiagnosisDAO dao = appContext.getBean("diagnosisDAO", DiagnosisDAO.class);
		Diagnosis diagnosis = new Diagnosis();
		diagnosis.setClinicID(111);
		diagnosis.setMpiid("222333");
		diagnosis.setDiagnosisType(1);
		diagnosis.setDiseaseID("K02.900");
		dao.save(diagnosis);
	}
}
