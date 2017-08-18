package eh.bus.service;

import ctd.mvc.weixin.entity.OAuthWeixinMP;
import ctd.mvc.weixin.entity.OAuthWeixinMPDAO;
import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.activity.service.DogDaysService;
import eh.base.dao.DoctorDAO;
import eh.bus.constant.WeChatAutoMsgConstant;
import eh.bus.constant.WeChatAutoMsgTplConstant;
import eh.bus.service.common.CurrentUserInfo;
import eh.cdr.dao.DoctorLiveDAO;
import eh.entity.base.Doctor;
import eh.entity.bus.msg.SimpleWxAccount;
import eh.entity.cdr.DoctorLive;
import eh.entity.mpi.Patient;
import eh.entity.wx.WeChatAutoMsg;
import eh.mpi.constant.FollowConstant;
import eh.mpi.dao.PatientDAO;
import eh.mpi.service.follow.FollowPlanTriggerService;
import eh.op.service.WeChatAutoMsgService;
import eh.push.SmsPushService;
import eh.remote.IWXPMServiceInterface;
import eh.utils.LocalStringUtil;
import eh.utils.params.ParamUtils;
import eh.utils.params.ParameterConstant;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;

public class AutoMsgService {
	private static final Log logger = LogFactory.getLog(AutoMsgService.class);

	//扫码非日间手术团队，个人医生推送消息
	private static String patientScanDoctorSuccessReplyText = "我是${docName}，欢迎关注我，有任何问题可随时向我咨询哦。\n<a href=\"{}\">马上咨询医生</a>";
	//扫码日间手术团队推送消息
	private static String patientScanDoctorForDayTimeOperText = "为了您的手术更加顺畅，请先阅读<a href=\"{}\">《邵逸夫医院日间手术中心术前宣教》</a>！\n马上阅读>";
	//未注册用户扫码关注医生
	private static String patientScanDoctorUnregisterMsgText = "我是${docName}，欢迎关注我，请先进行<a href=\"{}\">用户绑定</a>，即可完成关注。关注后可随时向我咨询。<a href=\"{}\">马上咨询医生</a>";
	//用户扫码关注医生-医生报道
	private static String patientScanDoctorReportMsgText = "为了更好地为您提供诊疗服务，${docName}医生需要您进行患者报到。\n\n<a href=\"{}\">进行患者报到>></a>";
	//用户扫码关注-宣教文章
	private static String patientScanDoctorArticleMsgText = "${docName}医生给您发来了一篇文章！\n\n<a href=\"{}\">马上阅读</a>";
	//宣教文章链接
	private static String articlePrefixText = "http://www.ngarihealth.com/html5.php/Index/article_detail/itemId/${articleId}.html";
	//扫码-发送医生直播间链接
	private static String doctorLiveText = "<a href=\"{}\">点此报名</a>参加${docName}医生直播";

	@RpcService
	public void sendAutoMsgByType(String appId,String openId, Integer replyType, Map<String,Object> tplParam, Map<String,String> urlMap){
		try{
			if(StringUtils.isEmpty(appId)|| StringUtils.isEmpty(openId)){
				logger.info("自动回复对象不明确appId="+appId+",openId="+openId+",replyType="+replyType);
				return;
			}

			WeChatAutoMsgService service= AppContextHolder.getBean("eh.weChatAutoMsgService",WeChatAutoMsgService.class);
			WeChatAutoMsg autoMsg=service.getAutoMsgContent(appId,replyType);
			if(autoMsg==null){
				autoMsg=getdefaultAutoMsg(replyType);
			}

			Integer msgType=autoMsg.getMsgType()==null?Integer.valueOf(0):autoMsg.getMsgType();
			String tpl=autoMsg.getMsgContent();
			if(StringUtils.isEmpty(tpl)){
				logger.info("未配置自动回复内容appId="+appId+",replyType="+replyType);
				return;
			}
			String tplStr= LocalStringUtil.processTemplate(tpl,tplParam);

			IWXPMServiceInterface wxpmService = AppContextHolder.getBean("eh.wxPushMessService", IWXPMServiceInterface.class);

			if(WeChatAutoMsgConstant.MSGTYPE_TEXT==msgType.intValue()){
				String result=wxpmService.sendCustomerMsgWithCallbackLink(appId,openId,tplStr,urlMap);
				logger.info("发送文本消息结果=["+appId+"]-["+tplStr+"]-["+result+"]");
			}
			if(WeChatAutoMsgConstant.MSGTYPE_NEWS==msgType.intValue()) {
				String result=wxpmService.sendCustomerNewsMsg(appId,openId,tplStr,urlMap);
				logger.info("发送图文消息结果=["+appId+"]-["+tplStr+"]-["+result+"]");
			}
		}catch (Exception e){
			logger.error("发送自动回复消息异常："+e.getMessage());
		}
	}

