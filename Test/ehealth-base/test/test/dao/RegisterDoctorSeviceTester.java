package test.dao;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import eh.base.user.RegisterDoctorSevice;

public class RegisterDoctorSeviceTester extends TestCase {

	private static ClassPathXmlApplicationContext appContext;

	static {
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	private static RegisterDoctorSevice service = appContext.getBean("registerDoctorSevice", RegisterDoctorSevice.class);

	/**
	 * 将一个医院下的科室拷贝到另一个科室下
	 * 
	 * @author zhangx
	 * @date 2015-10-10下午7:27:23
	 */
	public void testSaveXNDept() {
		service.saveXNDept(1000050, 1000053);
	}

}
