package test.dao;

import java.util.List;

import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import ctd.controller.exception.ControllerException;
import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import eh.base.dao.DoctorTempDAO;
import eh.entity.base.Doctortemp;

public class DoctorTempDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	
	public void testDoctorTempDAO() throws DAOException, ControllerException{
		int doctorId = 1;
		String bussType = "2";
		DoctorTempDAO dao = appContext.getBean("doctorTempDAO", DoctorTempDAO.class);
		List<Doctortemp> dp = dao.findAllDoctorTemp(doctorId, bussType);
		System.out.println(JSONUtils.toString(dp));
	}
	
	public void testSaveDoctorTemp() throws DAOException, ControllerException {
		int doctorId = 1;
		String bussType = "2";
		String tempText = "怀疑高烧";
		DoctorTempDAO dao = appContext.getBean("doctorTempDAO",DoctorTempDAO.class);
		dao.saveDoctorTemp(doctorId, bussType, tempText);
	}
	
	/**
	 * 修改医生模板服务测试
	 */
	public void testUpdateDoctorTemp(){
		int doctorTempId=82;
		String tempText = "放弃治疗吧！";
		DoctorTempDAO dao = appContext.getBean("doctorTempDAO",DoctorTempDAO.class);
		dao.updateDoctorTemp(tempText, doctorTempId);
	}
	

	/**
	 * 删除医生模板服务测试
	 */
	public void testDeleteDoctorTempByDoctorTempId(){
		int doctorTempId=70;
		DoctorTempDAO dao = appContext.getBean("doctorTempDAO",DoctorTempDAO.class);
		dao.deleteDoctorTempByDoctorTempId(doctorTempId);
	}
	
	
}
