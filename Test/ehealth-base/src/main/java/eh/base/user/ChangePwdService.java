package eh.base.user;

import com.alibaba.fastjson.JSONObject;
import ctd.account.AccountCenter;
import ctd.account.UserRoleToken;
import ctd.account.user.PasswordUtils;
import ctd.account.user.User;
import ctd.account.user.UserController;
import ctd.controller.exception.ControllerException;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.impl.user.UserDAO;
import ctd.persistence.support.impl.user.UserRoleTokenDAO;
import ctd.util.annotation.RpcService;
import eh.base.dao.ValidateCodeDAO;
import eh.entity.base.ValidateCode;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChangePwdService {
	private static final Logger log = LoggerFactory.getLogger(ChangePwdService.class);

	/**
	 * 重置密码服务	yaozh
	 *
	 * @param phone
	 * @param identify
	 * @param newpwd
	 * @param repwd
	 */
	@RpcService
	public void doChangePwd(String phone, String identify, String newpwd,
							String repwd) {
		if (StringUtils.isEmpty(phone)) {
			throw new DAOException(DAOException.VALUE_NEEDED, "手机号码为空");
		}
		if (StringUtils.isEmpty(identify)) {
			throw new DAOException(DAOException.VALUE_NEEDED, "验证码为空");
		}
		if (StringUtils.isEmpty(newpwd)) {
			throw new DAOException(DAOException.VALUE_NEEDED, "新密码为空");
		}
		if (StringUtils.isEmpty(repwd)) {
			throw new DAOException(DAOException.VALUE_NEEDED, "确认密码为空");
		}
		if (!newpwd.equals(repwd)) {
			throw new DAOException(DAOException.VALIDATE_FALIED, "新密码和确认密码不一致");
		}
		ValidateCodeDAO vcDAO = DAOFactory.getDAO(ValidateCodeDAO.class);
		ValidateCode vCode = vcDAO.getByMobileAndCode(phone, identify);
		if (vCode == null) {
			throw new DAOException(DAOException.VALIDATE_FALIED, "验证码错误");
		}else {
			vcDAO.deleteValidateCodeByValidateId(vCode.getValidateId());
		}
		User user = null;
		try {
			user = AccountCenter.getUser(phone);
		} catch (ControllerException e) {
			throw new DAOException(DAOException.EVAL_FALIED, "获取用户失败");
		}
		if (user == null) {
			throw new DAOException(DAOException.VALIDATE_FALIED, "用户不存在");
		}
		String uid = user.getId();
		log.info("[{}] [doChangePwd] user[{}] newPwd[{}] afterEncoder[{}]", this.getClass().getSimpleName(), JSONObject.toJSONString(user), newpwd, PasswordUtils.encodeFromMD5(newpwd, uid));
		try {
			UserController
					.instance()
					.getUpdater()
					.setProperty(uid, "password",
							PasswordUtils.encodeFromMD5(newpwd, uid));
		} catch (ControllerException e) {
			throw new DAOException(DAOException.EVAL_FALIED, "密码修改失败");
		}

	}


	/**
	 * 平台管理员修改机构管理员密码服务	yaozh
	 *
	 * @param superUserId
	 * @param organUserId
	 * @param newpwd
	 */
	@RpcService
	public void doChangeOrganPwd(String superUserId,String organUserId, String newpwd) {
		if (StringUtils.isEmpty(superUserId)) {
			throw new DAOException(DAOException.VALUE_NEEDED, "平台管理员账户不能为空");
		}
		if (StringUtils.isEmpty(organUserId)) {
			throw new DAOException(DAOException.VALUE_NEEDED, "机构管理员账户不能为空");
		}
		if (StringUtils.isEmpty(newpwd)) {
			throw new DAOException(DAOException.VALUE_NEEDED, "新密码不能为空");
		}
		UserDAO userDao = DAOFactory.getDAO(UserDAO.class);
		if (!userDao.exist(superUserId)) {
			throw new DAOException(DAOException.EVAL_FALIED, "不存在这个平台管理员账户");
		}
		UserRoleTokenDAO tokenDao = DAOFactory
				.getDAO(UserRoleTokenDAO.class);
		Object object = tokenDao.getExist(superUserId,"eh","admin");
		if (object == null) {
			throw new DAOException(DAOException.EVAL_FALIED, "不存在这个平台管理员账户");
		}
		User user = null;
		try {
			user = AccountCenter.getUser(organUserId);
		} catch (ControllerException e) {
			throw new DAOException(DAOException.EVAL_FALIED, "获取用户失败");
		}
		if (user == null) {
			throw new DAOException(DAOException.VALIDATE_FALIED, "不存在这个机构管理员账户");
		}
		String uid = user.getId();
		try {
			UserController
					.instance()
					.getUpdater()
					.setProperty(uid, "password",
							PasswordUtils.encodeFromMD5(newpwd, uid));
		} catch (ControllerException e) {
			throw new DAOException(DAOException.EVAL_FALIED, "密码修改失败");
		}

	}

	/**
	 * 运营平台 修改管理员密码
	 *
	 */
	@RpcService
	public void changeAdminPwd(String oldPwd,String pwd) {
		UserRoleToken urt = UserRoleToken.getCurrent();
		if(urt==null){
			throw new DAOException(DAOException.EVAL_FALIED, "获取登录用户失败");
		}
		if (StringUtils.isEmpty(pwd)) {
			throw new DAOException(DAOException.VALUE_NEEDED, "新密码不能为空");
		}
		User user = null;
		try {
			user = AccountCenter.getUser(urt.getUserId());
		} catch (ControllerException e) {
			throw new DAOException(DAOException.EVAL_FALIED, "获取用户失败");
		}
		if(!this.validatePwd(user,oldPwd)){
			throw new DAOException(DAOException.EVAL_FALIED, "旧密码不正确");
		}



		String uid = user.getId();
		try {
			UserController
					.instance()
					.getUpdater()
					.setProperty(uid, "password",
							PasswordUtils.encodeFromMD5(pwd, uid));
		} catch (ControllerException e) {
			throw new DAOException(DAOException.EVAL_FALIED, "密码修改失败");
		}

	}

	private boolean validatePwd(User user, String pwd) {
		int len = pwd.length();
		if (len == 32) {
			return user.validateMD5Password(pwd);
		} else if (len == 64) {
			return user.validatePassword(pwd);
		} else {
			throw new IllegalArgumentException("pwd format invalid.");
		}
	}


}
