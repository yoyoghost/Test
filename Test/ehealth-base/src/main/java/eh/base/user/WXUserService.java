package eh.base.user;

import com.alibaba.fastjson.JSONObject;
import ctd.account.Client;
import ctd.account.UserRoleToken;
import ctd.account.session.SessionItemManager;
import ctd.account.session.SessionKey;
import ctd.account.user.User;
import ctd.account.user.UserController;
import ctd.account.user.UserRoleTokenEntity;
import ctd.controller.exception.ControllerException;
import ctd.controller.updater.ConfigurableItemUpdater;
import ctd.mvc.alilife.AliUserInfo;
import ctd.mvc.alilife.entity.OauthAliMP;
import ctd.mvc.alilife.entity.OauthAliMPDAO;
import ctd.mvc.controller.support.LogonManager;
import ctd.mvc.controller.util.ClientProvider;
import ctd.mvc.upload.FileMetaRecord;
import ctd.mvc.upload.FileService;
import ctd.mvc.weixin.WXUser;
import ctd.mvc.weixin.WXUserInfo;
import ctd.mvc.weixin.entity.OAuthWeixinMP;
import ctd.mvc.weixin.entity.OAuthWeixinMPDAO;
import ctd.mvc.weixin.exception.WXException;
import ctd.net.broadcast.MQHelper;
import ctd.net.broadcast.Publisher;
import ctd.persistence.DAOFactory;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.persistence.support.impl.user.UserDAO;
import ctd.util.AppContextHolder;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import ctd.util.context.Context;
import ctd.util.event.GlobalEventExecFactory;
import eh.base.constant.SystemConstant;
import eh.base.dao.ValidateCodeDAO;
import eh.bus.constant.ConsultConstant;
import eh.bus.dao.ConsultDAO;
import eh.bus.service.AutoMsgService;
import eh.bus.service.common.ClientPlatformEnum;
import eh.bus.service.common.CurrentUserInfo;
import eh.bus.service.consult.ConsultMessageService;
import eh.bus.service.consult.OnsConfig;
import eh.cdr.constant.OtherdocConstant;
import eh.cdr.dao.CdrOtherdocDAO;
import eh.coupon.constant.CouponConstant;
import eh.coupon.dao.CouponInfoDAO;
import eh.coupon.service.CouponPushService;
import eh.entity.bus.Consult;
import eh.entity.bus.SystemNotificationMsgBody;
import eh.entity.bus.msg.SimpleWxAccount;
import eh.entity.cdr.Otherdoc;
import eh.entity.coupon.CouponInfo;
import eh.entity.mpi.Patient;
import eh.entity.mpi.UserSource;
import eh.mpi.constant.UserSourceConstant;
import eh.mpi.dao.PatientDAO;
import eh.mpi.dao.UserSourceDAO;
import eh.mpi.service.FamilyMemberService;
import eh.mpi.service.PatientService;
import eh.msg.bean.EasemobRegiste;
import eh.util.Easemob;
import eh.util.RetryTaskPro;
import eh.utils.ValidateUtil;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.StatelessSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * Created by sean on 15/12/10.
 */
public class WXUserService {
	private static final Logger logger = LoggerFactory
			.getLogger(WXUserService.class);

