package eh.wxpay.service;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Maps;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.bus.dao.AppointRecordDAO;
import eh.bus.dao.OrderDao;
import eh.bus.dao.OutpatientDAO;
import eh.bus.dao.PayBusinessDAO;
import eh.entity.bus.AppointRecord;
import eh.entity.bus.Order;
import eh.entity.bus.Outpatient;
import eh.entity.bus.PayBusiness;
import eh.entity.bus.pay.BusTypeEnum;
import eh.entity.bus.pay.NumInfo;
import eh.entity.bus.pay.NumTypeEnum;
import eh.entity.bus.pay.OfflineRequest;
import eh.utils.ValidateUtil;
import eh.wxpay.constant.PayConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Created by Administrator on 2017/3/6 0006.
 */
public class OfflineRefundService {

    private static final Logger log = LoggerFactory.getLogger(OfflineRefundService.class);

    /**
     * 线下（医院窗口）退款接口
     * @param request
     * @return
     */
    @RpcService
    public Map<String, Object> refund(OfflineRequest request){
        log.info("refund start in with params: request[{}]", JSONObject.toJSONString(request));
        Map<String, Object> resultMap = Maps.newHashMap();
        try{
            validateOfflineRequestPrameter(request);
            NumInfo numInfo = NumInfo.selectNumber(request);
            Order order = null;
            OrderDao orderDao = DAOFactory.getDAO(OrderDao.class);
            if(numInfo.getNumberType().equals(NumTypeEnum.ORDER_NO)){
                order = orderDao.getByOutTradeNo(numInfo.getNumber());
            }else if(numInfo.getNumberType().equals(NumTypeEnum.TRADE_NO)){
                order = orderDao.getByTradeNo(numInfo.getNumber());
            }else {
                log.error("not support hospitalNo yet, hospitalNo[{}]", request.getHospitalNo());
                throw new DAOException(ErrorCode.SERVICE_ERROR, "暂时不支持医院内码退款");
            }
            if(order==null){
                log.error("the order not exists!");
                throw new DAOException(ErrorCode.SERVICE_ERROR, "该订单不存在");
            }
            if(!checkBusOrderStatus(order.getPayFlag())){
                resultMap.put("status", 0);
                resultMap.put("refundNo", null);
                resultMap.put("msg", "success");
                return resultMap;
            }
            BusTypeEnum busTypeEnum = BusTypeEnum.fromCode(order.getBusType());
            int offlineRefundMode = request.getOfflineRefundMode();
            switch (busTypeEnum){
                case APPOINT:
                case APPOINTPAY:
                    AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
                    AppointRecord appointRecord = appointRecordDAO.getByOutTradeNo(order.getOutTradeNo());
                    if(appointRecord==null){
                        log.error("the order not exists!");
                        throw new DAOException(ErrorCode.SERVICE_ERROR, "该订单不存在");
                    }
                    if(PayConstant.OFFLINE_REFUND_MODE_EXECUTE_EXCEPT_SETTLE == offlineRefundMode) {
                        int updatedItems = orderDao.updateOrderForOffline(appointRecord.getOutTradeNo(), offlineRefundMode);
                        NgariRefundService refundService = AppContextHolder.getBean("ngariRefundService", NgariRefundService.class);
                        resultMap = refundService.refund(appointRecord.getAppointRecordId(), busTypeEnum.getCode());
                        log.info("execute OFFLINE_REFUND_MODE_EXECUTE_EXCEPT_SETTLE mode, outTradeNo[{}], updatedItems[{}], resultMap[{}]", appointRecord.getOutTradeNo(), updatedItems, resultMap);
                    }else if(PayConstant.OFFLINE_REFUND_MODE_ONLY_CHANGE_STATUS == offlineRefundMode){
                        int updatedItems = appointRecordDAO.updateToRefundSuccessForOffline(appointRecord.getOutTradeNo(), offlineRefundMode);
                        log.info("refund outTradeNo[{}] update items [{}]", appointRecord.getOutTradeNo(), updatedItems);
                    }else {
                        log.info("unsupport offlineRefundMode[{}], outTradeNo[{}] ", offlineRefundMode, appointRecord.getOutTradeNo());
                        throw new DAOException(ErrorCode.SERVICE_ERROR, "暂不支持此种线下退款模式");
                    }
                    break;
                case OUTPATIENT:
                    OutpatientDAO outpatientDao = DAOFactory.getDAO(OutpatientDAO.class);
                    Outpatient outpatient = outpatientDao.getByOutTradeNo(order.getOutTradeNo());
                    if(outpatient==null){
                        log.error("the order not exists!");
                        throw new DAOException(ErrorCode.SERVICE_ERROR, "该订单不存在");
                    }
                    if(PayConstant.OFFLINE_REFUND_MODE_EXECUTE_EXCEPT_SETTLE ==offlineRefundMode) {
                        int updatedItems = orderDao.updateOrderForOffline(outpatient.getOutTradeNo(), offlineRefundMode);
                        NgariRefundService refundService = AppContextHolder.getBean("ngariRefundService", NgariRefundService.class);
                        resultMap = refundService.refund(outpatient.getId(), busTypeEnum.getCode());
                        log.info("execute OFFLINE_REFUND_MODE_EXECUTE_EXCEPT_SETTLE mode, outTradeNo[{}], updatedItems[{}], resultMap[{}]", outpatient.getOutTradeNo(), updatedItems, resultMap);
                    }else if(PayConstant.OFFLINE_REFUND_MODE_ONLY_CHANGE_STATUS == offlineRefundMode){
                        int updatedItems = outpatientDao.updateToRefundSuccessForOffline(outpatient.getOutTradeNo(), offlineRefundMode);
                        log.info("refund outTradeNo[{}] update items [{}]", outpatient.getOutTradeNo(), updatedItems);
                    }else {
                        log.info("unsupport offlineRefundMode[{}], outTradeNo[{}] ", offlineRefundMode, outpatient.getOutTradeNo());
                        throw new DAOException(ErrorCode.SERVICE_ERROR, "暂不支持此种线下退款模式");
                    }
                    break;
                case PREPAY:
                    PayBusinessDAO payBusinessDao = DAOFactory.getDAO(PayBusinessDAO.class);
                    PayBusiness paybusiness = payBusinessDao.getByOutTradeNo(order.getOutTradeNo());
                    if(paybusiness==null){
                        log.error("the order not exists!");
                        throw new DAOException(ErrorCode.SERVICE_ERROR, "该订单不存在");
                    }
                    if(PayConstant.OFFLINE_REFUND_MODE_EXECUTE_EXCEPT_SETTLE ==offlineRefundMode) {
                        int updatedItems = orderDao.updateOrderForOffline(paybusiness.getOutTradeNo(), offlineRefundMode);
                        NgariRefundService refundService = AppContextHolder.getBean("ngariRefundService", NgariRefundService.class);
                        resultMap = refundService.refund(paybusiness.getId(), busTypeEnum.getCode());
                        log.info("execute OFFLINE_REFUND_MODE_EXECUTE_EXCEPT_SETTLE mode, outTradeNo[{}], updatedItems[{}], resultMap[{}]", paybusiness.getOutTradeNo(), updatedItems, resultMap);
                    }else if(PayConstant.OFFLINE_REFUND_MODE_ONLY_CHANGE_STATUS == offlineRefundMode){
                        int updatedItems = payBusinessDao.updateToRefundSuccessForOffline(paybusiness.getOutTradeNo(), offlineRefundMode);
                        log.info("refund outTradeNo[{}] update items [{}]", paybusiness.getOutTradeNo(), updatedItems);
                    }else {
                        log.info("unsupport offlineRefundMode[{}], outTradeNo[{}] ", offlineRefundMode, paybusiness.getOutTradeNo());
                        throw new DAOException(ErrorCode.SERVICE_ERROR, "暂不支持此种线下退款模式");
                    }
                    break;
                default:
                    log.info("unsupport busType, busType[{}]", busTypeEnum);
                    throw new DAOException(ErrorCode.SERVICE_ERROR, "暂不支持此种业务线下退款操作！");
            }
            if(resultMap.containsKey("code") && PayConstant.RESULT_SUCCESS.equals(resultMap.get("code"))) {
                resultMap.put("status", 0);
                resultMap.put("msg", "success");
                resultMap.put("refundNo", null);
            }else {
                resultMap.put("status", ErrorCode.SERVICE_ERROR);
                resultMap.put("msg", "退款失败");
            }
            return resultMap;
        } catch (Exception e){
            log.error("refund error, request[{}], errorMessage[{}], stackTrace[{}]", JSONObject.toJSONString(request), e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            resultMap.put("status", ErrorCode.SERVICE_ERROR);
            String errorMessage = (e.getMessage()==null||"".equals(e.getMessage())||"null".equalsIgnoreCase(e.getMessage()))?"系统错误，请联系开发人员":e.getMessage();
            resultMap.put("msg", errorMessage);
            return resultMap;
        }
    }

    private boolean checkBusOrderStatus(int payFlag) {
        if(PayConstant.PAY_FLAG_NOT_PAY == payFlag){
            log.error("the order's status can not refund, payFlag[{}]", payFlag);
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该订单未支付，不能退款");
        }else if(PayConstant.PAY_FLAG_REFUND_SUCCESS==payFlag){
            log.error("the order's status can not refund, payFlag[{}]", payFlag);
//            throw new DAOException(ErrorCode.SERVICE_ERROR, "该订单已退款成功");
            return false;
        }else if(PayConstant.PAY_FLAG_REFUNDING==payFlag){
            log.error("the order's status can not refund, payFlag[{}]", payFlag);
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该订单正在退款，不能重复操作");
        }else if(PayConstant.PAY_FLAG_REFUND_FAIL==payFlag){
            log.error("the order's status can not refund, payFlag[{}]", payFlag);
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该订单退款失败，请联系管理员");
        }
        return true;
    }

    private boolean validateOfflineRequestPrameter(OfflineRequest request) {
        if(ValidateUtil.blankString(request.getHospitalNo())
                && ValidateUtil.blankString(request.getOrderNo())
                && ValidateUtil.blankString(request.getTradeNo())){
            log.error("validateOfflineRequestPrameter false, three no is all null, please check!");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "订单号、交易单号、医院内码至少有一个不能为空！");
        }
        if(ValidateUtil.nullOrZeroInteger(request.getOfflineRefundMode())){
            log.error("validateOfflineRequestPrameter false, offlineRefundMode null!");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "退款模式不能为空！");
        }
        return true;
    }

}