	private WeChatAutoMsg getdefaultAutoMsg(Integer replyType){
		String tplContent="";
		switch(replyType.intValue()){
			//扫码关注普通医生
			case WeChatAutoMsgConstant.REPLYTYPE_SUBSCRIBE_ORDINARY:
				tplContent= ParamUtils.getParam(ParameterConstant.KEY_PATIENT_SCAN_DOCTOR_MSG,patientScanDoctorSuccessReplyText);
				break;
			//扫码关注日间手术团队医生
			case WeChatAutoMsgConstant.REPLYTYPE_SUBSCRIBE_DAYTIMEOPER:
				tplContent= ParamUtils.getParam(ParameterConstant.KEY_PATIENT_SCAN_DOCTOR_DAYTIMEOPER_MSG,patientScanDoctorForDayTimeOperText);
				break;
			//注册
			case WeChatAutoMsgConstant.REPLYTYPE_PATIENT_REGISTER:
				tplContent= ParamUtils.getParam(ParameterConstant.KEY_PATIENT_REGISTER_MSG,"");
				break;
			//完善信息
			case WeChatAutoMsgConstant.REPLYTYPE_PATIENT_PERFECT:
				tplContent= ParamUtils.getParam(ParameterConstant.KEY_PATIENT_PERFECT_MSG,"");
				break;
			default:
				logger.error("未找到类型["+replyType+"]的文案");
				break;
		}

		WeChatAutoMsg autoMsg=new WeChatAutoMsg();
		autoMsg.setMsgContent(tplContent);
		autoMsg.setMsgType(WeChatAutoMsgConstant.MSGTYPE_TEXT);
		return autoMsg;
	}


