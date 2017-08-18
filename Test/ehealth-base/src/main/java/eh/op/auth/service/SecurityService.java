package eh.op.auth.service;

import ctd.account.AccountCenter;
import ctd.account.UserRoleToken;
import ctd.account.user.User;
import ctd.account.user.UserController;
import ctd.account.user.UserRoleTokenEntity;
import ctd.controller.exception.ControllerException;
import ctd.controller.updater.ConfigurableItemUpdater;
import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.impl.user.UserDAO;
import ctd.persistence.support.impl.user.UserRoleTokenDAO;
import ctd.util.annotation.RpcService;
import eh.base.constant.SystemConstant;
import eh.base.dao.OrganDAO;
import eh.base.dao.UserRolesDAO;
import eh.base.service.BusActionLogService;
import eh.base.user.UserSevice;
import eh.entity.base.Organ;
import eh.entity.base.UserRoles;
import eh.entity.opauth.OpGroup;
import eh.entity.opauth.Permission;
import eh.entity.opauth.UserGroup;
import eh.op.auth.dao.OpGroupDAO;
import eh.op.auth.dao.PermissionDAO;
import eh.op.auth.dao.UserGroupDAO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.ObjectUtils;

import java.util.*;

public class SecurityService {

    /**
     * 创建平台管理员用户
     *
     * @param userId       用户id
     * @param name         用户名
     * @param password     密码
     * @param email        email
     * @param avatarFileId 头像
     * @return
     */
    @RpcService
    public User createAdminUser(final String userId, final String name,
                                final String password, final String email, final Integer avatarFileId) {
        if (StringUtils.isEmpty(userId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "登录账户不能为空");
        }

        UserDAO userDao = DAOFactory.getDAO(UserDAO.class);
        User user = userDao.get(userId);

        if (user == null && StringUtils.isEmpty(name)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "姓名不能为空");
        }

