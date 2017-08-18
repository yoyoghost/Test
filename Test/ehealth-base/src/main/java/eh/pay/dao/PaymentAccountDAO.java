package eh.pay.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.pay.PaymentAccount;

import java.util.List;

/**
 * @author jianghc
 * @create 2017-07-18 17:17
 **/
public abstract class PaymentAccountDAO extends HibernateSupportDelegateDAO<PaymentAccount> {
    public PaymentAccountDAO() {
        super();
        setEntityName(PaymentAccount.class.getName());
        setKeyField("id");
    }
    @DAOMethod
    public abstract List<PaymentAccount> findByPaymentType(Integer paymentType);

    @DAOMethod(sql = " from PaymentAccount order By paymentType,id",limit = 0)
    public abstract List<PaymentAccount> findAllAccount();


    @DAOMethod(sql = " from PaymentAccount where paymentType=:paymentType",limit = 0)
    public abstract List<PaymentAccount> findAllAccountByPaymentType(@DAOParam("paymentType") Integer paymentType);



}
