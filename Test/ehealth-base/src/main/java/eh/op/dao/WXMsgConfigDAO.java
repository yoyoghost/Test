package eh.op.dao;

import ctd.account.UserRoleToken;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import eh.base.constant.ErrorCode;
import eh.entity.wx.WXMsgConfig;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * Created by houxr on 2016/9/12.
 */
public abstract class WXMsgConfigDAO extends HibernateSupportDelegateDAO<WXMsgConfig> {

    private static final Log logger = LogFactory.getLog(WXMsgConfigDAO.class);

    public WXMsgConfigDAO() {
        super();
        this.setEntityName(WXMsgConfig.class.getName());
        this.setKeyField("wxMsgId");
    }

    @DAOMethod
    public abstract WXMsgConfig getByWxMsgId(Integer wxMsgId);

    /**
     * 根据appId 查找有效的 微信自动回复消息
     *
     * @param appId
     * @param status
     * @return
     */
    @DAOMethod(sql = "from WXMsgConfig where appId=:appId and status=:status", limit = 0)
    public abstract List<WXMsgConfig> findWXMsgConfigByAppIdAndStatus(@DAOParam("appId") String appId,
                                                                      @DAOParam("status") Integer status);


    /**
     * 新建 wxMsgConfig
     *
     * @param wxMsgConfig
     * @return
     * @author houxr
     */
    public WXMsgConfig addOneWXMsgConfig(WXMsgConfig wxMsgConfig) {
        if (StringUtils.isEmpty(wxMsgConfig.getAppId())) {
            new DAOException(DAOException.VALUE_NEEDED, "AppID is required!");
        }

        if (StringUtils.isEmpty(wxMsgConfig.getMsgKey())) {
            new DAOException(DAOException.VALUE_NEEDED, "MsgKey is required!");
        }
        UserRoleToken urt = UserRoleToken.getCurrent();
        wxMsgConfig.setUserId(urt.getId());
        wxMsgConfig.setUserName(urt.getUserName());
        wxMsgConfig.setCreateDate(new Date());
        WXMsgConfig target = save(wxMsgConfig);
        if (target.getWxMsgId() > 0) {
            return target;
        }
        return null;
    }

    public WXMsgConfig updateWXMsgConfig(final WXMsgConfig wxMsgConfig) {
        if (null == wxMsgConfig) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "WXMsgConfig is null");
        }
        logger.info(JSONUtils.toString(wxMsgConfig));
        WXMsgConfig target = get(wxMsgConfig.getWxMsgId());
        if (null == target) {
            throw new DAOException(DAOException.ENTITIY_NOT_FOUND, "WXMsgConfig not exist!");
        }
        UserRoleToken urt = UserRoleToken.getCurrent();
        wxMsgConfig.setUserId(urt.getId());
        wxMsgConfig.setUserName(urt.getUserName());
        wxMsgConfig.setModifyDate(new Date());
        BeanUtils.map(wxMsgConfig, target);
        target = update(target);
        return target;
    }

    /**
     * [运营平台] 微信消息查询
     *
     * @param appId
     * @param start
     * @param limit
     * @return
     */
    public QueryResult<WXMsgConfig> queryWXConfigByStartAndLimit(final String appId, final int start, final int limit) {
        HibernateStatelessResultAction<QueryResult<WXMsgConfig>> action = new AbstractHibernateStatelessResultAction<QueryResult<WXMsgConfig>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                long total = 0;
                StringBuilder hql = new StringBuilder("FROM WXMsgConfig w WHERE 1=1 ");
                HashMap<String, Object> params = new HashMap<String, Object>();
                if (!StringUtils.isEmpty(appId)) {
                    hql.append(" and w.appId = :appId");
                    params.put("appId", appId);
                }

                Query query = ss.createQuery("SELECT count(*) " + hql.toString());
                query.setProperties(params);
                total = (long) query.uniqueResult();//获取总条数

                query = ss.createQuery("SELECT w " + hql.toString());
                query.setProperties(params);
                query.setFirstResult(start);
                query.setMaxResults(limit);
                setResult(new QueryResult<WXMsgConfig>(total, query.getFirstResult(), query.getMaxResults(), query.list()));
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }


}
