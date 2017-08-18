package eh.wxpay.bus.service;

import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import eh.base.dao.OrganConfigDAO;
import eh.cdr.constant.RecipeConstant;
import eh.cdr.dao.RecipeOrderDAO;
import eh.cdr.service.RecipeOrderService;
import eh.coupon.constant.CouponConstant;
import eh.entity.base.OrganConfig;
import eh.entity.bus.Order;
import eh.entity.bus.pay.BusTypeEnum;
import eh.entity.bus.pay.ConfirmOrder;
import eh.entity.bus.pay.SimpleBusObject;
import eh.entity.cdr.RecipeOrder;
import eh.entity.coupon.CouponParam;
import eh.util.CdrUtil;
import eh.wxpay.bus.service.support.AbstractBusPayService;
import eh.wxpay.bus.service.support.BusPayService;
import eh.wxpay.constant.PayConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
@Scope("prototype")
public class RecipeBusPayService extends AbstractBusPayService implements BusPayService {
    private static final Logger log = LoggerFactory.getLogger(RecipeBusPayService.class);
    private RecipeOrderDAO recipeOrderDAO;

    public RecipeBusPayService() {
        recipeOrderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
    }

    @Override
    public ConfirmOrder obtainConfirmOrder(String busType, Integer busId, Map<String, String> extInfo) {
        RecipeOrderService orderService = AppContextHolder.getBean("eh.recipeOrderService", RecipeOrderService.class);
        //先判断处方是否已创建订单
        RecipeOrder order = recipeOrderDAO.getOrderByRecipeId(busId);
        if (null == order) {
            order = orderService.createBlankOrder(Arrays.asList(busId), extInfo);
        }
        ConfirmOrder confirmOrder = new ConfirmOrder();
        confirmOrder.setBusId(busId);
        confirmOrder.setBusType(busType);
        BusTypeEnum busTypeEnum = BusTypeEnum.fromCode(busType);
        if(busTypeEnum!=null) {
            confirmOrder.setBusTypeName(busTypeEnum.getDesc());
        }
        confirmOrder.setCouponId(order.getCouponId());
        confirmOrder.setDiscountAmount(order.getCouponName());
        confirmOrder.setOrderAmount(order.getTotalFee().stripTrailingZeros().toPlainString());
        confirmOrder.setActualPrice(BigDecimal.valueOf(order.getActualPrice()).stripTrailingZeros().toPlainString());
        confirmOrder.setBusObject(order);
        // 加载确认订单页面的时候需要将页面属性字段做成可配置的
        confirmOrder.setExt(CdrUtil.getParamFromOgainConfig(order));
        return confirmOrder;
    }

    @Override
    public void doCancelForUnPayOrder(Date deadTime) {

    }

    @Override
    public SimpleBusObject getSimpleBusObject(Integer busId) {
        RecipeOrder order = recipeOrderDAO.get(busId);
        SimpleBusObject simpleBusObject = new SimpleBusObject();
        simpleBusObject.setBusId(busId);
        simpleBusObject.setPrice(order.getTotalFee().stripTrailingZeros().doubleValue());
        simpleBusObject.setActualPrice(BigDecimal.valueOf(order.getActualPrice()).stripTrailingZeros().doubleValue());
        simpleBusObject.setCouponId(order.getCouponId());
        simpleBusObject.setCouponName(order.getCouponName());
        simpleBusObject.setMpiId(order.getMpiId());
        simpleBusObject.setOrganId(order.getOrganId());
        simpleBusObject.setOutTradeNo(order.getOutTradeNo());
        simpleBusObject.setPayFlag(order.getPayFlag());
        simpleBusObject.setBusObject(order);
        return simpleBusObject;
    }

    @Override
    public void handleCoupon(Integer busId, Integer couponId) {
        RecipeOrderService orderService = AppContextHolder.getBean("eh.recipeOrderService", RecipeOrderService.class);
        RecipeOrder order = recipeOrderDAO.get(busId);
        if (null != order && order.getCouponId() == null) {
            CouponParam couponParam = new CouponParam();
            couponParam.setCouponId(couponId);
            //只对处方金额做优惠
            couponParam.setOrderAmount(order.getRecipeFee());
            couponParam.setBusType(CouponConstant.COUPON_BUSTYPE_RECIPE);
            couponParam.setSubType(CouponConstant.COUPON_SUBTYPE_RECIPE_HOME_PAYONLINE);
            order.setCouponId(couponId);
            fetchAndLockCoupon(busId, this.getClass().getSimpleName(), couponId, order, couponParam);
            Map<String, Object> orderAttrMap = new HashMap<>();
            if (orderService.isUsefulCoupon(couponId)) {
                //由于只对处方金额做优惠，所有返回的order对象里的actualPrice实际上是处方优惠后的金额，需要做处理
                BigDecimal actualPrice = orderService.countOrderTotalFeeWithCoupon(new BigDecimal(order.getActualPrice().toString()), order);
                orderAttrMap.put("actualPrice", actualPrice.doubleValue());
                orderAttrMap.put("couponFee", order.getTotalFee().subtract(actualPrice));
            } else {
                //如果没有使用优惠券，则实际金额不变
                orderAttrMap.put("couponFee", BigDecimal.ZERO);
            }
            orderAttrMap.put("couponId", order.getCouponId());
            orderAttrMap.put("couponName", order.getCouponName());

            orderService.updateOrderInfo(order.getOrderCode(), orderAttrMap, null);
        }
    }

    @Override
    public void handleBusWhenNoNeedPay(Integer busId, String outTradeNo,Map<String,Object> map) {
        RecipeOrder recipeOrder = recipeOrderDAO.get(busId);
        RecipeOrderService orderService = AppContextHolder.getBean("eh.recipeOrderService", RecipeOrderService.class);
        orderService.finishOrderPay(recipeOrder.getOrderCode(), PayConstant.PAY_FLAG_PAY_SUCCESS, RecipeConstant.PAYMODE_ONLINE);
    }

    @Override
    public void onOrder(Order order) {
        RecipeOrder recipeOrder = recipeOrderDAO.get(order.getBusId());
        Map<String, Object> changeAttr = new HashMap<>();
        changeAttr.put("wxPayWay", order.getPayway());
        changeAttr.put("outTradeNo", order.getOutTradeNo());
        changeAttr.put("payOrganId", order.getPayOrganId());
        recipeOrderDAO.updateByOrdeCode(recipeOrder.getOrderCode(), changeAttr);
    }
}
