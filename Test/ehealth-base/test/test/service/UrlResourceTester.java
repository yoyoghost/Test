package test.service;

import eh.base.service.UrlResourceService;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;


public class UrlResourceTester {
    private static ClassPathXmlApplicationContext appContext;
    private static UrlResourceService service;

    static
    {
        appContext = new ClassPathXmlApplicationContext("classpath:spring.xml");
        service =appContext.getBean("urlResourceService", UrlResourceService.class);
    }
    @Test
    public  void testToString(){
      System.out.print(service.toString());
    }
    @Test
    public void testGetUrlByName(){
        System.out.print(service.getUrlByName("pacsUrl"));
        System.out.print(service.getUrls());
    }
    @Test
    public void testGetTeachUrl(){
        System.out.print(service.getTeachUrl(1177));

    }

}
