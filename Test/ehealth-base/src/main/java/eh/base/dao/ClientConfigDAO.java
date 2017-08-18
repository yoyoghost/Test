package eh.base.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import eh.entity.base.ClientConfig;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author jianghc
 * @create 2017-07-18 14:00
 **/
public abstract class ClientConfigDAO extends HibernateSupportDelegateDAO<ClientConfig> {
    public ClientConfigDAO() {
        super();
        setEntityName(ClientConfig.class.getName());
        setKeyField("id");
    }


    /**
     * 分页查询
     *
     * @param type
     * @param start
     * @param limit
     * @return
     */
    public QueryResult<ClientConfig> queryByType(final Integer type, final int start, final int limit) {
        final HibernateStatelessResultAction<QueryResult<ClientConfig>> action = new AbstractHibernateStatelessResultAction<QueryResult<ClientConfig>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuffer hql = new StringBuffer(" from ClientConfig where 1=1");
                if (type != null) {
                    hql.append(" and type=:type");
                }
                Query query = ss.createQuery("select count(*)" + hql.toString());
                if (type != null) {
                    query.setParameter("type", type);
                }
                long l = (long) query.uniqueResult();
                if (l <= 0) {
                    setResult(new QueryResult<ClientConfig>(l, start, limit, new ArrayList<ClientConfig>()));
                }
                query = ss.createQuery(hql + " order by id");
                if (type != null) {
                    query.setParameter("type", type);
                }
                query.setMaxResults(limit);
                query.setFirstResult(start);
                setResult(new QueryResult<ClientConfig>(l, start, limit, query.list()));
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();

    }

    @DAOMethod
    public abstract ClientConfig getByTypeAndClientId(Integer type, Integer clientId);


    @DAOMethod(sql = " from ClientConfig order By type")
    public abstract List<ClientConfig> findAllClientByType();


    @Override
    public ClientConfig save(ClientConfig o) throws DAOException {
        o.setCreateTime(new Date());
        o.setLastModify(new Date());
        return super.save(o);
    }

    @Override
    public ClientConfig update(ClientConfig o) throws DAOException {
        o.setLastModify(new Date());
        return super.update(o);
    }


    public ClientConfig addOneClient(Integer type, String clientClass,
                                     Integer clientId, String clientName) {
        return this.save(new ClientConfig(type,clientClass,clientId,clientName));
    }
}
