package test.dao.mpi.service;

import ctd.persistence.exception.DAOException;
import eh.entity.mpi.FamilyMember;
import eh.entity.mpi.Patient;
import eh.mpi.dao.FamilyMemberDAO;
import eh.mpi.service.FamilyMemberService;
import eh.mpi.service.sign.AcceptSignService;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class FamilyMemberServiceTester extends TestCase {

	private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	private static FamilyMemberService service = appContext.getBean("familyMemberService",FamilyMemberService.class);
	
	public void testisFamily(){
		System.out.println(service.isFamily("2c9081814d48badc014d48cf97c80000","2c90818253a64f2c0153a6a50ddd0001"));
		System.out.println(service.isFamily("2c90818253a64f2c0153a6a50ddd0001","2c9081814d48ba14d48cf97c80000"));
	}

	public void testAddFamilyMemberAndPatient() throws DAOException {
		Patient patient = new Patient();
		patient.setPatientName("超级big大脸皮");
		patient.setRawIdcard("350121193907064362");
		patient.setIdcard("350121193907064362");
		patient.setMobile("13856567888");
		patient.setHomeArea("330104");
		service.addFamilyMemberAndPatient("2c9081895987d7a601598b7c1c490000",patient,"4");
	}

}
