package eh.wxpay.bus.callback;

import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import eh.base.constant.BussTypeConstant;
import eh.bus.asyndobuss.bean.BussCreateEvent;
import eh.bus.asyndobuss.service.AsynDoBussService;
import eh.coupon.service.CouponService;
import eh.entity.mpi.SignRecord;
import eh.mpi.constant.SignRecordConstant;
import eh.mpi.dao.SignRecordDAO;
import eh.push.SmsPushService;
import eh.utils.ValidateUtil;
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
public class SignPayCallback implements PayCallback<PayResult> {
    private static final Logger logger = LoggerFactory.getLogger(SignPayCallback.class);

    @Override
    public boolean handle(PayResult payResult) throws Exception {
        Integer busId = payResult.getBusId();
        String outTradeNo = payResult.getOutTradeNo();
        Date paymentDate = payResult.getPaymentDate();
        String tradeNo = payResult.getTradeNo();

        SignRecordDAO signRecordDAO = DAOFactory.getDAO(SignRecordDAO.class);
        SignRecord signRecord = signRecordDAO.get(busId);
        if (signRecord == null) {
            logger.error("doBusinessAfterOrderSuccess busObject not exists, busId[{}]", busId);
            return false;
        }
        Integer payflag = signRecord.getPayFlag();
        if (payflag != null && payflag == 1) {//已处理
            logger.info("doBusinessAfterOrderSuccess payflag has been set true, busId[{}]", busId);
            return true;
        }
        signRecord.setPayFlag(1);
        signRecord.setPaymentDate(paymentDate);
        signRecord.setTradeNo(tradeNo);
        signRecord.setOutTradeNo(outTradeNo);
        signRecord.setRecordStatus(SignRecordConstant.RECORD_STATUS_APPLYING);
        signRecordDAO.update(signRecord);
        AppContextHolder.getBean("asynDoBussService", AsynDoBussService.class).fireEvent(new BussCreateEvent(signRecord, BussTypeConstant.SIGN));
        AppContextHolder.getBean("eh.smsPushService", SmsPushService.class).pushMsgData2Ons(signRecord.getSignRecordId(), signRecord.getOrgan(), "SignMessage", "", 0);
        if(ValidateUtil.notNullAndZeroInteger(signRecord.getCouponId()) && signRecord.getCouponId()!=-1){
            CouponService couponService = AppContextHolder.getBean("couponService", CouponService.class);
            couponService.useCouponById(signRecord.getCouponId());
        }
        return true;

    }
}
