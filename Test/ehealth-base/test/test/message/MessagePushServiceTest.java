package test.message;

import eh.msg.service.MessagePushService;
import org.junit.Test;
import org.mvel2.util.Make;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.HashMap;

import static org.junit.Assert.*;

/**
 * Created by w on 2016/5/19.
 */
public class MessagePushServiceTest {
    private static ClassPathXmlApplicationContext appContext;

    static {
        appContext = new ClassPathXmlApplicationContext("classpath:spring.xml");
    }
    @Test
    public void pushMsg() throws Exception {
        MessagePushService service=appContext.getBean("messagePushService",MessagePushService.class);
        //自定义参数
        HashMap<String,Object> map=new HashMap<>();
        map.put("action_type","1");// 动作类型，1打开activity或app本身
        map.put("activity","TEACH_DETAIL");//指定模块
        HashMap<String,Object> attr=new HashMap<>(); // activity属性，只针对action_type=1的情况
        attr.put("url","http://www.baidu.com/");
        map.put("aty_attr",attr);
        HashMap<String,Object> res=service.pushMsg("13735891715","你好，即可1",map);
        System.out.println(res);
    }

}