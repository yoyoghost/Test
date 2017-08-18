package eh.controller;

import com.alibaba.fastjson.JSONObject;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.converter.support.StringToDate;
import eh.alipay.constant.AliPayConstant;
import eh.base.constant.ErrorCode;
import eh.bus.dao.OrderDao;
import eh.bus.dao.OutpatientDAO;
import eh.bus.service.payment.QueryOutpatient;
import eh.cdr.constant.RecipeConstant;
import eh.cdr.constant.RecipeStatusConstant;
import eh.cdr.dao.RecipeOrderDAO;
import eh.cdr.service.RecipeLogService;
import eh.cdr.service.RecipeMsgService;
import eh.cdr.service.RecipeOrderService;
import eh.entity.bus.Order;
import eh.entity.bus.Outpatient;
import eh.entity.bus.pay.BusTypeEnum;
import eh.entity.bus.pay.DaBaiNotifyResult;
import eh.entity.cdr.RecipeOrder;
import eh.utils.DateConversion;
import eh.utils.LocalStringUtil;
import eh.utils.MapValueUtil;
import eh.utils.ValidateUtil;
import eh.wxpay.bus.callback.support.PayCallback;
import eh.wxpay.bus.callback.support.PayResult;
import eh.wxpay.constant.PayConstant;
import eh.wxpay.constant.PayServiceConstant;
import eh.wxpay.util.Util;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.List;
import java.util.Map;


/**
 * 已废弃
 */
@Deprecated
@Controller("payController")
public class PayController {
    public static final String DA_BAI_ASYNC_NOTIFY_URL = "/daBai/notify";
    private static final Logger logger = LoggerFactory.getLogger(PayController.class);