	/**
	 * 纳里健康微信2.0注册接口
	 * @param wxUser
	 * @param appId
	 * @param openId
     * @return
     */
	@RpcService
	public UserRoleToken createWXUserAndLogin(final WXUser wxUser,
			final String appId, final String openId) {
		logger.info("WXUser={},appId={}, openId={}",
				JSONUtils.toString(wxUser), appId, openId);

		HibernateStatelessResultAction<HashMap<String,Object>> action = new AbstractHibernateStatelessResultAction<HashMap<String,Object>>() {
			@SuppressWarnings({ "rawtypes", "unchecked" })
			@Override
			public void execute(StatelessSession statelessSession)
					throws Exception {
				HashMap<String,Object> map=new HashMap<String,Object>();

				UserDAO userDAO = DAOFactory.getDAO(UserDAO.class);
				PatientDAO patDao = DAOFactory.getDAO(PatientDAO.class);
				ValidateCodeDAO codeDao = DAOFactory
						.getDAO(ValidateCodeDAO.class);

				// 检验验证码
				codeDao.machValidateCode(wxUser.getMobile(),
						wxUser.getValidateCode());

				// 未实现:更新或插入Patient表
				String sex=StringUtils.isEmpty(wxUser.getGender().get("key"))?"1":wxUser.getGender().get("key");

				Patient p = new Patient();
				p.setPatientName(wxUser.getName());
				p.setPatientSex(sex);
				p.setBirthday(wxUser.getBirthday());
				p.setMobile(wxUser.getMobile());
				p.setIdcard(wxUser.getIdCard());
				p.setHomeArea(wxUser.getHomeArea());

				if(StringUtils.isEmpty(p.getFullHomeArea())){
					p.setFullHomeArea(patDao.getFullHomeArea(p.getHomeArea()));
				}

				Patient pat = patDao.createWXPatientUser(p);

				String rid = "patient";
				String uid = wxUser.getMobile();

				UserRoleTokenEntity ure = new UserRoleTokenEntity();
				ure.setRoleId(rid);
				ure.setUserId(uid);
				ure.setTenantId("eh");
				ure.setManageUnit("eh");

				User u = userDAO.get(uid);

				boolean isNewUser = false;
				ConfigurableItemUpdater updater = (ConfigurableItemUpdater) UserController
						.instance().getUpdater();

				if (u == null) {
					u = new User();
					u.setId(uid);
					u.setName(wxUser.getName());
					u.setCreateDt(new Date());
					u.setLastModify(System.currentTimeMillis());
					u.setPlainPassword(p.getIdcard().substring(
							p.getIdcard().length() - 6));
					u.setStatus("1");

					userDAO.createUser(u, ure);
					isNewUser = true;
				} else {
					List<UserRoleToken> urts = u.findUserRoleTokenByRoleId(rid);
					if (urts.size() > 0) {
						ure = (UserRoleTokenEntity) urts.get(0);
					} else {
						ure = (UserRoleTokenEntity) updater
								.createItem(uid, ure);
					}
				}
				ure.setProperty("patient", pat);
				if (!isNewUser) {
					updater.updateItem(uid, ure);
				}
				map.put("ure",ure);
				saveOrUpdateOauthMp(appId, openId, wxUser, pat, ure);
				setResult(map);
			}

		};

		HibernateSessionTemplate.instance().executeTrans(action);

		HashMap<String,Object> map=action.getResult();
		UserRoleToken ure=(UserRoleToken)map.get("ure");
		// 注册患者环信账户
		if (ure != null) {
			String userName = Easemob.getPatient(ure.getId()) ;
			Easemob.registUser(userName, SystemConstant.EASEMOB_PATIENT_PWD);
		}
		return ure;
	}

	private void saveOrUpdateOauthMp(String appId, String openId, WXUser wxUser, Patient patient, UserRoleToken urt) {
		String os = wxUser.getOs();
		if(StringUtils.isBlank(os)){
			Client client = CurrentUserInfo.getCurrentClient();
			if(client!=null) {
				os = client.getOs();
			} else {
				logger.error("createWXUserAndLogin saveOrUpdateOauthMp os is null! please check!");
			}
		}
		ClientPlatformEnum clientPlatFormEnum = ClientPlatformEnum.fromKey(os);
		switch (clientPlatFormEnum){
			case ALILIFE:
				suAlilife(appId, openId, wxUser, patient, urt);
				break;
			case WEIXIN:
				suWeixin(appId, openId, wxUser, patient, urt);
				break;
			default:
				logger.error("createWXUserAndLogin saveOrUpdateOauthMp unSupport os[{}]", os);
				break;
		}
	}

	private void suWeixin(String appId, String openId, WXUser wxUser, Patient patient, UserRoleToken urt) {
		String mobile = wxUser.getMobile();
		OAuthWeixinMPDAO mpDao = DAOFactory.getDAO(OAuthWeixinMPDAO.class);
		OAuthWeixinMP mp = mpDao.getByAppIdAndOpenId(appId, openId);
		if(mp == null){
			mp = new OAuthWeixinMP();
			mp.setAppId(appId);
			mp.setOpenId(openId);
			mp.setCreateDt(new Date());
		}
		mp.setUserId(mobile);
		mp.setUrt(urt.getId());
		mp.setLastModify(System.currentTimeMillis());
		mp.setSubscribe("1");//注册的时候默认关注公众号

		WXUserInfo userInfo = wxUser.getUserInfo();
		if (userInfo != null) {
			mp.setCity(userInfo.getCity());
			mp.setNation(userInfo.getCountry());
			mp.setProvince(userInfo.getProvince());
			mp.setNickname(userInfo.getNickname());
			mp.setUnitId(userInfo.getUnionid());
			tryLoadAvatarImage(userInfo.getHeadimgurl(), mobile, urt, patient);
		}
		if(mp.getOauthId() > 0){
			mpDao.update(mp);
		}else {
			mpDao.save(mp);
		}
	}

