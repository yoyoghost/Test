package test.dao;

import java.util.List;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.controller.exception.ControllerException;
import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import eh.base.dao.PatientFeedbackDAO;
import eh.entity.base.PatientFeedback;

public class PatientFeedbackDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;

	static {
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}


	/**
	 * 医生点评查询方法
	 */
	public void testFindByDoctorIdAndServiceType() {
		int doctorId = 1178;
		String serviceType = "1";
		PatientFeedbackDAO dao = appContext.getBean("patientFeedbackDAO",
				PatientFeedbackDAO.class);
		List<PatientFeedback> list = dao.findByDoctorIdAndServiceType(doctorId,
				serviceType);
		for (PatientFeedback p : list) {
			System.out.println(JSONUtils.toString(p));
		}

	}

	/**
	 * 查询医生点评数量
	 * 
	 * @author hyj
	 */
	public void testGetPatientFeedbackNum() {
		int doctorId = 1178;
		PatientFeedbackDAO dao = appContext.getBean("patientFeedbackDAO",
				PatientFeedbackDAO.class);
		Long count = dao.getPatientFeedbackNum(doctorId);
		System.out.println(count);
	}

/*	*//**
	 * 查询医生点评总分
	 *
	 * @author hyj
	 *//*
	public void testAddEvaValue() {
		int doctorId = 1178;
		PatientFeedbackDAO dao = appContext.getBean("patientFeedbackDAO",
				PatientFeedbackDAO.class);
		Object[] value = dao.addEvaValue(doctorId);

		System.out.println(value[0]);
		System.out.println(JSONUtils.toString(value));
	}

	*//**
	 * 查询医生点评平均分
	 *
	 * @author hyj
	 *//*
	public void testgetAverage() {
		int doctorId = 1182;
		PatientFeedbackDAO dao = appContext.getBean("patientFeedbackDAO",
				PatientFeedbackDAO.class);
		Object[] value = dao.addEvaValue(doctorId);
		Long count = dao.getPatientFeedbackNum(doctorId);
		System.out.println("总分" + (double) value[0] / count);
		System.out.println("服务总分" + (double) value[1] / count);
		System.out.println("技术总分" + (double) value[2] / count);
	}*/

	/**
	 * 医生点赞服务
	 * 
	 * @author hyj
	 * @throws DAOException
	 */
	public void testAddFeedBackByGood() throws DAOException {
		PatientFeedbackDAO dao = appContext.getBean("patientFeedbackDAO",
				PatientFeedbackDAO.class);

		PatientFeedback p = new PatientFeedback();
		p.setMpiid("8a287a564efd9e45014f05fd905f0001");
		p.setDoctorId(3933);
		p.setServiceId("824");
		p.setServiceType("3");
		p.setEvaText("服务态度很好");
		p.setUserId(1198);
		p.setUserType("doctor");

		dao.addFeedBackByGood(p);
	}

	/**
	 * 判断是否已经点过赞
	 * 
	 * @author ZX
	 * @date 2015-6-18 下午9:16:28
	 */
	public void testIsPraise() {
		String serviceType = "3";
		String serviceId = "417";
		Integer userId = 1197;
		String userType = "patient";
		PatientFeedbackDAO dao = appContext.getBean("patientFeedbackDAO",
				PatientFeedbackDAO.class);
		boolean is = dao.isPraise(serviceType, serviceId, userId, userType);
		System.out.println(is);
	}

	public void testGetByServiceAndUser() {
		String serviceType = "2";
		String serviceId = "529";
		Integer userId = 1559;
		String userType = "doctor";
		PatientFeedbackDAO dao = appContext.getBean("patientFeedbackDAO",
				PatientFeedbackDAO.class);
		List<PatientFeedback> list = dao.findPfbsByServiceAndUser(serviceType,
				serviceId, userId, userType);
		System.out.println(JSONUtils.toString(list));
	}

	public void testFindFeedInfoAndPhotoByDoctorId() {
		PatientFeedbackDAO dao = appContext.getBean("patientFeedbackDAO",
				PatientFeedbackDAO.class);
		try {
			System.out.println(JSONUtils.toString(dao
					.findFeedInfoAndPhotoByDoctorId(1292, "patient", 0)));
		} catch (ControllerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
