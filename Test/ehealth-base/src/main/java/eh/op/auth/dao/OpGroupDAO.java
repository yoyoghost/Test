package eh.op.auth.dao;

import ctd.persistence.bean.QueryResult;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateResultAction;
import ctd.persistence.support.hibernate.template.HibernateResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import eh.entity.opauth.OpGroup;
import eh.entity.opauth.Permission;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Query;
import org.hibernate.Session;
import org.springframework.util.ObjectUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class OpGroupDAO extends HibernateSupportDelegateDAO<OpGroup> {
    public OpGroupDAO() {
        super();
        this.setEntityName(OpGroup.class.getName());
        this.setKeyField("groupId");
    }

    /**
     * 根据 组名 查询 组信息
     *
     * @param groupName
     * @param start
     * @param limit
     * @return
     */
    public QueryResult<OpGroup> queryGroupByName(final String groupName, final int start, final int limit) {
        final HibernateResultAction<QueryResult<OpGroup>> action = new AbstractHibernateResultAction<QueryResult<OpGroup>>() {
            @Override
            public void execute(Session ss) throws Exception {
                long total = 0;
                StringBuilder countSql = new StringBuilder("select count(*) from OpGroup where 1=1");
                HashMap<String, Object> params = new HashMap<String, Object>();
                if (!StringUtils.isEmpty(groupName)) {
                    countSql.append(" and name like :groupName ");
                    params.put("groupName", "%" + groupName + "%");
                }

                Query countQuery = ss.createQuery(countSql.toString());
                countQuery.setProperties(params);
                total = (long) countQuery.uniqueResult();//获取总条数

                StringBuilder hql = new StringBuilder("from OpGroup g left outer join fetch g.permissions where 1=1 ");
                HashMap<String, Object> paramss = new HashMap<String, Object>();
                if (!StringUtils.isEmpty(groupName)) {
                    hql.append(" and g.name like :groupName ");
                    paramss.put("groupName", "%" + groupName + "%");
                }
                Query query = ss.createQuery(hql.toString());
                query.setProperties(paramss);
                query.setMaxResults(limit);
                query.setFirstResult(start);
                List<OpGroup> list = query.list();
                QueryResult<OpGroup> result = new QueryResult<>(total, start, limit, list);
                this.setResult(result);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    public OpGroup createGroup(final OpGroup group) {
        HibernateResultAction<OpGroup> action = new AbstractHibernateResultAction<OpGroup>() {
            @Override
            public void execute(Session ss) throws Exception {
                Set<Permission> permissions = group.getPermissions();
                group.setPermissions(new HashSet<Permission>());
                ss.save(group);
                for (Permission permission : permissions) {
                    permission = ss.get(Permission.class, permission.getPid());
                    group.getPermissions().add(permission);
                }
                ss.flush();
                setResult(group);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
        return action.getResult();
    }

    public OpGroup updateGroup(final OpGroup group) {
        HibernateResultAction<OpGroup> action = new AbstractHibernateResultAction<OpGroup>() {
            @Override
            public void execute(Session ss) throws Exception {
                OpGroup target = ss.get(OpGroup.class, group.getGroupId());
                target.setName(group.getName());
                target.setDescription(group.getDescription());
                target.getPermissions().clear();
                ss.flush();
                for (Permission permission : group.getPermissions()) {
                    permission = ss.get(Permission.class, permission.getPid());
                    target.getPermissions().add(permission);
                }
                ss.flush();
                setResult(target);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
        return action.getResult();
    }


    public OpGroup addGroupPermission(final Integer groupId, final Permission permission) {
        HibernateResultAction<OpGroup> action = new AbstractHibernateResultAction<OpGroup>() {
            @Override
            public void execute(Session ss) throws Exception {
                OpGroup group = ss.get(OpGroup.class, groupId);
                ss.load(permission, permission.getPid());
                group.getPermissions().add(permission);
                ss.flush();
                setResult(group);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
        return action.getResult();
    }

    public OpGroup removeGroupPermission(final Integer groupId, final Permission permission) {
        HibernateResultAction<OpGroup> action = new AbstractHibernateResultAction<OpGroup>() {
            @Override
            public void execute(Session ss) throws Exception {
                OpGroup group = ss.get(OpGroup.class, groupId);
                group.getPermissions().remove(permission);
                ss.flush();
                setResult(group);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
        return action.getResult();
    }

    /**
     * 根据 groupId 查询组
     *
     * @param groupId
     * @return
     */
    public List<OpGroup> findGroupByGroupId(final Integer groupId) {
        final HibernateResultAction<List<OpGroup>> action = new AbstractHibernateResultAction<List<OpGroup>>() {
            @Override
            public void execute(Session ss) throws Exception {
                StringBuilder hql = new StringBuilder("from OpGroup g where 1=1 ");
                HashMap<String, Object> params = new HashMap<String, Object>();
                if (!ObjectUtils.isEmpty(groupId)) {
                    hql.append(" and g.groupId= :groupId ");
                    params.put("groupId", groupId);
                }
                Query query = ss.createQuery(hql.toString());
                query.setProperties(params);
                List<OpGroup> list = query.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }
    
    public List<String> findGroupNamesByUrt(final Integer urt){
        final HibernateResultAction<List<String>> action = new AbstractHibernateResultAction<List<String>>() {
			@Override
            public void execute(Session ss) throws Exception {
                String hql = "select g.name from UserGroup u,OpGroup g where u.groupId = g.groupId and u.urt =:urt";
                HashMap<String, Object> params = new HashMap<String, Object>();
                params.put("urt", urt);
                Query query = ss.createQuery(hql.toString());
                query.setProperties(params);
                List<String> opgs = query.list();
                setResult(opgs);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    public  List<OpGroup> findGroupsByUrt(final Integer urt){
        final HibernateResultAction<List<OpGroup>> action = new AbstractHibernateResultAction<List<OpGroup>>() {
            @Override
            public void execute(Session ss) throws Exception {
                String hql = "select g from UserGroup u,OpGroup g where u.groupId = g.groupId and u.urt =:urt";
                HashMap<String, Object> params = new HashMap<String, Object>();
                params.put("urt", urt);
                Query query = ss.createQuery(hql.toString());
                query.setProperties(params);
                List<OpGroup> opgs = query.list();
                setResult(opgs);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }



}
