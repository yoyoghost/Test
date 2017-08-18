package eh.wxpay.service;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Maps;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.dao.DoctorDAO;
import eh.bus.dao.ConsultDAO;
import eh.bus.dao.OutpatientDAO;
import eh.bus.service.common.CurrentUserInfo;
import eh.bus.service.payment.QueryOutpatient;
import eh.cdr.constant.OrderStatusConstant;
import eh.cdr.dao.RecipeOrderDAO;
import eh.entity.bus.Consult;
import eh.entity.bus.Outpatient;
import eh.entity.bus.msg.SimpleWxAccount;
import eh.entity.bus.pay.BusTypeEnum;
import eh.entity.bus.pay.ConfirmOrder;
import eh.entity.bus.pay.OpReturnPayParams;
import eh.entity.bus.pay.SimpleBusObject;
import eh.entity.cdr.RecipeOrder;
import eh.entity.pay.PaymentClient;
import eh.entity.pay.PaymentConfig;
import eh.mpi.service.sign.RequestSignRecordService;
import eh.pay.dao.PaymentClientDAO;
import eh.pay.service.PaymentConfigService;
import eh.unifiedpay.constant.PayWayEnum;
import eh.util.DictionaryUtil;
import eh.utils.DateConversion;
import eh.utils.LocalStringUtil;
import eh.utils.ValidateUtil;
import eh.wxpay.bus.service.support.BusPayService;
import eh.wxpay.constant.PayConstant;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


public class NgariPayService {
    private static final Logger logger = LoggerFactory.getLogger(NgariPayService.class);
    private PayService payService;
    private PayWayService payWayService;
    private static OutpatientDAO outpatientDAO;

