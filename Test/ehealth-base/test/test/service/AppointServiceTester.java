package test.service;

import ctd.persistence.DAOFactory;
import ctd.util.JSONUtils;
import eh.bus.dao.AppointRecordDAO;
import eh.bus.service.AppointService;
import eh.entity.bus.AppointRecord;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.ArrayList;
import java.util.List;

public class AppointServiceTester extends TestCase{

	private static ClassPathXmlApplicationContext appContext;
	private static AppointService service;
	
	static
	{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		service =appContext.getBean("appointService", AppointService.class);
	}

	public void testFindAppointRecord(){
		List<AppointRecord> appointRecordlists=service.findAppointRecordByOrganSchedulingId("168");
		for (AppointRecord appointRecord:appointRecordlists) {
			System.out.println("status:"+appointRecord.getAppointStatus()+",scheduleId:"+appointRecord.getOrganSchedulingId()+",appointRecordId:"+appointRecord.getAppointRecordId()+",AppointName:"+appointRecord.getAppointName()+",TelClinicFlag:"+appointRecord.getTelClinicFlag()
					+",getPatientName:"+appointRecord.getPatientName());
		}
		String list=JSONUtils.toString(appointRecordlists);
		System.out.println("===Start stop appointRecord===");

		List<Integer> ids=new ArrayList<Integer>();
		ids.add(168);
		service.updateScheduleAndSourceStopOrNot(ids,1,true);
		System.out.println("===End stop appointRecord===");

	}
}