    /**
     * 用于接收支付平台支付结果异步通知
     *
     * @param httpServletRequest
     * @param res
     */
    @RequestMapping(value = "wxpay/payNotify")
    public void payNotify(HttpServletRequest httpServletRequest, HttpServletResponse res) {
        //处理咨询业务逻辑，更新支付标志
        PrintWriter writer = null;
        try {
            writer = res.getWriter();
            Map<String, String> map = Util.buildRequest(httpServletRequest);
            logger.info("收到支付平台支付结果异步通知请求：" + map);
            String tradeStatus = map.get("trade_status");
            if (PayConstant.RESULT_SUCCESS.equals(tradeStatus)) {
                doBusinessAfterOrderSuccessForNotify(map, PayServiceConstant.WEIXIN);
            } else if (AliPayConstant.TradeStatus.TRADE_SUCCESS.equals(tradeStatus)) {
                doBusinessAfterOrderSuccessForNotify(map, PayServiceConstant.ALIPAY);
            } else {
                logger.info("payNotify not handle the result, map[{}]", JSONObject.toJSONString(map));
            }
            logger.info("payNotify handle success!");
        } catch (Exception e) {
            logger.error("[{}] payNotify error, errorMessage[{}], stackTrace[{}]", this.getClass().getSimpleName(), e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
        } finally {
            if (writer != null) {
                writer.println("success");
                writer.close();
            }
        }
    }

    @RequestMapping(value = DA_BAI_ASYNC_NOTIFY_URL)
    public void medicalInsuranceAsyncNotify(HttpServletRequest request, HttpServletResponse response) {
        PrintWriter writer = null;
        logger.info("receive medicalInsuranceAsyncNotify and begin handle.....");
        try {
            writer = response.getWriter();
            Map<String, String> parameterMap = Util.buildRequest(request);
            String parameterMapJsonString = JSONObject.toJSONString(parameterMap);
            logger.info("medicalInsuranceAsyncNotify parameterMap[{}]", parameterMapJsonString);

            String code = MapValueUtil.getString(parameterMap, "code");
            String message = MapValueUtil.getString(parameterMap, "message");
            if (!"0".equals(code)) {
                logger.error("medicalInsuranceAsyncNotify error. message={}", message);
            }

            DaBaiNotifyResult notifyResult = JSONObject.parseObject(MapValueUtil.getString(parameterMap, "data"), DaBaiNotifyResult.class);
            String outTradeNo = notifyResult.getPartner_trade_no();
            if (StringUtils.isEmpty(outTradeNo)) {
                logger.error("medicalInsuranceAsyncNotify outTradeNo is empty. data={}", MapValueUtil.getString(parameterMap, "data"));
                return;
            }
            String tradeNo = notifyResult.getTrade_no();
            Date paymentDate = DateConversion.parseDate(notifyResult.getNotify_time(), "yyyy-MM-dd HH:mm:ss");
            if (outTradeNo.startsWith(BusTypeEnum.OUTPATIENT.applyPrefix())) {
                OutpatientDAO outpatientDAO = DAOFactory.getDAO(OutpatientDAO.class);
                Outpatient outpatient = outpatientDAO.getByOutTradeNo(outTradeNo);
                if (outpatient == null) {
                    writer.println("success");
                    return;
                }
                Integer payflag = outpatient.getPayflag();
                if (payflag != null && payflag == 1) {//已处理
                    writer.println("success");
                    return;
                }
                outpatient.setTradeStatus(ValidateUtil.blankString(notifyResult.getTrade_status()) ? 0 : Integer.valueOf(notifyResult.getTrade_status()));
                outpatient.setPaymentDate(paymentDate);
                outpatient.setTradeNo(tradeNo);
                outpatient.setAttr(JSONObject.toJSONString(notifyResult.getBill_details()));
                if ("3".equals(notifyResult.getTrade_status())) {
                    outpatient.setPayflag(1);
                    outpatientDAO.update(outpatient);
                    QueryOutpatient queryOutpatient = AppContextHolder.getBean("queryOutpatient", QueryOutpatient.class);
                    queryOutpatient.doAfterMedicalInsurancePaySuccessForOutpatient(outpatient.getId(), false);
                } else {
                    outpatient.setErrorInfo(parameterMapJsonString);
                    outpatientDAO.update(outpatient);
                    QueryOutpatient queryOutpatient = AppContextHolder.getBean("queryOutpatient", QueryOutpatient.class);
                    queryOutpatient.doAfterMedicalInsurancePaySuccessForOutpatient(outpatient.getId(), true);
                }
            } else if (outTradeNo.startsWith(BusTypeEnum.RECIPE.applyPrefix())) {
                //医保处理
                RecipeOrderDAO orderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
                RecipeOrderService orderService = AppContextHolder.getBean("eh.recipeOrderService", RecipeOrderService.class);

                RecipeOrder order = orderDAO.getByOutTradeNo(outTradeNo);
                if (null == order) {
                    writer.println("success");
                    return;
                }

                Integer payflag = order.getPayFlag();
                if (payflag != null && payflag == 1) {//已处理
                    writer.println("success");
                    return;
                }

                Integer recipeId = null;
                if (StringUtils.isNotEmpty(order.getRecipeIdList())) {
                    List<Integer> recipeIdList = JSONUtils.parse(order.getRecipeIdList(), List.class);
                    if (CollectionUtils.isNotEmpty(recipeIdList)) {
                        recipeId = recipeIdList.get(0);
                    }
                }
                RecipeLogService.saveRecipeLog(recipeId, RecipeStatusConstant.UNKNOW, RecipeStatusConstant.UNKNOW, "收到医快付支付消息 订单号:" + outTradeNo + ",流水号：" + tradeNo);
                boolean paySuccess = false;
                if ("3".equals(notifyResult.getTrade_status())) {
                    paySuccess = true;
                    orderService.finishOrderPay(order.getOrderCode(), PayConstant.PAY_FLAG_PAY_SUCCESS, RecipeConstant.PAYMODE_MEDICAL_INSURANCE);
                }
                if(null != recipeId) {
                    RecipeMsgService.doAfterMedicalInsurancePaySuccess(recipeId, paySuccess);
                }
            }
            writer.println("success");
        } catch (Exception e) {
            logger.error("medicalInsuranceAsyncNotify error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    private void doBusinessAfterOrderSuccessForNotify(Map<String, String> map, PayServiceConstant payServiceConstant) {
        String outTradeNo = map.get("applyno");//商户订单号
        String gmt_payment = map.get("gmt_payment");
        Date paymentDate = StringToDate.toDatetime(gmt_payment);
        String tradeNo = map.get("trade_no");
        PayResult payResult = new PayResult();
        payResult.setOutTradeNo(outTradeNo);
        payResult.setPaymentDate(paymentDate);
        payResult.setTradeNo(tradeNo);
//        payResult.setPayServiceConstant(payServiceConstant);
        doBusinessAfterOrderSuccessCommon(payResult);
    }

    public void doBusinessAfterOrderSuccessCommon(PayResult payResult) {
        OrderDao orderDao = DAOFactory.getDAO(OrderDao.class);

        // 第一步，查询订单信息
        Order order = orderDao.getByOutTradeNo(payResult.getOutTradeNo());

        // 校验订单
        checkOrder(order);

        // 更新订单表记录
        orderDao.updateOrderWithOutTradeNoForCallbackSuccess(payResult.getTradeNo(), payResult.getPaymentDate(), payResult.getOutTradeNo());

        // 支付成功回调处理
        payResult.setBusId(order.getBusId());
        doPaySuccessCallbackHandle(order, payResult);
    }

    private void doPaySuccessCallbackHandle(Order order, PayResult payResult) {
        BusTypeEnum busTypeEnum = BusTypeEnum.fromCode(order.getBusType());
        String beanName = StringUtils.uncapitalize(busTypeEnum.getCode()) + "PayCallback";
        PayCallback<PayResult> payCallback = AppContextHolder.getBean(beanName, PayCallback.class);
        if (payCallback == null) {
            logger.error("doBusinessAfterOrderSuccessCommon bean not exists in spring container with beanName[{}]", beanName);
            throw new DAOException(ErrorCode.SERVICE_ERROR, LocalStringUtil.format("beanName[{}] error", beanName));
        }
        try {
            payCallback.handle(payResult);
        } catch (Exception e) {
            logger.error("doBusinessAfterOrderSuccessCommon payCallback handle exception for order[{}], errorMessage[{}], stackTrace[{}]", JSONObject.toJSONString(order), e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
        }
    }

    private void checkOrder(Order order) {
        if (order == null) {
            logger.error("doBusinessAfterOrderSuccessCommon order not exists, payResult[{}]", JSONObject.toJSONString(order));
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该订单不存在！！！");
        }
        // 已处理过，直接返回
        if (order.getPayFlag() != null && order.getPayFlag() == PayConstant.PAY_FLAG_PAY_SUCCESS) {
            logger.warn("doBusinessAfterOrderSuccessCommon payflag has been set true, return! order[{}]", JSONObject.toJSONString(order));
            throw new DAOException(ErrorCode.SERVICE_ERROR, "重复回调，该订单已支付成功!");
        }

    }


    /**
     * 显示支付成功页面
     *
     * @param httpServletRequest
     * @param res
     */
    @RequestMapping(value = "/paySucc")
    public void paySucc(HttpServletRequest httpServletRequest, HttpServletResponse res) {
        res.setContentType("text/html;charset=UTF-8");
        PrintWriter writer;
        logger.info("[{}] paySucc..", this.getClass().getSimpleName());
        try {
            writer = res.getWriter();
            writer.println("支付成功!");
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }
}
