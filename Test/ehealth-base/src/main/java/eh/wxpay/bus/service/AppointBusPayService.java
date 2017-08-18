package eh.wxpay.bus.service;

import com.alibaba.fastjson.JSONObject;
import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import eh.bus.dao.AppointRecordDAO;
import eh.bus.his.service.AppointTodayBillService;
import eh.coupon.constant.CouponConstant;
import eh.entity.bus.AppointRecord;
import eh.entity.bus.Order;
import eh.entity.bus.pay.SimpleBusObject;
import eh.entity.coupon.CouponParam;
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
public class AppointBusPayService extends AbstractBusPayService implements BusPayService {
    private static final Logger log = LoggerFactory.getLogger(AppointBusPayService.class);
    private static AppointRecordDAO appointRecordDao;

    public AppointBusPayService() {
        appointRecordDao = DAOFactory.getDAO(AppointRecordDAO.class);
    }

    @Override
    public void doCancelForUnPayOrder(Date deadTime) {
        appointRecordDao.cancelOverTimeNoPayOrder(deadTime);
    }

    @Override
    public SimpleBusObject getSimpleBusObject(Integer busId) {
        AppointRecord appointRecord = appointRecordDao.getByAppointRecordId(busId);
        SimpleBusObject simpleBusObject = new SimpleBusObject();
        simpleBusObject.setBusId(busId);
        simpleBusObject.setPrice(appointRecord.getClinicPrice());
        simpleBusObject.setActualPrice(appointRecord.getActualPrice() == null ? appointRecord.getClinicPrice() : appointRecord.getActualPrice());
        simpleBusObject.setCouponId(appointRecord.getCouponId());
        simpleBusObject.setCouponName(appointRecord.getCouponName());
        simpleBusObject.setMpiId(appointRecord.getMpiid());
        simpleBusObject.setOrganId(appointRecord.getOrganId());
        simpleBusObject.setOutTradeNo(appointRecord.getOutTradeNo());
        simpleBusObject.setPayFlag(appointRecord.getPayFlag());
        simpleBusObject.setBusObject(appointRecord);
        return simpleBusObject;
    }

    @Override
    public void handleCoupon(Integer busId, Integer couponId) {
        AppointRecord appointRecord = appointRecordDao.getByAppointRecordId(busId);
        if (appointRecord.getCouponId() == null) {
            CouponParam couponParam = new CouponParam();
            couponParam.setCouponId(couponId);
            couponParam.setOrderAmount(BigDecimal.valueOf(appointRecord.getClinicPrice()));
            couponParam.setBusType(CouponConstant.COUPON_BUSTYPE_APPOINT);
            couponParam.setSubType(CouponConstant.COUPON_SUBTYPE_REGISTER);
            appointRecord.setCouponId(couponId);
            fetchAndLockCoupon(busId, this.getClass().getSimpleName(), couponId, appointRecord, couponParam);
            appointRecordDao.update(appointRecord);
        }
    }

    @Override
    public void handleBusWhenNoNeedPay(Integer busId, String outTradeNo,Map<String,Object> map) {
        AppointTodayBillService appointTodayBillService = AppContextHolder.getBean("eh.billService", AppointTodayBillService.class);
        AppointRecord appointRecord = appointRecordDao.getByAppointRecordId(busId);
        appointRecord.setPayFlag(PayConstant.PAY_FLAG_PAY_SUCCESS);
        appointRecord.setAppointStatus(0);
        appointRecord.setOutTradeNo(outTradeNo);
        appointRecordDao.update(appointRecord);
        log.info("开始调用HIS结算------------------>", JSONObject.toJSONString(appointRecord));
        Map<String, Object> appointRetMap=appointTodayBillService.settleRegBill(appointRecord);
        //返回结算标识
        if("success".equals(appointRetMap.get("code"))){
            map.put("saFlag", 1);
        }else{
            map.put("saFlag", 0);
        }
    }

    @Override
    public void onOrder(Order order) {
        AppointRecord appointRecord = appointRecordDao.getByAppointRecordId(order.getBusId());
        appointRecord.setPayOrganId(order.getPayOrganId());
        appointRecord.setOutTradeNo(order.getOutTradeNo());
        appointRecord.setPayWay(order.getPayway());
        appointRecordDao.update(appointRecord);
    }
}
