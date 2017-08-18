package test.service;

import java.util.Date;
import java.util.List;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.persistence.DAOFactory;

import eh.bus.dao.AppointSourceDAO;
import eh.bus.dao.TempTableDAO;
import eh.entity.bus.AppointSource;
import eh.entity.bus.DoctorDateSource;
import eh.entity.his.TempTable;

public class TempTableDAOTester extends TestCase{
	private static ClassPathXmlApplicationContext appContext;
	static TempTableDAO dao;
	static
	{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		dao=DAOFactory.getDAO(TempTableDAO.class);
	}
	public void testSave(){
		TempTable t=new TempTable();
		t.setAppointDepartId("11111111");
		t.setJobNumber("232342342342342");
		t.setOrderNum(2);
		t.setOrganSchedulingId("1111111111");
		t.setOrganSourceId("sdfsajflslfdjdf");
		t.setPredate(new Date());
		t.setOrganAppointId("test");
		t.setAppointDepartName("产科");
		dao.save(t);
		System.err.println(t.getId());
	}
	public void testUpdate(){
		List<TempTable> list=dao.getAll();
		AppointSourceDAO sdao=DAOFactory.getDAO(AppointSourceDAO.class);
		for(TempTable t:list){
			
			AppointSource old=sdao.getAppointSource(1, t.getOrganSchedulingId(), t.getOrganSourceId(),t.getWorkdate());
			if(old!=null){
				old.setUsedNum(1);
				List<DoctorDateSource> doctorAllSource=sdao.totalByDoctorDate(old.getDoctorId(), old.getSourceType());
				//sdao.update(old);
			}
			
		}
	}
	
}
