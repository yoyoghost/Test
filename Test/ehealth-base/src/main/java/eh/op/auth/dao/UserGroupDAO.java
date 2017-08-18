package eh.op.auth.dao;

import ctd.account.user.User;
import ctd.account.user.UserRoleTokenEntity;
import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import eh.entity.opauth.UserGroup;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public abstract class UserGroupDAO extends HibernateSupportDelegateDAO<UserGroup> {
    public UserGroupDAO() {
        super();
        this.setEntityName(UserGroup.class.getName());
        this.setKeyField("userGroupId");
    }

    /**
     * @return
     */
    @DAOMethod
    public abstract List<UserGroup> findByUrt(Integer urt);

    @DAOMethod
    public abstract UserGroup getByUrtAndGroupId(Integer urt, Integer groupId);

    @DAOMethod(sql = "delete from UserGroup where urt=:urt")
    public abstract void removeByUrt(@DAOParam("urt") Integer urt);
    
    @DAOMethod(sql = "delete from UserGroup where groupId=:groupId")
    public abstract void removeByGroupId(@DAOParam("groupId") Integer groupId);
    
    /**
     * 根据 组id 获取 组用户
     *
     * @param groupId
     * @return
     */
    public List<User> findByGroupId(final Integer groupId) {
        HibernateStatelessResultAction<List<User>> action = new AbstractHibernateStatelessResultAction<List<User>>() {
            @SuppressWarnings("unchecked")
			@Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder("select u from User u,UserRoleTokenEntity us,UserGroup g " +
                        " where us.roleId='admin' and u.id=us.userId and us.id=g.urt ");
                HashMap<String, Object> params = new HashMap<String, Object>();
                if (!ObjectUtils.isEmpty(groupId)) {
                    hql.append(" and g.groupId=:groupId");
                    params.put("groupId", groupId);
                }
                Query q = ss.createQuery(hql.toString());
                q.setProperties(params);
                List<User> list = q.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 查询用户
     *
     * @param keyword    用户名 或者 userId
     * @param manageUnit
     * @param status     用户状态 1正常 0注销
     * @param start
     * @param limit
     * @return
     */
    public QueryResult<User> queryAdminUser(final String keyword, final String manageUnit,
                                           final String status, final int start, final int limit) {
        HibernateStatelessResultAction<QueryResult<User>> action = new AbstractHibernateStatelessResultAction<QueryResult<User>>() {
            @SuppressWarnings("unchecked")
			@Override
            public void execute(StatelessSession ss) throws Exception {
                long total = 0;
                StringBuilder hql = new StringBuilder(
                        "from User a,UserRoleTokenEntity b where a.id = b.userId and b.roleId='admin'");
                HashMap<String, Object> params = new HashMap<String, Object>();
                if (!StringUtils.isEmpty(keyword)) {
                	if (StringUtils.isAsciiPrintable(keyword))
	                    hql.append(" and ( b.userId like :keyword or a.name like :keyword )");
                	else
                		hql.append(" and a.name like :keyword");
	                params.put("keyword", "%" + keyword + "%");
                }
                if (!StringUtils.isEmpty(manageUnit)) {
                    hql.append(" and b.manageUnit = :manageUnit");
                    params.put("manageUnit", manageUnit);
                }
                if (!StringUtils.isEmpty(status)) {
                    hql.append(" and a.status = :status");
                    params.put("status", status);
                }

                Query query = ss.createQuery("select count(a.id) " + hql.toString());
                query.setProperties(params);
                total = (long) query.uniqueResult();//获取总条数

                Query q = ss.createQuery("select a,b " + hql.toString());
                q.setProperties(params);
                q.setFirstResult(start);
                q.setMaxResults(limit);
                List<Object[]> list = q.list();
                List<User> users = new ArrayList<User>();
                OpGroupDAO authGroupDAO = DAOFactory.getDAO(OpGroupDAO.class);
                for (Object[] userAndRole : list) {
                    User user = (User) userAndRole[0];
                    UserRoleTokenEntity urt= (UserRoleTokenEntity) userAndRole[1];
                    user.addUserRoleToken(urt);
                    List<String> ops = authGroupDAO.findGroupNamesByUrt(urt.getId());
                    user.setProperty("opGroups", ops);
                    users.add(user);
                }
                QueryResult<User> userQueryResult = new QueryResult<>(total, start, limit, users);
                setResult(userQueryResult);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 根据userId获取单个admin用户
     *
     * @param userId
     * @return
     */
    public List<User> findAdminUserByUserId(final String userId) {
        if (StringUtils.isEmpty(userId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "用户id不能为空");
        }
        HibernateStatelessResultAction<List<User>> action = new AbstractHibernateStatelessResultAction<List<User>>() {
            @SuppressWarnings("unchecked")
			@Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select a,b from User a,UserRoleTokenEntity b where a.id = b.userId and b.roleId='admin'");
                if (!StringUtils.isEmpty(userId)) {
                    hql.append(" and b.userId=:userId");
                }
                Query q = ss.createQuery(hql.toString());
                if (!StringUtils.isEmpty(userId)) {
                    q.setParameter("userId", userId);
                }
                List<Object[]> list = q.list();
                List<User> users = new ArrayList<User>();
                for (Object[] userAndRole : list) {
                    User user = (User) userAndRole[0];
                    user.addUserRoleToken((UserRoleTokenEntity) userAndRole[1]);
                    users.add(user);
                }
                setResult(users);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List<User>) action.getResult();
    }


    @DAOMethod(sql = " from UserGroup where groupId=:groupId")
    public abstract List<UserGroup> findUserGroupByGroupId(@DAOParam("groupId") Integer groupId);
}
