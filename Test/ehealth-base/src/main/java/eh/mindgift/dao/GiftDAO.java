package eh.mindgift.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import eh.entity.mindgift.Gift;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class GiftDAO extends
        HibernateSupportDelegateDAO<Gift> {
    private static final Log logger = LogFactory.getLog(GiftDAO.class);

    public GiftDAO() {
        super();
        this.setEntityName(Gift.class.getName());
        this.setKeyField("giftId");
    }

    @DAOMethod(sql = "from Gift where giftStatus=1")
    public abstract List<Gift> findEffectiveGifts(long start, int limit);


    @DAOMethod(sql = " from Gift order by giftId desc,giftStatus desc")
    public abstract List<Gift> findALLGiftsByLimit(int start, int limit);


    public QueryResult<Gift> queryGiftByGiftTypeAndGiftStatus (Integer giftType, Integer giftStatus, final int start, final int limit) {
        StringBuffer sb = new StringBuffer(" from Gift where 1=1");
        final Map<String, Object> params = new HashMap<String, Object>();
        if (giftType != null) {
            sb.append(" and giftType=:giftType");
            params.put("giftType", giftType);
        }
        if (giftStatus != null) {
            sb.append(" and giftStatus=:giftStatus");
            params.put("giftStatus", giftStatus);
        }
        final String hql = sb.toString();
        HibernateStatelessResultAction<QueryResult<Gift>> action = new AbstractHibernateStatelessResultAction<QueryResult<Gift>>() {
            @SuppressWarnings("unchecked")
			@Override
            public void execute(StatelessSession ss) throws Exception {
                Query query = ss.createQuery("SELECT count(*) " + hql);
                query.setProperties(params);
                long total = (long) query.uniqueResult();//获取总条数
                query = ss.createQuery(hql + " order by giftId asc,giftStatus desc");
                query.setProperties(params);
                query.setFirstResult(start);
                query.setMaxResults(limit);
                setResult(new QueryResult<Gift>(total, start, limit, query.list()));
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }


}

