package eh.base.dao;

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
import eh.entity.base.CheckItem;
import eh.entity.base.OrganCheckItem;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class OrganCheckItemDAO extends HibernateSupportDelegateDAO<OrganCheckItem> {

    private static final Logger logger = Logger.getLogger(OrganCheckItemDAO.class);

    public OrganCheckItemDAO() {
        super();
        this.setEntityName(OrganCheckItem.class.getName());
        this.setKeyField("organItemId");
    }

    @RpcService
    @DAOMethod
    public abstract OrganCheckItem getByOrganItemId(Integer organItemId);

    @RpcService
    @DAOMethod(orderBy = "organId")
    public abstract List<OrganCheckItem> findByCheckItemId(Integer checkItemId);

    @DAOMethod
    public abstract List<OrganCheckItem> findByOrganId(Integer organId);

    public List<OrganCheckItem> findByTwoConnect(final Integer checkItemId, final String addrArea) {
        HibernateStatelessResultAction<List<OrganCheckItem>> action = new AbstractHibernateStatelessResultAction<List<OrganCheckItem>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                String hql = "select i from OrganCheckItem i,Organ o where i.checkItemId=:checkItemId "
                        + "and i.organId=o.organId and o.addrArea like :addrArea";
                Query q = ss.createQuery(hql);
                q.setParameter("checkItemId", checkItemId);
                q.setParameter("addrArea", addrArea + "%");//去除地区限制 后期会加上去
                //q.setParameter("addrArea", "" + "%");
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    @RpcService
    @DAOMethod(sql = "from OrganCheckItem where organId=:organId and checkItemId=:checkItemId and checkAppointId=:checkAppointId")
    public abstract OrganCheckItem getByOrganIdAndCheckItemIdAndCheckAppointId(@DAOParam("organId") Integer organId,
                                                                               @DAOParam("checkItemId") Integer checkItemId,
                                                                               @DAOParam("checkAppointId") Integer checkAppointId);

    /**
     * 获得指定机构，指定通用检查项目的机构检测项目，用于避免同一机构下出现多个相同的通用检查项目
     *
     * @param organId
     * @param checkItemId
     * @return
     * @deprecated 同一机构下允许出现相同的通用检查项目，只是不能出现在同一个队列中
     */
    @RpcService
    @DAOMethod
    public abstract List<OrganCheckItem> findByOrganIdAndCheckItemId(Integer organId, Integer checkItemId);

    /**
     * 查找未被指定机构使用的通用检查项目
     *
     * @param organId
     * @return
     */
    @RpcService
    public List<CheckItem> findUnusedCheckItemsByOrganId(final Integer organId) {
        HibernateStatelessResultAction<List<CheckItem>> action = new AbstractHibernateStatelessResultAction<List<CheckItem>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = new String("from CheckItem c where (select count(*) from OrganCheckItem where checkItemId=c.checkItemId and organId = :organId)=0");
                Query q = ss.createQuery(hql);
                q.setParameter("organId", organId);
                @SuppressWarnings("unchecked")
                List<CheckItem> list = q.list();

                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 增加 机构检查项目
     *
     * @param organCheckItem
     * @return
     */
    @RpcService
    public OrganCheckItem addOrganCheckItem(final OrganCheckItem organCheckItem) {
        logger.info("新增机构检查项目[addOrganCheckItem]:organCheckItem:" + JSONUtils.toString(organCheckItem));
        if (null == organCheckItem || null == organCheckItem.getOrganId()
                || null == organCheckItem.getOrganItemCode()
                || null == organCheckItem.getCheckItemId()
                || null == organCheckItem.getCheckAppointId()
                || null == organCheckItem.getCheckItemName()
                || null == organCheckItem.getCheckAddr()
                || null == organCheckItem.getCheckPrice()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "one or more parameter is required!");
        }
        //判断邵逸夫医院机构项目编码以A00开头：庆春A001|code 下沙A002|code
        if (organCheckItem.getOrganId() == 1) {
            if (!organCheckItem.getOrganItemCode().startsWith("A00")) {
                throw new DAOException(DAOException.VALIDATE_FALIED, "机构项目编码不是A00开头");
            }
        }
        //同一家机构同一个队列中不允许重复 checkItemId
        OrganCheckItem existed = getByOrganIdAndCheckItemIdAndCheckAppointId(organCheckItem.getOrganId(), organCheckItem.getCheckItemId(), organCheckItem.getCheckAppointId());
        if (existed != null) {
            throw new DAOException(609, "一个队列中不允许出现相同检查项目");
        }
        OrganCheckItem oci = save(organCheckItem);
        if (null == oci) {
            return null;
        }
        return oci;
    }

    /**
     * 更新 机构检查项目
     *
     * @param organCheckItem
     * @return
     */
    @RpcService
    public OrganCheckItem updateOrganCheckItem(final OrganCheckItem organCheckItem) {
        logger.info("更新机构检查项目[updateOrganCheckItem]:OrganCheckItem:" + JSONUtils.toString(organCheckItem));
        if (null == organCheckItem.getOrganItemId()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "OrganItemId is required!");
        }
        if (null == organCheckItem.getCheckPrice()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "CheckPrice is required!");
        }
        if (StringUtils.isEmpty(organCheckItem.getCheckItemName())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "CheckItemName is required!");
        }
        OrganCheckItem target = get(organCheckItem.getOrganItemId());
        if (null == target) {
            throw new DAOException(DAOException.ENTITIY_NOT_FOUND, "OrganCheckItem not exist!");
        }
        organCheckItem.setOrganId(null); // 机构不允许修改
        organCheckItem.setCheckItemId(null); // 通用检查项目不允许修改
        organCheckItem.setCheckAppointId(null); // 检查队列不允许修改
        BeanUtils.map(organCheckItem, target);
        return target = update(target);
    }

    @DAOMethod
    public abstract List<OrganCheckItem> findByCheckAppointId(Integer checkAppointId);

    /**
     * 运营平台调用 查询 机构检查项目
     *
     * @param organId 机构内码
     * @param start   起始位置
     * @param limit   分页
     * @return
     */
    @RpcService
    public QueryResult<Map<String, Object>> queryByOrganIdAndClass(final Integer organId,
                                                                   final String checkClass,
                                                                   final int start,
                                                                   final int limit) {
        HibernateStatelessResultAction<QueryResult<Map<String, Object>>> action = new AbstractHibernateStatelessResultAction<QueryResult<Map<String, Object>>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuffer hql = new StringBuffer("from OrganCheckItem o, CheckItem c, CheckAppointItem a where o.organId=:organId "
                        + "and o.checkItemId=c.checkItemId and o.checkAppointId=a.checkAppointId");
                if (StringUtils.isNotEmpty(checkClass)) {
                    hql.append(" and c.checkClass = :checkClass");
                }
                hql.append(" order by o.organItemId desc");
                Query query = ss.createQuery("select count(o.organItemId) " + hql.toString());
                query.setParameter("organId", organId);
                if (StringUtils.isNotEmpty(checkClass)) {
                    query.setParameter("checkClass", checkClass);
                }
                Long total = (Long) query.uniqueResult();
                query = ss.createQuery("select o, c, a " + hql.toString());
                query.setParameter("organId", organId);
                if (StringUtils.isNotEmpty(checkClass)) {
                    query.setParameter("checkClass", checkClass);
                }
                query.setFirstResult(start);
                query.setMaxResults(limit);
                List<Map<String, Object>> items = new ArrayList<>();
                List<Object[]> results = query.list();
                for (Object[] objects : results) {
                    Map<String, Object> item = new HashMap<String, Object>();
                    item.put("organCheckItem", objects[0]);
                    item.put("checkItem", objects[1]);
                    item.put("checkAppointItem", objects[2]);
                    items.add(item);
                }
                setResult(new QueryResult<>(total, start, limit, items));
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    public OrganCheckItem save(OrganCheckItem organCheckItem) {
        if (StringUtils.isEmpty(organCheckItem.getExtra())) {
            organCheckItem.setExtra("1");
        }
        return super.save(organCheckItem);
    }
}
