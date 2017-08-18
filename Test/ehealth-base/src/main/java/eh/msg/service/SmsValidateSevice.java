package eh.msg.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.impl.user.UserRoleTokenDAO;
import ctd.util.annotation.RpcService;
import eh.base.constant.SmsConstant;
import eh.base.dao.ValidateCodeDAO;
import eh.util.AlidayuSms;
import eh.util.SendTemplateSMS;

import java.util.HashMap;
import java.util.concurrent.ThreadLocalRandom;

public class SmsValidateSevice {

	// 短信验证
	private static final String VALIDATE_MSG_NO = "16843";

	/**
	 * 发送短信验证模板
	 * 
	 * @param mobile
	 * @return
	 */
	@RpcService
	public String sendValidateSmsOld(String mobile) {
		SendTemplateSMS sms = new SendTemplateSMS();
		int n4 = ThreadLocalRandom.current().nextInt(1000, 9999);

		sms.SendSMS(mobile, VALIDATE_MSG_NO, new String[] { n4 + "", "5" });
		return String.valueOf(n4 + "");
	}

	/**
	 * 发送短信验证模板
	 * 
	 * @param mobile
	 * @return
	 */
	@RpcService
	public String sendValidateSms(String mobile, String roleId) {
		int n4 = ThreadLocalRandom.current().nextInt(1000, 9999);

		sendValidateCodeByRole(mobile, n4 + "", roleId);
		return String.valueOf(n4 + "");
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
		ValidateCodeDAO validateCodeDAO = DAOFactory.getDAO(ValidateCodeDAO.class);
		validateCodeDAO.sendValidateCodeByRole(mobile,validatecode,roleId);
	}

	/**
	 * 发送短信验证模板--重置密码
	 * 
	 * @author hyj
	 * @param mobile
	 * @param roleId
	 * @return
	 */
	@RpcService
	public String sendValidateSmsForResetPassword(String mobile, String roleId) {
		UserRoleTokenDAO tokenDao = DAOFactory.getDAO(UserRoleTokenDAO.class);
		Object object = tokenDao.getExist(mobile, "eh", roleId);
		if (object == null) {
			throw new DAOException(609, "您的账户未注册");
		}
		int n4 = ThreadLocalRandom.current().nextInt(1000, 9999);

		String validatecode = n4 + "，请在5分钟内正确输入。";
		HashMap<String, String> smsParam = new HashMap<String, String>();
		smsParam.put("code", validatecode);

		AlidayuSms.sendSms(mobile, SmsConstant.ALIDAYU_PHONECODE, smsParam);

		return String.valueOf(n4 + "");
	}

	

}
