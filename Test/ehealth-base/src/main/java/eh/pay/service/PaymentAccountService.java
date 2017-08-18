package eh.pay.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.entity.pay.PaymentAccount;
import eh.pay.dao.PaymentAccountDAO;

import java.util.List;

/**
 * @author jianghc
 * @create 2017-07-24 22:00
 **/
public class PaymentAccountService {
    @RpcService
    public  List<PaymentAccount> findAllAccount(){
        return DAOFactory.getDAO(PaymentAccountDAO.class).findAllAccount();
    }

    @RpcService
    public  List<PaymentAccount> findAllAccountByPaymentType(Integer paymentType){
        if(paymentType==null){
            throw new DAOException(DAOException.VALUE_NEEDED,"paymentType is require");
        }
        return DAOFactory.getDAO(PaymentAccountDAO.class).findAllAccountByPaymentType(paymentType);
    }

}
