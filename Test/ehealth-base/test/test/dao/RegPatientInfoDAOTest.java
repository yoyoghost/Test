package test.dao;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import eh.bus.dao.OperationRecordsDAO;
import eh.bus.dao.RegPatientInfoDAO;
import eh.entity.bus.RegPatientInfo;

public class RegPatientInfoDAOTest extends TestCase{
	private static ClassPathXmlApplicationContext appContext;
	private static RegPatientInfoDAO dao;
	
	static {
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		dao = appContext.getBean("regPatientInfoDAO",
				RegPatientInfoDAO.class);
	}
	public void testRegPatientInfoDAO(){
		RegPatientInfo reg = new RegPatientInfo();
		reg.setCardNo("123456789");
		reg.setDoctorId(120);
		reg.setDoctorName("老赵");
		reg.setIdCard("456654789987");
		reg.setJobNumber("10001");
		reg.setMobile("13898746512");
		reg.setOrganId(122);
		reg.setPatientName("小明");
		reg.setPatientType("医保");
		
		dao.queryPatientInfo(reg);
	}
}
