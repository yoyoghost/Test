package eh.pay.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.pay.PaymentClient;

import java.util.List;

/**
 * @author jianghc
 * @create 2017-07-19 13:47
 **/
public abstract class PaymentClientDAO extends HibernateSupportDelegateDAO<PaymentClient> {
    public PaymentClientDAO() {
        super();
        setEntityName(PaymentClient.class.getName());
        setKeyField("id");
    }

    @DAOMethod (sql = " select paymentType from PaymentClient where clientId=:clientId")
    public abstract List<Integer> findPaymentTypesByClientId(@DAOParam("clientId") Integer clientId);

    @DAOMethod
    public abstract PaymentClient getByClientIdAndPaymentType(Integer clientId,Integer paymentType);

    @DAOMethod
    public abstract List<PaymentClient> findByClientId(Integer clientId);

    @DAOMethod(sql = " delete from PaymentClient where clientId =:clientId ")
    public abstract void deleteByClientId(@DAOParam("clientId") Integer clientId);

    @DAOMethod(sql = " delete from PaymentClient where clientId =:clientId and paymentType=:paymentType ")
    public abstract void deleteByClientIdAndPaymentType(@DAOParam("clientId") Integer clientId,@DAOParam("paymentType")Integer paymentType);

    @DAOMethod(sql = " update PaymentClient set payWay=:payWay where clientId =:clientId and paymentType=:paymentType ")
    public abstract void updatePayWayByClientIdAndPaymentType(@DAOParam("payWay")String payWay,@DAOParam("clientId") Integer clientId,@DAOParam("paymentType")Integer paymentType);



}
