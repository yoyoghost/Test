package eh.pay.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.pay.PaymentBusiness;

import java.util.List;

/**
 * @author jianghc
 * @create 2017-07-18 17:25
 **/
public abstract class PaymentBusinessDAO extends HibernateSupportDelegateDAO<PaymentBusiness> {
    public PaymentBusinessDAO() {
        super();
        setEntityName(PaymentBusiness.class.getName());
        setKeyField("id");
    }
    @DAOMethod
    public abstract PaymentBusiness getByBusinessTypeKey(String businessTypeKey);

    @DAOMethod(sql = " from PaymentBusiness order by id")
    public abstract List<PaymentBusiness> findAllPaymentBusiness();


}
