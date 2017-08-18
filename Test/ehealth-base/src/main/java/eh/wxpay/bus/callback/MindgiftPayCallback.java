package eh.wxpay.bus.callback;

import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import eh.coupon.service.CouponService;
import eh.entity.mindgift.MindGift;
import eh.mindgift.dao.MindGiftDAO;
import eh.mindgift.service.MindGiftMsgService;
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
public class MindgiftPayCallback implements PayCallback<PayResult> {
    private static final Logger logger = LoggerFactory.getLogger(MindgiftPayCallback.class);

    @Override
    public boolean handle(PayResult payResult) throws Exception {
        Integer busId = payResult.getBusId();
        String outTradeNo = payResult.getOutTradeNo();
        String tradeNo = payResult.getTradeNo();
        Date paymentDate = payResult.getPaymentDate();

        MindGiftDAO dao = DAOFactory.getDAO(MindGiftDAO.class);
        MindGift mind = dao.get(busId);
        if (mind == null) {
            logger.error("doBusinessAfterOrderSuccess busObject[mindGift] not exists, busId[{}]", busId);
            return false;
        }
        Integer payflag = mind.getPayFlag();
        if (payflag != null && payflag == 1) {//已处理
            logger.info("doBusinessAfterOrderSuccess payflag has been set true, busId[{}]", busId);
            return true;
        }
        // 支付成功后更新支付状态，并使用优惠劵
        dao.updatePayFlagByOutTradeNo(paymentDate, tradeNo, outTradeNo, busId);
        MindGiftMsgService.doAfterPaySuccess(mind.getMindGiftId());
        if(ValidateUtil.notNullAndZeroInteger(mind.getCouponId()) && mind.getCouponId()!=-1){
            CouponService couponService = AppContextHolder.getBean("couponService", CouponService.class);
            couponService.useCouponById(mind.getCouponId());
        }
        return true;
    }
}
