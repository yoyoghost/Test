package test.message;

import java.util.HashMap;
import java.util.Map;

import com.tencent.xinge.*;
import org.json.JSONObject;

import junit.framework.TestCase;

import static org.apache.zookeeper.server.ServerCnxn.me;

public class MessagePushTester extends TestCase{
	//ACCESS ID：2100088681
			//ACCESS KEY：A1BIN8T3X92T
			//SECRET KEY：2ad0db43008617d14c20fab3fee9ffbc
		//	XingeApp xinge = new XingeApp(2100088681, "2ad0db43008617d14c20fab3fee9ffbc");
	private static long access_id=2100088681L;
	private static String secret_key="2ad0db43008617d14c20fab3fee9ffbc";
	
	private static long access_id_ios=2200088682L;
	private static String secret_key_ios="3489cfbbc5e3b990d17e4cdf39cdc0ec";
	//公司账号
	private static long access_id_ios_new=2200185665L;
	private static String secret_key_ios_new="980936a8f73db51c3da4ce2bc6b6afac";

	private static String test_token="13735891715";
	private static String ios_acc="13735891715";
	public void testPushSingleAccout(){
		JSONObject ret= XingeApp.pushAccountAndroid(access_id, secret_key, "test", "测试",test_token);
		// JSONObject ret= XingeApp.pushTokenAndroid(access_id, secret_key, "test", "测试",test_token);
		System.out.println(ret);
	}
	public void pushAll(){
		JSONObject ret=XingeApp.pushAllIos(access_id, secret_key, "大家好!", XingeApp.IOSENV_PROD);
		System.out.println(ret);
	}
	public void testPushAccountIos(){
		XingeApp xinge = new XingeApp(access_id_ios, secret_key_ios);
		
		//JSONObject ret=	XingeApp.pushAccountIos(access_id_ios, secret_key_ios, "hello ...", ios_acc, XingeApp.IOSENV_DEV);
		JSONObject ret=	XingeApp.pushAccountIos(2200088682L, "f5d6b8c438b1f71bacf14e7d5bef3518", "hello1 ...", ios_acc, XingeApp.IOSENV_PROD);
		//JSONObject ret=XingeApp.pushTokenIos(access_id, secret_key, "hello ...", ios_acc, XingeApp.IOSENV_PROD);
		System.out.println(ret);
	}
	public void testAndroidPush(){
		//下发单个账号
		
			XingeApp xinge = new XingeApp(access_id, secret_key);
			Message message = new Message();
			message.setExpireTime(86400);
			message.setTitle("title");
			message.setContent("content2");
			message.setType(Message.TYPE_NOTIFICATION);
			Map<String, Object> custom = new HashMap<String, Object>();
			custom.put("key1", "value1");
			custom.put("key2", 2);
			message.setCustom(custom);
			JSONObject ret = xinge.pushSingleAccount(0, "13735891715", message);
			System.out.println(ret);
		
	}
	public void testPushSingleDevice(){
		XingeApp xinge = new XingeApp(access_id, secret_key);
		Message message = new Message();
		message.setExpireTime(86400);
		message.setTitle("title");
		message.setContent("content");
		message.setType(Message.TYPE_MESSAGE);
		Map<String, Object> custom = new HashMap<String, Object>();
		custom.put("key1", "value1");
		custom.put("key2", 2);
		message.setCustom(custom);

		JSONObject ret = xinge.pushSingleDevice(test_token, message);
		System.out.println(ret);
	}
	public void testPushSingleIos(){
		System.out.println(demoPushSingleAccountIOS());
	}
	//下发IOS单个账号
		protected JSONObject demoPushSingleAccountIOS() {
			MessageIOS message = new MessageIOS();
			message.setExpireTime(86400);
			message.setAlert("ios test1---------------------------------------------------------");
			message.setBadge(1);
			message.setSound("beep.wav");
			
			TimeInterval acceptTime1 = new TimeInterval(0,0,23,59);
			message.addAcceptTime(acceptTime1);
			Map<String, Object> custom = new HashMap<String, Object>();
			Map<String, Object> param = new HashMap<String, Object>();
			param.put("aa","aa");
			param.put("bb","bb");
			custom.put("key1", param);
			custom.put("key2", 2);
			message.setCustom(custom);
			ClickAction action=new ClickAction();
			action.setActionType(ClickAction.TYPE_URL);
			action.setUrl("http://xg.qq.com");


			XingeApp xinge = new XingeApp(access_id_ios_new, secret_key_ios_new);
			//JSONObject ret = xinge.pushSingleDevice(ios_acc, message, XingeApp.IOSENV_PROD);
			JSONObject ret = xinge.pushSingleAccount(0, ios_acc, message, XingeApp.IOSENV_PROD);
			return (ret);
		}
	public void  testDemoQueryTokensOfAccount()
	{
		XingeApp xg = new XingeApp(access_id_ios_new, secret_key_ios_new);
		JSONObject ret;
		//ret = xg.queryTokensOfAccount("13735891715");
		//System.out.print(ret);
	}
}
