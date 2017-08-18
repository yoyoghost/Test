package eh.pay.service;

import ctd.persistence.DAOFactory;
import ctd.util.annotation.RpcService;
import eh.entity.pay.PaymentBusiness;
import eh.pay.dao.PaymentBusinessDAO;

import java.util.List;

/**
 * @author jianghc
 * @create 2017-07-19 16:39
 **/
public class PaymentBusinessService {
    @RpcService
    public List<PaymentBusiness> findAllPaymentBusiness(){
        return DAOFactory.getDAO(PaymentBusinessDAO.class).findAllPaymentBusiness();
    }
}
