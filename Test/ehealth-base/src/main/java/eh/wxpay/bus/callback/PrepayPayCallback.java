package eh.wxpay.bus.callback;

import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import eh.bus.dao.PayBusinessDAO;
import eh.bus.service.payment.PaymentService;
import eh.entity.bus.PayBusiness;
import eh.wxpay.bus.callback.support.PayCallback;
import eh.wxpay.bus.callback.support.PayResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 命名规则： BusTypeEnum的code值 + PayCallback 后缀
 */
@Component
@Scope("prototype")
public class PrepayPayCallback implements PayCallback<PayResult> {
    private static final Logger logger = LoggerFactory.getLogger(PrepayPayCallback.class);

    @Override
    public boolean handle(PayResult payResult) throws Exception {
        Integer busId = payResult.getBusId();
        String outTradeNo = payResult.getOutTradeNo();
        String tradeNo = payResult.getTradeNo();
        Date paymentDate = payResult.getPaymentDate();

        PayBusinessDAO payDAO = DAOFactory.getDAO(PayBusinessDAO.class);
        PayBusiness pb = payDAO.getById(busId);
        if (pb == null) {
            logger.error("doBusinessAfterOrderSuccess busObject not exists, busId[{}]", busId);
            return false;
        }
        if (pb.getPayflag() != null && pb.getPayflag() >= 1) {
            logger.info("doBusinessAfterOrderSuccess payflag has been set true, busId[{}]", busId);
            return true;
        }
        pb.setPayflag(1);
        pb.setPaymentDate(paymentDate);
        pb.setOutTradeNo(outTradeNo);
        pb.setTradeNo(tradeNo);
        payDAO.update(pb);
        /*微信支付成功,调his住院预交款缴纳*/
        PaymentService paymentService = AppContextHolder.getBean("paymentService", PaymentService.class);
        paymentService.hisSettlement(pb);
        return true;
    }
}
