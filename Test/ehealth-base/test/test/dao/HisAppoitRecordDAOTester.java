package test.dao;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.util.JSONUtils;
import eh.bus.dao.HisAppointRecordDAO;
import eh.entity.bus.HisAppointRecord;

public class HisAppoitRecordDAOTester extends TestCase{
	private static ClassPathXmlApplicationContext appContext;
	private static HisAppointRecordDAO dao;
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		dao=appContext.getBean("hisAppoitRecordDAO", HisAppointRecordDAO.class);
	}
	public void testCreateAppointRecord(){
		HisAppointRecord rd=new HisAppointRecord();
		rd.setOrganId(1);
		rd.setOrganSchedulingId("000");
		rd.setOrganSourceId("123");
		rd.setWorkDate(getCurrentDate("2015-05-07","yyyy-MM-dd"));
		rd.setNumber(1);
		dao.save(rd);
	}
	public void testGetAppointRecord(){
		HisAppointRecord rd=new HisAppointRecord();
		rd.setOrganId(1);
		rd.setOrganSchedulingId("000");
		rd.setOrganSourceId("123");
		rd.setWorkDate(getCurrentDate("2015-05-07","yyyy-MM-dd"));
		rd.setNumber(1);
		HisAppointRecord ar=dao.getHisAppoitRecord(1, "000", "123", getCurrentDate("2015-05-07","yyyy-MM-dd"));
		System.out.println(JSONUtils.toString(ar));
		//HisAppoitRecord newar=JSONUtils.
	}
	public static Date getCurrentDate(String dateStr,String format) {
		   Date currentTime = new Date();
		   SimpleDateFormat formatter = new SimpleDateFormat(format);
		    try {
				currentTime = formatter.parse(dateStr);
			} catch (ParseException e) {
				// TODO Auto-generated catch block
				return null;
			}
		   return currentTime;
	}
}
