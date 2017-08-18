package eh.messagepush.config.test;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.alibaba.fastjson.JSONObject;

import ctd.util.AppContextHolder;
import eh.base.service.MessagePushConfigService;
import eh.entity.base.MessagePushConfigEntity;
/**
 * @author hexy
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:spring-test.xml")
public class MessagePushServiceTest {
    @Test
    public void query() {
    	MessagePushConfigService service = AppContextHolder.getBean("eh.messagePushConfigService",MessagePushConfigService.class);
    	List<MessagePushConfigEntity> allList = service.findAllEffective();
    	System.err.println(JSONObject.toJSONString(allList));
    	System.out.println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
    	MessagePushConfigEntity someList = service.findByQuery(2,2, 0);
    	System.err.println(JSONObject.toJSONString(someList));
    }
}
