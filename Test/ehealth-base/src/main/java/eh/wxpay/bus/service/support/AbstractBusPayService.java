package eh.wxpay.bus.service.support;

import com.alibaba.fastjson.JSONObject;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import eh.base.constant.ErrorCode;
import eh.bus.dao.OrderDao;
import eh.coupon.service.CouponService;
import eh.entity.bus.Order;
import eh.entity.bus.pay.BusTypeEnum;
import eh.entity.bus.pay.ConfirmOrder;
import eh.entity.bus.pay.CouponPart;
import eh.entity.bus.pay.SimpleBusObject;
import eh.entity.coupon.Coupon;
import eh.entity.coupon.CouponParam;
import eh.utils.ValidateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * 抽象类，定义部分公共行为
 */
public abstract class AbstractBusPayService implements BusPayService {
    private static final Logger log = LoggerFactory.getLogger(AbstractBusPayService.class);

    @Override
    public SimpleBusObject obtainBusForOrder(Integer busId, String busType) {
        SimpleBusObject simpleBusObject = getSimpleBusObject(busId);
        Order order = latestOrder(busId, busType);
        if(simpleBusObject!=null && order !=null){
            simpleBusObject.setOutTradeNo(order.getOutTradeNo());
            simpleBusObject.setPayFlag(order.getPayFlag());
            simpleBusObject.setTradeNo(order.getTradeNo());
            simpleBusObject.setPayWay(order.getPayway());
            simpleBusObject.setPaymentDate(order.getPaymentDate());
            simpleBusObject.setPayOrganId(order.getPayOrganId());
        }
        return simpleBusObject;
    }

    /**
     * 获取业务信息
     * @param busId
     * @return
     */
    protected abstract SimpleBusObject getSimpleBusObject(Integer busId);

    @Override
    public ConfirmOrder obtainConfirmOrder(String busType, Integer busId, Map<String, String> extInfo) {
        SimpleBusObject simpleBusObject = obtainBusForOrder(busId, busType);
        ConfirmOrder confirmOrder = new ConfirmOrder();
        if(simpleBusObject!=null){
            confirmOrder.setBusId(busId);
            confirmOrder.setBusType(busType);
            BusTypeEnum busTypeEnum = BusTypeEnum.fromCode(busType);
            if(busTypeEnum!=null) {
                confirmOrder.setBusTypeName(busTypeEnum.getDesc());
            }
            confirmOrder.setCouponId(simpleBusObject.getCouponId());
            confirmOrder.setDiscountAmount(simpleBusObject.getCouponName());
            confirmOrder.setOrderAmount(String.valueOf(simpleBusObject.getPrice()));
            confirmOrder.setActualPrice(String.valueOf(simpleBusObject.getActualPrice()));
            confirmOrder.setBusObject(simpleBusObject.getBusObject());
        }
        return confirmOrder;
    }

    /**
     * 获取并锁定优惠券
     * @param busId
     * @param className
     * @param couponId
     * @param couponPart
     * @param couponParam
     */
    protected void fetchAndLockCoupon(Integer busId, String className, Integer couponId, CouponPart couponPart, CouponParam couponParam) {
        if(couponId==-1){
            couponPart.setCouponName("不使用优惠券");
            log.info("fetchAndLockCoupon, busId is -1, return");
            return;
        }
        if(couponId==0){
            couponPart.setCouponName("无优惠券");
            log.info("fetchAndLockCoupon, busId is 0, return");
            return;
        }
        if (!validateCoupon(couponParam)) {
            log.info("fetchAndLockCoupon coupon invalid, couponId[{}], busId[{}], className[{}]", couponId, busId, className);
            throw new DAOException(ErrorCode.COUPON_EXPIRED, "亲，该优惠券已过期");
        }
        try {
            CouponService couponService = AppContextHolder.getBean("couponService", CouponService.class);
            Coupon coupon = couponService.lockCouponById(couponParam);
            couponPart.setCouponId(couponId);
            couponPart.setCouponName(coupon.getDiscountAmount() == null ? "0" : coupon.getDiscountAmount().toPlainString());
            couponPart.setActualPrice(coupon.getActualPrice().doubleValue());
        } catch (Exception e) {
            log.error("fetchAndLockCoupon error, busId[{}], className[{}], couponId[{}], errorMessage[{}], stackTrace[{}]", busId, className, couponId, e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw new DAOException(e.getMessage());
        }
    }

    /**
     * 验证优惠券的有效性
     * @param couponParam
     * @return
     */
    private boolean validateCoupon(CouponParam couponParam) {
        CouponService couponService = AppContextHolder.getBean("couponService", CouponService.class);
        return couponService.isCouponAvailable(couponParam);
    }

    /**
     * 获取最新的订单
     * @param busId
     * @param busType
     * @return
     */
    protected Order latestOrder(Integer busId, String busType) {
        List<Order> orderList = DAOFactory.getDAO(OrderDao.class).findOrderListByBusId(busId, busType);
        if(ValidateUtil.blankList(orderList)){
            return null;
        }
        return orderList.get(0);
    }

}
