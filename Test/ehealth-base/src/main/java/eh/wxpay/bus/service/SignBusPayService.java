package eh.wxpay.bus.service;

import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import eh.coupon.constant.CouponConstant;
import eh.entity.bus.Order;
import eh.entity.bus.pay.SimpleBusObject;
import eh.entity.coupon.CouponParam;
import eh.entity.mpi.SignRecord;
import eh.mpi.dao.SignRecordDAO;
import eh.push.SmsPushService;
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
public class SignBusPayService extends AbstractBusPayService implements BusPayService {
    private static final Logger log = LoggerFactory.getLogger(SignBusPayService.class);
    private static SignRecordDAO signRecordDAO;

    public SignBusPayService() {
        signRecordDAO = DAOFactory.getDAO(SignRecordDAO.class);
    }

    @Override
    public void doCancelForUnPayOrder(Date deadTime) {
        signRecordDAO.cancelOverTimeNoPayOrder(deadTime);
    }

    @Override
    public SimpleBusObject getSimpleBusObject(Integer busId) {
        SignRecord signRecord = signRecordDAO.get(busId);
        SimpleBusObject simpleBusObject = new SimpleBusObject();
        simpleBusObject.setBusId(busId);
        simpleBusObject.setPrice(signRecord.getSignCost());
        simpleBusObject.setActualPrice(signRecord.getActualPrice() == null ? signRecord.getSignCost() : signRecord.getActualPrice());
        simpleBusObject.setCouponId(signRecord.getCouponId());
        simpleBusObject.setCouponName(signRecord.getCouponName());
        simpleBusObject.setMpiId(signRecord.getRequestMpiId());
        simpleBusObject.setOrganId(signRecord.getOrgan());
        simpleBusObject.setOutTradeNo(signRecord.getOutTradeNo());
        simpleBusObject.setPayFlag(signRecord.getPayFlag());
        simpleBusObject.setBusObject(signRecord);
        return simpleBusObject;
    }

    @Override
    public void handleCoupon(Integer busId, Integer couponId) {
        SignRecord signRecord = signRecordDAO.get(busId);
        if (signRecord.getCouponId() == null) {
            CouponParam couponParam = new CouponParam();
            couponParam.setCouponId(couponId);
            couponParam.setOrderAmount(BigDecimal.valueOf(signRecord.getSignCost()));
            couponParam.setBusType(CouponConstant.COUPON_BUSTYPE_SIGN);
            signRecord.setCouponId(couponId);
            fetchAndLockCoupon(busId, this.getClass().getSimpleName(), couponId, signRecord, couponParam);
            signRecordDAO.update(signRecord);
        }
    }

    @Override
    public void handleBusWhenNoNeedPay(Integer busId, String outTradeNo,Map<String,Object> map) {
        SignRecord signRecord = signRecordDAO.get(busId);
        signRecord.setPayFlag(PayConstant.PAY_FLAG_PAY_SUCCESS);
        signRecord.setRecordStatus(0);
        signRecord.setOutTradeNo(outTradeNo);
        signRecordDAO.update(signRecord);
        AppContextHolder.getBean("eh.smsPushService", SmsPushService.class).pushMsgData2Ons(signRecord.getSignRecordId(), signRecord.getOrgan(), "SignMessage", "", 0);
    }

    @Override
    public void onOrder(Order order) {
        SignRecord signRecord = signRecordDAO.get(order.getBusId());
        signRecord.setPayOrganId(order.getPayOrganId());
        signRecord.setOutTradeNo(order.getOutTradeNo());
        signRecord.setPayWay(order.getPayway());
        signRecordDAO.update(signRecord);
    }
}
