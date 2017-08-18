package eh.op.dao;

import ctd.account.UserRoleToken;
import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.BeanUtils;
import ctd.util.annotation.RpcService;
import eh.base.dao.ClientConfigDAO;
import eh.entity.base.ClientConfig;
import eh.entity.wx.WXConfig;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by houxr on 2016/9/11.
 */
public abstract class WXConfigsDAO extends HibernateSupportDelegateDAO<WXConfig> {

    public WXConfigsDAO() {
        super();
        this.setEntityName(WXConfig.class.getName());
        this.setKeyField("id");
    }

    /**
     * 新建wxConfig
     *
     * @param wxConfig
     * @return
     * @author houxr
     */
    public WXConfig addOneWXConfig(WXConfig wxConfig) {
        /*if (StringUtils.isEmpty(wxConfig.getAppID())) {
            new DAOException(DAOException.VALUE_NEEDED, "AppID is required!");
        }*/

        if (StringUtils.isEmpty(wxConfig.getWxKey())) {
            new DAOException(DAOException.VALUE_NEEDED, "wxKey is required!");
        }
        super.save(wxConfig);
        if (wxConfig.getId() > 0) {
            return wxConfig;
        }
        return null;
    }

    /**
     * 查询所有机构微信公众号
     *
     * @return
     */
    @DAOMethod(limit = 0)
    public abstract List<WXConfig> findByStatus(Integer status);

    @DAOMethod(limit = 0, sql = " from WXConfig order by id")
    public abstract List<WXConfig> findAllConfig();


    /**
     * 查询所有机构微信公众号(区分类别)
     *
     * @return
     */
    @DAOMethod(limit = 0)
    public abstract List<WXConfig> findByWxType(Integer wxType);


    /**
     * [运营平台] 机构微信相关设置查询
     *
     * @param organName
     * @param start
     * @param limit
     * @return
     */
    public QueryResult<WXConfig> queryWXConfigByStartAndLimit(final String organName, final int start, final int limit) {
        HibernateStatelessResultAction<QueryResult<WXConfig>> action = new AbstractHibernateStatelessResultAction<QueryResult<WXConfig>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                long total = 0;
                StringBuilder hql = new StringBuilder("FROM WXConfig w WHERE 1=1 ");
                HashMap<String, Object> params = new HashMap<String, Object>();
                if (!StringUtils.isEmpty(organName)) {
                    hql.append(" and w.organName like :organName");
                    params.put("organName", "%" + organName + "%");
                }

                Query query = ss.createQuery("SELECT count(*) " + hql.toString());
                query.setProperties(params);
                total = (long) query.uniqueResult();//获取总条数

                query = ss.createQuery("SELECT w " + hql.toString());
                query.setProperties(params);
                query.setFirstResult(start);
                query.setMaxResults(limit);
                setResult(new QueryResult<WXConfig>(total, query.getFirstResult(), query.getMaxResults(), query.list()));
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * [运营平台] 机构微信相关设置查询 返回 QueryResult<Map>
     *
     * @param organName
     * @param start
     * @param limit
     * @return
     */
    public QueryResult<Map> queryMapByStartAndLimit(final String organName, final int start, final int limit) {
        QueryResult<WXConfig> payConfigQueryResult = this.queryWXConfigByStartAndLimit(organName, start, limit);
        Long total = 0L;
        if (payConfigQueryResult != null) {
            total = payConfigQueryResult.getTotal();
            List<WXConfig> configs = payConfigQueryResult.getItems();
            if (configs != null) {
                List<Map> maps = new ArrayList<Map>();
                for (WXConfig config : configs) {
                    Map<String, Object> map = new HashMap<String, Object>();
                    BeanUtils.map(config, map);
                    if (config.getParentId() != null) {
                        map.put("parent", this.getById(config.getParentId()));
                    }
                    maps.add(map);
                }
                return new QueryResult<Map>(total, start, configs.size(), maps);
            } else {
                return new QueryResult<Map>(total, start, 0, new ArrayList<Map>());
            }
        } else {
            return null;
        }

    }

    public WXConfig getConfigAndPropsById(Integer id) {
        WXConfig wxConfig = this.get(id);
        if (wxConfig != null) {
            WxAppPropsDAO wxAppPropsDAO = DAOFactory.getDAO(WxAppPropsDAO.class);
            wxConfig.setProps(wxAppPropsDAO.findByConfigId(id));
        }
        return wxConfig;
    }

    @RpcService
    @DAOMethod
    public abstract WXConfig getById(Integer id);

    @RpcService
    @DAOMethod
    public abstract WXConfig getByAppID(String appID);

    /**
     * 按管理层级查询所有WxConfig
     *
     * @param manageUnit
     * @return
     */
    @DAOMethod(limit = 0)
    public abstract List<WXConfig> findByManageUnit(String manageUnit);

    @DAOMethod(sql = " update WXConfig set manageUnit =:manageUnit where manageUnit=:oldManageUnit")
    public abstract void updateByManageUnit(@DAOParam("oldManageUnit") String oldManageUnit, @DAOParam("manageUnit") String manageUnit);

    @DAOMethod(sql = " update WXConfig set tempId =:tempId where id=:id")
    public abstract void updateTempIdByConfigID(@DAOParam("tempId") Integer tempId, @DAOParam("id") Integer id);


    public QueryResult<WXConfig> queryWXConfigByStatusAndName(final String name, final Integer status, final int start, final int limit) {
        HibernateStatelessResultAction<QueryResult<WXConfig>> action = new AbstractHibernateStatelessResultAction<QueryResult<WXConfig>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                long total = 0;
                UserRoleToken urt = UserRoleToken.getCurrent();
                if (urt == null) {
                    throw new DAOException("urt is require");
                }


                StringBuilder hql = new StringBuilder("FROM WXConfig w WHERE 1=1 and w.manageUnit like:manageUnit ");
                if (status != null) {
                    hql.append(" and w.status= :status");
                }
                if (!StringUtils.isEmpty(name)) {
                    hql.append(" and w.wxName like:name ");
                }
                Query cQuery = ss.createQuery("SELECT count(*) " + hql.toString());
                Query query = ss.createQuery("SELECT w " + hql.toString() + " order by w.id");
                if (status != null) {
                    cQuery.setParameter("status", status);
                    query.setParameter("status", status);
                }
                if (!StringUtils.isEmpty(name)) {
                    cQuery.setParameter("name", "%" + name + "%");
                    query.setParameter("name", "%" + name + "%");
                }
                cQuery.setParameter("manageUnit", urt.getManageUnit() + "%");
                query.setParameter("manageUnit", urt.getManageUnit() + "%");

                total = (long) cQuery.uniqueResult();//获取总条数
                query.setFirstResult(start);
                query.setMaxResults(limit);
                setResult(new QueryResult<WXConfig>(total, query.getFirstResult(), query.getMaxResults(), query.list()));
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    @Override
    public WXConfig save(WXConfig config) throws DAOException {
        config = super.save(config);
        //添加终端数据
        DAOFactory.getDAO(ClientConfigDAO.class).
                addOneClient(1, WXConfig.class.getName(), config.getId(), config.getWxName());
        return config;
    }
}
