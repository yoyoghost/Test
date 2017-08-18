package eh.util;

import com.tencent.xinge.Message;
import com.tencent.xinge.MessageIOS;
import com.tencent.xinge.TimeInterval;
import com.tencent.xinge.XingeApp;

import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import eh.base.service.MessagePushConfigService;
import eh.entity.base.MessagePushConfigEntity;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;

import java.util.Map;

/**
 * 信鸽消息推送工具类 
 * 参考网址http://developer.xg.qq.com/index.php/Java_SDK
 * 
 * @author w
 *
 */
public class MessagePushUtil {
	private static final Log logger = LogFactory.getLog(MessagePushUtil.class);
	
	
	private static MessagePushConfigService service = AppContextHolder.getBean("eh.messagePushConfigService",MessagePushConfigService.class);


	private static XingeApp xinge_and_new;
	private static XingeApp xinge_ios_new;
	
	/**
	 * 设备类型 1 安卓
	 */
	private static Integer DEVICE_TYPE_AND = 1;
	
	/**
	 * 设备类型 2 ios
	 */
	private static Integer DEVICE_TYPE_IOS = 2;
	
	/**
	 * 账号类型  1生产
	 */
	private static Integer ACCOUNT_TYPE_PRD = 1;
	
	/**
	 * 账号类型  2开发
	 */
	private static Integer ACCOUNT_TYPE_DEV = 2;

	/**
	 * 是否推送
	 */
	private static boolean canSend=true;
	
	/**
	 * ios 是否都推送
	 */
	private static boolean ISOALLSEND=true;
	/**
	 *是否生产环境,true是false否
	 */
	private static boolean isProd=true;

	/**
	 *是否开发模式,true是false否
	 */
	private static boolean isDev=false;

	public static boolean isCanSend() {
		return canSend;
	}
	public static void setCanSend(boolean canSend) {
		MessagePushUtil.canSend = canSend;
	}

	/**
	 * 设置开关 config.properties配置
	 * @param canSend 是否允许推送
	 * @param isProd 是否生产环境
	 * @param isDev 是否开发模式,true是false否
     */
	public  static  void setConfig(boolean canSend,boolean isProd,boolean isDev){
		logger.info("the message push config init..");
		//update hexy
		MessagePushConfigEntity config = service.findConfigByCode();
		
		if (null == config) {
			throw new RuntimeException("the message push config no data please check.");
		}
		logger.info("the message push config param:"+com.alibaba.fastjson.JSONObject.toJSONString(config));
		MessagePushUtil.canSend = config.isCanSend();
		MessagePushUtil.isProd = config.isProdFlag();
		MessagePushUtil.isDev = config.isDevFlag();
		MessagePushUtil.ISOALLSEND = config.isConfigType();
		
		logger.info("the message push config init ok the param is {canSend:"+canSend+",isProd:"+isProd+",isDev:"+isDev+",ISOALLSEND:"+ISOALLSEND);
		//安卓
		MessagePushConfigEntity androidConfig = service.findByQuery(DEVICE_TYPE_AND,ACCOUNT_TYPE_PRD,1);
		MessagePushConfigEntity iosConfig = null;
		xinge_and_new = new XingeApp(androidConfig.getAccessId(), androidConfig.getSecretKey());
		if(isProd){
			//ios
			iosConfig = service.findByQuery(DEVICE_TYPE_IOS,ACCOUNT_TYPE_PRD,1);
			xinge_ios_new = new XingeApp(iosConfig.getAccessId(), iosConfig.getSecretKey());
		}else{
			iosConfig = service.findByQuery(DEVICE_TYPE_IOS,ACCOUNT_TYPE_DEV,1);
			xinge_ios_new = new XingeApp(iosConfig.getAccessId(), iosConfig.getSecretKey());
		}
	}

