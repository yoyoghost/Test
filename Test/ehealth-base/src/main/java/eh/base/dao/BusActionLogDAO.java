package eh.base.dao;

import ctd.account.UserRoleToken;
import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.converter.support.StringToDate;
import eh.entity.base.BusActionLog;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.Date;
import java.util.List;

/**
 * Created by houxr on 2016/7/4.
 * 业务操作 日志记录
 */
public abstract class BusActionLogDAO extends HibernateSupportDelegateDAO<BusActionLog> {

    public BusActionLogDAO() {
        super();
        setEntityName(BusActionLog.class.getName());
        setKeyField("actionLogId");
    }

    public void saveBusActionLog(BusActionLog actionLog) {
        this.save(actionLog);
    }


    /**
     * 获取所有 日志类型
     *
     * @return
     */
    @DAOMethod(sql = "select DISTINCT actionType from BusActionLog")
    public abstract List<String> findActionTypeFromBusActionLog();


    /**
     * 业务日志查询
     *
     * @param actionType    业务类型
     * @param startDt       操作起始时间
     * @param endDt         操作结束时间
     * @param keyword       关键字：操作用户 or 姓名
     * @param actionContent 操作内容
     * @param start         起始页
     * @param limit         条数
     * @return
     * @author houxr
     */
    public QueryResult<BusActionLog> queryBusActionLogByStartAndLimit(final String actionType,
                                                                      final String startDt,
                                                                      final String endDt,
                                                                      final String keyword,
                                                                      final String actionContent,
                                                                      final int start, final int limit) {
        final Date startTime = new StringToDate().convert(startDt + " 00:00:00");
        final Date endTime = new StringToDate().convert(endDt + " 23:59:59");
        HibernateStatelessResultAction<QueryResult<BusActionLog>> action =
                new AbstractHibernateStatelessResultAction<QueryResult<BusActionLog>>() {
                    @SuppressWarnings("unchecked")
                    public void execute(StatelessSession ss) throws DAOException {
                        StringBuilder hql = new StringBuilder(" from BusActionLog where 1=1 and actionTime >=:startTime and actionTime <=:endTime ");
                        if (!StringUtils.isEmpty(actionType)) {
                            hql.append(" and actionType =:actionType");
                        }
                        if (!StringUtils.isEmpty(actionContent)) {
                            hql.append(" and actionContent like :actionContent ");
                        }
                        Integer userId = null;
                        if (!StringUtils.isEmpty(keyword)) {
                            try {
                                userId = Integer.valueOf(keyword);
                            } catch (Throwable throwable) {
                                userId = null;
                            }
                            hql.append(" and (");
                            hql.append(" userName like :keyword ");
                            if (userId != null)
                                hql.append(" or userId =:userId");
                            hql.append(")");
                        }
                        hql.append(" order by actionLogId desc");
                        Query countQuery = ss.createQuery("select count(*) " + hql.toString());
                        countQuery.setParameter("startTime", startTime);
                        countQuery.setParameter("endTime", endTime);
                        if (!StringUtils.isEmpty(actionType)) {
                            countQuery.setParameter("actionType", actionType);
                        }
                        if (!StringUtils.isEmpty(actionContent)) {
                            countQuery.setParameter("actionContent", "%" + actionContent + "%");
                        }
                        if (userId != null) {
                            countQuery.setParameter("userId", userId);
                        }
                        if (!StringUtils.isEmpty(keyword)) {
                            countQuery.setParameter("keyword", "%" + keyword + "%");
                        }
                        Long total = (Long) countQuery.uniqueResult();

                        Query query = ss.createQuery(hql.toString());
                        query.setParameter("startTime", startTime);
                        query.setParameter("endTime", endTime);
                        if (!StringUtils.isEmpty(actionType)) {
                            query.setParameter("actionType", actionType);
                        }
                        if (!StringUtils.isEmpty(actionContent)) {
                            query.setParameter("actionContent", "%" + actionContent + "%");
                        }
                        if (userId != null) {
                            query.setParameter("userId", userId);
                        }
                        if (!StringUtils.isEmpty(keyword)) {
                            query.setParameter("keyword", "%" + keyword + "%");
                        }
                        query.setFirstResult(start);
                        query.setMaxResults(limit);
                        List<BusActionLog> list = query.list();
                        setResult(new QueryResult<BusActionLog>(total, query.getFirstResult(), query.getMaxResults(), list));
                    }
                };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 运营平台操作日志记录
     *
     * @param actionLog
     */
    public void saveOperatorLog(BusActionLog actionLog) {
        if (null == actionLog) {
            throw new DAOException(DAOException.VALUE_NEEDED, "日志记录不能为空");
        }
        UserRoleToken urt = UserRoleToken.getCurrent();
        BusActionLogDAO actionLogDAO = DAOFactory.getDAO(BusActionLogDAO.class);
        actionLog.setActionTime(new Date());
        actionLog.setUserId(urt.getId());
        actionLog.setUserName(urt.getUserName() + (StringUtils.equals(urt.getManageUnit(), "eh") ? "" : "(" + urt.getManageUnit() + ")"));
        actionLog.setIpAddress(urt.getLastIPAddress());
        actionLog.setBizId(actionLog.getBizId());
        actionLog.setBizClass(actionLog.getBizClass());
        actionLog.setActionType(actionLog.getActionType());
        actionLog.setActionContent(actionLog.getActionContent());
        actionLog.setExecuteTime(1);
        actionLogDAO.saveBusActionLog(actionLog);
    }

    /**
     * 操作服务日志记录
     *
     * @param actionType 业务类型
     * @param bizId      业务id
     * @param bizClass   操作业务对象
     * @param content    记录日志内容
     */
    public void recordLog(String actionType, String bizId, String bizClass, String content) {
        BusActionLog busActionLog = new BusActionLog();
        busActionLog.setBizClass(bizClass);
        busActionLog.setActionType(actionType);
        busActionLog.setBizId(bizId);
        busActionLog.setActionContent(content);
        this.saveOperatorLog(busActionLog);
    }


}