	/**
	 * 扫码关注医生自动推送
	 * @param appId
	 * @param openId
     * @param d
	 *
	 * 2017-4-29 13:39:24 zhangx 健康3.0，将扫码关注流程改造，改造后的流程见文档：
	 * http://zentao.ngarihealth.com:8002/pages/viewpage.action?pageId=8028787
     */
	@RpcService
	public void sendSubscribeAutoMsg(String appId,String openId,String doctorId){
		DoctorDAO docDao=DAOFactory.getDAO(DoctorDAO.class);
        Doctor doctor = docDao.getByDoctorId(Integer.parseInt(doctorId));
		Integer replyType=0;
		Map<String,Object> tplParam=new HashMap<String,Object>();

		if(StringUtils.isEmpty(appId)|| StringUtils.isEmpty(openId)){
			logger.info("自动回复对象不明确appId="+appId+",openId="+openId+",replyType="+replyType);
			return;
		}

		if(null== doctor){
			logger.error("医生["+doctorId+"]相关基础信息未查到");
			return;
		}

		OAuthWeixinMPDAO oAuthWeixinMPDAO = DAOFactory.getDAO(OAuthWeixinMPDAO.class);
		OAuthWeixinMP mpbean = oAuthWeixinMPDAO.getByAppIdAndOpenId(appId, openId);
		if(mpbean==null){
			SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
			smsPushService.pushMsgData2Ons(Integer.parseInt(doctorId), doctor.getOrgan(), "UnRegisteredRelation", "UnRegisteredRelation", null);
		}

		boolean teams=doctor.getTeams()==null ? false:doctor.getTeams().booleanValue();
		int groupType=doctor.getGroupType()==null?0:doctor.getGroupType().intValue();

		String articleId=doctor.getArticleId();
		String articlePrefix= ParamUtils.getParam(ParameterConstant.KEY_ARTICLE_PREFIX,
				articlePrefixText);
		Boolean articleFlag=false;
		String articalUrl=null;//前端日间手术标记，默认为非日间手术

		tplParam.put(WeChatAutoMsgTplConstant.KEY_ARTICLE_ID,articleId);

		if(1 == groupType){
			articleFlag=true;
		}else{
			articleFlag= !StringUtils.isEmpty(articleId) && !StringUtils.isEmpty(articlePrefix);
			articalUrl=LocalStringUtil.processTemplate(articlePrefix,tplParam);
		}

		//查询医生是否开通【报道】功能
		FollowPlanTriggerService followPlanService= AppContextHolder.getBean("eh.followPlanTriggerService",FollowPlanTriggerService.class);
		boolean isReportBoolean=followPlanService.canReport(Integer.valueOf(doctorId), FollowConstant.TRIGGEREVENT_SCANCODE);
		String isReport=isReportBoolean==false?"0":"1";
		String	baodaoTpl= ParamUtils.getParam(ParameterConstant.KEY_PATIENT_SCAN_DOCTOR_REPORT_MSG,
				patientScanDoctorReportMsgText);

		//配置模板变量
		tplParam.put(WeChatAutoMsgTplConstant.KEY_DOC_NAME,doctor.getName());

		//查询运营平台是否配置消息
		replyType=WeChatAutoMsgConstant.REPLYTYPE_SUBSCRIBE_ORDINARY;

		WeChatAutoMsgService service= AppContextHolder.getBean("eh.weChatAutoMsgService",WeChatAutoMsgService.class);
		WeChatAutoMsg autoMsg=service.getAutoMsgContent(appId,replyType);
		//运营平台配置了消息
  		if(autoMsg!=null){
			Map<String, String> callbackParamMap =new HashMap<String, String>();
			callbackParamMap.put("module","register");
			callbackParamMap.put("did",doctorId);
			callbackParamMap.put("isReport",isReport);
			//发送消息
			sendAutoMsgByType(appId,openId,replyType,tplParam,callbackParamMap);
			logger.info("扫码关注医生自动推送-["+appId+"]-["+doctorId+"]运营平台配置了消息");
			/**
			 * zhongzx
			 * 运营平台配置了消息 也发送患者报道推送
			 */
			if(isReportBoolean) {
				//报道开关打开

				Map<String, String> patientReportParamMap=getPatientReportParamMap(doctorId,articleFlag,articalUrl);
				//发送消息
				snedTextMsg(appId,openId,baodaoTpl,tplParam,patientReportParamMap);
				logger.info("运营平台配置消息+医生报道打开自动推送-["+appId+"]-["+openId+"]-["+doctorId+"]");
			}
		}else{
			//运营平台未配置消息

			//未注册
			if(mpbean==null){
				List<Map<String, String>> callbackParamMapList=new ArrayList<Map<String, String>>();

				String	registerTpl= ParamUtils.getParam(ParameterConstant.KEY_PATIENT_SCAN_DOCTOR_UNREGISTER_MSG,
						patientScanDoctorUnregisterMsgText);

				Map<String, String> registerMap =new HashMap<String, String>();
				registerMap.put("module","register");
				registerMap.put("did",doctorId);
				registerMap.put("subscribe", "1");//扫码关注
				registerMap.put("isReport",isReport);
				if(articleFlag){
					registerMap.put("isEduArtical", "1");
				}
				if(!StringUtils.isEmpty(articalUrl)){
					registerMap.put("articalUrl", articalUrl);
				}
				callbackParamMapList.add(registerMap);

				Map<String, String> callbackParamMap =new HashMap<String, String>();
				if(teams){
					callbackParamMap.put("module", "teamDoctIndex");
				}else{
					callbackParamMap.put("module", "singleDoctIndex");
				}
				callbackParamMap.put("did",doctorId);
				callbackParamMapList.add(callbackParamMap);

				//发送消息
				snedTextMsg(appId,openId,registerTpl,tplParam,callbackParamMapList);
				logger.info("扫码关注医生未注册下自动推送-["+appId+"]-["+openId+"]-["+doctorId+"]");
			}else{
				//已注册
				if(isReportBoolean) {
					//报道开关打开

					Map<String, String> callbackParamMap =getPatientReportParamMap(doctorId,articleFlag,articalUrl);
					//发送消息
					snedTextMsg(appId,openId,baodaoTpl,tplParam,callbackParamMap);
					logger.info("扫码关注医生已注册+医生报道打开自动推送-["+appId+"]-["+openId+"]-["+doctorId+"]");
				}

				//2016-11-23 14:18:41 zhangx
				//患者扫医生二维码进入公众号，原关注医生推送消息改为：我是***，欢迎关注我，有任何问题可随时向我咨询哦。马上咨询医生（可点击）
				//点击关注推送，若患者未注册，则跳转到注册页，完善信息后，再跳转到扫码医生主页，若跳过注册页和完善信息页，则跳转到医生主页；若患者已注册，则跳转到医生主页；
				Map<String, String> docIndexParamMap =new HashMap<String, String>();
				String docIndexTpl= ParamUtils.getParam(ParameterConstant.KEY_PATIENT_SCAN_DOCTOR_MSG,
						patientScanDoctorSuccessReplyText);

				//2017-7-14 18:16:11 zhangx 三伏天活动需要将扫码关注后发送的文案最后，马上咨询医生改为[冬病夏治请点击]
				DogDaysService dogDaysService = AppContextHolder.getBean("eh.dogDaysService", DogDaysService.class);
				Boolean dogDaysFlag= dogDaysService.getDogDaysFlag(Integer.valueOf(doctorId));
				if(dogDaysFlag){
					docIndexTpl= ParamUtils.getParam(ParameterConstant.KEY_PATIENT_SCAN_DOCTOR_DOGDAYS_MSG);
				}
				if(teams){
					docIndexParamMap.put("module", "teamDoctIndex");
				}else{
					docIndexParamMap.put("module", "singleDoctIndex");
				}
				docIndexParamMap.put("did",doctorId);
				//发送消息
				snedTextMsg(appId,openId,docIndexTpl,tplParam,docIndexParamMap);
				logger.info("扫码关注医生已注册自动推送-["+appId+"]-["+openId+"]-["+doctorId+"]");
			}
		}

		// 判断医生条件进行日间手术消息推送
		// 是否是日间手术团队，docName为 日间手术中心护理团队（邵逸夫医院）
		if(!isReportBoolean){
			replyType=	WeChatAutoMsgConstant.REPLYTYPE_SUBSCRIBE_DAYTIMEOPER;
			if(1 == groupType){
				Map<String, String> callbackParamMap =new HashMap<String, String>();
				callbackParamMap.put("module","beforeOperation");
				callbackParamMap.put("did",doctorId);
				replyType=WeChatAutoMsgConstant.REPLYTYPE_SUBSCRIBE_DAYTIMEOPER;
				sendAutoMsgByType(appId,openId, replyType,tplParam,callbackParamMap);
			}

			//获取宣教文章id,以及前缀
			if(StringUtils.isEmpty(articleId) || StringUtils.isEmpty(articlePrefix)){
				logger.error("未配置宣教文章获取地址,或医生["+doctorId+"]未配置宣教文章");
			} else {
                //组装参数
                tplParam.put(WeChatAutoMsgTplConstant.KEY_ARTICLE_ID,articleId);
                String articleUrl=LocalStringUtil.processTemplate(articlePrefix,tplParam);
                Map<String, String> callbackParamMap =new HashMap<String, String>();
                callbackParamMap.put("module","docCobpywrite");
                callbackParamMap.put("url",articleUrl);

                String articleTpl= ParamUtils.getParam(ParameterConstant.KEY_PATIENT_SCAN_DOCTOR_ARTICLE_MSG,
                        patientScanDoctorSuccessReplyText);
                snedTextMsg(appId,openId,articleTpl,tplParam,callbackParamMap);
            }
		}
        //医生开启直播期间，患者扫码后自动推送医生直播间链接，点击跳转直播间
        DoctorLiveDAO doctorLiveDAO = DAOFactory.getDAO(DoctorLiveDAO.class);
        DoctorLive doctorLive = doctorLiveDAO.getLivingById(Integer.parseInt(doctorId), new Date());
        if(null != doctorLive){
            String doctorLiveURL = doctorLive.getURL();
            if(doctorLiveURL.isEmpty()){
                logger.info("["+doctorId+"]直播间链接地址为空");
            } else {
                Map<String, String> doctorLiveMap =new HashMap<String, String>();
                doctorLiveMap.put("module","docCobpywrite");
                doctorLiveMap.put("url",doctorLiveURL);

                String doctorLiveTpl= ParamUtils.getParam(ParameterConstant.KEY_DOCTOR_LIVE_MSG,
                        doctorLiveText);
                snedTextMsg(appId,openId,doctorLiveTpl,tplParam,doctorLiveMap);
            }
        }
	}
	private Map<String, String> getPatientReportParamMap(String doctorId,Boolean articleFlag,String articalUrl){
		Map<String, String> patientReportParamMap =new HashMap<String, String>();
		patientReportParamMap.put("module", "register");
		patientReportParamMap.put("did", doctorId);
		patientReportParamMap.put("subscribe", "0");
		patientReportParamMap.put("isReport", "1");
		if(articleFlag){
			patientReportParamMap.put("isEduArtical", "1");
		}
		if(!StringUtils.isEmpty(articalUrl)){
			patientReportParamMap.put("articalUrl", articalUrl);
		}
		return patientReportParamMap;
	}

