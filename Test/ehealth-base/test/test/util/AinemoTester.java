package test.util;

import ctd.util.JSONUtils;
import ctd.util.context.Context;
import eh.bus.constant.VideoInfoConstant;
import eh.util.Ainemo;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.Date;
import java.util.HashMap;

public class AinemoTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	static
	{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}

	public void testCreateMeeting(){
		HashMap<String, String> params = new HashMap<String, String>();
		params.put("meeting_name","测试张肖的会议");
		params.put("start_time", Context.instance().get("date.now",Date.class).getTime()+"");
		params.put("require_password","true");

		String res= Ainemo.createMeeting(params, VideoInfoConstant.VIDEO_FLAG_NGARI);
		System.out.println(JSONUtils.toString(res));

	}
}
