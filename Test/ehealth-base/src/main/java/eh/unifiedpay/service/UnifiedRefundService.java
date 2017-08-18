package eh.unifiedpay.service;

import com.alibaba.fastjson.JSONObject;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import easypay.entity.vo.param.RefundParam;
import easypay.entity.vo.param.RefundQueryParam;
import eh.bus.constant.BusPayConstant;
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
import eh.wxpay.service.RefundService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * Description:
 * User: xiangyf
 * Date: 2017-04-26 9:36.
 */
public class UnifiedRefundService extends RefundService {
    private static final Logger logger = LoggerFactory.getLogger(UnifiedRefundService.class);
    private CommonPayRequestService commonPayRequestService;

    @RpcService
    public Map<String, Object> refund(Integer busId, String busType) {
        BusTypeEnum busTypeEnum = BusTypeEnum.fromCode(busType);
        Map<String, Object> refundMap = getRefundParam(busId, busTypeEnum);
        return refundWithMap(busId, busType, refundMap);
    }

    private Map<String, Object> refundWithMap(Integer busId, String busType, Map<String, Object> refundMap) {
        String applyNo = (String) refundMap.get(BusPayConstant.PayParam.APPLYNO);
        //构建退款申请请求参数
        RefundParam refundParam = new RefundParam();
        refundParam.setApplyNo(applyNo);
        refundParam.setAmount(BigDecimal.valueOf((Double) refundMap.get(BusPayConstant.PayParam.AMOUNT)));
        refundParam.setRemark((String) refundMap.get(BusPayConstant.PayParam.REMARK));
        refundParam.setOperator(BusPayConstant.PayParam.OPERATOR_BASE);

        PayWayEnum payWayEnum = PayWayEnum.fromCode((String) refundMap.get(BusPayConstant.PayParam.PAYWAY));
        logger.info("【云平台退款申请】业务请求数据:[{}],业务类型=[{}],业务id=[{}]", refundParam, busType, busId);
        //payType:支付的渠道，1 支付宝；2 微信；3 一网通；
        String result = commonPayRequestService.payCommon((String) refundMap.get(BusPayConstant.PayParam.ORGANID), payWayEnum.getPayType(), refundParam, PayServiceConstant.ORDER_REFUND);
        logger.info("【云平台退款申请】应答数据: busType[{}], busId[{}], resultMap[{}]", busType, busId, result);
        JSONObject jsonObject = JSONObject.parseObject(result);

        Map<String, Object> resultMap = new HashMap<>();
        String code = (String) jsonObject.get(BusPayConstant.Return.CODE);
        String msg = (String) jsonObject.get(BusPayConstant.Return.MSG);
        if (!code.isEmpty() && code.equals(BusPayConstant.Result.CODE_SUCCESS)) {
            updatePayflag(applyNo, BusPayConstant.PayFlag.REFUND_SUCCESS);
            resultMap.put(BusPayConstant.Return.CODE, BusPayConstant.Result.SUCCESS);
        } else {
            //退款失败
            updatePayflag(applyNo, BusPayConstant.PayFlag.REFUND_FAIL);
            resultMap.put(BusPayConstant.Return.CODE, BusPayConstant.Result.REFUND_FAIL);
            resultMap.put(BusPayConstant.Return.MSG, msg);
        }

        return resultMap;
    }

    /**
     * 根据order对象对没有预约记录的单子进行退款
     *
     * @param busId
     * @param busType
     * @param order
     * @return
     */
    @RpcService
    public Map<String, Object> refundByOrder(Integer busId, String busType, Order order) {
        if (order == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "order object is required!");
        }

        Map<String, Object> refundMap = new HashMap<>();
        refundMap.put(BusPayConstant.PayParam.APPLYNO, order.getOutTradeNo());
        refundMap.put(BusPayConstant.PayParam.TRADENO, StringUtils.defaultString(order.getTradeNo(), BusPayConstant.Judgment.EMPTYSTRING));//第三方流水号可空
        refundMap.put(BusPayConstant.PayParam.AMOUNT, order.getPrice());
        refundMap.put(BusPayConstant.PayParam.PAYWAY, order.getPayway());
        refundMap.put(BusPayConstant.PayParam.ORGANID, order.getPayOrganId());

