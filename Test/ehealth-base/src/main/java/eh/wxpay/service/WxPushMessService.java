package eh.wxpay.service;

import com.alibaba.fastjson.JSONObject;
import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import eh.bus.dao.WxTemplateDAO;
import eh.entity.bus.msg.WxCustomerMsg;
import eh.entity.bus.msg.WxTemplateMsg;
import eh.remote.IWXPMServiceInterface;
import eh.utils.ValidateUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

public class WxPushMessService {
	private static final Logger log = LoggerFactory.getLogger(WxPushMessService.class);
	private String appId;
	private String openId;

	public WxPushMessService(String appId, String openId){
		this.appId = appId;
		this.openId = openId;
	}

	public boolean sendCustomerMessage(WxCustomerMsg msg) throws Exception {
		if(msg==null){
			log.error("sendCustomerMessage parameter msg is blank，please check! msg[{}]", msg);
			return false;
		}
		if(StringUtils.isBlank(openId)){
			log.error("sendCustomerMessage parameter openId is blank，please check! msg[{}]", msg);
			return false;
		}
		IWXPMServiceInterface service = AppContextHolder.getBean("eh.wxPushMessService", IWXPMServiceInterface.class);
		String result = "";
		if(msg.isWithCallBackLink()){
			result = service.sendCustomerMsgWithCallbackLink(appId, openId, msg.getContent(), msg.getKvMap());;
		}else {
			result = service.sendCustomerMsg(appId, openId, msg.getContent());
		}
		log.info("[{}] sendCustomerMessage result[{}] with param[{}]", this.getClass().getSimpleName(), result, JSONObject.toJSONString(msg));
		WxMsgResult wxResultObj = JSONObject.parseObject(result, WxMsgResult.class);
		if(wxResultObj.getErrcode()==0){
			return true;
		}else{
			throw new Exception(result);
		}
	}

	public boolean sendWxTemplateMessage(WxTemplateMsg msg) throws Exception {
		if(StringUtils.isBlank(openId)){
			log.error("pushWxTemplateMessage parameter openId is blank，please check! msg[{}]", msg);
			return false;
		}
		if(msg==null){
			log.error("pushWxTemplateMessage parameter msg is blank，please check! msg[{}]", msg);
			return false;
		}
		if(StringUtils.isBlank(msg.getTemplateKey())){
			log.error("pushWxTemplateMessage templateConfigKey is blank，please check! msg[{}]", msg);
			return false;
		}
		if(msg.getKeywordType()==null){
			log.error("pushWxTemplateMessage keyWordType can not be null! msg[{}]", msg);
			return false;
		}
		if(msg.getKeywords()==null || msg.getKeywords().length<=0){
			log.error("pushWxTemplateMessage keywords can not be empty! msg[{}]", msg);
			return false;
		}
		String keyword = msg.getKeywordType().getKeyword();
		HashMap<String, String> map = new HashMap<>();
		map.put("first", msg.getFirst());
		String[] keywords = msg.getKeywords();
		for(int i=0; i<keywords.length; i++){
			map.put(keyword+(i+1), keywords[i]);
		}
		map.put("remark", msg.getRemark());
		String templateId = DAOFactory.getDAO(WxTemplateDAO.class).getTemplateIdByTemplateKeyAndAppId(msg.getTemplateKey(), getAppId());
		if(ValidateUtil.blankString(templateId)){
			log.info("current templateId is null or empty! appId[{}], templateKey[{}]", getAppId(), msg.getTemplateKey());
			return false;
		}
		IWXPMServiceInterface service = AppContextHolder.getBean("eh.wxPushMessService", IWXPMServiceInterface.class);
		String result = "";
		switch (msg.getKeywordType()) {
			case KEYWORDS :
			case KEYNOTES:
				result = service.pushMessage(appId, templateId, openId, msg.getDetailUrl(), map);
				break;
			case KEYWORDS_WITH_CALLBACK_LINK:
			case KEYNOTES_WITH_CALLBACK_LINK:
				result = service.pushMessageWithCallbackLink(appId, templateId, openId, msg.getKvMap(), map);
				break;
			default:
				log.error("it's impossible! ");
		}
		log.info("[{}] sendWxTemplateMessage result[{}] with param[{}]", this.getClass().getSimpleName(), result, JSONObject.toJSONString(msg));
		WxMsgResult wxResultObj = JSONObject.parseObject(result, WxMsgResult.class);
		if(wxResultObj.getErrcode()==0){
			return true;
		}else{
			throw new Exception(result);
		}
	}

	public String getAppId() {
		return appId;
	}

	public void setAppId(String appId) {
		this.appId = appId;
	}

	static class WxMsgResult{

		private int errcode;
		private String errmsg;
		private long msgid;

		public long getMsgid() {
			return msgid;
		}

		public void setMsgid(long msgid) {
			this.msgid = msgid;
		}

		public int getErrcode() {
			return errcode;
		}

		public void setErrcode(int errcode) {
			this.errcode = errcode;
		}

		public String getErrmsg() {
			return errmsg;
		}

		public void setErrmsg(String errmsg) {
			this.errmsg = errmsg;
		}


	}

}
