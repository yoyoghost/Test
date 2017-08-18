package eh.unifiedpay.service;

import com.alibaba.fastjson.JSONObject;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.spring.AppDomainContext;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcBean;
import eh.base.constant.ErrorCode;
import eh.base.constant.SystemConstant;
import eh.bus.dao.AppointRecordDAO;
import eh.bus.dao.OrderDao;
import eh.bus.his.service.AppointTodayBillService;
import eh.entity.bus.AppointRecord;
import eh.entity.bus.Order;
import eh.entity.bus.pay.BusTypeEnum;
import eh.utils.DateConversion;
import eh.utils.LocalStringUtil;
import eh.wxpay.bus.callback.support.PayCallback;
import eh.wxpay.bus.callback.support.PayResult;
import eh.wxpay.constant.PayConstant;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * Description:
 * User: xiangyf
 * Date: 2017-04-25 10:34.
 */
@RpcBean("payNotify")
public class PayNotifyServiceImpl implements PayNotifyService {

    private static final Logger logger = LoggerFactory.getLogger(PayNotifyServiceImpl.class);
    private AppointTodayBillService appointTodayBillService = AppContextHolder.getBean("eh.billService", AppointTodayBillService.class);

    @Override
    public String notify(Map<String, String> map) {
        logger.info("收到支付平台支付结果异步通知请求：" + map);
        String tradeStatus= StringUtils.defaultString(map.get("trade_status"),"");
        String outTradeNo = StringUtils.defaultString(map.get("apply_no"),"");
        String tradeNo = StringUtils.defaultString(map.get("trade_no"),"");
        Date paymentDate = DateConversion.getCurrentDate(StringUtils.defaultString(map.get("gmt_payment"),""),DateConversion.DEFAULT_DATE_TIME);
        int busType = Integer.valueOf(StringUtils.defaultString(map.get("bus_type"),"0"));
        //判断支付结果 是否需要校验
        BusTypeEnum busTypeEnum = BusTypeEnum.fromId(busType);
        if(busTypeEnum==null){
            logger.error("无匹配的业务类型--busType:"+busType);
            return SystemConstant.FAIL;
        }
        if(tradeStatus.contains(PayConstant.RESULT_SUCCESS)){
            try {
                logger.info("业务类型："+busTypeEnum.getName());
                boolean isSuccess = updateBusinessPayStatus(outTradeNo,tradeNo,paymentDate,busTypeEnum);
                logger.info("更新业务记录状态："+isSuccess);
                if(isSuccess){
                    return SystemConstant.SUCCESS;
                }else{
                    return SystemConstant.FAIL;
                }
            }catch (Exception e){
                logger.error("AsynNotify is failed, outTradeNo[{}]", outTradeNo);
                return SystemConstant.FAIL;
            }
        }else if(tradeStatus.equals(PayConstant.RESULT_WAIT)) {
            // TODO: 2017/4/25 扫码、条码待支付状态回调
            if (BusTypeEnum.APPOINTCLOUD.getCode().equals(busTypeEnum.getCode())) {
                AppointRecordDAO recordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
                List<AppointRecord> records = recordDAO.findByOutTradeNo(outTradeNo);
                OrderDao orderDao = DAOFactory.getDAO(OrderDao.class);
                Order order = orderDao.getByOutTradeNo(outTradeNo);
                if (order == null) {
                    return SystemConstant.FAIL;
                }
                if (records == null || records.isEmpty()) {
                    if (order.getBusId() != null) {
                        UnifiedPayService payService = AppDomainContext.getBean("eh.unifiedPayService", UnifiedPayService.class);
                        payService.orderCancelByOut(order.getBusId(), BusTypeEnum.APPOINTCLOUD.getCode(), outTradeNo, order.getPayway(), order.getPayOrganId());
                        logger.error("order was cancel without appoint cloud record.....");
                    }
                } else {
                    AppointRecord record = records.get(0);
                    Date createTime = order.getCreateTime();
                    Date anHourAgo = DateConversion.getDateBFtHour(new Date(), 1);
                    if (!createTime.after(anHourAgo) || record.getAppointStatus().equals(2)) {
                        UnifiedPayService payService = AppDomainContext.getBean("eh.unifiedPayService", UnifiedPayService.class);
                        payService.orderCancel(record.getAppointRecordId(), BusTypeEnum.APPOINTCLOUD.getCode());
                        logger.error("cloudclinic record is over time when pay or record was cancel.....");
                    }
                }
            }
            return SystemConstant.SUCCESS;
        }else {
            return SystemConstant.FAIL;
        }

    }

    /**
     * 根据订单业务类型，更新各业务的支付信息
     * @param outTradeNo
     * @param tradeNo
     * @param paymentDate
     * @param busTypeEnum
     * @return
     */
    public boolean updateBusinessPayStatus(String outTradeNo,String tradeNo,Date paymentDate,BusTypeEnum busTypeEnum){
        PayResult payResult = new PayResult();
        payResult.setOutTradeNo(outTradeNo);
        payResult.setPaymentDate(paymentDate);
        payResult.setTradeNo(tradeNo);
        OrderDao orderDao = DAOFactory.getDAO(OrderDao.class);

        // 第一步，查询订单信息
        Order order = orderDao.getByOutTradeNo(payResult.getOutTradeNo());

        // 校验订单
        if(!checkOrder(order)){
            return false;
        }

        // 更新订单表记录
        orderDao.updateOrderWithOutTradeNoForCallbackSuccess(payResult.getTradeNo(), payResult.getPaymentDate(), payResult.getOutTradeNo());

        // 支付成功回调处理
        payResult.setBusId(order.getBusId());
        return doPaySuccessCallbackHandle(order, payResult);
    }

    private boolean doPaySuccessCallbackHandle(Order order, PayResult payResult) {
        BusTypeEnum busTypeEnum = BusTypeEnum.fromCode(order.getBusType());
        String beanName = StringUtils.uncapitalize(busTypeEnum.getCode()) + "PayCallback";
        PayCallback<PayResult> payCallback = AppContextHolder.getBean(beanName, PayCallback.class);
        if (payCallback == null) {
            logger.error("doBusinessAfterOrderSuccessCommon bean not exists in spring container with beanName[{}]", beanName);
            throw new DAOException(ErrorCode.SERVICE_ERROR, LocalStringUtil.format("beanName[{}] error", beanName));
        }
        try {
            return payCallback.handle(payResult);
        } catch (Exception e) {
            logger.error("doBusinessAfterOrderSuccessCommon payCallback handle exception for order[{}], errorMessage[{}], stackTrace[{}]", JSONObject.toJSONString(order), e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
        }
        return true;
    }

    private boolean checkOrder(Order order) {
        if (order == null) {
            logger.error("doBusinessAfterOrderSuccessCommon order not exists, payResult[{}]", JSONObject.toJSONString(order));
            return false;
        }
        // 已处理过，直接返回
        if (order.getPayFlag() != null && order.getPayFlag() == PayConstant.PAY_FLAG_PAY_SUCCESS) {
            logger.warn("doBusinessAfterOrderSuccessCommon payflag has been set true, return! order[{}]", JSONObject.toJSONString(order));
            return false;
        }
        return true;
    }
}