	/**
	 * 注册成功自动回复
	 * @param appId
	 * @param openId
	 * @param mpiId
     */
	@RpcService
	public void sendRegisterAutoMsg(String appId,String openId,String mpiId){
		PatientDAO patDao=DAOFactory.getDAO(PatientDAO.class);
		Patient pat=patDao.get(mpiId);
		if(null !=pat){
			Integer replyType=WeChatAutoMsgConstant.REPLYTYPE_PATIENT_REGISTER;
			Map<String,Object> tplParam=new HashMap<String,Object>();

			if(!StringUtils.isEmpty(pat.getPatientName())){
				tplParam .put(WeChatAutoMsgTplConstant.KEY_PAT_NAME,pat.getPatientName());
			}

			sendAutoMsgByType(appId,openId, replyType,tplParam,null);
		}
	}

	/**
	 * 完善信息成功自动回复
	 * @param appId
	 * @param openId
	 * @param mpiId
	 */
	@RpcService
	public void sendPerfectAutoMsg(String mpiId){
		String openId = "";
		String appId="";
		SimpleWxAccount simpleWxAccount = CurrentUserInfo.getSimpleWxAccount();
		if (null != simpleWxAccount) {
			openId = simpleWxAccount.getOpenId();
			appId=simpleWxAccount.getAppId();
		}

		PatientDAO patDao=DAOFactory.getDAO(PatientDAO.class);
		Patient pat=patDao.get(mpiId);
		if(null !=pat){
			Integer replyType=WeChatAutoMsgConstant.REPLYTYPE_PATIENT_PERFECT;

			Map<String,Object> tplParam=new HashMap<String,Object>();
			if(!StringUtils.isEmpty(pat.getPatientName())){
				tplParam .put(WeChatAutoMsgTplConstant.KEY_PAT_NAME,pat.getPatientName());
			}
			sendAutoMsgByType(appId,openId, replyType,tplParam,null);
		}
	}


