package eh.wxpay.bus.callback;

import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import eh.account.constant.ServerPriceConstant;
import eh.base.dao.DoctorAccountDAO;
import eh.bus.constant.EmergencyConstant;
import eh.bus.dao.EmergencyDao;
import eh.bus.push.MessagePushExecutorConstant;
import eh.coupon.service.CouponService;
import eh.entity.bus.Emergency;
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
public class EmergencyPayCallback implements PayCallback<PayResult> {
    private static final Logger logger = LoggerFactory.getLogger(ConsultPayCallback.class);
    private SmsPushService smsPushService = AppContextHolder.getBean("smsPushService", SmsPushService.class);

    @Override
    public boolean handle(PayResult payResult) throws Exception {
        Integer busId = payResult.getBusId();

        EmergencyDao emergencyDao = DAOFactory.getDAO(EmergencyDao.class);
        Emergency emergency = emergencyDao.get(busId);
        if (emergency == null) {
            logger.error("handle busObject not exists, busId[{}]", busId);
            return false;
        }
        if (emergency.getStatus() == 2) {//已处理
            logger.info("handle emergency has been set true, busId[{}]", busId);
            return true;//订单支付状态已经更新直接回写支付平台success
        }
        emergency.setStatus(EmergencyConstant.EMERGENCY_STATUS_PAID);
        emergency.setUpdateTime(new Date());
        emergencyDao.update(emergency);
        smsPushService.pushMsgData2Ons(busId, emergency.getOrganId(), MessagePushExecutorConstant.EMERGENCY_PAY_SUCCESS_REMIND, MessagePushExecutorConstant.EMERGENCY_PAY_SUCCESS_REMIND, null);

        // 给医生增加积分收入
        DAOFactory.getDAO(DoctorAccountDAO.class).addDoctorRevenue(emergency.getDoctorId(), ServerPriceConstant.ID_EMERGENCY_FINISH,
                busId, emergency.getActualPrice());
//        AppContextHolder.getBean("emergencyService", EmergencyService.class).paySuccess(busId);
        if (ValidateUtil.notNullAndZeroInteger(emergency.getCouponId()) && emergency.getCouponId() != -1) {
            CouponService couponService = AppContextHolder.getBean("couponService", CouponService.class);
            couponService.useCouponById(emergency.getCouponId());
        }
        return true;
    }
}
