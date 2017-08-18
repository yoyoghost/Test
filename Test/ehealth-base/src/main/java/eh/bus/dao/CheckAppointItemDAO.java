package eh.bus.dao;

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
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.dao.OrganCheckItemDAO;
import eh.entity.bus.CheckAppointItem;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class CheckAppointItemDAO extends
        HibernateSupportDelegateDAO<CheckAppointItem> {

    private static final Logger logger = Logger.getLogger(AppointSourceDAO.class);

    public CheckAppointItemDAO() {
        super();
        this.setEntityName(CheckAppointItem.class.getName());
        this.setKeyField("checkAppointId");
    }

    /**
     * 根据organId查询医技检查项目名称
     *
     * @param organId
     * @return
     */
    @RpcService
    @DAOMethod(sql = "select checkAppointName From CheckAppointItem where organId=:organId")
    public abstract List<String> findCheckAppointByOrganId(@DAOParam("organId") Integer organId);


    protected void validateCheckAppointItem(final CheckAppointItem checkAppointItem) {
        if (checkAppointItem.getOrganId() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organId is required.");
        }
        if (StringUtils.isEmpty(checkAppointItem.getCheckAppointName())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "checkAppointName is required.");
        }
        if (StringUtils.isEmpty(checkAppointItem.getCheckRoom())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "checkRoom is required.");
        }
    }


    /**
     * 新增 机构检查队列
     *
     * @param checkAppointItem 检查队列
     * @return
     */
    @RpcService
    public CheckAppointItem addCheckAppointItem(final CheckAppointItem checkAppointItem) {
        logger.info("新增检查队列[addCheckAppointItem]:CheckAppointItem:" + JSONUtils.toString(checkAppointItem));
        validateCheckAppointItem(checkAppointItem);
        return save(checkAppointItem);
    }

    /**
     * 更新 检查队列
     *
     * @param checkAppointItem
     * @return
     */
    @RpcService
    public CheckAppointItem updateCheckAppointItem(final CheckAppointItem checkAppointItem) {
        logger.info("update检查队列[updateCheckAppointItem]:CheckAppointItem:" + JSONUtils.toString(checkAppointItem));
        if (null == checkAppointItem) {
            throw new DAOException(DAOException.VALUE_NEEDED, "checkAppointItem is null.");
        }
        if (null == checkAppointItem.getCheckAppointId()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "checkAppointId is null.");
        }
        if (null == checkAppointItem.getOrganId()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organId is null.");
        }
        CheckAppointItem target = get(checkAppointItem.getCheckAppointId());
        if (null == target) {
            throw new DAOException(DAOException.ENTITIY_NOT_FOUND, "CheckAppointItem not exist!");
        }
        BeanUtils.map(checkAppointItem, target);
        validateCheckAppointItem(checkAppointItem);
        return target = update(target);
    }

    /**
     * 根据机构查询检查队列列表
     *
     * @param organId
     * @return
     */
    @RpcService
    @DAOMethod(limit = 10000)
    public abstract List<CheckAppointItem> findByOrganId(Integer organId);

    /**
     * 运营平台 查询调用
     *
     * @param organId 机构内码
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    public QueryResult<Map<String, Object>> queryByOrganId(final Integer organId,
                                                           final int start,
                                                           final int limit) {
        HibernateStatelessResultAction<QueryResult<Map<String, Object>>> action = new AbstractHibernateStatelessResultAction<QueryResult<Map<String, Object>>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = "from CheckAppointItem c where c.organId=:organId ";
                Query query = ss.createQuery("select count(*) " + hql);
                query.setParameter("organId", organId);
                Long total = (Long) query.uniqueResult();
                query = ss.createQuery(hql);
                query.setParameter("organId", organId);
                query.setFirstResult(start);
                query.setMaxResults(limit);
                List<Map<String, Object>> items = new ArrayList<>();
                List<CheckAppointItem> results = query.list();
                OrganCheckItemDAO dao = DAOFactory.getDAO(OrganCheckItemDAO.class);
                for (CheckAppointItem checkAppointItem : results) {
                    Map<String, Object> item = new HashMap<String, Object>();
                    item.put("checkAppointItem", checkAppointItem);
                    item.put("organCheckItems", dao.findByCheckAppointId(checkAppointItem.getCheckAppointId()));
                    items.add(item);
                }
                setResult(new QueryResult<>(total, start, limit, items));
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

}
