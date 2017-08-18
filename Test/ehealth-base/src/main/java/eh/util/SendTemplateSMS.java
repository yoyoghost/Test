package eh.util;

import com.cloopen.rest.sdk.CCPRestSDK;
import ctd.persistence.DAOFactory;
import ctd.util.JSONUtils;
import eh.bus.dao.SmsRecordDAO;
import eh.entity.bus.SmsRecord;
import eh.entity.msg.SmsContent;
import org.apache.log4j.Logger;

import java.util.Date;
import java.util.HashMap;
import java.util.Set;

public class SendTemplateSMS {
	public static final Logger log = Logger.getLogger(SendTemplateSMS.class);

	/**
	 * 绎盛谷云通讯account sid
	 */
	private final static String ACCOUNT_SID="aaf98f894c273858014c2ad410510147";
	/**
	 * 绎盛谷云通讯auth token
	 */
	private final static String ACCOUNT_TOKEN="a9602b330c3949b8a4ffca00d778d42f";
	/**
	 * 沙盒环境（用于应用开发调试）
	 */
	private final static String SANDBOX_URL="sandboxapp.cloopen.com";
	/**
	 * 生产环境（用户应用上线使用）
	 */
	private final static String PRODUCT_URL="app.cloopen.com";
	private final static String PORT="8883";
	/**
	 * 纳里医生appid
	 */
	private final static String APPID_NAGRIDOCTOR="8a48b5514df54891014df58c1353004f";
	/**
	 * 纳里健康appid
	 */
	private final static String APPID_NAGRIPATINET="aaf98f894c9d994b014cb5c02a8b1064";
	
	/**
	 * 是否推送
	 */
	private static boolean canSend=true;
    public static boolean isCanSend() {
		return canSend;
	}
	public static void setCanSend(boolean canSend) {
		SendTemplateSMS.canSend = canSend;
	}

	/*public SendTemplateSMS(){
			
	}*/

	/**
	 * 纳里医生，向医生发送短信
	 * @param content
	 */
	public static void sendMesToDoctor(SmsContent content){
		sendMessage(APPID_NAGRIDOCTOR,content);
	}
	/**
	 * 纳里健康，向患者发送短信
	 * @param content
	 */
	public static void sendMesToPatient(SmsContent content){
		sendMessage(APPID_NAGRIPATINET,content);
	}
	private static void sendMessage(String appid,SmsContent content){
		if(!canSend){
			return;
		}
		HashMap<String, Object> result = null;

		//初始化SDK
		CCPRestSDK restAPI = new CCPRestSDK();
		restAPI.init(PRODUCT_URL, PORT);
		restAPI.setAccount(ACCOUNT_SID, ACCOUNT_TOKEN);
		restAPI.setAppId(appid);
		
		result = restAPI.sendTemplateSMS(content.getMobile(), content.getTemplateId(), content.getParameter());
		
		log.info("SDKTestGetSubAccounts result=" + result);
		if("000000".equals(result.get("statusCode"))){
			//正常返回输出data包体信息（map）
			HashMap<String,Object> data = (HashMap<String, Object>) result.get("data");
			Set<String> keySet = data.keySet();
			for(String key:keySet){
				Object object = data.get(key);
				log.info(key +" = "+object);
			}
		}else{
			//异常返回输出错误码和错误信息
			log.info("错误码=" + result.get("statusCode") +" 错误信息= "+result.get("statusMsg"));
		}
		//保存短信发送记录
		SmsRecordDAO dao =DAOFactory.getDAO(SmsRecordDAO.class);
		SmsRecord sr=new SmsRecord();
		sr.setMobile(content.getMobile());
		sr.setContent(JSONUtils.toString(content));
		sr.setResult(result.toString());
		sr.setCreateTime(new Date());
		sr.setAppid(appid);
		dao.save(sr);
	}
	/**
	 * 发送短信
	 * @param mobile 手机号
	 * @param templateId  短信模板
	 * @param parameter 短信参数
	 */
	public  void  SendSMS(String mobile, String templateId, String[] parameter)
	{
		if(!canSend)
			return;
		HashMap<String, Object> result = null;

		//初始化SDK
		CCPRestSDK restAPI = new CCPRestSDK();
		
		//******************************注释*********************************************
		//*初始化服务器地址和端口                                                       *
		//*沙盒环境（用于应用开发调试）：restAPI.init("sandboxapp.cloopen.com", "8883");*
		//*生产环境（用户应用上线使用）：restAPI.init("app.cloopen.com", "8883");       *
		//*******************************************************************************
		restAPI.init(PRODUCT_URL, PORT);
		
		//******************************注释*********************************************
		//*初始化主帐号和主帐号令牌,对应官网开发者主账号下的ACCOUNT SID和AUTH TOKEN     *
		//*ACOUNT SID和AUTH TOKEN在登陆官网后，在“应用-管理控制台”中查看开发者主账号获取*
		//*参数顺序：第一个参数是ACOUNT SID，第二个参数是AUTH TOKEN。                   *
		//*******************************************************************************
		restAPI.setAccount(ACCOUNT_SID, ACCOUNT_TOKEN);
		
		
		//******************************注释*********************************************
		//*初始化应用ID                                                                 *
		//*测试开发可使用“测试Demo”的APP ID，正式上线需要使用自己创建的应用的App ID     *
		//*应用ID的获取：登陆官网，在“应用-应用列表”，点击应用名称，看应用详情获取APP ID*
		//*******************************************************************************
		restAPI.setAppId(APPID_NAGRIPATINET);
		
		
		//******************************注释****************************************************************
		//*调用发送模板短信的接口发送短信                                                                  *
		//*参数顺序说明：                                                                                  *
		//*第一个参数:是要发送的手机号码，可以用逗号分隔，一次最多支持100个手机号                          *
		//*第二个参数:是模板ID，在平台上创建的短信模板的ID值；测试的时候可以使用系统的默认模板，id为1。    *
		//*系统默认模板的内容为“【云通讯】您使用的是云通讯短信模板，您的验证码是{1}，请于{2}分钟内正确输入”*
		//*第三个参数是要替换的内容数组。																														       *
		//**************************************************************************************************
		
		//**************************************举例说明***********************************************************************
		//*假设您用测试Demo的APP ID，则需使用默认模板ID 1，发送手机号是13800000000，传入参数为6532和5，则调用方式为           *
		//*result = restAPI.sendTemplateSMS("13800000000","1" ,new String[]{"6532","5"});																		  *
		//*则13800000000手机号收到的短信内容是：【云通讯】您使用的是云通讯短信模板，您的验证码是6532，请于5分钟内正确输入     *
		//*********************************************************************************************************************
		result = restAPI.sendTemplateSMS(mobile, templateId, parameter);
		
		log.info("SDKTestGetSubAccounts result=" + result);
		if("000000".equals(result.get("statusCode"))){
			//正常返回输出data包体信息（map）
			HashMap<String,Object> data = (HashMap<String, Object>) result.get("data");
			Set<String> keySet = data.keySet();
			for(String key:keySet){
				Object object = data.get(key);
				log.info(key +" = "+object);
			}
		}else{
			//异常返回输出错误码和错误信息
			log.info("错误码=" + result.get("statusCode") +" 错误信息= "+result.get("statusMsg"));
		}
	}
}
