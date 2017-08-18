package eh.wxpay.bus.callback;

import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import eh.bus.constant.ConsultConstant;
import eh.bus.dao.ConsultDAO;
import eh.bus.service.consult.RequestConsultService;
import eh.coupon.service.CouponService;
import eh.entity.bus.Consult;
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
public class ConsultPayCallback implements PayCallback<PayResult> {
    private static final Logger logger = LoggerFactory.getLogger(ConsultPayCallback.class);

    @Override
    public boolean handle(PayResult payResult) throws Exception {
        Integer busId = payResult.getBusId();
        String outTradeNo = payResult.getOutTradeNo();
        String tradeNo = payResult.getTradeNo();
        Date paymentDate = payResult.getPaymentDate();

        ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
        Consult c = consultDAO.getById(busId);
        if (c == null) {
            logger.error("handle busObject not exists, busId[{}]", busId);
            return false;
        }
        if (c.getPayflag() != null && c.getPayflag() == 1) {//已处理
            logger.info("handle payflag has been set true, busId[{}]", busId);
            return true;//订单支付状态已经更新直接回写支付平台success
        }
        Integer status = null;
        if(ConsultConstant.CONSULT_TYPE_PROFESSOR==c.getRequestMode()||
                ConsultConstant.CONSULT_TYPE_RECIPE == c.getRequestMode() ||
                ConsultConstant.CONSULT_TYPE_GRAPHIC == c.getRequestMode()){
            if(c.getTeams()){
                status = 1;
            }else{
                status = 3;
            }
        }else{
            status = 2;
        }
        if(status == null){
            logger.error("doBusinessAfterOrderSuccess consult status not exists, busId[{}]", busId);
            return false;
        }
        consultDAO.updatePayFlagByOutTradeNo(paymentDate, tradeNo, outTradeNo,status, busId);
        new RequestConsultService().doAfterPaySuccess(c.getConsultId());
        if(ValidateUtil.notNullAndZeroInteger(c.getCouponId()) && c.getCouponId()!=-1){
            CouponService couponService = AppContextHolder.getBean("couponService", CouponService.class);
            couponService.useCouponById(c.getCouponId());
        }
        return true;
    }
}
