package test.dao;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import eh.bus.dao.ConsultSetDAO;
import eh.entity.base.Doctor;
import eh.entity.bus.ConsultSet;
import eh.utils.DateConversion;

public class ConsultSetDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	
	public void testCreate() throws DAOException{
		ConsultSetDAO dao =appContext.getBean("consultSetDAO", ConsultSetDAO.class);
		
		
//		int nmr = ThreadLocalRandom.current().nextInt(10000);
		int n4 = ThreadLocalRandom.current().nextInt(1000,9999);
		
		ConsultSet r = new ConsultSet();
		r.setAppointDays(2+n4);
		r.setAppointStatus(1);
		dao.save(r);
	}
	
	/**
	 * 获取医生咨询设置服务
	 * @throws DAOException
	 */
	public void testGetById() throws DAOException{
		int doctorid=36;
		ConsultSetDAO dao =appContext.getBean("consultSetDAO", ConsultSetDAO.class);
		ConsultSet consultset=dao.getById(doctorid);
		System.out.println(JSONUtils.toString(consultset));
	}
	
	/**
	 * 医生咨询设置新增或修改服务
	 * @throws DAOException
	 */
	public void testAddConsultSet() throws DAOException{
		ConsultSet ConsultSet=new ConsultSet();
		ConsultSet.setDoctorId(31);
		ConsultSet.setOnLineStatus(1);
		ConsultSet.setAppointStatus(0);
		ConsultSetDAO dao =appContext.getBean("consultSetDAO", ConsultSetDAO.class);
		dao.addOrupdateConsultSet(ConsultSet);
		
	}
	
	public void test(){
		ConsultSetDAO dao =appContext.getBean("consultSetDAO", ConsultSetDAO.class);
		List<Doctor> list=dao.findDoctor();
		System.out.println(list.size());
		for(Doctor d:list){
			ConsultSet c=new ConsultSet();
			c.setDoctorId(d.getDoctorId());
			c.setTransferStatus(1);
			c.setMeetClinicStatus(1);
			c.setOnLineStatus(1);
			dao.addOrupdateConsultSet(c);
		}
	}
	
	/**
	 * 整理数据
	 * 
	 * @author zhangx
	 * @date 2015- 11- 2 下午10:03:44
	 */
	public void testCleanData() {
		ConsultSetDAO dao =appContext.getBean("consultSetDAO", ConsultSetDAO.class);
		dao.cleanData();
	}
	
	/**
	 * 供 getEffConsultTime 调用（consultdao）
	 * 
	 * @author luf
	 * @param consultDate
	 *            咨询时间
	 * @param doctorId
	 *            医生内码
	 * @return Object[] --0开始时间 1结束时间 2时间间隔
	 */
	public void testGetThreeByDoctorAndDate() {
		ConsultSetDAO dao =appContext.getBean("consultSetDAO", ConsultSetDAO.class);
		Date consultDate = DateConversion.getCurrentDate("2015-12-05 19:59:00", "yyyy-MM-dd HH:mm:ss");
		int doctorId = 40;
		Object[] os = dao.getThreeByDoctorAndDate(consultDate, doctorId);
		System.out.println(JSONUtils.toString(os));
	}

	public void testAddOrupdateConsultSet(){
		ConsultSetDAO dao =appContext.getBean("consultSetDAO", ConsultSetDAO.class);
		ConsultSet consultSet= new ConsultSet();
		consultSet.setDoctorId(494);
		dao.addOrupdateConsultSet(consultSet);
	}
	
}
