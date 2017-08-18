package eh.base.user;

import ctd.account.AccountCenter;
import ctd.account.UserRoleToken;
import ctd.account.user.User;
import ctd.account.user.UserController;
import ctd.account.user.UserRoleTokenEntity;
import ctd.controller.exception.ControllerException;
import ctd.controller.updater.ConfigurableItemUpdater;
import ctd.dictionary.DictionaryController;
import ctd.dictionary.DictionaryItem;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.persistence.support.impl.user.UserDAO;
import ctd.persistence.support.impl.user.UserRoleTokenDAO;
import ctd.util.annotation.RpcService;
import eh.base.constant.SystemConstant;
import eh.base.dao.OrganDAO;
import eh.entity.base.Organ;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class AdminUserSevice {

	public static final Logger log = Logger.getLogger(AdminUserSevice.class);
	
	private String adminRoleId = "admin";

	/**
	 * 创建管理员账户
	 * 
	 * @author ZX
	 * @date 2015-5-27 上午10:59:00
	 * @param patient
	 * @param password
	 * @return
	 * @throws ControllerException
	 * @throws DAOException
	 */
	@RpcService
	@SuppressWarnings("unchecked")
	public String createAdminUser(final String userId, final String name,
			final String password, final int organId)
			throws ControllerException {

		if (StringUtils.isEmpty(userId)) {
			throw new DAOException(DAOException.VALUE_NEEDED, "登录账户不能为空");
		}

		if (StringUtils.isEmpty(name)) {
			throw new DAOException(DAOException.VALUE_NEEDED, "姓名不能为空");
		}

		if (StringUtils.isEmpty(password)) {
			throw new DAOException(DAOException.VALUE_NEEDED, "密码不能为空");
		}

		final OrganDAO organDao = DAOFactory.getDAO(OrganDAO.class);

		if (!organDao.exist(organId)) {
			throw new DAOException(602, "不存在这个机构");
		}

		AbstractHibernateStatelessResultAction<String> action = new AbstractHibernateStatelessResultAction<String>() {
			@Override
			public void execute(StatelessSession ss) throws Exception {

				UserDAO userDao = DAOFactory.getDAO(UserDAO.class);
				UserRoleTokenDAO tokenDao = DAOFactory
						.getDAO(UserRoleTokenDAO.class);

				// 获取管理员管理机构
				String ManageUnit = organDao.getByOrganId(organId)
						.getManageUnit();

				User user = new User();
				user.setId(userId);
				user.setPlainPassword(password);
				user.setName(name);
				user.setCreateDt(new Date());
				user.setStatus("1");

				UserRoleTokenEntity urt = new UserRoleTokenEntity();
				urt.setUserId(user.getId());
				urt.setRoleId("admin");
				urt.setTenantId("eh");
				urt.setManageUnit(ManageUnit);

				// user表中不存在记录
				if (!userDao.exist(userId)) {

					// 创建角色(user，userrole两张表插入数据)
					userDao.createUser(user, urt);

				} else {
					// user表中存在记录,角色表中不存在记录
					Object object = tokenDao.getExist(userId,ManageUnit,adminRoleId);
					if (object == null) {

						// userrole插入数据
						ConfigurableItemUpdater<User, UserRoleToken> up = (ConfigurableItemUpdater<User, UserRoleToken>) UserController
								.instance().getUpdater();
						up.createItem(user.getId(), urt);

					} else {
						// user表中存在记录,角色表中存在记录
						throw new DAOException(602, "该用户已注册过");
					}
				}
				setResult(user.getId());
			}

		};
		HibernateSessionTemplate.instance().executeTrans(action);

		return action.getResult();
	}

	/**
	 * 修改管理员信息
	 * 
	 * @author ZX
	 * @date 2015-5-27 下午4:11:02
	 * @param userId
	 *            用户id
	 * @param name
	 *            用户姓名
	 * @param email
	 *            用户Email
	 * @param avatarFileId
	 *            用户头像文件id
	 */
	@SuppressWarnings("unchecked")
	@RpcService
	public void resetAdminUserInfo(String userId, String name, String email,
			Integer avatarFileId) {
			UserRoleTokenDAO tokenDao = DAOFactory
					.getDAO(UserRoleTokenDAO.class);

			if (StringUtils.isEmpty(userId)) {
				throw new DAOException(DAOException.VALUE_NEEDED, "登录账户不能为空");
			}

			UserDAO userDao = DAOFactory.getDAO(UserDAO.class);
			User user = null;
			try {
				user = AccountCenter.getUser(userId);
			} catch (ControllerException e) {
				throw new DAOException(DAOException.EVAL_FALIED, "获取用户失败");
			}
			if (user == null) {
				throw new DAOException(DAOException.VALIDATE_FALIED, "用户不存在");
			}

			if (!StringUtils.isEmpty(name)) {
				user.setName(name);
			}
			if (!StringUtils.isEmpty(email)) {
				user.setEmail(email);
			}
			if (avatarFileId != null && avatarFileId > 0) {
				user.setAvatarFileId(avatarFileId);
			}

			userDao.update(user);

		//刷新服务器缓存
		new UserSevice().updateUserCache(userId, SystemConstant.ROLES_ADMIN,"",null);

	}

	/**
	 * 获取当前管理员所管机构的区域
	 * 
	 * @author hyj
	 * @param manageUnit
	 * @return
	 */
	@RpcService
	public List<DictionaryItem> getAddrAreaByAdmin(String manageUnit) {
		OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
		Organ o = organDAO.getByManageUnit(manageUnit);
		List<DictionaryItem> list = new ArrayList<DictionaryItem>();
		try {
			ctd.dictionary.Dictionary dic = DictionaryController.instance()
					.get("eh.base.dictionary.AddrArea");
			list = dic.getSlice(o.getAddrArea(), 0, "");
			list.add(dic.getItem(o.getAddrArea()));

		} catch (ControllerException e) {
			log.error("getAddrAreaByAdmin() error: "+e);
		}
		return list;
	}

	/**
	 * 根据机构ID获取该机构下的所有管理员列表
	 * 
	 * @author yaozh
	 * @param unit
	 * @return
	 */
	@RpcService
	public List<User> findByManageUnit(final String unit) {
		HibernateStatelessResultAction<List<User>> action = new AbstractHibernateStatelessResultAction<List<User>>() {
			@Override
			public void execute(StatelessSession ss) throws Exception{
				String hql = new String(
						"select distinct a from User a,UserRoleTokenEntity b where a.id = b.userId and b.manageUnit = :unit and b.roleId='admin'");
				Query q = ss.createQuery(hql);
				q.setParameter("unit", unit);
				q.setFirstResult(0);
				q.setMaxResults(100);
				@SuppressWarnings("unchecked")
				List<User> list = q.list();
				setResult(list);
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		return (List<User>)action.getResult();
	}
}