	private void suAlilife(String appId, String openId, WXUser wxUser, Patient patient, UserRoleToken urt) {
		String mobile = wxUser.getMobile();
		OauthAliMPDAO mpDao = DAOFactory.getDAO(OauthAliMPDAO.class);
		OauthAliMP mp = mpDao.getByAppIdAndOpenId(appId, openId);
		if(mp == null){
			mp = new OauthAliMP();
			mp.setAppId(appId);
			mp.setOpenId(openId);
			mp.setCreateDt(new Date());
		}
		mp.setUserId(mobile);
		mp.setUrt(urt.getId());
		mp.setLastModify(System.currentTimeMillis());
		mp.setSubscribe("1");//注册的时候默认关注服务窗

		AliUserInfo userInfo = BeanUtils.map(wxUser.getUserInfo(), AliUserInfo.class);
		if (userInfo != null) {
			mp.setCity(userInfo.getCity());
			mp.setNation(userInfo.getCountry());
			mp.setProvince(userInfo.getProvince());
			mp.setNickname(userInfo.getNickname());
			mp.setUnitId(userInfo.getUnionid());
			mp.setUserType(userInfo.getUserType());
			mp.setIsCertified(userInfo.getIsCertified());
			mp.setIsIdAuth(userInfo.getIsIdAuth());
			mp.setIsCertifyGradeA(userInfo.getIsCertifyGradeA());
			mp.setIsStudent(userInfo.getIsStudent());
			mp.setIsBankAuth(userInfo.getIsBankAuth());
			mp.setIsMobileAuth(userInfo.getIsMobileAuth());
			mp.setUserStatus(userInfo.getUserStatus());
			tryLoadAvatarImage(userInfo.getHeadimgurl(), mobile, urt, patient);
		}
		if(ValidateUtil.notNullAndZeroInteger(mp.getOauthId())){
			mpDao.update(mp);
		}else {
			mpDao.save(mp);
		}
	}

	/**
	 * 纳里健康微信2.6注册接口-不需要身份证
	 * 手机号验证码通过后，进行注册
	 * 1>注册时，发现该手机号已有患者用户>>直接进入
	 * 2>注册时，发现该手机号未有患者用户>>创建患者用户-mpi的身份证是null
	 * @param wxUser
	 * @param appId
	 * @param openId
	 * @return
	 */
	@RpcService
	public UserRoleToken createWXUserAndLogin2(final WXUser wxUser,
											  final String appId, final String openId) {
		logger.info("WXUser={},appId={}, openId={}",
				JSONUtils.toString(wxUser), appId, openId);
		// 校验参数
		validateParameter(wxUser, appId, openId);
		final String rid = "patient";
		final String uid = wxUser.getMobile();
		final Patient patient = createPatient(wxUser);
		HibernateStatelessResultAction<RegisterResultTo> action = new AbstractHibernateStatelessResultAction<RegisterResultTo>() {
			@SuppressWarnings({ "rawtypes", "unchecked" })
			@Override
			public void execute(StatelessSession statelessSession)
					throws Exception {
				RegisterResultTo registerResult = new RegisterResultTo();

				registerResult.setPatient(patient);
				// 创建或更新user、urt
				createOrUpdateUserAndUre(registerResult);
				// 创建或更新映射信息
				saveOrUpdateOauthMp(appId, openId, wxUser, patient, registerResult.getUrt());

				setResult(registerResult);
			}

			private RegisterResultTo createOrUpdateUserAndUre(RegisterResultTo registerResult) throws ControllerException {
				UserDAO userDAO = DAOFactory.getDAO(UserDAO.class);
				User u = userDAO.get(uid);
				UserRoleTokenEntity ure = new UserRoleTokenEntity();
				ure.setRoleId(rid);
				ure.setUserId(uid);
				ure.setTenantId("eh");
				ure.setManageUnit("eh");

				boolean isNewUser = false;
				boolean couponFlag = false;
				if (u == null) {
					u = new User();
					u.setId(uid);
					u.setName(wxUser.getName());
					u.setCreateDt(new Date());
					u.setLastModify(System.currentTimeMillis());
					u.setPlainPassword(SystemConstant.WXUSERPWD);
					u.setStatus("1");

					userDAO.createUser(u, ure);
					isNewUser = true;
					couponFlag=true;
				} else {
					List<UserRoleToken> urts = u.findUserRoleTokenByRoleId(rid);
					if (urts.size() > 0) {
						ure = (UserRoleTokenEntity) urts.get(0);
					} else {
						ConfigurableItemUpdater updater = (ConfigurableItemUpdater) UserController.instance().getUpdater();
						ure = (UserRoleTokenEntity) updater.createItem(uid, ure);
						couponFlag=true;
					}
				}
				registerResult.setUrt(ure);
				registerResult.setNewUser(isNewUser);
				registerResult.setCouponFlag(couponFlag);
				return registerResult;
			}

		};
		HibernateSessionTemplate.instance().executeTrans(action);
		UserRoleToken ure = afterRegisterSuccess(action.getResult(), appId, openId, wxUser, uid);
		logger.info("注册成功，返回ure，进行自动登录"+JSONUtils.toString(ure));
		return ure;
	}

