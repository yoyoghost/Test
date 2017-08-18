package test.service.base;

import eh.base.service.ScratchableService;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class ScratchableServiceTester extends TestCase{
	private static ClassPathXmlApplicationContext appContext;
	private static ScratchableService service;
	static {
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		service=appContext.getBean("scratchableService", ScratchableService.class);
	}
    public void testfindByTempIdAndLevel() {

//        System.out.println(JSONUtils.toString(service.findModleByAppID("ssssss",0)));
    }

}
