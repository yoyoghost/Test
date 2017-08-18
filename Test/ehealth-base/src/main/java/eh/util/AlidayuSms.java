package eh.util;

import com.taobao.api.ApiException;
import com.taobao.api.DefaultTaobaoClient;
import com.taobao.api.TaobaoClient;
import com.taobao.api.internal.util.TaobaoHashMap;
import com.taobao.api.request.AlibabaAliqinFcSmsNumSendRequest;
import com.taobao.api.response.AlibabaAliqinFcSmsNumSendResponse;
import com.taobao.top.link.LinkException;
import ctd.spring.AppDomainContext;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.SmsConstant;
import eh.base.constant.SystemConstant;
import eh.remote.IAlidayuSmsInterface;
import org.apache.log4j.Logger;

import java.util.HashMap;

public class AlidayuSms {

	public static final Logger log = Logger.getLogger(AlidayuSms.class);

	private static  final String url="http://gw.api.taobao.com/router/rest";
	private static  final String appkey="23255725";
	private static  final String secret="baaa5117a40f29f8ca99a6bbb6aafe4e";
	/**
	 * 是否推送
	 */
	private static boolean canSend=true;
    public static boolean isCanSend() {
		return canSend;
	}
	public static void setCanSend(boolean canSend) {
		AlidayuSms.canSend = canSend;
	}
    /**
     * 阿里大鱼短信发送，默认签名是"纳里健康"
     * @param mobile 手机号
     * @param templateCode  模板
     * @param smsParam 内容参数
     */
	@RpcService
	public static  void sendSms(String mobile,String templateCode,HashMap<String,String> smsParam){
		sendSms(SystemConstant.PATIENT_APP_NAME,mobile,templateCode,smsParam);
	}
	/**
	 * 
	 * @param signName 签名 纳里健康or 纳里医生
	 * @param mobile 手机号
	 * @param templateCode 模板
	 * @param smsParam 内容参数
	 */
	@RpcService
	public static  void sendSms(String signName,String mobile,String templateCode,HashMap<String,String> smsParam){
		if(!canSend){
			return;
		}
		TaobaoClient client = new DefaultTaobaoClient(url, appkey, secret);
		AlibabaAliqinFcSmsNumSendRequest req = new AlibabaAliqinFcSmsNumSendRequest();
		//req.setExtend("10001");
		req.setSmsType("normal");
		req.setSmsFreeSignName(signName);
		req.setSmsParam(JSONUtils.toString(smsParam));
		req.setRecNum(mobile);
		req.setSmsTemplateCode(templateCode);
		AlibabaAliqinFcSmsNumSendResponse response;
		try {
			response = client.execute(req);
			log.info("dao:"+JSONUtils.toString(response));
		} catch (ApiException e) {
		log.error(e);
		}
	}
	
	@RpcService
	public void sendSmsUpGrade(String mobile,String templateCode,HashMap<String,String> smsParam,Integer smsInfoId){
		IAlidayuSmsInterface alidayuSms = AppDomainContext.getBean("sms.alidayuSms",IAlidayuSmsInterface.class);
		alidayuSms.sendSmsUpGrade(mobile, templateCode, smsParam, smsInfoId);
	}
	
	@RpcService
	public static void sendAppointSms(){
		TaobaoHashMap smsParam =new TaobaoHashMap();
		smsParam.put("patientname", "王宁武您好");
		smsParam.put("applydoctor", "邵逸夫医院呼吸内科蒋旭辉");
		smsParam.put("targetdoctor", "邵逸夫医院骨科赵斌强");
		smsParam.put("sourceinfo", "：:");
		smsParam.put("clinicinfo", "ffff");
		smsParam.put("targetorgan", "0571-66666666");
		smsParam.put("servicetel", "0571-88888888");
		sendSms("13735891715","SMS_1090001",smsParam);

	}
	
	@RpcService
	public void reSendSmsByIdOrExtendId(Integer id,String extendId){
		IAlidayuSmsInterface alidayuSms = AppDomainContext.getBean("sms.alidayuSms",IAlidayuSmsInterface.class);
		alidayuSms.reSendSmsByIdOrExtendId(id, extendId);
	}
	public static void main(String[] args) throws LinkException, InterruptedException {
		HashMap<String, String> smsParam = new HashMap<String, String>();
		smsParam.put("app", "纳里健康");
		smsParam.put("sms", "1234");

		AlidayuSms.sendSms("13735891715", SmsConstant.ALIDAYU_CAPTCHA, smsParam);
		
	}

}