	private Patient createPatient(WXUser wxUser) {
		PatientService patientService= AppContextHolder.getBean("eh.patientService",PatientService.class);
		return patientService.createWXPatientUser2(wxUser);
	}

	private boolean validateParameter(WXUser wxUser, String appId, String openId) {
		// 检验验证码
		ValidateCodeDAO codeDao = DAOFactory.getDAO(ValidateCodeDAO.class);
		return codeDao.machValidateCode(wxUser.getMobile(),wxUser.getValidateCode());
	}

	private UserRoleToken afterRegisterSuccess(RegisterResultTo registResult, String appId, String openId, WXUser wxUser, String uid) {
		UserRoleTokenEntity ure = (UserRoleTokenEntity) registResult.getUrt();
		Patient pat = registResult.getPatient();

		// 注册患者环信账户
		registerEaseMob(ure);

		//新创建的创建患者用户-mpi的身份证是null

		if(StringUtils.isEmpty(pat.getIdcard())){
			saveWXUserSource(wxUser,pat.getMpiId(),appId,openId);
		}

		//wx2.7 更新缓存问题,Ure用于登录并注册必须从缓存AccountCenter中获取
		//7617 【咨询】未完善信息时发起咨询，患者端头像显示不正确
		if (ure!=null && !registResult.isNewUser()) {
			try{
				ure.setProperty("patient", pat);
				ConfigurableItemUpdater updater = (ConfigurableItemUpdater) UserController.instance().getUpdater();
				updater.updateItem(uid, ure);
			}catch (Exception e){
				logger.error("注册更新用户缓存失败:"+e.getMessage());
			}
		}
		// 新用户注册、登录发放优惠券
		releaseCouponForNewUser(registResult.isCouponFlag(), ure);

		// 注册成功后自动发送消息，根据运营平台配置
		autoSendMsgWhenSuccessRegister(appId, openId, pat.getMpiId());

		// 创建用户的时候，添加自己为就诊人
		addSelfAsFamilyMember(pat.getMpiId());
		return ure;
	}

	private void addSelfAsFamilyMember(String mpiId) {
		FamilyMemberService familyMemberService = AppContextHolder.getBean("eh.familyMemberService", FamilyMemberService.class);
		familyMemberService.saveSelf(mpiId);
	}

	private void autoSendMsgWhenSuccessRegister(String appId, String openId, String mpiId) {
		try{
			AutoMsgService autoMsgService = AppContextHolder.getBean("eh.autoMsgService", AutoMsgService.class);
			autoMsgService.sendRegisterAutoMsg(appId,openId,mpiId);
		}catch (Exception e){
			logger.error("注册自动发送消息"+e.getMessage());
		}
	}

