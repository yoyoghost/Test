package test.service.base;

import com.alibaba.fastjson.JSON;
import eh.base.service.organ.QueryOrganService;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.List;
import java.util.Map;

/**
 * Created by Administrator on 2016/9/30.
 */
public class QueryOrganServiceTester extends TestCase {

    private static ClassPathXmlApplicationContext appContext;
    private static QueryOrganService service;

    static {
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
        service = appContext.getBean("queryOrganService", QueryOrganService.class);
    }

    public void testFindByAddrAreaLikeGroupByGrade() throws Exception {
        String addr = "3301";
        List<Map<String, Object>> map = service.findByAddrAreaLikeGroupByGrade(addr);
        System.out.println(JSON.toJSONString(map));

    }

}