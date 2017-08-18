package eh.unifiedpay.service;

import com.alibaba.fastjson.JSONObject;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcBean;
import ctd.util.annotation.RpcService;
import easypay.entity.vo.param.OrderCancelParam;
import easypay.entity.vo.param.OrderQueryParam;
import eh.bus.dao.*;
import eh.cdr.dao.RecipeOrderDAO;
import eh.entity.bus.*;
import eh.entity.bus.pay.BusTypeEnum;
import eh.entity.cdr.RecipeOrder;
import eh.entity.mindgift.MindGift;
import eh.entity.mpi.SignRecord;
import eh.mindgift.dao.MindGiftDAO;
import eh.mpi.dao.SignRecordDAO;
import eh.unifiedpay.constant.PayServiceConstant;
import eh.unifiedpay.constant.PayWayEnum;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;


/**
 * Created by IntelliJ IDEA.
 * Description:
 * User: xiangyf
 * Date: 2017-05-04 10:17.
 */
@RpcBean
public class UnifiedPayService {
    private static final Logger logger = LoggerFactory.getLogger(UnifiedPayService.class);
    private CommonPayRequestService commonPayRequestService;

    @RpcService
    public Map<String, Object> orderCancel(Integer busId, String busType) {
        BusTypeEnum busTypeEnum = BusTypeEnum.fromCode(busType);
        Map<String, Object> refundMap = getOrderCancelParam(busId, busTypeEnum);
        return orderCancelWithMap(busId, busType, refundMap);
    }

    private Map<String, Object> orderCancelWithMap(Integer busId, String busType, Map<String, Object> refundMap) {
        String applyNo = (String) refundMap.get("applyNo");
        //构建退款申请请求参数
        OrderCancelParam orderCancelParam = new OrderCancelParam();
        orderCancelParam.setApplyNo(applyNo);

        PayWayEnum payWayEnum = PayWayEnum.fromCode((String) refundMap.get("payWay"));
        logger.info("【云平台订单撤销申请】业务请求数据:" + orderCancelParam.toString() + ",业务类型=" + busType + ",业务id=" + busId);
        //payType:支付的渠道，1 支付宝；2 微信；3 一网通；
        String result = commonPayRequestService.payCommon((String) refundMap.get("organId"), payWayEnum.getPayType(), orderCancelParam, PayServiceConstant.ORDER_CANCEL);
        logger.info("【云平台订单撤销申请】应答数据: busType[{}], busId[{}], resultMap[{}]", busType, busId, result);
        JSONObject jsonObject = JSONObject.parseObject(result);

        Map<String, Object> resultMap = new HashMap<>();// 返回对象
        String code = (String) jsonObject.get("code");
        String msg = (String) jsonObject.get("msg");
        if (!code.isEmpty() && code.equals("200")) {
            String retry_flag = (String)jsonObject.getJSONObject("data").get("retry_flag");
            String apply_no = (String)jsonObject.getJSONObject("data").get("apply_no");
            resultMap.put("apply_no", apply_no);
            resultMap.put("trade_no", jsonObject.getJSONObject("data").get("trade_no"));
            resultMap.put("retry_flag", retry_flag);
            resultMap.put("action", jsonObject.getJSONObject("data").get("action"));
            resultMap.put("code", "SUCCESS");
            if(retry_flag.equals("N")){
                DAOFactory.getDAO(OrderDao.class).updateOrderWithOutTradeNoForRefundResult(5, apply_no);
            }
        } else {
            //支付平台异常，调用失败
            resultMap.put("code", "FAIL");
            resultMap.put("msg", msg);
        }

        return resultMap;
    }

