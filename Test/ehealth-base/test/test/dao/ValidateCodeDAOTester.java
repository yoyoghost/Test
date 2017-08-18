package test.dao;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import eh.base.dao.ValidateCodeDAO;
import eh.entity.base.ValidateCode;

public class ValidateCodeDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;

	static {
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}

	public void testCreate() throws DAOException {
		ValidateCodeDAO dao = appContext.getBean("validateCodeDAO",
				ValidateCodeDAO.class);

		ValidateCode r = new ValidateCode();
		r.setMobile("15990151091");
		r.setRequestDt(new Date());

		r.setValidateCode("1515");
		r.setEmail("963249742@qq.com");
		System.out.println(JSONUtils.toString(r));
		ValidateCode rr = new ValidateCode();
		rr = dao.save(r);
		System.out.println(JSONUtils.toString(rr));
	}

	public void testGetByMobile() {
		ValidateCodeDAO dao = appContext.getBean("validateCodeDAO",
				ValidateCodeDAO.class);
		String mobile = "15990151091";
		ValidateCode r = dao.getByMobile(mobile);
		System.out.println(JSONUtils.toString(r));
	}

	public void testGetByMobileAndCode() {
		ValidateCodeDAO dao = appContext.getBean("validateCodeDAO",
				ValidateCodeDAO.class);
		String mobile = "15990151091";
		String code = "1515";
		ValidateCode r = dao.getByMobileAndCode(mobile, code);
		System.out.println(JSONUtils.toString(r));
	}

	public void testDeleteValidateCodeByValidateId() {
		int validateId = 3;
		ValidateCodeDAO dao = appContext.getBean("validateCodeDAO",
				ValidateCodeDAO.class);
		dao.deleteValidateCodeByValidateId(validateId);

	}

	public void testSendValidateCode() {
		String mobile = "15990092533";
		String roleId = "patient";
		System.out.println(mobile + "," + roleId);
		ValidateCodeDAO dao = appContext.getBean("validateCodeDAO",
				ValidateCodeDAO.class);
		String code = dao.sendValidateCode(mobile, roleId);
		System.out.println(code);

	}

	/**
	 * 患者端短信验证服务测试
	 */
	public void testSendValidateCodeToPatient() {
		String mobile = "18768177768";
		String roleId = "patient";
		ValidateCodeDAO dao = appContext.getBean("validateCodeDAO",
				ValidateCodeDAO.class);
		String code = dao.sendValidateCode(mobile, roleId);
		System.out.println(code);
		// 将线程睡眠2秒，否则短信发送不成功
		try {
			TimeUnit.SECONDS.sleep(2);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * 医生端短信验证服务测试
	 */
	public void testSendValidateCodeToDoctor() {
		String mobile = "18768177768";
		String roleId = "doctor";
		ValidateCodeDAO dao = appContext.getBean("validateCodeDAO",
				ValidateCodeDAO.class);
		String code = dao.sendValidateCode(mobile, roleId);
		System.out.println(code);
		// 将线程睡眠2秒，否则短信发送不成功
		try {
			TimeUnit.SECONDS.sleep(2);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	/**
	 * 验证码校验
	 * 
	 * @author luf
	 * @param phone
	 *            手机号
	 * @param identify
	 *            验证码
	 * @return Boolean
	 */
	public void testMachValidateCode() {
		String phone = "15990092533";
		String identify = "8587";
		ValidateCodeDAO dao = appContext.getBean("validateCodeDAO",
				ValidateCodeDAO.class);
		Boolean b = dao.machValidateCode(phone, identify);
		System.out.println(b);
	}
}
