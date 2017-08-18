package test.dao;

import java.util.HashMap;
import java.util.Map;

import com.tencent.xinge.MessageIOS;
import com.tencent.xinge.TimeInterval;

import eh.util.MessagePushUtil;
import junit.framework.TestCase;

public class MessagePushUtilTester extends TestCase {
	
	public void testPushTokenIos(){
		Map<String, Object> custom = new HashMap<String, Object>();
		custom.put("page", "test");
	
		MessageIOS messageIos = new MessageIOS();
    	messageIos.setExpireTime(86400);
    	messageIos.setAlert("Test 测试");
    	messageIos.setBadge(1);
    	messageIos.setSound("beep.wav");
		TimeInterval acceptTime1 = new TimeInterval(0,0,23,59);
		messageIos.addAcceptTime(acceptTime1);
		messageIos.setCustom(custom);
		MessagePushUtil.pushSingleAccount("80e0be1a 4a6d0157 042085ec 92d0e11a a5697914 da488d59 74fe7409 03b96d8b",messageIos );
	}
	public void testPushAnd(){
		// 构建推送消息附加信息
				Map<String, Object> custom = new HashMap<String, Object>();
				custom.put("page", "transfer");
				custom.put("id", 1111);

				String title = "转诊申请消息";
		//MessagePushUtil.MsgPushOfAND("13588745152", custom,"纳里医生", "你有一条转诊消息");
		MessagePushUtil.MsgPushOfIOS("13658664885", custom,
				 "你有一条转诊消息");
	}
	
}