    public NgariPayService() {
        outpatientDAO = DAOFactory.getDAO(OutpatientDAO.class);
    }
    /**
     * 定时任务
     * 执行策略： 0 0/1 * * * ?
     * 取消超过24小时的待支付订单
     */
    @RpcService
    public void cancelTimeOutOrder() {
        Date now = new Date();
        Date bf24Hour = DateConversion.getDateBFtHour(now, PayConstant.ORDER_OVER_TIME_HOURS);
        for (BusTypeEnum busTypeEnum : BusTypeEnum.values()) {
            if(busTypeEnum.equals(BusTypeEnum.CHECK) || busTypeEnum.equals(BusTypeEnum.MEETCLINIC)){
                continue;
            }
            try {
                String serviceName = StringUtils.uncapitalize(busTypeEnum.getCode()) + "BusPayService";
                BusPayService busPayService = AppContextHolder.getBean(serviceName, BusPayService.class);
                busPayService.doCancelForUnPayOrder(bf24Hour);
            } catch (Exception e) {
                logger.error("cancelTimeOutOrder error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            }
        }
    }

    /**
     * findConfirmOrderInfo扩展方法
     *
     * @param busType
     * @param busId
     * @param extInfo
     * @return
     */
    @RpcService
    public ConfirmOrder findConfirmOrderInfoExt(String busType, Integer busId, Map<String, String> extInfo) {
        logger.info("findConfirmOrderInfo start in, busType[{}], busId[{}]", busType, busId);
        if (ValidateUtil.blankString(busType) || ValidateUtil.nullOrZeroInteger(busId)) {
            logger.error("findConfirmOrderInfo necessary param null");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "必填参数为空");
        }
        try {
            String serviceName = StringUtils.uncapitalize(busType) + "BusPayService";
            BusPayService busPayService = AppContextHolder.getBean(serviceName, BusPayService.class);
            ConfirmOrder confirmOrder = busPayService.obtainConfirmOrder(busType, busId, extInfo);
            return confirmOrder;
        } catch (Exception e) {
            logger.error("findConfirmOrderInfo error, busType[{}], busId[{}], errorMessage[{}], stackTrace[{}]", busType, busId, e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }
    }

    /**
     * 查询加入优惠券之后的确认订单页信息
     *
     * @param busType
     * @param busId
     * @return
     */
    @RpcService
    public ConfirmOrder findConfirmOrderInfo(String busType, Integer busId) {
        return findConfirmOrderInfoExt(busType, busId, null);
    }


    /**
     * 获取机构业务支持的支付方式
     * @param clientId  端标识
     * @param busId     业务ID
     * @param busType   业务类型
     * @return
     */
    @RpcService
    public List<HashMap<String,Object>> getPayChannel(Integer clientId, String busId, String busType){
        logger.info("获取机构支付方式列表...params: clientId[{}], busId[{}], busType[{}]", clientId, busId, busType);
        List<HashMap<String,Object>> payChannelList = new ArrayList<HashMap<String, Object>>();
//        根据当前机构信息、业务类型、端信息，从运营平台获取对应支持的支付方式
        BusTypeEnum busTypeEnum = BusTypeEnum.fromCode(busType);
        Integer busOrgan = getBusOrgan(busId, busTypeEnum);
        PaymentConfigService paymentConfigService = AppContextHolder.getBean("paymentConfigService", PaymentConfigService.class);
        List<PaymentConfig> payList = paymentConfigService.findPaymentTypes(clientId,busType,busOrgan);//
        //
        for (PaymentConfig pc:payList){
            PaymentClientDAO paymentClientDAO =DAOFactory.getDAO(PaymentClientDAO.class);
            PaymentClient paymentClient = paymentClientDAO.getByClientIdAndPaymentType(clientId,pc.getPaymentType());
            if(paymentClient==null){
                throw new DAOException(ErrorCode.SERVICE_ERROR, "未配置默认支付方式");
            }
            String paymentName = "";
            try {
                paymentName =DictionaryController.instance().get("eh.pay.dictionary.PaymentType").getText(pc.getPaymentType());
            } catch (ControllerException e) {
                throw new DAOException(ErrorCode.SERVICE_ERROR,"获取字典 eh.pay.dictionary.PaymentType 异常");
            }
            HashMap<String, Object> hashMap = new HashMap<>();
            hashMap.put("paymentType", pc.getPaymentType());//支付渠道
            hashMap.put("paymentName", paymentName);//支付方式字典中名称
            hashMap.put("isDefault", pc.getCheck());//是否为默认支付
            hashMap.put("payWay", paymentClient.getPayWay());//payWay 方式07/08
            payChannelList.add(hashMap);
        }
        if(payChannelList.size()<1){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "未查询到可用的支付方式");
        }
        logger.info("查询到的支付列表："+payChannelList);
        return payChannelList;
    }

    /**
     * 微信网页下单统一接口（带优惠券）
     *
     * @param payway
     * @param busType
     * @param busId
     * @param couponId
     * @return
     */
    @RpcService
    public Map<String, Object> order(String payway, String busType, String busId, String couponId) {
        logger.info("微信网页下单进入...params: payway[{}], busType[{}], busId[{}], couponId[{}]", payway, busType, busId, couponId);
        SimpleWxAccount wxAccount = CurrentUserInfo.getSimpleWxAccount();
        return unifiedOrder(wxAccount.getAppId(), payway, busType, busId, couponId, wxAccount.getOpenId());
    }

    /**
     * app下单统一接口（带优惠券）
     *
     * @param appId
     * @param payway
     * @param busType
     * @param busId
     * @param couponId
     * @return
     */
    @RpcService
    public Map<String, Object> appOrder(String appId, String payway, String busType, String busId, String couponId) {
        logger.info("app order start in, appId[{}], payway[{}], busType[{}], busId[{}], couponId[{}]", appId, payway, busType, busId, couponId);
        return unifiedOrder(appId, payway, busType, busId, couponId, null);
    }

    private Map<String, Object> unifiedOrder(String appId, String payway, String busType, String busId, String couponId, String openId) {
        logger.info("unifiedOrder step in, appId[{}], payway[{}], busType[{}], busId[{}], couponId[{}], openId[{}]", appId, payway, busType, busId, couponId, openId);
        Map<String, Object> resultMap = Maps.newHashMap();
        if (ValidateUtil.blankString(payway) || ValidateUtil.blankString(busType) || ValidateUtil.blankString(busId)) {
            logger.info("order necessary params null, params: payway[{}], busType[{}], busId[{}], couponId[{}]", payway, busType, busId, couponId);
            resultMap.put("code", PayConstant.RESULT_FAIL);
            resultMap.put("msg", "必填参数为空");
            return resultMap;
        }
        Map<String, String> callbackParams = Maps.newHashMap();
        if (BusTypeEnum.fromCode(busType).equals(BusTypeEnum.SIGN)) {
            RequestSignRecordService requestSignRecordService = AppContextHolder.getBean("requestSignRecordService", RequestSignRecordService.class);
            requestSignRecordService.canPay(Integer.valueOf(busId));
        } else if (BusTypeEnum.fromCode(busType).equals(BusTypeEnum.RECIPE)) {
            //判断该订单是否还有效
            RecipeOrderDAO orderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
            RecipeOrder order = orderDAO.get(Integer.parseInt(busId));
            if (new Integer(1).equals(order.getEffective()) && OrderStatusConstant.READY_PAY.equals(order.getStatus())
                    && PayConstant.PAY_FLAG_NOT_PAY == order.getPayFlag()) {
                //允许支付
            } else {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "该订单已处理，请刷新后重试");
            }

        } else if (BusTypeEnum.fromCode(busType).equals(BusTypeEnum.CONSULT)) {
            ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
            Consult consult = consultDAO.getById(Integer.valueOf(busId));
            if (ValidateUtil.isNotTrue(consult.getTeams())) {
                List<Consult> samelist = consultDAO.findApplyingConsultByPatientsAndDoctorAndRequestMode(consult.getRequestMpi(), consult.getConsultDoctor(), consult.getRequestMode());
                checkIsExistsUnFinishedConsultWithCurrentDoctor(consult, samelist);
            }
            callbackParams.put("requestMode", String.valueOf(consult.getRequestMode()));
        } else if (BusTypeEnum.fromCode(busType).equals(BusTypeEnum.APPOINTCLOUD)) {
            //// TODO: 2017/5/3 云门诊业务支付前状态判断 
        }
        // 处理优惠信息F
        if (ValidateUtil.notBlankString(couponId)) {
            handleCouponInfo(busType, busId, couponId);
        }
        try {
            NgariPayService ngariPayService = AppContextHolder.getBean("ngariPayService", NgariPayService.class);
            resultMap = ngariPayService.actualDoPayApply(appId, payway, busType, busId, openId, callbackParams);
        } catch (Exception e) {
            logger.info("payApply exception, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            resultMap.put("code", PayConstant.RESULT_FAIL);
            resultMap.put("msg", e.getMessage());
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }
        return resultMap;
    }

    private void checkIsExistsUnFinishedConsultWithCurrentDoctor(Consult consult, List<Consult> samelist) {
        /**
         * 情况一：患者与个人医生A存在一个待支付的图文咨询，又向A所在的医生团队B发起一个咨询，且支付，
         * A接收团队咨询且回复，若此时患者去支付第一条待支付的个人咨询时，提示弹框：当前你正在和***医生咨询中，不能重复发起咨询哦。
         */
        if (ValidateUtil.notBlankList(samelist) && samelist.size() >= 2) {
            boolean exists = false;
            for (Consult cc : samelist) {
                if (!cc.getConsultId().equals(consult.getConsultId())) {
                    exists = true;
                }
            }
            if (exists) {
                String doctorName = DAOFactory.getDAO(DoctorDAO.class).getNameById(consult.getConsultDoctor());
                logger.info("unifiedOrder exists not ended consult with currentDoctor[{}], requestMode[{}]", consult.getConsultDoctor(), consult.getRequestMode());
                throw new DAOException(ErrorCode.SERVICE_ERROR, LocalStringUtil.format("当前你正在和{}医生咨询中，不能重复发起咨询哦。", doctorName));
            }
        }
    }

    private void handleCouponInfo(String busType, String busIdString, String couponIdString) {
        Integer busId = Integer.valueOf(busIdString);
        Integer couponId = Integer.valueOf(couponIdString);
        String serviceName = StringUtils.uncapitalize(busType) + "BusPayService";
        BusPayService busPayService = AppContextHolder.getBean(serviceName, BusPayService.class);
        busPayService.handleCoupon(busId, couponId);
    }


    /**
     * 此方法仅用于微信端网页各业务模块发起“申请并支付”时调用
     *
     * @param payway                      支付方式， 参见PayWayEnum
     * @param busType                     业务类型， 参见BusTypeEnum
     * @param busId                       业务id
     * @param organId                     机构id, 业务的目标方对应的机构id
     * @param callbackUrlAdditionalParams 跨公众号支付时，回调地址拼接时增加的业务相关参数，可传空
     * @return
     */
    public Map<String, Object> immediatlyPayForBus(String payway, String busType, Integer busId, Integer organId, Map<String, String> callbackUrlAdditionalParams) {
        logger.info("immediatlyPay start in, params: payway[{}], busType[{}], busId[{}], organId[{}], callbackUrlAdditionalParams[{}]", payway, busType, busId, organId, callbackUrlAdditionalParams);
        if (ValidateUtil.nullOrZeroInteger(busId) || ValidateUtil.blankString(payway) || ValidateUtil.blankString(busType) || ValidateUtil.nullOrZeroInteger(organId)) {
            logger.error("immediatlyPay necessary params is null, please check, organId[{}], payway[{}], busType[{}], busId[{}]", organId, payway, busType, busId);
            throw new DAOException("必填参数为空");
        }
        if (!PayWayEnum.WEIXIN_WAP.getCode().equals(payway) && !PayWayEnum.ALIPAY_WAP.getCode().equals(payway) && !PayWayEnum.CMB_WAP.getCode().equals(payway)) {
            logger.error("immediatlyPay payway only support weixinWap or alipayWap or cmbPayWap, payway[{}]", payway);
            throw new DAOException("此方法仅支持微信/支付宝/一网通网页支付");
        }
        try {
            SimpleWxAccount wxAccount = CurrentUserInfo.getSimpleWxAccount();
            Map<String, String> callbackParams = Maps.newHashMap();
            callbackParams.put("busId", String.valueOf(busId));
            callbackParams.put("busType", busType);
            callbackParams.put("sourceAppId", wxAccount.getAppId());
            if (callbackUrlAdditionalParams != null) {
                callbackParams.putAll(callbackUrlAdditionalParams);
            }
            OpReturnPayParams opReturnPayParams = payWayService.fetchPayTargetInfo(wxAccount.getAppId(), organId, payway, busType, callbackParams);
            PayWayEnum payWayEnum = PayWayEnum.fromCode(payway);
            return callDoPay(busType, String.valueOf(busId), payWayEnum, wxAccount.getOpenId(), opReturnPayParams);
        }catch (DAOException e){
            logger.error("immediatlyPay DAOException, payway[{}], busType[{}], busId[{}], organId[{}], callbackUrlAdditionalParams[{}], errorMessage[{}], stackTrace[{}]", payway, busType, busId, organId, callbackUrlAdditionalParams, e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw e;
        } catch (Exception e) {
            logger.error("immediatlyPay error, payway[{}], busType[{}], busId[{}], organId[{}], callbackUrlAdditionalParams[{}], errorMessage[{}], stackTrace[{}]", payway, busType, busId, organId, callbackUrlAdditionalParams, e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }
    }

    private Map<String, Object> callDoPay(String busType, String busId, PayWayEnum payWayEnum, String openId, OpReturnPayParams opReturnPayParams) throws Exception {
        Map<String, Object> map = new HashMap<>();
        if (handleBusPriceZero(busType, busId, payWayEnum,map)) {
            map.put(PayConstant.ORDER_NEED_PAY, 0);
        } else {
            if (opReturnPayParams.isStepOverMerchant()) {
                if (payWayEnum.getPayWay().equals("APP")) {
                    logger.error("AppPay can not stepOver, please check, opReturnParams[{}]", JSONObject.toJSONString(opReturnPayParams));
                    throw new DAOException(PayConstant.ORGAN_NOT_OPEN_ONLINE_PAY_MSG);
                }
                map.put(PayConstant.KEY_WECHAT_AUTHURL, opReturnPayParams.getWeChatAuthUrl());
            } else {
                map = payService.doPay(opReturnPayParams.getTargetAppId(), opReturnPayParams.getPayOrganId(), payWayEnum.getCode(), busType, busId, openId);
            }
            map.put("saFlag", -1);
            map.put(PayConstant.KEY_ISSTEPOVERWECHAT, opReturnPayParams.isStepOverMerchant());
        }
        if (!map.containsKey(PayConstant.ORDER_NEED_PAY)) {
            map.put(PayConstant.ORDER_NEED_PAY, 1);
        }
        map.put("payWay", payWayEnum.getPayWay());//前端根据payway判断调用的支付方式
        logger.info("返回前端支付结果：" + map);
        return map;
    }

    private boolean handleBusPriceZero(String busType, String busIdString, PayWayEnum payWayEnum,Map<String,Object> map) {
        logger.info("handleBusPriceZero busType[{}], busId[{}], payWayEnum[{}]", busType, busIdString, payWayEnum);
        try {
            int busId = Integer.valueOf(busIdString);
            String serviceName = StringUtils.uncapitalize(busType) + "BusPayService";
            BusPayService busPayService = AppContextHolder.getBean(serviceName, BusPayService.class);
            SimpleBusObject simpleBusObject = busPayService.obtainBusForOrder(busId, busType);
            if (simpleBusObject == null) {
                logger.info("handleBusPriceZero simpleBusObject is null! busType[{}], busId[{}]", busType, busIdString);
                return false;
            }
            if (PayConstant.PAY_FLAG_PAY_SUCCESS == simpleBusObject.getPayFlag()) {
                logger.error("handleBusPriceZero repeat apply order, please check! simpleBusObject[{}]", JSONObject.toJSONString(simpleBusObject));
                throw new DAOException(ErrorCode.REPEAT_ORDER, "该订单已支付，请勿重复下单");
            }
            if (ValidateUtil.nullOrZeroDouble(simpleBusObject.getActualPrice())) {
                String outTradeNo = payService.produceApplyNo(BusTypeEnum.fromCode(busType), simpleBusObject.getOrganId(), payWayEnum.getPayType());
                busPayService.handleBusWhenNoNeedPay(busId, outTradeNo, map);
                return true;
            }
        } catch (DAOException e){
            logger.error("handleBusPriceZero DAOException, busType[{}], busId[{}], errorMessage[{}], errorStackTrace[{}]", busType, busIdString, e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw e;
        } catch (Exception e) {
            logger.error("handleBusPriceZero error, busType[{}], busId[{}], errorMessage[{}], errorStackTrace[{}]", busType, busIdString, e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
        }
        return false;
    }


    /**
     * app端下单统一接口（不带优惠券）
     *
     * @param appId   app在
     * @param payway  支付方式，参见PayWayEnum的code值
     * @param busType 业务类型，参见BusTypeEnum的code值
     * @param busId   业务id
     * @return
     */
    @RpcService
    public Map<String, Object> payApply(String appId, String payway, String busType, String busId) {
        return actualDoPayApply(appId, payway, busType, busId, null, null);
    }

    /**
     * 前端支付结果查询统一接口
     *
     * @param busType 业务类型，参见BusTypeEnum的code值
     * @param busId   业务id
     * @return
     */
    @RpcService
    public Map<String, Object> payQuery(String busType, String busId) {
        logger.info("payQuery start in, busType[{}], busId[{}]", busType, busId);
        try {
            return payService.payQuery(busId, busType);
        } catch (Exception e) {
            logger.error("payQuery error, busType[{}], busId[{}], errorMessage[{}], stackTrace[{}]", busType, busId, e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw new DAOException(ErrorCode.SERVICE_ERROR, "订单查询错误");
        }
    }

    public Map<String, Object> actualDoPayApply(String appId, String payway, String busType, String busId, String platformUserId, Map<String, String> callbackParams) {
        logger.info("actualDoPayApply step in, params: appId[{}], payway[{}], busType[{}], busId[{}], platformUserId[{}], callbackParams[{}]", appId, payway, busType, busId, platformUserId, callbackParams);
        try {
            if (ValidateUtil.blankString(appId) || ValidateUtil.blankString(payway) || ValidateUtil.blankString(busType) || ValidateUtil.blankString(busId)) {
                logger.error("actualDoPayApply necessary params is null, please check, appId[{}], payway[{}], busType[{}], busId[{}]", appId, payway, busType, busId);
                throw new DAOException(ErrorCode.SERVICE_ERROR, "必填参数为空");
            }
            PayWayEnum payWayEnum = PayWayEnum.fromCode(payway);
            BusTypeEnum busTypeEnum = BusTypeEnum.fromCode(busType);
            Integer busOrgan = getBusOrgan(busId, busTypeEnum);
            if (callbackParams == null) {
                callbackParams = Maps.newHashMap();
            }
            callbackParams.put("busId", String.valueOf(busId));
            callbackParams.put("busType", busType);
            callbackParams.put("sourceAppId", appId);
            OpReturnPayParams opReturnPayParams = payWayService.fetchPayTargetInfo(appId, busOrgan, payway, busType, callbackParams);
            return callDoPay(busType, busId, payWayEnum, platformUserId, opReturnPayParams);
        } catch (DAOException e){
            logger.error("actualDoPayApply DAOException, appId[{}], payway[{}], busType[{}], busId[{}], errorMessage[{}], stackTrace[{}]", appId, payway, busType, busId, e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw e;
        } catch (Exception e) {
            logger.error("actualDoPayApply error, appId[{}], payway[{}], busType[{}], busId[{}], errorMessage[{}], stackTrace[{}]", appId, payway, busType, busId, e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }
    }

    private Integer getBusOrgan(String busIdString, BusTypeEnum busTypeEnum) {
        int busId = Integer.valueOf(busIdString);
        String serviceName = StringUtils.uncapitalize(busTypeEnum.getCode()) + "BusPayService";
        BusPayService busPayService = AppContextHolder.getBean(serviceName, BusPayService.class);
        SimpleBusObject simpleBusObject = busPayService.obtainBusForOrder(busId, busTypeEnum.getCode());
        return simpleBusObject.getOrganId();
    }

    public PayService getPayService() {
        return payService;
    }

    public void setPayService(PayService payService) {
        this.payService = payService;
    }

    public PayWayService getPayWayService() {
        return payWayService;
    }

    public void setPayWayService(PayWayService payWayService) {
        this.payWayService = payWayService;
    }
}
