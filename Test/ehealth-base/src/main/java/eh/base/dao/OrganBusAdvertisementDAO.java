package eh.base.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.annotation.RpcService;
import eh.entity.base.OrganBusAdvertisement;
import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.List;

/**
 * @author jianghc
 * @create 2016-11-10 10:59
 **/
public abstract class OrganBusAdvertisementDAO extends HibernateSupportDelegateDAO<OrganBusAdvertisement> {

    private static final Logger logger = Logger.getLogger(OrganBusAdvertisementDAO.class);


    public OrganBusAdvertisementDAO(){
        super();
        this.setEntityName(OrganBusAdvertisement.class.getName());
        this.setKeyField("id");
    }

    @RpcService
    @DAOMethod(sql = " from OrganBusAdvertisement order by organId,busType")
    public abstract List<OrganBusAdvertisement> findAllAdvertisements(@DAOParam(pageStart = true) int start,
                                                                      @DAOParam(pageLimit = true) int limit);

    @RpcService
    @DAOMethod(sql = " from OrganBusAdvertisement where organId=:organId order by busType")
    public abstract List<OrganBusAdvertisement> findAllAdvertisementsByOrganId(@DAOParam("organId") Integer organId);

    @RpcService
    @DAOMethod(sql = " from OrganBusAdvertisement where id=:id")
    public abstract OrganBusAdvertisement getbyId(@DAOParam("id") Integer id);

    @RpcService
    @DAOMethod(sql = " delete from OrganBusAdvertisement where id=:id")
    public abstract void deleteById(@DAOParam("id") Integer id);

    @RpcService
    @DAOMethod(sql = "from OrganBusAdvertisement")
    public abstract List<OrganBusAdvertisement> findAll();

    @DAOMethod
    public abstract OrganBusAdvertisement getByOrganIdAndBusType(Integer organId,Integer busType);


    @RpcService
    public QueryResult<OrganBusAdvertisement> findByOrganAndBusType(Integer organId,Integer busType,final int start,final int limit){
        final StringBuilder sbHQL = new StringBuilder(" from OrganBusAdvertisement where 1=1 ");
        if(organId!=null){
            sbHQL.append(" and organId=").append(organId);
        }
        if (busType!=null){
            sbHQL.append(" and busType=").append(busType);
        }
        HibernateStatelessResultAction<QueryResult<OrganBusAdvertisement>> action =
                new AbstractHibernateStatelessResultAction<QueryResult<OrganBusAdvertisement>>() {
                    public void execute(StatelessSession ss) throws DAOException {
                        Query countQuery = ss.createQuery("select count(*) " + sbHQL.toString());
                        Long total = (Long) countQuery.uniqueResult();
                        Query query = ss.createQuery(sbHQL.append(" order by organId,busType").toString());
                        query.setFirstResult(start);
                        query.setMaxResults(limit);
                        setResult(new QueryResult<OrganBusAdvertisement>(total, query.getFirstResult(), query.getMaxResults(), query.list()));
                    }
                };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();

    }


}