        return refundWithMap(busId, busType, refundMap);
    }

    @Override
    public Map<String, Object> refundQuery(Integer busId, String busType) {
        Map<String, Object> resultMap = new HashMap<>();// 返回对象

        BusTypeEnum busTypeEnum = BusTypeEnum.fromCode(busType);
        Map<String, Object> refundMap = getRefundQueryParam(busId, busTypeEnum);
        String applyNo = (String) refundMap.get("applyNo");
        //构建退款申请请求参数
        RefundQueryParam refundQueryParam = new RefundQueryParam();
        refundQueryParam.setApplyNo(applyNo);

        PayWayEnum payWayEnum = PayWayEnum.fromCode((String) refundMap.get("payWay"));
        logger.info("【云平台退款申请】业务请求数据:" + refundQueryParam.toString() + ",业务类型=" + busType + ",业务id=" + busId);
        //payType:支付的渠道，1 支付宝；2 微信；3 一网通；
        String result = commonPayRequestService.payCommon((String) refundMap.get("organId"), payWayEnum.getPayType(), refundQueryParam, PayServiceConstant.ORDER_REFUND_QUERY);
        logger.info("【云平台退款申请】应答数据: busType[{}], busId[{}], resultMap[{}]", busType, busId, result);
        JSONObject jsonObject = JSONObject.parseObject(result);

        String code = (String) jsonObject.get("code");
        String msg = (String) jsonObject.get("msg");
        if (!code.isEmpty() && code.equals("200")) {
            String refund_status = (String) jsonObject.getJSONObject("data").get("refund_status");
            if (refund_status.equals("SUCCESS")) {
                updatePayflag(applyNo, 3);
            } else if (refund_status.equals("PROCESS")) {//退款中
                updatePayflag(applyNo, 2);
            } else {
                updatePayflag(applyNo, 4);
            }
            resultMap.put("refund_status",refund_status);
            resultMap.put("code", "SUCCESS");
        } else {
            //退款查询失败
            resultMap.put("code", "REFUND_QUERY_FAIL");
            resultMap.put("msg", msg);
        }

        return resultMap;
    }


    private Map<String, Object> getRefundParam(Integer busId, BusTypeEnum busTypeEnum) {
        logger.info("【退款申请】开始请求前业务数据查询...");
        Map<String, Object> refundMap = new HashMap<>();
        String applyNo = "";
        String tradeNo = "";
        String payWay = "";
        String organId = "";
        Integer payFlag = 0;
        Double price = 0.00;
        switch (busTypeEnum) {
            case TRANSFER:
                TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
                Transfer transfer = transferDAO.getById(busId);
                if (transfer == null) {
                    throw new DAOException("该转诊业务记录不存在");
                }
                applyNo = transfer.getOutTradeNo();
                payWay = transfer.getPayWay();
                tradeNo = transfer.getTradeNo();
                payFlag = transfer.getPayflag();
                price = transfer.getTransferCost();
                organId = transfer.getPayOrganId();
                refundMap.put("remark", "转诊超时/拒绝自动退款");
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
                payWay = consult.getPayWay();
                tradeNo = consult.getTradeNo();
                payFlag = consult.getPayflag();
                price = consult.getActualPrice() == null ? consult.getConsultCost() : consult.getActualPrice();
                organId = consult.getPayOrganId();
                refundMap.put("remark", "处方单自动退款");
                break;
            case APPOINTPAY:
            case APPOINT:
                AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
                AppointRecord appointRecord = appointRecordDAO.getByAppointRecordId(busId);
                if (appointRecord == null) {
                    throw new DAOException("该转诊业务记录不存在");
                }
                applyNo = appointRecord.getOutTradeNo();
                payWay = appointRecord.getPayWay();
                tradeNo = appointRecord.getTradeNo();
                payFlag = appointRecord.getPayFlag();
                price = appointRecord.getActualPrice() != null ? appointRecord.getActualPrice() : appointRecord.getClinicPrice();
                organId = appointRecord.getPayOrganId();
                refundMap.put("remark", "转诊超时/拒绝自动退款");
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
                payWay = order.getWxPayWay();
                tradeNo = order.getTradeNo();
                payFlag = order.getPayFlag();
                price = order.getActualPrice();
                organId = order.getPayOrganId();
                refundMap.put("remark", "处方单自动退款");
                break;
            case SIGN:
                SignRecordDAO signRecordDAO = DAOFactory.getDAO(SignRecordDAO.class);
                SignRecord signRecord = signRecordDAO.get(busId);
                if (signRecord == null) {
                    throw new DAOException("该签约业务记录不存在");
                }
                applyNo = signRecord.getOutTradeNo();
                payWay = signRecord.getPayWay();
                tradeNo = signRecord.getTradeNo();
                payFlag = signRecord.getPayFlag();
                price = signRecord.getActualPrice() != null ? signRecord.getActualPrice() : signRecord.getSignCost();
                organId = signRecord.getPayOrganId();
                refundMap.put("remark", "签约费用退款");
                break;
            case OUTPATIENT:
                OutpatientDAO outpatientDAO = DAOFactory.getDAO(OutpatientDAO.class);
                Outpatient outpatient = outpatientDAO.getById(busId);
                if (outpatient == null) {
                    throw new DAOException("该门诊缴费业务记录不存在");
                }
                applyNo = outpatient.getOutTradeNo();
                payWay = outpatient.getPayWay();
                tradeNo = outpatient.getTradeNo();
                payFlag = outpatient.getPayflag();
                price = outpatient.getTotalFee();
                organId = outpatient.getPayOrganId();
                refundMap.put("remark", "门诊缴费费用退款");
                break;
            case PREPAY:
                PayBusinessDAO payDAO = DAOFactory.getDAO(PayBusinessDAO.class);
                PayBusiness payBuss = payDAO.get(busId);
                if (payBuss == null) {
                    throw new DAOException("该住院缴费业务记录不存在");
                }
                applyNo = payBuss.getOutTradeNo();
                payWay = payBuss.getPayWay();
                tradeNo = payBuss.getTradeNo();
                payFlag = payBuss.getPayflag() == null ? 0 : payBuss.getPayflag();
                price = payBuss.getTotalFee();
                organId = payBuss.getPayOrganId();
                refundMap.put("remark", "住院缴费费用退款");
                break;
            case MINDGIFT:
                MindGiftDAO mindGiftDAO = DAOFactory.getDAO(MindGiftDAO.class);
                MindGift mind = mindGiftDAO.get(busId);
                if (mind == null) {
                    throw new DAOException("该心意业务记录不存在");
                }
                applyNo = mind.getOutTradeNo();
                payWay = mind.getPayWay();
                tradeNo = mind.getTradeNo();
                payFlag = mind.getPayFlag();
                price = mind.getActualPrice() == null ? mind.getAmount() : mind.getActualPrice();
                organId = mind.getPayOrganId();
                refundMap.put("remark", "心意费用退款");
                break;
            case APPOINTCLOUD:
                AppointRecordDAO appointRecordCloudDAO = DAOFactory.getDAO(AppointRecordDAO.class);
                AppointRecord appointRecordCloud = appointRecordCloudDAO.getByAppointRecordId(busId);
                if (appointRecordCloud == null) {
                    throw new DAOException("该转诊业务记录不存在");
                }
                applyNo = appointRecordCloud.getOutTradeNo();
                payWay = appointRecordCloud.getPayWay();
                tradeNo = appointRecordCloud.getTradeNo();
                payFlag = appointRecordCloud.getPayFlag();
                price = appointRecordCloud.getActualPrice() != null ? appointRecordCloud.getActualPrice() : appointRecordCloud.getClinicPrice();
                organId = appointRecordCloud.getPayOrganId();
                refundMap.put("remark", "转诊超时/拒绝自动退款");
                break;
            default:
                logger.error("getPartOfBusData busType not support, busType[{}]", busTypeEnum);
                throw new DAOException("未找到匹配的业务类型");
        }

        // 判断支付状态
        checkPayFlag(applyNo, payFlag, price, busTypeEnum.getDesc());
        refundMap.put("applyNo", applyNo);
        refundMap.put("tradeNo", StringUtils.defaultString(tradeNo, ""));//第三方流水号可空
        refundMap.put("amount", price);
        refundMap.put("payWay", payWay);
        refundMap.put("organId", organId);
        return refundMap;
    }

    private void checkPayFlag(String applyNo, Integer payFlag, Double price, String busDesc) {
        if (StringUtils.isEmpty(applyNo)) {
            throw new DAOException("商户订单号为空");
        }
        if (payFlag == null || payFlag == 0 || payFlag == 2) {
            String msg = "";
            if (payFlag == null) {
                payFlag = 0;
            }
            switch (payFlag) {
                case 0:
                    msg = "尚未付费，不能发起退款";
                    break;
                case 2:
                    msg = "已有一笔金额在退款中,暂不能发起退款";
                    break;
            }
            throw new DAOException("该" + busDesc + msg);
        } else {
            if (price <= 0.00) {
                throw new DAOException("待退款金额必须大于0.00");
            }
        }
    }

    private Map<String, Object> getRefundQueryParam(Integer busId, BusTypeEnum busTypeEnum) throws DAOException {
        logger.info("【退款查询申请】开始请求前业务数据查询...");
        Map<String, Object> refundMap = new HashMap<>();
        String applyNo = "";
        String payWay = "";
        String organId = "";
        Integer payFlag = 0;
        switch (busTypeEnum) {
            case TRANSFER:
                TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
                Transfer transfer = transferDAO.getById(busId);
                if (transfer == null) {
                    throw new DAOException("该转诊业务记录不存在");
                }
                applyNo = transfer.getOutTradeNo();
                payWay = transfer.getPayWay();
                payFlag = transfer.getPayflag();
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
                payWay = consult.getPayWay();
                payFlag = consult.getPayflag();
                organId = consult.getPayOrganId();
                break;
            case APPOINT:
                AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
                AppointRecord appointRecord = appointRecordDAO.getByAppointRecordId(busId);
                if (appointRecord == null) {
                    throw new DAOException("该转诊业务记录不存在");
                }
                applyNo = appointRecord.getOutTradeNo();
                payWay = appointRecord.getPayWay();
                payFlag = appointRecord.getPayFlag();
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
                payWay = order.getWxPayWay();
                payFlag = order.getPayFlag();
                organId = order.getPayOrganId();
                break;
            case SIGN:
                SignRecordDAO signRecordDAO = DAOFactory.getDAO(SignRecordDAO.class);
                SignRecord signRecord = signRecordDAO.get(busId);
                if (signRecord == null) {
                    throw new DAOException("该签约业务记录不存在");
                }
                applyNo = signRecord.getOutTradeNo();
                payWay = signRecord.getPayWay();
                payFlag = signRecord.getPayFlag();
                organId = signRecord.getPayOrganId();
                break;
            case OUTPATIENT:
                OutpatientDAO outpatientDAO = DAOFactory.getDAO(OutpatientDAO.class);
                Outpatient outpatient = outpatientDAO.getById(busId);
                if (outpatient == null) {
                    throw new DAOException("该门诊缴费业务记录不存在");
                }
                applyNo = outpatient.getOutTradeNo();
                payWay = outpatient.getPayWay();
                payFlag = outpatient.getPayflag();
                organId = outpatient.getPayOrganId();
                break;
            case PREPAY:
                PayBusinessDAO payDAO = DAOFactory.getDAO(PayBusinessDAO.class);
                PayBusiness payBuss = payDAO.get(busId);
                if (payBuss == null) {
                    throw new DAOException("该住院缴费业务记录不存在");
                }
                applyNo = payBuss.getOutTradeNo();
                payWay = payBuss.getPayWay();
                payFlag = payBuss.getPayflag() == null ? 0 : payBuss.getPayflag();
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
                payWay = mind.getPayWay();
                payFlag = mind.getPayFlag();
                organId = mind.getPayOrganId();
                break;
            default:
                logger.error("getPartOfBusData busType not support, busType[{}]", busTypeEnum);
                throw new DAOException("未找到匹配的业务类型");
        }

        // 判断有无发生状态
        checkPayFlag(applyNo, payFlag, busTypeEnum.getDesc());
        refundMap.put("applyNo", applyNo);
        refundMap.put("payWay", payWay);
        refundMap.put("organId", organId);
        return refundMap;
    }

    private void checkPayFlag(String applyNo, Integer payFlag, String busDesc) {
        if (StringUtils.isEmpty(applyNo)) {
            throw new DAOException("商户订单号为空");
        }
        if (payFlag == null || payFlag == 0 || payFlag == 1) {
            throw new DAOException("该" + busDesc + "没有退款信息");

        }
    }
}
