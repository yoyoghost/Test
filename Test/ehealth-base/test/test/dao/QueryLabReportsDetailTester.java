package test.dao;

import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.HashMap;

/**
 * Created by WALY on 2016/6/14.
 */
public class QueryLabReportsDetailTester extends TestCase {
    private static ClassPathXmlApplicationContext appContext;
//    private static QueryLabReportsDetailService service;

    static {
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
//        service = appContext.getBean("queryLabReportsDetailService", QueryLabReportsDetailService.class);
    }

    public void test1(){
        HashMap<String, String> map =new HashMap<String, String>();
        map.put("MpiID","2c9081814cc3ad35014cc3e0361f0000");
        map.put("OrganID","1");
        map.put("CardType","");
        map.put("CardOrgan","");
        map.put("CardID","");
        map.put("TestItemName","");
        map.put("ReportID","");
        map.put("ReportSeq","");
        map.put("RePortDate","");
        map.put("RePortType","1");

//        Map<String,Object> list= service.getLableReportsDetail(map);
//        System.out.println("---"+JSONUtils.toString(list));

    }

}
