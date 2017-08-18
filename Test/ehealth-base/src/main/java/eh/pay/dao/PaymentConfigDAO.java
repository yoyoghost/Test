package eh.pay.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import eh.entity.pay.PaymentConfig;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author jianghc
 * @create 2017-07-18 17:21
 **/
public abstract class PaymentConfigDAO extends HibernateSupportDelegateDAO<PaymentConfig> {
    public PaymentConfigDAO() {
        super();
        setEntityName(PaymentConfig.class.getName());
        setKeyField("id");
    }


    /**
     * 获取个性化支付列表（客户端）
     *
     * @return
     */
    @DAOMethod(sql = " from PaymentConfig where isDefault =0  and clientId=:clientId and baseOrganID =:baseOrganID and businessKey=:businessKey and paymentType in(:paymentTypes)")
    public abstract List<PaymentConfig> findIndividualizationPayments(@DAOParam("clientId") Integer clientId, @DAOParam("baseOrganID") Integer baseOrganID,
                                                                      @DAOParam("businessKey") String businessKey, @DAOParam("paymentTypes") List<Integer> paymentTypes);

    /**
     * 获取默认支付列表（客户端）
     *
     * @return
     */
    @DAOMethod(sql = " from PaymentConfig where isDefault =1 and baseOrganID =:baseOrganID and businessKey=:businessKey and paymentType in(:paymentTypes)")
    public abstract List<PaymentConfig> findDefaultPayments(@DAOParam("baseOrganID") Integer baseOrganID
            , @DAOParam("businessKey") String businessKey, @DAOParam("paymentTypes") List<Integer> paymentTypes);

    /**
     * 获取个性化支付方式（客户端）
     *
     * @return
     */
    @DAOMethod(sql = " from PaymentConfig where isDefault =0  and clientId=:clientId and baseOrganID =:baseOrganID and businessKey=:businessKey and paymentType =:paymentType")
    public abstract PaymentConfig getIndividualizationPayment(@DAOParam("clientId") Integer clientId, @DAOParam("baseOrganID") Integer baseOrganID,
                                                              @DAOParam("businessKey") String businessKey, @DAOParam("paymentType") Integer paymentType);


    /**
     * 获取默认支付方式（客户端）
     *
     * @return
     */
    @DAOMethod(sql = " from PaymentConfig where isDefault =1 and baseOrganID =:baseOrganID and paymentType =:paymentTypes")
    public abstract PaymentConfig getDefaultPayment(@DAOParam("baseOrganID") Integer baseOrganID
            , @DAOParam("paymentTypes") Integer paymentTypes);

    public QueryResult<PaymentConfig> queryPaymentConfigs(final Integer clientId, final Integer baseOrganID, final Integer isDefault, final int start, final int limit) {

        HibernateStatelessResultAction<QueryResult<PaymentConfig>> action = new AbstractHibernateStatelessResultAction<QueryResult<PaymentConfig>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                Integer df = (isDefault == null ? 1 : isDefault);
                Map<String, Object> parameters = new HashMap<String, Object>();
                parameters.put("isDefault", isDefault);
                StringBuffer hql = new StringBuffer(" from PaymentConfig where isDefault=:isDefault");
                if (df.equals(0) && clientId != null) {
                    hql.append(" and clientId=:clientId");
                    parameters.put("clientId", clientId);
                }
                if (baseOrganID != null) {
                    hql.append(" and baseOrganID=:baseOrganID");
                    parameters.put("baseOrganID", baseOrganID);
                }
                hql.append(" group by clientId,clientName,baseOrganID,baseOrganName");
                Query cQuery = ss.createQuery("select 1" + hql);
                Query query = ss.createQuery(" select new eh.entity.pay.PaymentConfig(clientId,clientName,baseOrganID,baseOrganName)"+hql );
                query.setFirstResult(start);
                query.setMaxResults(limit);
                for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                    cQuery.setParameter(entry.getKey(), entry.getValue());
                    query.setParameter(entry.getKey(), entry.getValue());
                }
                List<Integer> cList = cQuery.list();
                Integer c = 0;
                for (Integer cl:cList){
                    c+=(cl==null?0:cl);
                }
                setResult(new QueryResult<PaymentConfig>(c, start, limit, query.list()));
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }


    @DAOMethod(orderBy = " businessKey,paymentType",limit = 0)
    public abstract List<PaymentConfig> findByClientIdAndBaseOrganID(Integer clientId,Integer BaseOrganID);


    @DAOMethod(sql = " from PaymentConfig where isDefault=1 and baseOrganID=:baseOrganID order by paymentType ")
    public abstract List<PaymentConfig> findDefaultByBaseOrganID(@DAOParam("baseOrganID") Integer baseOrganID);


    @DAOMethod(sql = " from PaymentConfig where isDefault=0 and clientId =:clientId and baseOrganID=:baseOrganID order by paymentType ")
    public abstract List<PaymentConfig> findIndividualizationByClientIdAndBaseOrganID(@DAOParam("clientId") Integer clientId,@DAOParam("baseOrganID") Integer baseOrganID);


    @DAOMethod(sql = " from PaymentConfig where isDefault=0 and clientId =:clientId and baseOrganID=:baseOrganID and businessKey=:businessKey order by paymentType ")
    public abstract List<PaymentConfig> findIndividualizationByClientIdAndBaseOrganIDAndBusinessKey(@DAOParam("clientId") Integer clientId,@DAOParam("baseOrganID") Integer baseOrganID,@DAOParam("businessKey")String businessKey);

    @DAOMethod(sql = " delete from PaymentConfig where isDefault=0 and clientId =:clientId and baseOrganID=:baseOrganID and businessKey=:businessKey")
    public abstract void deleteIndividualizationByClientIdAndBaseOrganIDAndBusinessKey(@DAOParam("clientId") Integer clientId,@DAOParam("baseOrganID") Integer baseOrganID,@DAOParam("businessKey")String businessKey);



}
