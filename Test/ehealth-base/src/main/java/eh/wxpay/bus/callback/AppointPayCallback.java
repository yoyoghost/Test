package eh.wxpay.bus.callback;

import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import eh.bus.dao.AppointRecordDAO;
import eh.bus.his.service.AppointTodayBillService;
import eh.coupon.service.CouponService;
import eh.entity.bus.AppointRecord;
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
public class AppointPayCallback implements PayCallback<PayResult> {
    private static final Logger logger = LoggerFactory.getLogger(AppointPayCallback.class);
    private AppointTodayBillService appointTodayBillService = AppContextHolder.getBean("eh.billService", AppointTodayBillService.class);

    @Override
    public boolean handle(PayResult payResult) throws Exception {
        Integer busId = payResult.getBusId();
        String outTradeNo = payResult.getOutTradeNo();
        String tradeNo = payResult.getTradeNo();
        Date paymentDate = payResult.getPaymentDate();

        AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
        AppointRecord appointRecord = appointRecordDAO.getByAppointRecordId(busId);
        logger.info("预约支付成功回调"+appointRecord.getAppointRecordId());
        if (appointRecord == null) {
            logger.error("doBusinessAfterOrderSuccess busObject not exists, busId[{}]", busId);
            return false;
        }
        Integer payflag = appointRecord.getPayFlag();
        if (payflag != null && payflag >= 1) {//已处理
            logger.info("doBusinessAfterOrderSuccess payflag has been set true, busId[{}]", busId);
            return true;
        }

        appointRecord.setTradeNo(tradeNo);
        appointRecord.setPayFlag(1);
        appointRecord.setOutTradeNo(outTradeNo);
        appointRecord.setPaymentDate(paymentDate);
        int res = appointRecordDAO.updateRecordAfterPay(tradeNo, 1, paymentDate, outTradeNo, busId);
        if(res ==0){
            logger.info("支付重复回调busId[{}]", busId);
            return true;
        }
        //支付成功调用his结算接口
        appointTodayBillService.settleRegBill(appointRecord);
        if(ValidateUtil.notNullAndZeroInteger(appointRecord.getCouponId()) && appointRecord.getCouponId()!=-1){
            CouponService couponService = AppContextHolder.getBean("couponService", CouponService.class);
            couponService.useCouponById(appointRecord.getCouponId());
        }
        return true;
    }
}
