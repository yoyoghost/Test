package test.dao;

import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;

import eh.cdr.dao.ClinicListDAO;
import eh.entity.cdr.ClinicList;

import junit.framework.TestCase;

public class ClinicListDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	
	public void testCreate() throws DAOException{
		ClinicListDAO dao =appContext.getBean("clinicListDAO", ClinicListDAO.class);
		
		
		int nmr = ThreadLocalRandom.current().nextInt(10000);
		int n4 = ThreadLocalRandom.current().nextInt(1000,9999);
		
		ClinicList r = new ClinicList();
		r.setAppointmentId("1"+n4);
		Date date=new Date();
		DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");  
		String dateStr = sdf.format(date);  
		Timestamp ts = new Timestamp(System.currentTimeMillis());  
		ts = Timestamp.valueOf(dateStr); 
		r.setAppointmentDate(ts);
		r.setClinicDate(ts);
		r.setClinicDepart(1);
		r.setClinicDoctor(13);
		r.setClinicType(1);
		r.setClinicStatus(43+n4);
		r.setClinicOrgan(1);
		r.setFirstVisitSign(23+n4);
		r.setMpiid("sss"+nmr);
		r.setOrganClinicId("1"+n4);
		dao.save(r);
	}
	
	/**
	 *  就诊记录查询服务之情况一测试(按主索引和就诊时间查询,并根据就诊时间逆向排序)
	 * @throws DAOException
	 */
	public void testFindByMpiidAndClinicDate() throws DAOException{
		String mpiid="sss6801";
		String strDate="2015-02-09 10:34:26";
		DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");  
		Date StartDate=null;
		try {
			StartDate=sdf.parse(strDate);
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Date EndDate=new Date();
		ClinicListDAO dao =appContext.getBean("clinicListDAO", ClinicListDAO.class);
		List<ClinicList> list=dao.findByMpiidAndClinicDate(mpiid, StartDate, EndDate);
		if(list.size()==0){
			System.out.print("没有查询到数据");
		}else{
			System.out.println(list.size()+JSONUtils.toString(list.get(0)));
		}
	}
	
	/**
	 * 就诊记录查询服务之情况二测试(按机构就诊序号查询,并根据就诊时间逆向排序)
	 * @throws DAOException
	 */
	public void testFindByOrganClinicId() throws DAOException{
		String OrganClinicId="11046";
		ClinicListDAO dao =appContext.getBean("clinicListDAO", ClinicListDAO.class);
		List<ClinicList> list=dao.findByOrganClinicId(OrganClinicId);
		if(list.size()==0){
			System.out.print("没有查询到数据");
		}else{
			System.out.println(list.size()+JSONUtils.toString(list.get(0)));
		}
	}
	
	/**
	 * 就诊记录查询服务之情况三测试(按主索引、就诊机构和就诊时间查询,并根据就诊时间逆向排序)
	 * @throws DAOException
	 */
	public void testFindByMpiidAndClinicOrganAndClinicDate() throws DAOException{
		String mpiid="sss6801";
		int ClinicOrgan=1;
		String strDate="2015-02-09 10:34:26";
		DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");  
		Date StartDate=null;
		try {
			StartDate=sdf.parse(strDate);
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Date EndDate=new Date();
		ClinicListDAO dao =appContext.getBean("clinicListDAO", ClinicListDAO.class);
		List<ClinicList> list=dao.findByMpiidAndClinicOrganAndClinicDate(mpiid, ClinicOrgan, StartDate, EndDate);
		if(list.size()==0){
			System.out.print("没有查询到数据");
		}else{
			System.out.println(list.size()+JSONUtils.toString(list.get(0)));
		}
	}
	
	/**
	 * 就诊记录查询服务之情况四测试(按主索引、就诊医生和就诊时间查询,并根据就诊时间逆向排序)
	 * @throws DAOException
	 */
	public void testFindByMpiidAndClinicDoctorAndClinicDate() throws DAOException{
		String mpiid="sss6801";
		int ClinicDoctor=13;
		String strDate="2015-02-09 10:34:26";
		DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");  
		Date StartDate=null;
		try {
			StartDate=sdf.parse(strDate);
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Date EndDate=new Date();
		ClinicListDAO dao =appContext.getBean("clinicListDAO", ClinicListDAO.class);
		List<ClinicList> list=dao.findByMpiidAndClinicDoctorAndClinicDate(mpiid, ClinicDoctor, StartDate, EndDate);
		if(list.size()==0){
			System.out.print("没有查询到数据");
		}else{
			System.out.println(list.size()+JSONUtils.toString(list.get(0)));
		}
		
	}
	
	/**
	 * 就诊记录查询服务之情况五测试(按主索引、就诊类别和就诊时间查询,并根据就诊时间逆向排序)
	 * @throws DAOException
	 */
	public void testFindByMpiidAndClinicTypeAndClinicDate() throws DAOException{
		String mpiid="sss6801";
		int ClinicType=1;
		String strDate="2015-02-09 10:34:26";
		DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");  
		Date StartDate=null;
		try {
			StartDate=sdf.parse(strDate);
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Date EndDate=new Date();
		ClinicListDAO dao =appContext.getBean("clinicListDAO", ClinicListDAO.class);
		List<ClinicList> list=dao.findByMpiidAndClinicTypeAndClinicDate(mpiid, ClinicType, StartDate, EndDate);
		if(list.size()==0){
			System.out.print("没有查询到数据");
		}else{
			System.out.println(list.size()+JSONUtils.toString(list.get(0)));
		}
	}
	
	/**
	 * 就诊记录查询服务之情况六测试(按就诊医生和就诊时间查询,并根据就诊时间逆向排序)
	 * @throws DAOException
	 */
	public void testFindByClinicDoctorAndClinicDate() throws DAOException{
		int ClinicDoctor=13;
		String strDate="2015-02-09 10:34:26";
		DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");  
		Date StartDate=null;
		try {
			StartDate=sdf.parse(strDate);
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Date EndDate=new Date();
		ClinicListDAO dao =appContext.getBean("clinicListDAO", ClinicListDAO.class);
		List<ClinicList> list=dao.findByClinicDoctorAndClinicDate(ClinicDoctor, StartDate, EndDate);
		if(list.size()==0){
			System.out.print("没有查询到数据");
		}else{
			System.out.println(list.size()+JSONUtils.toString(list.get(0)));
		}
	}
	
}
