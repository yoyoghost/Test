package test.rpc;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Client2 {
	@SuppressWarnings("resource")
	public static void main(String[] args) throws Exception{
		
		ClassPathXmlApplicationContext appContext = new ClassPathXmlApplicationContext("test/spring-client.xml");
		
		IRemoteService service = appContext.getBean("eh.appointRecord",IRemoteService.class);
		service.reTryAppoint(27941);
	}
}
