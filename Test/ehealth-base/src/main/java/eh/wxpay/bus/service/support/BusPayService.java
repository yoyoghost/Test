package eh.wxpay.bus.service.support;

import eh.entity.bus.Order;
import eh.entity.bus.pay.ConfirmOrder;
import eh.entity.bus.pay.SimpleBusObject;

import java.util.Date;
import java.util.Map;

/**
 * 此接口用于统一各业务在下单、查询订单等操作时的行为
 * 注意：
 *      1、新增支付业务类型，需实现该类方法（无对应业务场景情况除外）；
 *      2、子类命名规则：BusTypeEnum的code字段值首字母小写+BusPayService
 *      3、子类需添加@Component注解或是显示声明为springBean
 */
public interface BusPayService {


    /**
     * 超过XX小时未支付取消   当有此类业务需求时，需实现此方法
     * @param deadTime 截止时间
     */
    void doCancelForUnPayOrder(Date deadTime);

    /**
     * 获取支付所需业务数据信息
     * @param busId
     * @return
     */
    SimpleBusObject obtainBusForOrder(Integer busId, String busType);

    /**
     * 获取确认订单页信息
     * @param busType
     * @param busId
     * @param extInfo
     * @return
     */
    ConfirmOrder obtainConfirmOrder(String busType, Integer busId, Map<String, String> extInfo);

    /**
     * 处理优惠券信息       当有此类业务需求时，需实现此方法
     * @param busId
     * @param couponId
     */
    void handleCoupon(Integer busId, Integer couponId);

    /**
     * 下单时当订单金额为0时，调用该方法进行业务处理
     * @param busId
     */
    void handleBusWhenNoNeedPay(Integer busId, String outTradeNo,Map<String,Object> map);

    /**
     * 下单时保存平台订单号等信息
     * @param order
     */
    void onOrder(Order order);
}
