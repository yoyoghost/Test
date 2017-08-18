package eh.base.dao;

import com.google.common.collect.Maps;
import ctd.account.Client;
import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.impl.user.UserRoleTokenDAO;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.bus.constant.OrganConstant;
import eh.bus.service.UnLoginSevice;
import eh.bus.service.common.ClientPlatformEnum;
import eh.bus.service.common.CurrentUserInfo;
import eh.entity.base.Doctor;
import eh.entity.base.Organ;
import eh.entity.base.ValidateCode;
import eh.entity.bus.msg.SimpleThird;
import eh.entity.bus.msg.SimpleWxAccount;
import eh.entity.msg.SmsInfo;
import eh.push.SmsPushService;
import org.apache.commons.lang3.StringUtils;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public abstract class ValidateCodeDAO extends
		HibernateSupportDelegateDAO<ValidateCode> {

	public ValidateCodeDAO() {
		super();
		this.setEntityName(ValidateCode.class.getName());
		this.setKeyField("validateId");
	}

	@RpcService
	@DAOMethod(sql = "from ValidateCode where mobile=:mobile and TIMESTAMPDIFF(MINUTE,requestDt,current_timestamp())<=5")
	public abstract ValidateCode getByMobile(@DAOParam("mobile") String mobile);

	@RpcService
	@DAOMethod(sql = "from ValidateCode where mobile=:mobile and TIMESTAMPDIFF(MINUTE,requestDt,current_timestamp())<=5 and validateCode=:validateCode")
	public abstract ValidateCode getByMobileAndCode(
			@DAOParam("mobile") String mobile,
			@DAOParam("validateCode") String validateCode);

	@RpcService
	@DAOMethod
	public abstract void deleteValidateCodeByValidateId(int validateId);

	/**
	 * [忘记密码]发送验证码
	 * 
	 * @param mobile
	 * @param roleId
	 * @return
	 */
	@RpcService
	public String sendValidateCode(String mobile, String roleId) {
		// 判断账户是否存在
		UserRoleTokenDAO tokenDao = DAOFactory.getDAO(UserRoleTokenDAO.class);
		DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);

		Object object = null;

		// 判断医生角色是否存在
		if (roleId.trim().equals("doctor")) {
			Doctor d = doctorDAO.getByMobile(mobile);
			if (d == null) {
				throw new DAOException(609, "您的账户未注册");
			}
			OrganDAO organdao = DAOFactory.getDAO(OrganDAO.class);
			Organ o = organdao.getByOrganId(d.getOrgan());

			object = tokenDao.getExist(mobile, o.getManageUnit(), roleId);
		}

		// 判断患者角色是否存在
		if (roleId.trim().equals("patient")) {
			object = tokenDao.getExist(mobile, "eh", roleId);
			if(object==null) {
				UnLoginSevice unLoginSevice = AppContextHolder.getBean("unLoginSevice", UnLoginSevice.class);
				object = unLoginSevice.createPatientForRegisteredUser(mobile);
			}
		}

		if (object == null) {
			throw new DAOException(609, "您的账户未注册");
		}

		return sendVcodeNoUser(mobile,roleId);
	}
	@RpcService
	public String sendVcodeNoUser(String mobile, String roleId){
		// 查询该用户是否有有效验证码
		ValidateCode v = this.getByMobile(mobile);
		if (v == null) {
			// 发送新的短信验证码
			int n4 = ThreadLocalRandom.current().nextInt(1000, 9999);

			sendValidateCodeByRole(mobile, n4 + "", roleId);

			// 将验证码新增到数据库
			ValidateCode validatecode = new ValidateCode();
			validatecode.setMobile(mobile);
			validatecode.setValidateCode(String.valueOf(n4 + ""));
			validatecode.setRequestDt(new Date());
			save(validatecode);
			return String.valueOf(n4 + "");

		} else {
			sendValidateCodeByRole(mobile, v.getValidateCode(), roleId);
			return v.getValidateCode();
		}
	}

	/**
	 * 医生端/患者端短信验证</br>
	 * 
	 * 【纳里健康】您好，我是健康助手小纳，欢迎使用纳里医生，您的验证码是{1}，请在{2}分钟内正确输入。</br>
	 * 【纳里健康】您好，我是健康助手小纳，欢迎使用纳里健康，您的验证码是{1}，请在{2}分钟内正确输入。</br>
	 * 
	 * @author zsq
	 * @param mobile
	 * @param roleId
	 * @return
	 */
	@RpcService
	public void sendValidateCodeByRole(String mobile, String validatecode,
			String roleId) {
//		String appName = "纳里健康";
//		// 发送给医生端
//		if (roleId.equals("doctor")) {
//			appName = "纳里医生";
//		}
//		validatecode += "，请在5分钟内正确输入。";
//
//		HashMap<String, String> smsParam = new HashMap<String, String>();
//		smsParam.put("app", appName);
//		smsParam.put("sms", validatecode);

//		AlidayuSms.sendSms(mobile, SmsConstant.ALIDAYU_CAPTCHA, smsParam);


		SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
		SmsInfo smsInfo = new SmsInfo();
		smsInfo.setBusId(0);
		smsInfo.setOrganId(0);
		smsInfo.setBusType("VCode");
		smsInfo.setSmsType("");
		smsInfo.setClientId(0);
		smsInfo.setExtendValue(roleId+"|"+validatecode+"|"+mobile);
		smsPushService.pushMsgData2OnsExtendValue(smsInfo);
	}

	/**
	 * IOS原生测试 固定验证码为8888
	 * 
	 * @param mobile
	 * @param roleId
	 * @return
	 */
	@RpcService
	public String sendValidateCodeTest(String mobile, String roleId) {
		// 判断账户是否存在
		UserRoleTokenDAO tokenDao = DAOFactory.getDAO(UserRoleTokenDAO.class);
		DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);

		Object object = null;

		// 判断医生角色是否存在
		if (roleId.trim().equals("doctor")) {
			Doctor d = doctorDAO.getByMobile(mobile);
			if (d == null) {
				throw new DAOException(609, "您的账户未注册");
			}
			OrganDAO organdao = DAOFactory.getDAO(OrganDAO.class);
			Organ o = organdao.getByOrganId(d.getOrgan());

			object = tokenDao.getExist(mobile, o.getManageUnit(), roleId);
		}

		// 判断患者角色是否存在
		if (roleId.trim().equals("patient")) {
			object = tokenDao.getExist(mobile, "eh", roleId);
		}

		if (object == null) {
			throw new DAOException(609, "您的账户未注册");
		}

		// 查询该用户是否有有效验证码
		ValidateCode v = this.getByMobile(mobile);
		if (v == null) {
			// 将验证码新增到数据库
			ValidateCode validatecode = new ValidateCode();
			validatecode.setMobile(mobile);
			validatecode.setValidateCode("8888");
			validatecode.setRequestDt(new Date());
			save(validatecode);
			return String.valueOf("8888");

		} else {
			v.setValidateCode("8888");
			update(v);
			return String.valueOf("8888");
		}
	}

	/**
	 * 注册发送验证码(医生)
	 */
	@RpcService
	public String sendValidateCodeToRegister(String mobile) {
		return sendValidateCodeToRoles(mobile, "doctor");
	}
	
	/**
	 * 注册发送验证码(患者端)
	 */
	@RpcService
	public String sendValidateCodeToPatient(String mobile) {
		return sendValidateCodeToRoles(mobile, "patient");
	}

	/**
	 * 根据手机号发送验证码
	 * 
	 * @author zhangx
	 * @date 2016-2-18 下午3:26:25
	 * @param mobile
	 *            手机号
	 * @param role
	 *            patient患者;doctor医生
	 * @return
	 */
	public String sendValidateCodeToRoles(String mobile, String role) {
		ValidateCode v = this.getByMobile(mobile);
		if (v == null) {
			// 发送新的短信验证码
			int n4 = ThreadLocalRandom.current().nextInt(1000, 9999);

			sendValidateCodeByRole(mobile, n4 + "", role);
			// 将验证码新增到数据库
			ValidateCode validatecode = new ValidateCode();
			validatecode.setMobile(mobile);
			validatecode.setValidateCode(String.valueOf(n4 + ""));
			validatecode.setRequestDt(new Date());
			save(validatecode);
			return String.valueOf(n4 + "");

		} else {
			sendValidateCodeByRole(mobile, v.getValidateCode(), role);
			return v.getValidateCode();
		}
	}

	/**
	 * 根据手机号发送验证码
	 *
	 * @author zhangx
	 * @date 2016-2-18 下午3:26:25
	 * @param mobile
	 *            手机号
	 * @param role
	 *            patient患者;doctor医生
	 * @return
	 */
	public String sendRegisterValidateCodeToRoles(String mobile, String role) {
		ValidateCode v = this.getByMobile(mobile);
		if (v == null) {
			// 发送新的短信验证码
			int n4 = ThreadLocalRandom.current().nextInt(1000, 9999);

			sendRegisterValidateCode(mobile, n4 + "", role);
			// 将验证码新增到数据库
			ValidateCode validatecode = new ValidateCode();
			validatecode.setMobile(mobile);
			validatecode.setValidateCode(String.valueOf(n4 + ""));
			validatecode.setRequestDt(new Date());
			save(validatecode);
			return String.valueOf(n4 + "");

		} else {
			sendRegisterValidateCode(mobile, v.getValidateCode(), role);
			return v.getValidateCode();
		}
	}

	@RpcService
	public void sendRegisterValidateCode(String mobile,String validatecode, String roleId) {
		String wxTitle="";
		String appId = "";
		String organIdStr=String.valueOf(OrganConstant.ORGAN_NGARI);

		Client client = CurrentUserInfo.getCurrentClient();
		Map<String,String> wxAppProperties = CurrentUserInfo.getCurrentWxProperties();

		SimpleWxAccount simple=CurrentUserInfo.getSimpleWxAccount();
		if(simple!=null){
			appId=simple.getAppId();
			ClientPlatformEnum clientPlatformEnum = ClientPlatformEnum.fromKey(client.getOs());
			if (ClientPlatformEnum.WX_WEB.equals(clientPlatformEnum)) {
				SimpleThird third = (SimpleThird) simple;
				appId=third.getAppkey();
			}
		}

		if(wxAppProperties != null){
			wxTitle=StringUtils.isEmpty(wxAppProperties.get("wxTitle"))?"":wxAppProperties.get("wxTitle");
			organIdStr=StringUtils.isEmpty(wxAppProperties.get("organId"))?
					String.valueOf(OrganConstant.ORGAN_NGARI):wxAppProperties.get("organId");
		}


		Integer clientId=Integer.valueOf(0);
		if(client!=null && client.getId()!=null){
			clientId=client.getId();
		}

		Map<String,String> extendValue= Maps.newHashMap();
		extendValue.put("roleId",roleId);
		extendValue.put("validatecode",validatecode);
		extendValue.put("mobile",mobile);
		extendValue.put("title",wxTitle);
		extendValue.put("appId",appId);

		SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
		SmsInfo smsInfo = new SmsInfo();
		smsInfo.setBusId(0);
		smsInfo.setOrganId(Integer.valueOf(organIdStr));
		smsInfo.setBusType("RegisterValidateCode");
		smsInfo.setSmsType("RegisterValidateCode");
		smsInfo.setClientId(clientId);
		smsInfo.setExtendValue(JSONUtils.toString(extendValue));
		smsPushService.pushMsgData2OnsExtendValue(smsInfo);
	}

	/**
	 * 验证码校验
	 * 
	 * @author luf
	 * @param phone
	 *            手机号
	 * @param identify
	 *            验证码
	 * @return Boolean
	 */
	@RpcService
	public Boolean machValidateCode(String phone, String identify) {
		if (StringUtils.isEmpty(phone)) {
			throw new DAOException(DAOException.VALUE_NEEDED, "手机号码为空");
		}
		if (StringUtils.isEmpty(identify)) {
			throw new DAOException(DAOException.VALUE_NEEDED, "验证码为空");
		}

		ValidateCode vCode = this.getByMobileAndCode(phone, identify);
		if (vCode == null) {
			throw new DAOException(DAOException.VALIDATE_FALIED, "验证码错误");
		} else {
			this.deleteValidateCodeByValidateId(vCode.getValidateId());
			return true;
		}
	}

	/**
	 * 网站修改密码发送验证码
	 * @param mobile
	 * @param roleId
     */
	@RpcService
	public void sendValidateCodeForWeb(String mobile, String roleId){
		this.sendValidateCode(mobile,roleId);
	}
}
