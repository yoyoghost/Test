package eh.wxpay.bus.callback;

import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import eh.cdr.constant.RecipeConstant;
import eh.cdr.constant.RecipeStatusConstant;
import eh.cdr.dao.RecipeOrderDAO;
import eh.cdr.service.RecipeLogService;
import eh.cdr.service.RecipeOrderService;
import eh.coupon.service.CouponService;
import eh.entity.cdr.RecipeOrder;
import eh.utils.ValidateUtil;
import eh.wxpay.bus.callback.support.PayCallback;
import eh.wxpay.bus.callback.support.PayResult;
import eh.wxpay.constant.PayConstant;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;

/**
 * 命名规则： BusTypeEnum的code值 + PayCallback 后缀
 */
@Component
@Scope("prototype")
public class RecipePayCallback implements PayCallback<PayResult> {
    private static final Logger logger = LoggerFactory.getLogger(RecipePayCallback.class);

    @Override
    public boolean handle(PayResult payResult) throws Exception {
        Integer busId = payResult.getBusId();
        String outTradeNo = payResult.getOutTradeNo();
        String tradeNo = payResult.getTradeNo();
        RecipeOrderDAO orderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
        RecipeOrder order = orderDAO.get(busId);
        if (null == order) {
            logger.error("doBusinessAfterOrderSuccess busObject not exists, busId[{}]", busId);
            return false;
        }

        if (order.getPayFlag() != null && order.getPayFlag() == 1) {//已处理
            logger.info("doBusinessAfterOrderSuccess payflag has been set true, busId[{}]", busId);
            return true;
        }
        HashMap<String, Object> attr = new HashMap<>();
        attr.put("tradeNo", tradeNo);
        attr.put("outTradeNo", outTradeNo);
        orderDAO.updateByOrdeCode(order.getOrderCode(), attr);
        RecipeOrderService orderService = AppContextHolder.getBean("eh.recipeOrderService", RecipeOrderService.class);
        orderService.finishOrderPay(order.getOrderCode(),PayConstant.PAY_FLAG_PAY_SUCCESS, RecipeConstant.PAYMODE_ONLINE);

        Integer _bussId = order.getOrderId();
        if(StringUtils.isNotEmpty(order.getRecipeIdList())){
            List<Integer> recipeIdList = JSONUtils.parse(order.getRecipeIdList(), List.class);
            if (CollectionUtils.isNotEmpty(recipeIdList)) {
                _bussId = recipeIdList.get(0);
            }
        }
        RecipeLogService.saveRecipeLog(_bussId, RecipeStatusConstant.UNKNOW, RecipeStatusConstant.UNKNOW, "订单: 收到支付平台支付消息 商户订单号:" + outTradeNo + ",第三方流水号：" + tradeNo);
        if(ValidateUtil.notNullAndZeroInteger(order.getCouponId()) && order.getCouponId()!=-1){
            CouponService couponService = AppContextHolder.getBean("couponService", CouponService.class);
            couponService.useCouponById(order.getCouponId());
        }
        return true;
    }
}
