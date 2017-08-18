package test.dao;

import java.util.Date;
import java.util.HashMap;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import eh.bus.dao.EventDataDAO;
import eh.entity.bus.EventData;

import junit.framework.TestCase;

public class EventdataTest extends TestCase{
private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
public void testSaveEventData(){
	EventDataDAO dao =appContext.getBean("eventDataDAO", EventDataDAO.class);

	EventData eventData = new EventData("33333", "33333", "333333", new Date(), "33333333");
	dao.save(eventData);
	
}
public void testSchedulingModify(){
	HashMap<String, Object> data=new HashMap<String, Object>();
	HashMap<String, Object> contentMap=new HashMap<String, Object>();
	EventDataDAO dao =appContext.getBean("eventDataDAO", EventDataDAO.class);
	    contentMap.put("OrganId", "1");
		contentMap.put("OrganSchedulingId", "12246");
		contentMap.put("Active1", "2");
		contentMap.put("Active2", "2");
		data.put("OrganScheduling", contentMap);
		dao.schedulingModify(data);
		
		
}
}
