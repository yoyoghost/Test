package eh.pay.service;

import com.alibaba.druid.util.StringUtils;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.entity.pay.PaymentClient;
import eh.pay.dao.PaymentClientDAO;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author jianghc
 * @create 2017-07-27 19:44
 **/
public class PaymentClientService {

    @RpcService
    public List<PaymentClient> findByClientId(Integer clientId) {
        if (clientId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "client is require");
        }
        return DAOFactory.getDAO(PaymentClientDAO.class).findByClientId(clientId);
    }

    @RpcService
    public void saveOrUpdatePaymentClients(Integer clientId, List<PaymentClient> clients) {
        if (clientId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "client is require");
        }
        PaymentClientDAO paymentClientDAO = DAOFactory.getDAO(PaymentClientDAO.class);
        if (clients == null) {
            paymentClientDAO.deleteByClientId(clientId);
        }
        List<PaymentClient> olds = paymentClientDAO.findByClientId(clientId);
        if (olds == null) {
            for (PaymentClient pc : clients) {
                if (pc.getPaymentType() != null) {
                    pc.setId(null);
                }
            }
        } else {
            Map<Integer, PaymentClient> map = new HashMap<Integer, PaymentClient>();
            for (PaymentClient o : olds) {
                map.put(o.getPaymentType(), o);
            }
            for (PaymentClient pc : clients) {
                Integer pt = pc.getPaymentType();
                String payWay = pc.getPayWay();
                if (!StringUtils.isEmpty(payWay)) {
                    PaymentClient old = map.get(pt);
                    if (old == null) {
                        pc.setId(null);
                        paymentClientDAO.save(pc);
                    } else {
                        paymentClientDAO.updatePayWayByClientIdAndPaymentType(payWay, clientId, pt);
                    }
                } else {
                    paymentClientDAO.deleteByClientIdAndPaymentType(clientId, pt);
                }
            }
        }
    }


}
