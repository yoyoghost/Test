package eh.wxpay.bus.callback;

import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import eh.bus.dao.TransferDAO;
import eh.entity.bus.Transfer;
import eh.push.SmsPushService;
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
public class TransferPayCallback implements PayCallback<PayResult> {
    private static final Logger logger = LoggerFactory.getLogger(TransferPayCallback.class);

    @Override
    public boolean handle(PayResult payResult) throws Exception {
        Integer busId = payResult.getBusId();
        String outTradeNo = payResult.getOutTradeNo();
        Date paymentDate = payResult.getPaymentDate();
        String tradeNo = payResult.getTradeNo();
        TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
        Transfer t = transferDAO.getById(busId);
        if (t == null) {
            logger.error("handle busObject not exists, busId[{}]", busId);
            return false;
        }
        if (t.getPayflag() != null && t.getPayflag() == 1) {//已处理
            logger.info("handle payflag has been set true, busId[{}]", busId);
            return true;
        }
        transferDAO.updatePayFlagByOutTradeNo(paymentDate, tradeNo, outTradeNo, busId);
        AppContextHolder.getBean("eh.smsPushService", SmsPushService.class).pushMsgData2Ons(t.getTransferId(), t.getTargetOrgan()==null?0:t.getTargetOrgan(), "PatTransferApply", "PatTransferApply", t.getDeviceId());
        return true;
    }
}
