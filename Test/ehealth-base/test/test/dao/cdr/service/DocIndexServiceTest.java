package test.dao.cdr.service;

import com.alibaba.druid.support.json.JSONUtils;
import eh.entity.cdr.DocIndex;
import eh.util.Easemob;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by zhangx on 2016/5/24.
 */
public class DocIndexServiceTest extends TestCase {

    private static ClassPathXmlApplicationContext appContext;

    static{
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
    }

}
