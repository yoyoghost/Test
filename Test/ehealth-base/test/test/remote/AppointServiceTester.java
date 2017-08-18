package test.remote;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import eh.bus.service.AppointService;
import eh.entity.bus.HisAppointRecord;
import eh.entity.his.HisAppointSource;

public class AppointServiceTester extends TestCase{
	private static ClassPathXmlApplicationContext appContext;

	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		
	}
  public void testUpdate(){
	 // AppointService service=new AppointService();
	  AppointService service  = appContext.getBean("eh.appointService",AppointService.class);
	  List<HisAppointRecord> list=new ArrayList<>();
	  HisAppointRecord hs=new HisAppointRecord();
	  hs.setJobNumber("");
	  hs.setNumber(1);
	  hs.setOrganId(1);
	  hs.setOrganSchedulingId("12765");
	  hs.setOrganSourceId("150371");
	  hs.setType("2");
	  hs.setWorkDate(getCurrentDate("2015-05-6","yyyy-MM-dd"));
	  list.add(hs);
	  service.updateAppointSource(list);
  }
  public void testUpdateSource(){
		HisAppointRecord r=new HisAppointRecord();
		r.setOrganId(1);
		r.setOrganSchedulingId("298");
		r.setOrganSourceId("20150603|298|2|15");
		r.setOrganAppointId("3830431");
		 r.setWorkDate(getCurrentDate("2015-06-03 15:00","yyyy-MM-dd HH:mm"));
		 r.setOrderNum(15);
		 r.setSourceType(1);
		 r.setType("1");
		 r.setNumber(1);
		AppointService service  = appContext.getBean("eh.appointService",AppointService.class);
		service.updateSource(r);
  }
  /**
	 * 根据时间字符串 和指定格式 返回日期
	 * @param dateStr
	 * @param format
	 * @return
	 */
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
