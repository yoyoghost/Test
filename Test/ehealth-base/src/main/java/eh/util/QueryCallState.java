package eh.util;

import com.cloopen.rest.sdk.CCPRestSDK;
import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.Set;

public class QueryCallState {

	public static final Logger log = Logger.getLogger(QueryCallState.class);
	/**
	 * 查询通话状态
	 * @author LF
	 * @param callSid
	 * @return
	 */
	@SuppressWarnings("unchecked")
//	public static void main(String[] args) {
	public String SDKQueryCallState(String callSid) {
		HashMap<String, Object> result = null;

		CCPRestSDK restAPI = new CCPRestSDK();

		// ******************************注释*********************************************
		// *初始化服务器地址和端口 *
		// *沙盒环境（用于应用开发调试）：restAPI.init("sandboxapp.cloopen.com", "8883");*
		// *生产环境（用户应用上线使用）：restAPI.init("app.cloopen.com", "8883"); *
		// *******************************************************************************
		restAPI.init("app.cloopen.com", "8883");// 初始化服务器地址和端口，格式如下，服务器地址不需要写https://
		
		//******************************注释*********************************************
		//*初始化主帐号和主帐号令牌,对应官网开发者主账号下的ACCOUNT SID和AUTH TOKEN     *
		//*ACOUNT SID和AUTH TOKEN在登陆官网后，在“应用-管理控制台”中查看开发者主账号获取*
		//*参数顺序：第一个参数是ACOUNT SID，第二个参数是AUTH TOKEN。                   *
		//*******************************************************************************
//		restAPI.setAccount("ff8080813d8ab3ed013e04d299680478", "4382c55a7cc44c22ba29de2faec6d1e0");
//		restAPI.setSubAccount("aaf98f894c273858014c2ad410510147", "a9602b330c3949b8a4ffca00d778d42f");// 初始化子帐号名称和子帐号令牌
		restAPI.setAccount("aaf98f894c273858014c2ad410510147", "a9602b330c3949b8a4ffca00d778d42f");// 初始化主帐号和主帐号TOKEN
		
		// ******************************注释*********************************************
		// *初始化应用ID *
		// *测试开发可使用“测试Demo”的APP ID，正式上线需要使用自己创建的应用的App ID *
		// *应用ID的获取：登陆官网，在“应用-应用列表”，点击应用名称，看应用详情获取APP ID*
		// *******************************************************************************
		// restAPI.setAppId("aaf98f894b00309b014b003149800001");
		restAPI.setAppId("aaf98f894c9d994b014cb5c02a8b1064");// 初始化应用ID

		// ******************************注释*********************************************
		// *参数一  callid	String	必选	呼叫Id
		// *参数二  action	String	可选	查询结果通知的回调url地址
		// *******************************************************************************
		result = restAPI.QueryCallState(callSid, "");

		log.info("SDKTestQueryCallState result=" + result);
		
		if("000000".equals(result.get("statusCode"))){
			//正常返回输出data包体信息（map）
			HashMap<String,Object> data = (HashMap<String, Object>) result.get("data");
			Set<String> keySet = data.keySet();
			for(String key:keySet){
				Object object = data.get(key);
				log.info(key +" = "+object);
				return key +" = "+object;
			}
		}else{
			//异常返回输出错误码和错误信息
			log.info("错误码=" + result.get("statusCode") +" 错误信息= "+result.get("statusMsg"));
			return "错误码=" + result.get("statusCode") +" 错误信息= "+result.get("statusMsg");
		}
		return null;
	}

}
