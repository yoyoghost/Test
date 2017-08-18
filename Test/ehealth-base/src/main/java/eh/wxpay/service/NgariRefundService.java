package eh.wxpay.service;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Maps;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.bus.constant.ConsultConstant;
import eh.bus.dao.*;
import eh.cdr.dao.RecipeOrderDAO;
import eh.cdr.service.RecipeService;
import eh.coupon.service.CouponService;
import eh.entity.bus.*;
import eh.entity.bus.pay.BusTypeEnum;
import eh.entity.bus.pay.PayBasicParam;
import eh.entity.cdr.RecipeOrder;
import eh.entity.mindgift.MindGift;
import eh.entity.mpi.SignRecord;
import eh.entity.msg.SmsInfo;
import eh.mindgift.constant.MindGiftConstant;
import eh.mindgift.dao.MindGiftDAO;
import eh.mpi.dao.SignRecordDAO;
import eh.task.executor.AliSmsSendExecutor;
import eh.unifiedpay.service.UnifiedRefundService;
import eh.utils.LocalStringUtil;
import eh.utils.ValidateUtil;
import eh.wxpay.constant.PayConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Created by Administrator on 2016/11/16 0016.
 */
public class NgariRefundService {
    private static final Logger log = LoggerFactory.getLogger(NgariRefundService.class);

    private UnifiedRefundService unifiedRefundService;