	private void releaseCouponForNewUser(boolean isCouponFlag, UserRoleTokenEntity ure) {
		CouponPushService couponService = new CouponPushService();
		CouponInfoDAO couponInfoDAO = DAOFactory.getDAO(CouponInfoDAO.class);
		if(isCouponFlag && ure!=null){
			//wx2.7 注册发放优惠劵
			couponService.sendRegisteCouponMsg(ure.getId(),"微信端注册新用户发送优惠劵");
		}
		if(ure!=null){
			//wx2.9版本,添加就诊人,系统给就诊人注册.就诊人第一次登录发放优惠劵 @cuill
			List<CouponInfo> couponInfoList = couponInfoDAO.findCouponInfoByUrtAndServiceType(ure.getId(), CouponConstant.COUPON_SERVICETYPE_REGIST);
			if (ObjectUtils.isEmpty(couponInfoList) && couponInfoList.size() == 0) {
				couponService.sendRegisteCouponMsg(ure.getId(),"微信新用户端登录发送优惠劵");
			}
		}
	}

	private void registerEaseMob(UserRoleToken ure) {
		if (ure != null) {
			String userName = Easemob.getPatient(ure.getId()) ;
			Easemob.registUser(userName, SystemConstant.EASEMOB_PATIENT_PWD);
		}
	}

	private void sendToMq(String userName) {
        final EasemobRegiste register = new EasemobRegiste(userName,SystemConstant.EASEMOB_PATIENT_PWD);
		final Publisher publisher = MQHelper.getMqPublisher();
		new RetryTaskPro(2,3) {
			@Override
			protected Object action() {
                publisher.publish(OnsConfig.easemobTopic, register);
				return 1;
			}
		}.retry();
	}

	private void saveWXUserSource(WXUser wxUser,String mpi,String appId,String openId){
		//邀请码不为null，则为扫码注册
		if(wxUser.getInvitationCode()!=null){
			wxUser.setRoad(UserSourceConstant.ROAD_SCAN);
		}

		UserSource source=new UserSource();
		source.setAppId(appId);
		source.setOpenId(openId);
		source.setInvitationCode(wxUser.getInvitationCode());
		source.setRoad(wxUser.getRoad()==null?UserSourceConstant.ROAD_DIRECT:wxUser.getRoad());
		source.setMpiId(mpi);
		source.setRemindStatus(UserSourceConstant.REMIND_STATUS_UNREMIND);
		source.setSource(UserSourceConstant.SOURCE_WX);
		source.setCreateDate(new Date());

		Client client = SessionItemManager.instance().get(SessionKey.of(Context.CLIENT).<Client>deviceSupport(true));
		source.setClientId(client==null?null:client.getId());

		UserSourceDAO sourceDAO=DAOFactory.getDAO(UserSourceDAO.class);
		sourceDAO.save(source);
	}

	/**
	 * 纳里健康微信2.9过后,患者增加就诊人(家庭成员),会验证就诊人是否注册过
	 * 如果没有注册平台会给就诊人注册,儿童会验监护人是否注册过.
	 * @param patient 患者基本信息
	 * @return
	 * @author cuill
	 * @date  2017/3/22
	 */
	public void createWXUserForFamilyMember(final Patient patient) {
		final String roleId = "patient";
		final String userId = patient.getMobile();
		final ConfigurableItemUpdater updater = (ConfigurableItemUpdater) UserController
				.instance().getUpdater();
		HibernateStatelessResultAction<HashMap<String, Object>> action = new AbstractHibernateStatelessResultAction<HashMap<String, Object>>() {
			@SuppressWarnings({"rawtypes", "unchecked"})
			@Override
			public void execute(StatelessSession statelessSession)
					throws Exception {
				HashMap<String, Object> map = new HashMap<String, Object>();
				UserDAO userDAO = DAOFactory.getDAO(UserDAO.class);
				User user = userDAO.get(userId);
				UserRoleTokenEntity userRoleTokenEntity = new UserRoleTokenEntity();
				userRoleTokenEntity.setRoleId(roleId);
				userRoleTokenEntity.setUserId(userId);
				userRoleTokenEntity.setTenantId("eh");
				userRoleTokenEntity.setManageUnit("eh");
				Boolean isNewUser = false;
				if (user == null) {
					user = new User();
					user.setId(userId);
					user.setName(patient.getPatientName());
					user.setCreateDt(new Date());
					user.setLastModify(System.currentTimeMillis());
					user.setPlainPassword(SystemConstant.WXUSERPWD);
					user.setStatus("1");
					//在base_user和base_userroles表里面插入数据
					userDAO.createUser(user, userRoleTokenEntity);
					isNewUser = true;
				} else {
					List<UserRoleToken> urts = user.findUserRoleTokenByRoleId(roleId);
					if (urts.size() > 0) {
						userRoleTokenEntity = (UserRoleTokenEntity) urts.get(0);
					} else {
						userRoleTokenEntity = (UserRoleTokenEntity) updater
								.createItem(userId, userRoleTokenEntity);
					}
				}
				map.put("ure", userRoleTokenEntity);
				map.put("isNewUser", isNewUser);
				setResult(map);
			}
		};
		HibernateSessionTemplate.instance().executeTrans(action);
		HashMap<String, Object> map = action.getResult();
		UserSevice userService = AppContextHolder.getBean("eh.userSevice", UserSevice.class);
		UserRoleTokenEntity userRoleTokenEntity = userService.getAccountCenterUre(userId, roleId);
		Boolean isNewUser = (Boolean) map.get("isNewUser");
		if (userRoleTokenEntity != null && !isNewUser) {
			try {
				updater.updateItem(userId, userRoleTokenEntity);
			} catch (Exception e) {
				logger.error("注册更新用户缓存失败:" + e.getMessage());
			}
		}
		logger.info("就诊人注册成功,返回userRoleTokenEntity为: " + JSONUtils.toString(userRoleTokenEntity));
	}

