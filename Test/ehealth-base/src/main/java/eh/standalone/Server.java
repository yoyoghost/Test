package eh.standalone;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Server {
	@SuppressWarnings({ "unused" })
	public static void main(String[] args){
		ClassPathXmlApplicationContext appContext = new ClassPathXmlApplicationContext("spring-configure.xml");
	}
}