    /**
     * 根据商户订单号更新撤销订单
     *
     * @param busId
     * @param busType
     * @param applyNo
     * @param payWay
     * @param organId
     * @return
     */
    @RpcService
    public Map<String, Object> orderCancelByOut(Integer busId, String busType, String applyNo, String payWay, String organId) {
        logger.info("orderCancelByOut params:busId=[{}],busType=[{}],applyNo=[{}],payway=[{}],organId=[{}]", busId, busType, applyNo, payWay, organId);
        Map<String, Object> refundMap = new HashMap<>();
        refundMap.put("applyNo", applyNo);
        refundMap.put("payWay", payWay);
        refundMap.put("organId", organId);

        return orderCancelWithMap(busId, busType, refundMap);
    }

    @RpcService
    public Map<String, Object> orderQuery(Integer busId, String busType) {
        Map<String, Object> resultMap = new HashMap<String, Object>();// 返回对象

        BusTypeEnum busTypeEnum = BusTypeEnum.fromCode(busType);
        Map<String, Object> refundMap = getOrderCancelParam(busId, busTypeEnum);
        String applyNo = (String) refundMap.get("applyNo");
        //构建退款申请请求参数
        OrderQueryParam orderQueryParam = new OrderQueryParam();
        orderQueryParam.setApplyNo(applyNo);

        PayWayEnum payWayEnum = PayWayEnum.fromCode((String) refundMap.get("payWay"));
        logger.info("【云平台订单查询申请】业务请求数据:" + orderQueryParam.toString() + ",业务类型=" + busType + ",业务id=" + busId);
        //payType:支付的渠道，1 支付宝；2 微信；3 一网通；
        String result = commonPayRequestService.payCommon((String) refundMap.get("organId"), payWayEnum.getPayType(), orderQueryParam, PayServiceConstant.ORDER_QUERY);
        logger.info("【云平台订单查询申请】应答数据: busType[{}], busId[{}], resultMap[{}]", busType, busId, result);
        JSONObject jsonObject = JSONObject.parseObject(result);

        String code = (String) jsonObject.get("code");
        String msg = (String) jsonObject.get("msg");
        if (code != null && code.equals("200")) {
            resultMap.put("total_amount", jsonObject.getJSONObject("data").get("total_amount"));
            resultMap.put("apply_no", jsonObject.getJSONObject("data").get("apply_no"));
            resultMap.put("trade_status", jsonObject.getJSONObject("data").get("trade_status"));
            resultMap.put("trade_no", jsonObject.getJSONObject("data").get("trade_no"));
            resultMap.put("code", "SUCCESS");
        } else {
            //支付平台异常，调用失败
            resultMap.put("code", "FAIL");
            resultMap.put("msg", msg);
        }

        logger.info("返回支付平台查询结果："+resultMap);
        return resultMap;
    }