	/**
	 * 完善患者信息
	 * 1>身份证不存在>>更新mpi数据，绑定患者用户
	 * 2>身份证存在且未绑定患者用户>>该身份证已被使用，若有问题，请联系客服400-116-5175。
	 * 3>身份证存在且绑定另一个用户名>>提示：该身份证已被尾号4567的用户注册，若有问题，请联系客服400-116-5175。
	 * @param p
	 * @return
	 */
	@RpcService
	public Patient perfectUserInfo(Patient p,List<Otherdoc> cdrOtherdocs) throws Exception{
		logger.info("完善患者信息：patient={},cdrOtherdocs={}",
				JSONUtils.toString(p), cdrOtherdocs);

		Patient pat;
		if(cdrOtherdocs==null){
			cdrOtherdocs=new ArrayList<Otherdoc>();
		}

		PatientService patService= AppContextHolder.getBean("eh.patientService",PatientService.class);
		pat=patService.perfectPatientUserInfo(p);


		//上传电子病历 yuy
        //设置病历图片默认数据
        if(CollectionUtils.isNotEmpty(cdrOtherdocs) && StringUtils.isNotEmpty(p.getMpiId())) {
            logger.info("perfectUserInfo cdrOtherdocs size : "+cdrOtherdocs.size());
            for (Otherdoc otherdoc : cdrOtherdocs) {
                if(StringUtils.isEmpty(otherdoc.getDocType())) {
                    otherdoc.setDocType("9"); //对应字典 eh\cdr\dictionary\DocType.dic
                }
                otherdoc.setDocName(otherdoc.getDocType());
                otherdoc.setDocFormat("13"); //eh\cdr\dictionary\DocFormat.dic
                otherdoc.setMpiid(p.getMpiId());
            }

            CdrOtherdocDAO cdrOtherdocDAO = DAOFactory.getDAO(CdrOtherdocDAO.class);
            cdrOtherdocDAO.saveOtherDocList(OtherdocConstant.CLINIC_TYPE_UPLOAD_PATIENT,0,cdrOtherdocs);
        }

		//2017-2-22 12:05:34 zhangx  wx2.8 完善信息，根据运营平台配置
		try{
			AutoMsgService autoMsgService = AppContextHolder.getBean("eh.autoMsgService", AutoMsgService.class);
			autoMsgService.sendPerfectAutoMsg(p.getMpiId());
		}catch (Exception e){
			logger.error("完善信息自动发送消息："+e.getMessage());
		}

		return pat;
	}

    /**
     * 完善信息扩展方法，
     * @param p
     * @param cdrOtherdocs
     * @param consultId
     * @return
     * @throws Exception
     */
    @RpcService
    public Patient perfectUserInfoExt(Patient p,List<Otherdoc> cdrOtherdocs,Integer consultId) throws Exception{
        Patient pat = perfectUserInfo(p,cdrOtherdocs);
		sendPerfectUserInfoMessage(consultId,p.getMpiId());
        return pat;
    }

	/**
	 * 用于判断用户是否已经完善信息，如完善则发送系统消息提醒
	 */
	@RpcService
	public Boolean judgePerfectUserInfo(Integer consultId, String mpiId){
		Boolean result = sendPerfectUserInfoMessage(consultId, mpiId);
		return result;
	}