        if (user == null && StringUtils.isEmpty(password)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "密码不能为空");
        }

        UserRoleTokenEntity urt = new UserRoleTokenEntity();
        urt.setUserId(userId);
        urt.setRoleId("admin");
        urt.setTenantId("eh");
        urt.setManageUnit("eh");

        if (user == null) { // user表中不存在记录
            // 创建角色(user，userrole两张表插入数据)

            user = new User();
            user.setId(userId);
            user.setPlainPassword(password);
            user.setName(name);
            if (avatarFileId != null && avatarFileId > 0) {
                user.setAvatarFileId(avatarFileId);
            }
            user.setEmail(email);
            user.setCreateDt(new Date());
            user.setStatus("1");

            userDao.createUser(user, urt);
        } else { // user表中存在记录
            UserRoleTokenDAO tokenDao = DAOFactory.getDAO(UserRoleTokenDAO.class);
            UserRoleToken userRoleToken = tokenDao.getExist(userId, "eh", "admin");
            if (userRoleToken == null) { //角色表中不存在记录
                // 插入角色数据
                @SuppressWarnings("unchecked")
                ConfigurableItemUpdater<User, UserRoleToken> up = (ConfigurableItemUpdater<User, UserRoleToken>) UserController.instance().getUpdater();
                try {
                    up.createItem(userId, urt);
                } catch (ControllerException e) {
                    throw new DAOException(500, "管理员角色注册失败");
                }

            } else { //角色表中存在admin,eh记录
                throw new DAOException(602, "该用户已注册过");
            }
        }

        BusActionLogService.recordBusinessLog("用户管理", userId, "User", "添加了一个用户(" + userId + ")[真实姓名:" + name + "]");
        return user;
    }

    /**
     * 删除管理员用户 - 只删除管理员角色
     *
     * @param userId
     */
    @RpcService
    public void removeAdminUser(String userId,String mu) {
        UserRoleTokenDAO tokenDao = DAOFactory.getDAO(UserRoleTokenDAO.class);
        mu = (mu==null)?"eh":mu;
        UserRoleToken userRoleToken = tokenDao.getExist(userId, mu, "admin");

        if(userRoleToken==null){
            throw new DAOException("未找到该账户信息");
        }

        // 删除角色前删除对应组成员关系
        UserGroupDAO userGroupDAO = DAOFactory.getDAO(UserGroupDAO.class);
        userGroupDAO.removeByUrt(userRoleToken.getId());

        // 删除角色数据
        tokenDao.remove(userRoleToken.getId());

        // 刷新缓存
        try {
            UserController.instance().getUpdater().reload(userId);
        } catch (ControllerException e) {
            throw new DAOException(500, "刷新用户缓存失败");
        }
    }


    /**
     * 根据userId获取单个admin用户
     *
     * @param userId
     * @return
     */
    @RpcService
    public List<User> findAdminUserByUserId(final String userId) {
        UserGroupDAO userGroupDao = DAOFactory.getDAO(UserGroupDAO.class);
        return userGroupDao.findAdminUserByUserId(userId);
    }

    /**
     * 更新用户基本信息
     *
     * @param userId
     * @param name
     * @param email
     * @param avatarFileId
     */
    @RpcService
    public void updateAdminUser(String userId, String name, String email,
                                Integer avatarFileId) {
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
        BusActionLogService.recordBusinessLog("用户管理", userId, "User",
                "修改了用户(" + userId + ")[真实姓名:" + user.getName() + "]->[真实姓名:" + name + (StringUtils.isEmpty(email) ? "]" : ",Email:" + email + "]"));

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
        //new UserSevice().updateUserCache(userId, SystemConstant.ROLES_ADMIN, "", null);
        // 刷新缓存
        try {
            UserController.instance().getUpdater().reload(userId);
        } catch (ControllerException e) {
            throw new DAOException(500, "刷新用户缓存失败");
        }
    }


    /**
     * 查询用户
     *
     * @param keyword 用户名 或者 userId
     * @param status  用户状态
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    public QueryResult<User> queryAdminUser(final String keyword, final String manageUnit,
                                            final String status, final int start, final int limit) {
        UserGroupDAO userGroupDao = DAOFactory.getDAO(UserGroupDAO.class);
        QueryResult<User> users = userGroupDao.queryAdminUser(keyword, manageUnit, status, start, limit);
        return users;
    }

    /**
     * 禁用/启用 用户
     *
     * @param userId
     * @param status 用户状态0禁用 1启用
     * @return
     */
    @RpcService
    public User updateUserStatusByUserId(String userId, String status) {
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
        user.setStatus(status);
        User target = userDao.update(user);
        //刷新服务器缓存
        new UserSevice().updateUserCache(userId, SystemConstant.ROLES_ADMIN, "", null);
        return target;
    }

    /**
     * 根据组名查询组
     *
     * @param groupName
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    public QueryResult<OpGroup> queryGroupByGroupName(String groupName, int start, int limit) {
        OpGroupDAO dao = DAOFactory.getDAO(OpGroupDAO.class);
        QueryResult<OpGroup> result = dao.queryGroupByName(groupName, start, limit);
        for (OpGroup group : result.getItems()) {
            group.getPermissions().size(); // Fetch/Eager
        }
        return result;
    }

    /**
     * 根据组Id查询组
     *
     * @param groupId
     * @return
     */
    @RpcService
    public OpGroup getGroupByGroupId(Integer groupId) {
        if (ObjectUtils.isEmpty(groupId)) {
            throw new DAOException(DAOException.VALIDATE_FALIED, "groupId不能为空");
        }
        OpGroupDAO dao = DAOFactory.getDAO(OpGroupDAO.class);
        return dao.get(groupId);
    }

    /**
     * 创建组
     *
     * @param name        组名
     * @param description 组名描述
     * @param permissions 权限
     * @return
     */
    @RpcService
    public OpGroup createGroup(String name, String description, List<String> permissions) {
        OpGroupDAO dao = DAOFactory.getDAO(OpGroupDAO.class);
        OpGroup group = new OpGroup();
        group.setName(name);
        group.setDescription(description);
        for (String permissionKey : permissions) {
            group.getPermissions().add(new Permission(permissionKey));
        }
        OpGroup target = dao.createGroup(group);
        BusActionLogService.recordBusinessLog("用户组管理", String.valueOf(target.getGroupId()), "OpGroup",
                "添加了一个用户组[" + target.getGroupId() + "]{"+target.toString()+"}");
        return target;
    }

    /**
     * 更新组信息
     *
     * @param groupId
     * @param name
     * @param description
     * @param permissions
     * @return
     */
    @RpcService
    public OpGroup updateGroup(Integer groupId, String name, String description, List<String> permissions) {
        if (ObjectUtils.isEmpty(groupId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "groupId null");
        }
        OpGroup oldGroup = getGroupByGroupId(groupId);
        OpGroupDAO dao = DAOFactory.getDAO(OpGroupDAO.class);
        OpGroup group = new OpGroup();
        group.setGroupId(groupId);
        group.setName(name);
        group.setDescription(description);
        for (String key : permissions) {
            group.getPermissions().add(new Permission(key));
        }
        OpGroup target = dao.updateGroup(group);
        BusActionLogService.recordBusinessLog("用户组管理", String.valueOf(groupId), "OpGroup",
                "修改用户组(" + groupId + "){"+oldGroup.toString()+
                        "}>{"+target.toString()+"}");
        return target;
    }

    /**
     * 新增 组权限
     *
     * @param groupId
     * @param permission
     * @return
     */
    @RpcService
    public OpGroup addGroupPermission(Integer groupId, String permission) {
        OpGroupDAO dao = DAOFactory.getDAO(OpGroupDAO.class);
        PermissionDAO pdao = DAOFactory.getDAO(PermissionDAO.class);
        return dao.addGroupPermission(groupId, pdao.get(permission));
    }

    /**
     * 删除组权限
     *
     * @param groupId
     * @param permission
     * @return
     */
    @RpcService
    public OpGroup removeGroupPermission(Integer groupId, String permission) {
        OpGroupDAO dao = DAOFactory.getDAO(OpGroupDAO.class);
        PermissionDAO pdao = DAOFactory.getDAO(PermissionDAO.class);
        return dao.removeGroupPermission(groupId, pdao.get(permission));
    }

    /**
     * 删除组
     *
     * @param groupId
     */
    @RpcService
    public void deleteGroup(Integer groupId) {
        OpGroupDAO dao = DAOFactory.getDAO(OpGroupDAO.class);
        OpGroup opGroup = dao.get(groupId);
        BusActionLogService.recordBusinessLog("用户组管理", String.valueOf(groupId), "OpGroup",
                "删除用户组[" + opGroup.getName() + "](" + opGroup.getGroupId() + ")及其成员");
        //删除组同时对应删除组成员
        UserGroupDAO userGroupDAO = DAOFactory.getDAO(UserGroupDAO.class);
        userGroupDAO.removeByGroupId(groupId);
        dao.remove(groupId);
    }

    /**
     * 查询所有权限
     *
     * @return
     */
    @RpcService
    public List<Permission> findAllPermissions() {
        PermissionDAO dao = DAOFactory.getDAO(PermissionDAO.class);
        return dao.findAll();
    }

    protected Integer getUrtByUserId(String userId) {
        UserRolesDAO urtDao = DAOFactory.getDAO(UserRolesDAO.class);
        List<UserRoles> urts = urtDao.findUrtByUserId(userId);
        Integer urtId = null;
        for (UserRoles urt : urts) {
            if (urt.getRoleId().equals("admin")) {
                urtId = urt.getId();
            }
        }
        if (urtId == null) {
            throw new DAOException(DAOException.ENTITIY_NOT_FOUND, "用户名为 [" + userId + "] 的管理员不存在");
        }
        return urtId;
    }

    /**
     * 新建用户组
     *
     * @param userId
     * @param groupId
     */
    @RpcService
    public void addUserGroup(String userId, Integer groupId) {
        OpGroupDAO dao = DAOFactory.getDAO(OpGroupDAO.class);
        OpGroup opGroup = dao.get(groupId);
        UserDAO userDAO = DAOFactory.getDAO(UserDAO.class);
        User user = userDAO.get(userId);
        BusActionLogService.recordBusinessLog("用户组管理", String.valueOf(groupId), "OpGroup",
                "用户组[" + opGroup.getName() + "](" + opGroup.getGroupId() + ")增加了成员[" + user.getName() + "](" + userId + ")");

        Integer urt = getUrtByUserId(userId);
        UserGroupDAO ugDao = DAOFactory.getDAO(UserGroupDAO.class);
        UserGroup userGroup = new UserGroup();
        userGroup.setUrt(urt);
        userGroup.setGroupId(groupId);
        ugDao.save(userGroup);
    }

    /**
     * 删除用户组
     *
     * @param userId
     * @param groupId
     */
    @RpcService
    public void removeUserGroup(String userId, Integer groupId) {
        OpGroupDAO dao = DAOFactory.getDAO(OpGroupDAO.class);
        OpGroup opGroup = dao.get(groupId);
        UserDAO userDAO = DAOFactory.getDAO(UserDAO.class);
        User user = userDAO.get(userId);
        BusActionLogService.recordBusinessLog("用户组管理", String.valueOf(groupId), "OpGroup",
                "用户组[" + opGroup.getName() + "](" + opGroup.getGroupId() + ")移除了成员[" + user.getName() + "](" + userId + ")");

        Integer urt = getUrtByUserId(userId);
        UserGroupDAO ugDao = DAOFactory.getDAO(UserGroupDAO.class);
        UserGroup ug = ugDao.getByUrtAndGroupId(urt, groupId);
        ugDao.remove(ug.getUserGroupId());
    }

    /**
     * 根据 组id 获取 组用户
     *
     * @param groupId
     * @return
     */
    @RpcService
    public List<User> findUserByGroupId(final Integer groupId) {
        UserGroupDAO ugDao = DAOFactory.getDAO(UserGroupDAO.class);
        return ugDao.findByGroupId(groupId);
    }


    /**
     * 获取用户权限列表
     *
     * @param userId
     * @return
     */
    @RpcService
    public List<Permission> getUserPermissions(String userId) {
        return getUrtPermissions(getUrtByUserId(userId));
    }

    /**
     * 根据 用户urt 获取用户访问权限
     *
     * @param urt
     * @return
     */
    @RpcService
    public List<Permission> getUrtPermissions(Integer urt) {
        // get userGroup
        UserGroupDAO ugDao = DAOFactory.getDAO(UserGroupDAO.class);
        List<UserGroup> userGroups = ugDao.findByUrt(urt);
        OpGroupDAO groupDao = DAOFactory.getDAO(OpGroupDAO.class);
        Set<Permission> permissions = new HashSet<Permission>();
        // 用 set 避免 Permission 对象重复
        for (UserGroup userGroup : userGroups) {
            OpGroup group = groupDao.get(userGroup.getGroupId());
            permissions.addAll(group.getPermissions());
        }
        return new ArrayList<Permission>(permissions);
    }

    /**
     * 是否 授权访问服务
     *
     * @param urt
     * @param serviceId
     * @param method
     * @return
     */
    public boolean isAuthoritied(Integer urt, String serviceId, String method) {
        List<Permission> permissions = getUrtPermissions(urt);
        for (Permission permission : permissions) {
            if (permission.hasAuthority(serviceId, method)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAuthoritiedOrgan(Set<Integer> organs){
        UserRoleToken urt = UserRoleToken.getCurrent();
        if(urt==null){
            throw new DAOException(DAOException.EVAL_FALIED,"获取登录信息失败");
        }
        String mu = urt.getManageUnit();
        if("eh".equals(mu)){
            return true;
        }
        organs.add(0);
        List<Integer> lOrgan = new ArrayList<Integer>();
        lOrgan.addAll(organs);
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        Long l = organDAO.getAuthoritiedOrgan(mu+"%",lOrgan);
        if(l==null||l.intValue()<=0){
            throw new DAOException(DAOException.ACCESS_DENIED,"权限验证失败");
        }
        return true;
    }




}
