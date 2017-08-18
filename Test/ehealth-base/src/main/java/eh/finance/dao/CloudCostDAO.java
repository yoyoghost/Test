package eh.finance.dao;

import com.alibaba.druid.util.StringUtils;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import eh.entity.finance.CloudCost;
import eh.utils.DateConversion;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author jianghc
 * @create 2017-04-26 14:51
 **/
public abstract class CloudCostDAO extends HibernateSupportDelegateDAO<CloudCost>{
    public CloudCostDAO() {
        super();
        setEntityName(CloudCost.class.getName());
        setKeyField("Id");
    }


    @DAOMethod(sql = " from CloudCost where bussType ='appoint' and billId=:billId")
    public abstract CloudCost getAppointCostByBillId(@DAOParam("billId") Integer billId);

    @DAOMethod(sql = " from CloudCost where bussType ='meet' and billId=:billId and subBillId=:subBillId")
    public abstract CloudCost getMeetCostByBillId(@DAOParam("billId") Integer billId,@DAOParam("subBillId") Integer subBillId);


    @DAOMethod(sql = " from CloudCost where bussType ='meet' and billId=:billId ")
    public abstract List<CloudCost> findMeetCostByBillId(@DAOParam("billId") Integer billId);


    public QueryResult<CloudCost> queryCloudCostByDateAndOrgan(final Integer superiorOrganId, final Date startDate, final Date endDate,String bussType,
                                                               Integer baseOrganId,String superiorDepartment,Integer payType,final int start,final int limit ){

        if (superiorOrganId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "superiorOrganId is require");
        }
        if (startDate == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "startDate is require");
        }
        if (endDate == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "endDate is require");
        }
        final StringBuffer hql =new StringBuffer(" from CloudCost where " +
                "superiorOrganId=:superiorOrganId and createDate>=:startDate and createDate<:endDate");
        final Map<String, Object> parameters = new HashMap<String, Object>();

        if (!StringUtils.isEmpty(bussType)) {
            hql.append(" and bussType=:bussType");
            parameters.put("bussType", bussType);
        }
        if (baseOrganId != null) {
            hql.append(" and baseOrganId=:baseOrganId");
            parameters.put("baseOrganId", baseOrganId);
        }
        if (!StringUtils.isEmpty(superiorDepartment)) {
            hql.append(" and superiorDepartment=:superiorDepartment");
            parameters.put("superiorDepartment", superiorDepartment);
        }
        if (payType != null) {
            hql.append(" and payType=:payType");
            parameters.put("payType", payType);
        }
        HibernateStatelessResultAction<QueryResult<CloudCost>> action
                = new AbstractHibernateStatelessResultAction<QueryResult<CloudCost>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                Query cQuery = ss.createQuery(" select count(*)"+hql);
                Query query = ss.createQuery(hql+" order By id desc");
                query.setFirstResult(start);
                query.setMaxResults(limit);
                query.setParameter("superiorOrganId",superiorOrganId);
                cQuery.setParameter("superiorOrganId",superiorOrganId);
                cQuery.setParameter("startDate",startDate);
                query.setParameter("startDate",startDate);
                Date lDate = DateConversion.getDateAftXDays(endDate,1);
                cQuery.setParameter("endDate",lDate);
                query.setParameter("endDate",lDate);
                for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                    cQuery.setParameter(entry.getKey(),entry.getValue());
                    query.setParameter(entry.getKey(),entry.getValue());
                }
                Long total = (Long) cQuery.uniqueResult();
                setResult(new QueryResult<CloudCost>(total, start, limit, query.list()));
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }



}
