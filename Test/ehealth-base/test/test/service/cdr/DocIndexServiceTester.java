package test.service.cdr;

import eh.cdr.service.DocIndexService;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Created by w on 2016/5/12.
 */
public class DocIndexServiceTester extends TestCase{
    private static ClassPathXmlApplicationContext appContext;
    private static DocIndexService service;

    static
    {
        appContext = new ClassPathXmlApplicationContext("classpath:spring.xml");
        service =appContext.getBean("docIndexService", DocIndexService.class);
    }
    public  void testGetDocContentUrl(){

       String url=service.getDocContentUrl(1125);
        System.out.print(url);
    }
}
