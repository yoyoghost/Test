package test.dao;

import java.util.Date;
import java.util.List;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import eh.bus.dao.AppointInhospDAO;
import eh.entity.bus.AppointInhosp;
import eh.entity.his.AppointInHosResponse;

public class AppointInhospDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	static AppointInhospDAO dao=null;
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		dao=appContext.getBean("appointInhospDAO", AppointInhospDAO.class);
	}
	
	public void testCreate() throws DAOException{
		AppointInhospDAO dao = appContext.getBean("appointInhospDAO", AppointInhospDAO.class);
		
		AppointInhosp r = new AppointInhosp();
		r.setMpiid("4028811f4bce1c7f014bce1cc5fa0000");
		r.setOrganId(1);
		r.setAppointRoad(1);
		r.setIdcard("330184199305152620");
		dao.save(r);
	}
	
	public void testSaveAppointInhosp() throws DAOException {
		AppointInhospDAO dao = appContext.getBean("appointInhospDAO", AppointInhospDAO.class);
		
		AppointInhosp r = new AppointInhosp();
		r.setMpiid("4028811f4bce1c7f014bce1cc5fa0000");
		r.setOrganId(1);
		r.setAppointRoad(1);
		r.setIdcard("330184199305152620");
		
		dao.saveAppointInhosp(r);
	}
	
	public void testGetById() throws DAOException {
		int id=7;
		AppointInhospDAO dao = appContext.getBean("appointInhospDAO", AppointInhospDAO.class);
		AppointInhosp a=dao.getById(id);
		System.out.println(JSONUtils.toString(a));
	}
	public void testUpdateAppointId(){
		AppointInHosResponse res=new AppointInHosResponse();
		res.setOrganAppointInhospID("0000");
		res.setId("12");
		dao.updateOrganAppointInHosId(res);
	}
	public void testCancelForHisFail(){
		AppointInHosResponse res=new AppointInHosResponse();
		res.setOrganAppointInhospID("0000");
		res.setId("12");
		res.setErrMsg("转诊失败");
		dao.cancelInHospForHisFail(res);
	}
	/*public void testfindByOrganIDAndStatus(){
		//List<AppointInhosp> appointInhosps = (List<AppointInhosp>) new AppointInhosp();
		List<AppointInhosp> appointInhosps = dao.findByOrganIDAndStatus(1, 0);
		System.out.println(JSONUtils.toString(appointInhosps));
	}*/
}