	/**
	 * 发送完善信息系统消息
	 * @param consultId
     */
	public Boolean sendPerfectUserInfoMessage(Integer consultId, String mpiId){
		Boolean result = false;
		if(consultId != null){
			ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
			Consult consult = consultDAO.getById(consultId);
			//咨询只有在线续方才需要发消息
			if(consult != null && ConsultConstant.CONSULT_TYPE_RECIPE.equals(consult.getRequestMode())){
				if(consult.getMpiid().equals(mpiId)){
					String notificationText = "您已完善信息";
					SystemNotificationMsgBody msgObj = new SystemNotificationMsgBody();
					msgObj.setType(ConsultConstant.SYSTEM_MSG_TYPE_WITHOUT_LINK);
					msgObj.setText(notificationText);
					msgObj.setUrl(null);

					Date sendTime = new Date();
					// 插入系统通知消息到消息表
					ConsultMessageService msgService = AppContextHolder.getBean("eh.consultMessageService", ConsultMessageService.class);
					msgService.handleSystemNotificationMessage(consultId, msgObj, sendTime, consult.getRequestMode());
					result = true;
				}
			}
		}
		logger.info("发送完善信息系统消息: result={}",result);
		return result;
	}



	/**
	 * 微信解绑
	 * @author zhangx
	 * @throws ControllerException
	 * @date 2016-4-6 上午10:08:47
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@RpcService
	public void delWXUserAndLogout(String uid,Integer oauthId) throws ControllerException {

		UserRoleToken urt = UserRoleToken.getCurrent();
	    if (urt == null) {
	      throw new WXException("user not login.");
	    }
		SimpleWxAccount simpleWxAccount = CurrentUserInfo.getSimpleWxAccount();
		if(simpleWxAccount != null){
			Integer clientId = SessionItemManager.instance().checkClientAndGet();
			ClientProvider clientProvider = LogonManager.instance().getClientProvider();
			if(clientProvider != null){
				clientProvider.leave(clientId);
			}
		}

		SessionItemManager.instance().invalidate(true);
	}


	@RpcService
	public  UserRoleTokenEntity  getAccountCenterUre(String userId,String roles){
		UserSevice userService= AppContextHolder.getBean("eh.userSevice",UserSevice.class);
		return userService.getAccountCenterUre(userId,roles);
	}

	private void tryLoadAvatarImage(final String url, final String uid,
			final UserRoleToken urt,final Patient patient) {

        if(StringUtils.isEmpty(url)){
            logger.error("tryLoadAvatarImage url is empty!");
            return;
        }

		//2016-12-6 09:24:11 zhangx wx2.6 绑定原来的用户信息，头像不做改动
		if(patient.getPhoto()!=null){
            logger.error("tryLoadAvatarImage patient.getPhoto is not null, uid[{}], patient[{}]", uid, JSONObject.toJSONString(patient));
            return;
        }
		//2017-1-12 16:16:53 zhangx wx2.7 将上传头像改成从网络流上获取
		GlobalEventExecFactory.instance().getExecutor().submit(new Runnable() {
			@Override
			public void run() {
				try	{

					URL urlObject = new URL(url);
					URLConnection connection = urlObject.openConnection();
					InputStream is = connection.getInputStream();
					String contentType=connection.getContentType();
					int size = connection.getContentLength();

					String fileName=uid + ".jpg";
					Date now = new Date();
					FileMetaRecord meta = new FileMetaRecord();
					meta.setFileName(fileName);
					meta.setCatalog("patient-avatar");
					meta.setTenantId(urt.getTenantId());
					meta.setMode(31);//31是未登录可用,mode=0需要验证
					meta.setManageUnit(urt.getManageUnit());
					meta.setContentType(contentType);
					meta.setFileSize(size);
					meta.setOwner(urt.getUserId());
					meta.setLastModify(now);
					meta.setUploadTime(now);

					String mpiId=patient.getMpiId();
					meta.setProperty("mpiId",patient.getMpiId());

					logger.info("upload weixin avatar image start ...... ");
					FileService.instance().upload(meta, is);
					logger.info("upload weixin avatar image end ...... ");

					logger.info(
							"user[{}] avatar image[{}] loaded from Weixin.",
							urt.getUserId(), meta.getFileId());
				} catch (Exception e) {
					logger.error(
							"user[{}] avatar image load from Weixin falied.", e);
				}
			}
		});


	}
}