	private void snedTextMsg(String appId,String openId,String tpl,Map<String,Object> tplParam,List<Map<String, String>> urlMapList){
		try{
			if(StringUtils.isEmpty(tpl)){
				logger.info("未配置自动回复内容appId="+appId+",registerTpl="+tpl);
				return;
			}
			String tplStr=LocalStringUtil.processTemplate(tpl,tplParam);
			IWXPMServiceInterface wxpmService = AppContextHolder.getBean("eh.wxPushMessService", IWXPMServiceInterface.class);
			wxpmService.sendCustomerMsgWithListLink(appId,openId,tplStr,urlMapList);
		}catch (Exception e){
			logger.error("发送自动回复消息异常："+e.getMessage());
		}

	}

	private void snedTextMsg(String appId,String openId,String tpl,Map<String,Object> tplParam,Map<String, String> urlMap){
		try{
			if(StringUtils.isEmpty(tpl)){
				logger.info("未配置自动回复内容appId="+appId+",tpl="+tpl);
				return;
			}

			String tplStr=LocalStringUtil.processTemplate(tpl,tplParam);
			IWXPMServiceInterface wxpmService = AppContextHolder.getBean("eh.wxPushMessService", IWXPMServiceInterface.class);
			wxpmService.sendCustomerMsgWithCallbackLink(appId,openId,tplStr,urlMap);
		}catch (Exception e){
			logger.error("发送自动回复消息异常："+e.getMessage());
		}

	}
}