    /**
	 * iOS平台推送消息给单个帐号,用这个推送
	 * @param content 消息内容
	 * @param token 被推送人token信息
	 * @return
	 */
    public static JSONObject PushAccountIos(String content,String token){
		JSONObject ret=null;
		MessagePushConfigEntity newIos = null;
		MessagePushConfigEntity testIos = null;
		if (ISOALLSEND) {
			newIos = service.findByQuery(DEVICE_TYPE_IOS,ACCOUNT_TYPE_PRD,1);
			testIos = service.findByQuery(DEVICE_TYPE_IOS,ACCOUNT_TYPE_DEV,1);
			ret=XingeApp.pushAccountIos(newIos.getAccessId(), newIos.getSecretKey(), content, token, XingeApp.IOSENV_PROD);
			ret=XingeApp.pushAccountIos(testIos.getAccessId(), testIos.getSecretKey(), content, token, XingeApp.IOSENV_PROD);
		}else if(isProd){
			newIos = service.findByQuery(DEVICE_TYPE_IOS,ACCOUNT_TYPE_PRD,1);
			ret=XingeApp.pushAccountIos(newIos.getAccessId(), newIos.getSecretKey(), content, token, XingeApp.IOSENV_PROD);
		}else{
			testIos = service.findByQuery(DEVICE_TYPE_IOS,ACCOUNT_TYPE_DEV,1);
			ret=XingeApp.pushAccountIos(testIos.getAccessId(), testIos.getSecretKey(), content, token, XingeApp.IOSENV_DEV);
		}

    	return ret;
    }
    /**
     * 暂不用
     * @param title
     * @param content
     * @param token
     * @return
     */
    public static JSONObject pushTokenAndroid(String title,String content,String token){
    	MessagePushConfigEntity androidConfig = service.findByQuery(DEVICE_TYPE_AND,ACCOUNT_TYPE_PRD,1);
    	JSONObject ret=XingeApp.pushTokenAndroid(androidConfig.getAccessId(), androidConfig.getSecretKey(), title, content, token);
    	return ret;
    }
    /**
     * 安卓快捷推送消息
     * @param title 消息标题
     * @param content 内容
     * @param token 用户token
     * @return
     */
    public static JSONObject pushAccountAndroid(String title,String content,String token){
    	MessagePushConfigEntity androidConfig = service.findByQuery(DEVICE_TYPE_AND,ACCOUNT_TYPE_PRD,1);
    	JSONObject ret=XingeApp.pushAccountAndroid(androidConfig.getAccessId(), androidConfig.getSecretKey(), title, content, token);
    	return ret;
    }
    /**
     * 安卓推送高级接口
     * @param token
     * @param message 自定义消息参数
     * @return
     */
    public static JSONObject pushSingleAccount(String token,Message message){
    	if(!canSend){
			return null;
		}
		JSONObject ret = xinge_and_new.pushSingleAccount(0, token, message);
		return ret; 
    }
    /**
     * ios高级推送接口
     * @param token
     * @param message ios自定义消息参数
     * @return
     */
    public static  JSONObject pushSingleAccount(String token,MessageIOS message){

    	if(!canSend){
			return null;
		}
		JSONObject ret = null;
		int iosEnv=XingeApp.IOSENV_PROD;
		if(isDev){
			iosEnv=XingeApp.IOSENV_DEV;
		}

		MessagePushConfigEntity newIos = null;
		MessagePushConfigEntity testIos = null;
		if (ISOALLSEND) {
			newIos = service.findByQuery(DEVICE_TYPE_IOS,ACCOUNT_TYPE_PRD,1);
			testIos = service.findByQuery(DEVICE_TYPE_IOS,ACCOUNT_TYPE_DEV,1);
			XingeApp xinge_ios_new = new XingeApp(newIos.getAccessId(), newIos.getSecretKey());
			XingeApp xinge_ios_test = new XingeApp(testIos.getAccessId(), testIos.getSecretKey());
			ret=xinge_ios_new.pushSingleAccount(0, token, message, XingeApp.IOSENV_PROD);
			ret= xinge_ios_test.pushSingleAccount(0, token, message, XingeApp.IOSENV_DEV);
		}else if(isProd){
			ret=xinge_ios_new.pushSingleAccount(0, token, message, iosEnv);
		}else{
			ret= xinge_ios_new.pushSingleAccount(0, token, message, iosEnv);
		}
		return ret;
    }
    
    
    
    /**
	 * IOS设备发送信息
	 * @param iosTel 发送电话
	 * @param custom 携带信息
	 * @param Msg 显示信息
	 * @author ZX
	 */
    public static void MsgPushOfIOS(String iosTel,Map<String, Object> custom,String Msg){
		String sound="beep.wav";
		MsgPushOfIOSWithSound(iosTel,custom,Msg,sound);
	}

	public static void MsgPushOfIOSWithSound(String iosTel,Map<String, Object> custom,String Msg,String sound){
		// 2016-3-5 luf:IOS端需要拿到消息内容，故将Msg加到custom里面
		logger.info("IOS推送消息to["+iosTel+"],内容:"+Msg+",自定义参数:"+ JSONUtils.toString(custom));
		if(StringUtils.isEmpty(sound)){
			sound="beep.wav";
		}

		custom.put("msg", Msg);
		JSONObject ret=null;
		MessageIOS messageIos = new MessageIOS();
		messageIos.setExpireTime(86400);
		messageIos.setAlert(Msg);
		messageIos.setBadge(1);
		messageIos.setSound(sound);
		TimeInterval acceptTime1 = new TimeInterval(0,0,23,59);
		messageIos.addAcceptTime(acceptTime1);
		messageIos.setCustom(custom);
		ret=MessagePushUtil.pushSingleAccount(iosTel,messageIos);
		logger.info("IOS推送消息结果:"+ret);
	}

	
	/**
	 * Android设备发送信息
	 * @param andTel 发送电话
	 * @param custom 携带信息
	 * @param title 显示标题(一般为软件名称)
	 * @param Msg 显示信息
	 * @author ZX
	 */
    public static void MsgPushOfAND(String andTel,Map<String, Object> custom,String title,String Msg){
		logger.info("Android推送消息to["+andTel+"],内容:"+Msg+",自定义参数:"+ JSONUtils.toString(custom));

		JSONObject ret=null;
    	//推送给Android
    	Message message = new Message();
		message.setExpireTime(86400);
		message.setTitle(title);
		message.setContent(Msg);
		message.setType(Message.TYPE_NOTIFICATION);
		message.setCustom(custom);
    	ret=MessagePushUtil.pushSingleAccount(andTel,message);
		logger.info("Android推送消息结果:"+ret);
	}
}
