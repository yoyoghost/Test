package test.dao;

import ctd.util.JSONUtils;
import eh.bus.dao.CloudClinicSetDAO;
import eh.entity.bus.CloudClinicSet;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CloudClinicSetDAOTester extends TestCase {

	private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	private static CloudClinicSetDAO dao = appContext.getBean("cloudClinicSetDAO",CloudClinicSetDAO.class);
	
	/**
	 * 更新在线状态
	 * @author zhangx
	 * @date 2015-12-29 下午5:01:58
	 */
	public void testUpdateOnlineStatus(){
//		dao.updateOnlineStatus(1,2151);
//		dao.updateFactStatusByDoctorIdAndPlatform(1,1182, CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_NGARI);
//		dao.updateOnLineStatusByDoctorIdAndPlatform(1,1182, CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_NGARI);
	}
	
	/**
	 * 更新接诊方、出诊方的视频状态
	 * @author zhangx
	 * @date 2015-12-29 下午5:08:39
	 */
	public void testUpdateFactStatus(){
		dao.updateFactStatus(1,1182,1178,0);

	}
	
	/**
	 * 获取一个医生的在线/视频状态
	 * @author zhangx
	 * @date 2015-12-29 下午5:12:24
	 */
	public void testGetDoctorSet(){
		int doctorId=1178;
		CloudClinicSet set=dao.getDoctorSet(doctorId);
		System.out.println(JSONUtils.toString(set));
	}
}