    @RpcService
    public Map<String, Object> refund(Integer busId, String busType) {
        log.info("refund start in, busId[{}], busType[{}]", busId, busType);
        if (ValidateUtil.nullOrZeroInteger(busId) || ValidateUtil.blankString(busType)) {
            log.error("refund necessary param null, busId[{}], busType[{}]", busId, busType);
            throw new DAOException(ErrorCode.SERVICE_ERROR, "必填参数为空");
        }
        try {
            Map<String, Object> resultMap = Maps.newHashMap();
            BusTypeEnum busTypeEnum = BusTypeEnum.fromCode(busType);
            PayBasicParam payBasicParam = getPartOfBusData(busId, busTypeEnum);
            if(ValidateUtil.notBlankString(payBasicParam.getPayWay())) {//payWay 可能为null
                resultMap = unifiedRefundService.refund(busId,busType);
            } else {
                resultMap.put("code", PayConstant.RESULT_SUCCESS);
            }
            // 返还优惠券
            returnCoupon(busId, busType);
            log.info("refund end, busId[{}], busType[{}], resultMap[{}]", busId, busType, JSONObject.toJSONString(resultMap));
            return resultMap;
        } catch (Exception e) {
            log.error("refund error, busId[{}], busType[{}], errorMessage[{}], stackTrace[{}]", busId, busType, e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }
    }

    private void returnCoupon(Integer busId, String busType) {
        BusTypeEnum busTypeEnum = BusTypeEnum.fromCode(busType);
        switch (busTypeEnum) {
            case TRANSFER:
                break;
            case MEETCLINIC:
                break;
            case CONSULT:
                ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
                Consult consult = consultDAO.getById(busId);
                if (ValidateUtil.notNullAndZeroInteger(consult.getCouponId()) && consult.getCouponId() != -1
                        && (consult.getConsultStatus() == ConsultConstant.CONSULT_STATUS_CANCEL || (consult.getConsultStatus()==ConsultConstant.CONSULT_STATUS_REJECT && consult.getRefuseFlag()!=null && consult.getRefuseFlag()==0))) {
                    CouponService couponService = AppContextHolder.getBean("couponService", CouponService.class);
                    couponService.unuseCouponById(consult.getCouponId());
                }
                break;
            case APPOINT:
            case APPOINTPAY:
                AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
                AppointRecord appointRecord = appointRecordDAO.getByAppointRecordId(busId);
                if (ValidateUtil.notNullAndZeroInteger(appointRecord.getCouponId()) && appointRecord.getCouponId() != -1) {
                    CouponService couponService = AppContextHolder.getBean("couponService", CouponService.class);
                    couponService.unuseCouponById(appointRecord.getCouponId());
                }
                break;
            case CHECK:

                break;
            case RECIPE:
//                RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
//                Recipe recipe = recipeDAO.getByRecipeId(busId);
                RecipeOrderDAO orderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
                RecipeOrder order = orderDAO.get(busId);
                if(ValidateUtil.notNullAndZeroInteger(order.getCouponId()) && order.getCouponId()!=-1){
                    CouponService couponService = AppContextHolder.getBean("couponService", CouponService.class);
                    couponService.unuseCouponById(order.getCouponId());
                }
                break;
            case SIGN:
                SignRecordDAO signRecordDAO = DAOFactory.getDAO(SignRecordDAO.class);
                SignRecord signRecord = signRecordDAO.get(busId);
                if (ValidateUtil.notNullAndZeroInteger(signRecord.getCouponId()) && signRecord.getCouponId() != -1) {
                    CouponService couponService = AppContextHolder.getBean("couponService", CouponService.class);
                    couponService.unuseCouponById(signRecord.getCouponId());
                }
                break;
            case OUTPATIENT:

                break;
            case PREPAY:

                break;

            case MINDGIFT:
                MindGiftDAO mindGiftDAO = DAOFactory.getDAO(MindGiftDAO.class);
                MindGift mindGift = mindGiftDAO.get(busId);
                if (ValidateUtil.notNullAndZeroInteger(mindGift.getCouponId()) && mindGift.getCouponId() != -1
                        && (mindGift.getMindGiftStatus() == MindGiftConstant.MINDGIFT_STATUS_CANCEL)) {
                    CouponService couponService = AppContextHolder.getBean("couponService", CouponService.class);
                    couponService.unuseCouponById(mindGift.getCouponId());
                }
                break;

        }
    }

    /**
     * 退款查询
     *
     * @param busId
     * @param busType
     * @return
     */
    @RpcService
    public Map<String, Object> refundQuery(Integer busId, String busType) {
        log.info("refundQuery start in, busId[{}], busType[{}]", busId, busType);
        if (ValidateUtil.nullOrZeroInteger(busId) || ValidateUtil.blankString(busType)) {
            log.error("refundQuery necessary param null, busId[{}], busType[{}]", busId, busType);
            throw new DAOException(ErrorCode.SERVICE_ERROR, "必填参数为空");
        }
        try {
//            BusTypeEnum busTypeEnum = BusTypeEnum.fromCode(busType);
//            PayBasicParam payBasicParam = getPartOfBusData(busId, busTypeEnum);
            Map<String, Object> resultMap = unifiedRefundService.refundQuery(busId,busType);
            log.info("refundQuery end, busId[{}], busType[{}], resultMap[{}]", busId, busType, JSONObject.toJSONString(resultMap));
            return resultMap;
        } catch (Exception e) {
            log.error("refundQuery error, busId[{}], busType[{}], errorMessage[{}], stackTrace[{}]", busId, busType, e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }
    }

    @RpcService
    public void refundQueryOrUpdate() {
        log.info("云平台进入退款状态定时查询服务");
        // 转诊业务
        TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
        List<Transfer> trList = transferDAO.findByPayflag(2);// 退款中
        log.info("转诊退款中状态数量:" + trList.size());
        for (Transfer transfer : trList) {
            Map<String, Object> map = refundQuery(transfer.getTransferId(), "transfer");
            sendMessage(map, transfer.getTransferId(), "transfer");
        }
        // 咨询业务
        ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
        List<Consult> coList = consultDAO.findByPayflag(2);// 退款中
        log.info("咨询退款中状态数量:" + coList.size());
        for (Consult consult : coList) {
            Map<String, Object> map = refundQuery(consult.getConsultId(), "consult");
            sendMessage(map, consult.getConsultId(), "consult");
        }
        // 处方业务
//        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
//        List<Recipe> recipeList = recipeDAO.findByPayFlag(2);// 退款中
        RecipeOrderDAO orderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
        List<RecipeOrder> orderList = orderDAO.findByPayFlag(PayConstant.PAY_FLAG_REFUNDING);
        log.info("处方订单退款中状态数量:" + orderList.size());
        for (RecipeOrder order : orderList) {
            Map<String, Object> map = null;
            try {
                NgariRefundService refundService = AppContextHolder.getBean("ngariRefundService", NgariRefundService.class);
                map = refundService.refundQuery(order.getOrderId(), RecipeService.WX_RECIPE_BUSTYPE);
            } catch (Exception e) {
                log.error("refundQueryOrUpdate 处方订单退款查询 error. recipeId:"+order.getOrderId()+",map:"+ JSONUtils.toString(map)+",error:"+e.getMessage());
            }
        }

        log.info("云平台结束退款状态定时查询服务");
    }

    /**
     * @param map
     * @param busId
     * @param busType
     * @return void
     * @function 根据查询返回结果判断是否需要发送失败通知短信
     * @author zhangjr
     * @date 2016-4-5
     */
    private void sendMessage(Map<String, Object> map, Integer busId, String busType) {
        String code = (String) map.get("code");
        if("SUCCESS".equals(code)){
            String refund_status = (String) map.get("refund_status");
            if (!"SUCCESS".equals(refund_status) && !"PROCESSING".equals(refund_status)) {
                SmsInfo info = new SmsInfo();
                info.setBusId(busId);
                info.setBusType(busType);
                info.setSmsType("refundFailMsg");//微信退款失败通知
                info.setStatus(0);
                info.setOrganId(0);// 短信服务对应的机构， 0代表通用机构
                AliSmsSendExecutor exe = new AliSmsSendExecutor(info);
                exe.execute();
                log.error("退款查询返回退款结果失败！发送失败短信通知");
            }
        }
    }

    private PayBasicParam getPartOfBusData(Integer busId, BusTypeEnum busTypeEnum) {
        log.info("getPartOfBusData start in, busId[{}], busTypeEnum[{}]", busId, busTypeEnum);
        try {
            PayBasicParam payBasicParam = null;
            switch (busTypeEnum) {
                case TRANSFER:
                    Transfer transfer = DAOFactory.getDAO(TransferDAO.class).getById(busId);
                    payBasicParam = BeanUtils.map(transfer, PayBasicParam.class);
                    break;
                case RECIPE:
                    RecipeOrder order = DAOFactory.getDAO(RecipeOrderDAO.class).get(busId);
                    payBasicParam = BeanUtils.map(order, PayBasicParam.class);
                    payBasicParam.setPayWay(order.getWxPayWay());
                    break;
                case CONSULT:
                    Consult consult = DAOFactory.getDAO(ConsultDAO.class).getById(busId);
                    payBasicParam = BeanUtils.map(consult, PayBasicParam.class);
                    break;
                case APPOINT:
                case APPOINTPAY:
                    AppointRecord appointRecord = DAOFactory.getDAO(AppointRecordDAO.class).getByAppointRecordId(busId);
                    payBasicParam = BeanUtils.map(appointRecord, PayBasicParam.class);
                    break;
                case SIGN:
                    SignRecord signRecord = DAOFactory.getDAO(SignRecordDAO.class).get(busId);
                    payBasicParam = BeanUtils.map(signRecord, PayBasicParam.class);
                    break;
                case OUTPATIENT:
                    Outpatient outpatient = DAOFactory.getDAO(OutpatientDAO.class).getById(busId);
                    payBasicParam = BeanUtils.map(outpatient, PayBasicParam.class);
                    break;
                case PREPAY:
                    PayBusiness payBusiness = DAOFactory.getDAO(PayBusinessDAO.class).getById(busId);
                    payBasicParam = BeanUtils.map(payBusiness, PayBasicParam.class);
                    break;
                case MINDGIFT:
                    MindGift mind=DAOFactory.getDAO(MindGiftDAO.class).get(busId);
                    payBasicParam=BeanUtils.map(mind,PayBasicParam.class);
                    break;
                default:
                    log.info("getPartOfBusData busType not support, busType[{}]", busTypeEnum);
                    throw new DAOException(ErrorCode.SERVICE_ERROR, "暂不支持此类型");
            }
            if (payBasicParam == null) {
                String errorMessage = LocalStringUtil.format("the bus not exists, busId[{}], busType[{}]", busId, busTypeEnum);
                log.info(errorMessage);
                throw new DAOException(errorMessage);
            }
            return payBasicParam;
        } catch (Exception e) {
            log.error("getPartOfBusData error, busId[{}], busTypeEnum[{}], errorMessage[{}], stackTrace[{}]", busId, busTypeEnum, e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw new DAOException(e.getMessage());
        }
    }

    public UnifiedRefundService getUnifiedRefundService() {
        return unifiedRefundService;
    }

    public void setUnifiedRefundService(UnifiedRefundService unifiedRefundService) {
        this.unifiedRefundService = unifiedRefundService;
    }
}
