package test.dao;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.persistence.exception.DAOException;
import eh.cdr.dao.ClinicEmrDAO;
import eh.entity.cdr.ClinicEmr;

import junit.framework.TestCase;

public class ClinicEmrDAOTester extends TestCase{
private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	public void testCreate() throws DAOException{
		ClinicEmrDAO dao = appContext.getBean("clinicEmrDAO",ClinicEmrDAO.class);
		ClinicEmr clinicEmr = new ClinicEmr();
		clinicEmr.setClinicId(111);
		clinicEmr.setMpiId("111123");
		clinicEmr.setChiefComplaint("这是一个主诉");
		clinicEmr.setPresentHistory("这是一个现病史");
		clinicEmr.setPastHistory("这是一个既往史");
		clinicEmr.setPhysicalExamination("体格检查");
		clinicEmr.setInitiativeDiagnose("这是一个初步诊断");
		clinicEmr.setTreatOpinion("这是一个处理意见");
		dao.save(clinicEmr);
	}
}
