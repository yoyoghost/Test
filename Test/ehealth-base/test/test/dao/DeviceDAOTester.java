package test.dao;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import eh.base.dao.DeviceDAO;
import eh.entity.base.Device;

public class DeviceDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	private static DeviceDAO dao;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		dao =appContext.getBean("deviceDAO", DeviceDAO.class);
	}
	
	public void testAddDevice() throws DAOException{
		Device device = new Device();
		device.setUserId("18768177768");
		device.setToken("22222");
		device.setVersion("v1.0");
		device.setOs("Android");
		
		dao.addDevice(device);
	}
	
	public void testgetLastLoginAPP() throws DAOException{
		System.out.println(JSONUtils.toString(dao.getLastLoginAPP("18768177768","doctor")));
	}

	public void testgetDocAppByUserIdAndOs() throws DAOException{
		System.out.println(JSONUtils.toString(dao.getDocAppByUserIdAndOs("18768177768","IOS")));
	}
}