    private Map<String, Object> getOrderCancelParam(Integer busId, BusTypeEnum busTypeEnum) {
        logger.info("【订单撤销申请】开始请求前业务数据查询...");
        Map<String, Object> orderCancelMap = new HashMap<String, Object>();
        String applyNo = "";
        String payWay = "";
        String organId = "";
        switch (busTypeEnum) {
            case TRANSFER:
                TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
                Transfer transfer = transferDAO.getById(busId);
                if (transfer == null) {
                    throw new DAOException("该转诊业务记录不存在");
                }
                applyNo = transfer.getOutTradeNo();
                if (StringUtils.isEmpty(applyNo)) {
                    throw new DAOException("商户订单号为空");
                }
                payWay = transfer.getPayWay();
                organId = transfer.getPayOrganId();
                break;
            case MEETCLINIC:
                break;
            case CONSULT://咨询
                ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
                Consult consult = consultDAO.getById(busId);
                if (consult == null) {
                    throw new DAOException("该咨询业务记录不存在");
                }
                applyNo = consult.getOutTradeNo();
                if (StringUtils.isEmpty(applyNo)) {
                    throw new DAOException("商户订单号为空");
                }
                payWay = consult.getPayWay();
                organId = consult.getPayOrganId();
                break;
            case APPOINT:
                AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
                AppointRecord appointRecord = appointRecordDAO.getByAppointRecordId(busId);
                if (appointRecord == null) {
                    throw new DAOException("该转诊业务记录不存在");
                }
                applyNo = appointRecord.getOutTradeNo();
                if (StringUtils.isEmpty(applyNo)) {
                    throw new DAOException("商户订单号为空");
                }
                payWay = appointRecord.getPayWay();
                organId = appointRecord.getPayOrganId();
                break;
            case CHECK:
                break;
            case RECIPE://处方
                RecipeOrderDAO orderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
                RecipeOrder order = orderDAO.get(busId);
                if (order == null) {
                    throw new DAOException("该处方业务记录不存在");
                }
                applyNo = order.getOutTradeNo();
                if (StringUtils.isEmpty(applyNo)) {
                    throw new DAOException("商户订单号为空");
                }
                payWay = order.getWxPayWay();
                organId = order.getPayOrganId();
                break;
            case SIGN:
                SignRecordDAO signRecordDAO = DAOFactory.getDAO(SignRecordDAO.class);
                SignRecord signRecord = signRecordDAO.get(busId);
                if (signRecord == null) {
                    throw new DAOException("该签约业务记录不存在");
                }
                applyNo = signRecord.getOutTradeNo();
                if (StringUtils.isEmpty(applyNo)) {
                    throw new DAOException("商户订单号为空");
                }
                payWay = signRecord.getPayWay();
                organId = signRecord.getPayOrganId();
                break;
            case OUTPATIENT:
                OutpatientDAO outpatientDAO = DAOFactory.getDAO(OutpatientDAO.class);
                Outpatient outpatient = outpatientDAO.getById(busId);
                if (outpatient == null) {
                    throw new DAOException("该门诊缴费业务记录不存在");
                }
                applyNo = outpatient.getOutTradeNo();
                if (StringUtils.isEmpty(applyNo)) {
                    throw new DAOException("商户订单号为空");
                }
                payWay = outpatient.getPayWay();
                organId = outpatient.getPayOrganId();
                break;
            case PREPAY:
                PayBusinessDAO payDAO = DAOFactory.getDAO(PayBusinessDAO.class);
                PayBusiness payBuss = payDAO.get(busId);
                if (payBuss == null) {
                    throw new DAOException("该住院缴费业务记录不存在");
                }
                applyNo = payBuss.getOutTradeNo();
                if (StringUtils.isEmpty(applyNo)) {
                    throw new DAOException("商户订单号为空");
                }
                payWay = payBuss.getPayWay();
                organId = payBuss.getPayOrganId();
                break;
            case APPOINTPAY:
                break;
            case MINDGIFT:
                MindGiftDAO mindGiftDAO = DAOFactory.getDAO(MindGiftDAO.class);
                MindGift mind = mindGiftDAO.get(busId);
                if (mind == null) {
                    throw new DAOException("该心意业务记录不存在");
                }
                applyNo = mind.getOutTradeNo();
                if (StringUtils.isEmpty(applyNo)) {
                    throw new DAOException("商户订单号为空");
                }
                payWay = mind.getPayWay();
                organId = mind.getPayOrganId();
                break;
            case APPOINTCLOUD:
                AppointRecordDAO appointRecordCloudDAO = DAOFactory.getDAO(AppointRecordDAO.class);
                AppointRecord appointRecordCloud = appointRecordCloudDAO.getByAppointRecordId(busId);
                if (appointRecordCloud == null) {
                    throw new DAOException("该转诊业务记录不存在");
                }
                applyNo = appointRecordCloud.getOutTradeNo();
                if (StringUtils.isEmpty(applyNo)) {
                    throw new DAOException("商户订单号为空");
                }
                payWay = appointRecordCloud.getPayWay();
                organId = appointRecordCloud.getPayOrganId();
                break;
            default:
                logger.error("getPartOfBusData busType not support, busType[{}]", busTypeEnum);
                throw new DAOException("未找到匹配的业务类型");
        }

        orderCancelMap.put("applyNo", applyNo);
        orderCancelMap.put("payWay", payWay);
        orderCancelMap.put("organId", organId);
        return orderCancelMap;
    }

}
