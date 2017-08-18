package test.remote;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import junit.framework.TestCase;
import eh.entity.bus.AppointmentRequest;
import eh.task.executor.AppointSendExecutor;

public class AppointSendExecutorTester extends TestCase{

	private static ClassPathXmlApplicationContext appContext;

	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		
	}
	public void testSend(){
		AppointmentRequest req=new AppointmentRequest();
		req.setAppointRoad(1);
		req.setOrganID("1");
		AppointSendExecutor exe=new AppointSendExecutor(req);
		exe.sendToHis();
	}
}
