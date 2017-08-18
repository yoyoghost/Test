package eh.wxpay.bus.service;

import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import eh.coupon.constant.CouponConstant;
import eh.entity.bus.Order;
import eh.entity.bus.pay.SimpleBusObject;
import eh.entity.coupon.CouponParam;
import eh.entity.mindgift.MindGift;
import eh.mindgift.constant.MindGiftConstant;
import eh.mindgift.dao.MindGiftDAO;
import eh.mindgift.service.MindGiftMsgService;
import eh.mindgift.service.MindGiftService;
import eh.wxpay.bus.service.support.AbstractBusPayService;
import eh.wxpay.bus.service.support.BusPayService;
import eh.wxpay.constant.PayConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;

@Component
@Scope("prototype")
public class MindgiftBusPayService extends AbstractBusPayService implements BusPayService {
    private static final Logger log = LoggerFactory.getLogger(MindgiftBusPayService.class);
    private static MindGiftDAO mindGiftDAO;

    public MindgiftBusPayService() {
        mindGiftDAO = DAOFactory.getDAO(MindGiftDAO.class);
    }

    @Override
    public void doCancelForUnPayOrder(Date deadTime) {
        MindGiftService mindService = AppContextHolder.getBean("eh.mindGiftService", MindGiftService.class);
        mindService.cancelOverTimeNoPayOrder(deadTime);
    }

    @Override
    public SimpleBusObject getSimpleBusObject(Integer busId) {
        MindGift mind = mindGiftDAO.get(busId);
        SimpleBusObject simpleBusObject = new SimpleBusObject();
        simpleBusObject.setBusId(busId);
        simpleBusObject.setPrice(mind.getAmount());
        simpleBusObject.setActualPrice(mind.getActualPrice() == null ? mind.getAmount() : mind.getActualPrice());
        simpleBusObject.setCouponId(mind.getCouponId());
        simpleBusObject.setCouponName(mind.getCouponName());
        simpleBusObject.setMpiId(mind.getMpiId());
        simpleBusObject.setOrganId(mind.getOrgan());
        simpleBusObject.setOutTradeNo(mind.getOutTradeNo());
        simpleBusObject.setPayFlag(mind.getPayFlag());
        simpleBusObject.setBusObject(mind);
        return simpleBusObject;
    }

    @Override
    public void handleCoupon(Integer busId, Integer couponId) {
        MindGift mind = mindGiftDAO.get(busId);
        if (mind.getCouponId() == null) {
            CouponParam couponParam = new CouponParam();
            couponParam.setCouponId(couponId);
            couponParam.setOrderAmount(BigDecimal.valueOf(mind.getAmount()));
            couponParam.setBusType(CouponConstant.COUPON_BUSTYPE_MINDGIFT);
            couponParam.setSubType(CouponConstant.COUPON_SUBTYPE_MINDGIFT_PENNANTS);//锦旗
            mind.setCouponId(couponId);
            mind.setLastModify(new Date());
            fetchAndLockCoupon(busId, this.getClass().getSimpleName(), couponId, mind, couponParam);
            mindGiftDAO.update(mind);
        }
    }

    @Override
    public void handleBusWhenNoNeedPay(Integer busId, String outTradeNo,Map<String,Object> map) {
        MindGift mind = mindGiftDAO.get(busId);
        mind.setPayFlag(PayConstant.PAY_FLAG_PAY_SUCCESS);
        mind.setPaymentDate(new Date());
        mind.setMindGiftStatus(MindGiftConstant.MINDGIFT_STATUS_UNAUDIT);
        mind.setOutTradeNo(outTradeNo);
        mind.setLastModify(new Date());
        mindGiftDAO.update(mind);
        //发送消息等
        MindGiftMsgService.doAfterPaySuccess(busId);
    }

    @Override
    public void onOrder(Order order) {
        MindGift mind = mindGiftDAO.get(order.getBusId());
        mind.setPayOrganId(order.getPayOrganId());
        mind.setOutTradeNo(order.getOutTradeNo());
        mind.setPayWay(order.getPayway());
        mind.setLastModify(new Date());
        mindGiftDAO.update(mind);
    }
}
