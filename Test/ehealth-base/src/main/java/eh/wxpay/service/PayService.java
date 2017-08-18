package eh.wxpay.service;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Maps;
import ctd.account.Client;
import ctd.account.UserRoleToken;
import ctd.account.thirdparty.ThirdParty;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.impl.thirdparty.ThirdPartyDao;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import easypay.entity.vo.param.OrderQueryParam;
import easypay.entity.vo.param.PayBizParam;
import eh.base.constant.ErrorCode;
import eh.base.dao.OrganDAO;
import eh.bus.dao.OrderDao;
import eh.bus.service.common.ClientPlatformEnum;
import eh.bus.service.common.CurrentUserInfo;
import eh.entity.base.Organ;
import eh.entity.bus.Order;
import eh.entity.bus.pay.BusTypeEnum;
import eh.entity.bus.pay.SimpleBusObject;
import eh.entity.mpi.Patient;
import eh.mpi.dao.PatientDAO;
import eh.remote.IAliServiceInterface;
import eh.remote.IWXPMServiceInterface;
import eh.remote.IWXServiceInterface;
import eh.unifiedpay.constant.PayServiceConstant;
import eh.unifiedpay.constant.PayWayEnum;
import eh.unifiedpay.service.CommonPayRequestService;
import eh.unifiedpay.service.PayNotifyServiceImpl;
import eh.utils.DateConversion;
import eh.utils.LocalStringUtil;
import eh.utils.ValidateUtil;
import eh.wxpay.bus.service.support.BusPayService;
import eh.wxpay.constant.PayConstant;
import eh.wxpay.exception.PriceZeroException;
import eh.wxpay.util.RandomStringGenerator;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Administrator on 2016/11/8 0008.
 */
public class PayService {
    private static final Logger logger = LoggerFactory.getLogger(PayService.class);
    private CommonPayRequestService commonPayRequestService = new CommonPayRequestService();

    @RpcService
    public Map<String, Object> doPay(String targetAppId, String payOrganId, String payway, String busType, String idString, String platformUserId) {
        logger.info("支付下单进入...params: targetAppId[{}], payOrganId[{}], payway[{}], busType[{}], idString[{}], platformUserId[{}]", targetAppId, payOrganId, payway, busType, idString, platformUserId);
        Map<String, Object> resultMap = new HashMap<>();
        try {
            if (ValidateUtil.blankString(payOrganId) || ValidateUtil.blankString(payway) || ValidateUtil.blankString(idString) || ValidateUtil.blankString(busType)) {
                logger.error("[{}] doPay requestParam invalid, targetAppId[{}], payOrganId[{}], payway[{}], busType[{}], idString[{}]", this.getClass().getSimpleName(), targetAppId, payOrganId, payway, busType, idString);
                throw new Exception("未获取到必填参数");
            }
            PayWayEnum payWayEnum = PayWayEnum.fromCode(payway);
            BusTypeEnum busTypeEnum = BusTypeEnum.fromCode(busType);
            Integer busId = Integer.valueOf(idString);
            if (payWayEnum == null || busTypeEnum == null || ValidateUtil.nullOrZeroInteger(busId)) {
                String errorMessage = LocalStringUtil.format("unknown busType or payWay error, busType[{}], payWay[{}], busId[{}]", busType, payway, busId);
                logger.info(errorMessage);
                throw new Exception(errorMessage);
            }
            //构建支付申请请求参数
            PayBizParam payBizParam = assemblingDocumentForPayApply(targetAppId, payOrganId, payWayEnum, busTypeEnum, busId, platformUserId);
            logger.info("【云平台支付申请】请求原始参数: busType[{}], busId[{}], paramMap[{}]", busType, busId, JSONUtils.toString(payBizParam));
            String result = commonPayRequestService.payCommon(payOrganId, payWayEnum.getPayType(), payBizParam, PayServiceConstant.ORDER_PAY);
            logger.info("【云平台支付申请】应答数据: busType[{}], busId[{}], resultMap[{}]", busType, busId, result);
            JSONObject jsonObject = JSONObject.parseObject(result);

            String code = (String) jsonObject.get("code");
            String msg = (String) jsonObject.get("msg");
            if (code != null && code.equals("200")) {
                resultMap = assemblePaymentResponse(resultMap, payWayEnum, jsonObject, busId);
                resultMap.put("price", payBizParam.getAmount().doubleValue());//跨公众号支付时显示的金额
            } else {
                resultMap.put("code", PayConstant.RESULT_FAIL);
                resultMap.put("msg", msg);
            }

        } catch (Exception e) {
            logger.error("method[payApply] 【云平台支付申请】异常, params: payway[{}], busType[{}], busId[{}], canonicalClassName[{}], simpleClassName[{}],  errorMessage[{}], stackTrace[{}]", payway, busType, idString, e.getMessage(), e.getClass().getCanonicalName(), e.getClass().getSimpleName(), JSONObject.toJSONString(e.getStackTrace()));
            if (e instanceof PriceZeroException || PriceZeroException.class.getCanonicalName().equals(e.getClass().getCanonicalName())) {
                logger.info("PriceZeroException...");
                resultMap.put(PayConstant.ORDER_NEED_PAY, 0);
            } else {
                resultMap.put("code", PayConstant.RESULT_FAIL);
                resultMap.put("msg", e.getMessage());
                throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
            }
        }
        if (!resultMap.containsKey(PayConstant.ORDER_NEED_PAY)) {
            resultMap.put(PayConstant.ORDER_NEED_PAY, 1);
        }
        return resultMap;
    }

    private Map<String, Object> assemblePaymentResponse(Map<String, Object> resultMap, PayWayEnum payWayEnum, JSONObject jsonObject, Integer busId) {
        //WAP、WEB、APP、JSAPI、QR、AUTH
        switch (payWayEnum.getPayWay()) {
            case "WAP":
                resultMap.put("formData", jsonObject.getJSONObject("data").get("html_form"));
                break;
            case "WEB":
                resultMap.put("formData", jsonObject.getJSONObject("data").get("html_form"));
                break;
            case "APP":
                if (payWayEnum.equals(PayWayEnum.WEIXIN_APP)) {
                    JSONObject resultString = jsonObject.getJSONObject("data");
                    resultMap.put("dao", resultString);
                } else {
                    Map<String, Object> result = new HashMap<>();
                    Map<String, Object> response = new HashMap<>();
                    //兼容老版本接口出参。。
                    result.put("order_info", jsonObject.getJSONObject("data").get("prepay_str"));
                    response.put("result", result);
                    resultMap.put("dao", response);
                }
                break;
            case "JSAPI":
                if (payWayEnum.equals(PayWayEnum.WEIXIN_WAP)) {
                    JSONObject resultString = jsonObject.getJSONObject("data");
                    resultMap = resultString;
                } else {
                    resultMap.put("prepay_id", jsonObject.getJSONObject("data").get("prepay_id"));
                }
                break;
            case "QR":
                resultMap.put("qr_code", jsonObject.getJSONObject("data").get("qr_code"));
                resultMap.put("prepay_id", jsonObject.getJSONObject("data").get("apply_no"));
                break;
            /*前端一般不存在这种支付方式
            case "AUTH":
                resultMap.put("qr_code",jsonObject.getJSONObject("data").get("qr_code"));
                resultMap.put("prepay_id",jsonObject.getJSONObject("data").get("prepay_id"));
                break;*/
            default:
                //不存在的其他支付方式。若不存在，在支付时已经报异常，此处可以不处理
                break;
        }
        resultMap.put("code", PayConstant.RESULT_SUCCESS);
        resultMap.put("busId", busId);
        return resultMap;
    }

    private PayBizParam assemblingDocumentForPayApply(String targetAppId, String payOrganId, PayWayEnum payWayEnum, BusTypeEnum busTypeEnum, Integer busId, String platFormUserId) throws Exception {
        logger.info("【云平台支付申请】开始组装第三方下单请求参数..getPayReqMap...targetAppId[{}], payOrganId[{}], busId[{}], busType[{}], payway[{}], platformUserId[{}]", targetAppId, payOrganId, busId, busTypeEnum, payWayEnum, platFormUserId);
        try {
            PayBizParam payBizParam = createDocumentAndPackCommonBusParam(payOrganId, busTypeEnum, busId, payWayEnum);
            logger.info("【云平台支付申请】平台openId:" + platFormUserId);
            payBizParam.setOpenId(platFormUserId);
            payBizParam.setNotifyUrl("client=ehealth-base");//base发起的支付，调用RPC服务处理异步通知结果
            // TODO: 2017/4/25 支付宝同步回调地址处理
            if (payWayEnum == PayWayEnum.ALIPAY_WAP || payWayEnum == PayWayEnum.CMB_WAP) {
                packIndividualizationParam(targetAppId, payBizParam, payWayEnum, platFormUserId, busId, busTypeEnum);
            }
            logger.info("【云平台支付申请】成功组装第三方下单请求参数.");
            return payBizParam;
        } catch (Exception e) {
            logger.info(LocalStringUtil.format("【云平台支付申请】组装第三方下单请求参数失败：[{}] build exception, errorMessage[{}], stackTrace[{}]", this.getClass().getSimpleName(), e.getMessage(), JSONObject.toJSONString(e.getStackTrace())));
            throw e;
        }
    }

    /**
     * 支付下单——封装个性化参数
     *
     * @param payWayEnum
     * @param platformUserId
     */
    protected void packIndividualizationParam(String targetAppId, PayBizParam payBizParam, PayWayEnum payWayEnum, String platformUserId, Integer busId, BusTypeEnum busTypeEnum) {
        String alipayWapReturnUrl = "";
        try {
            Client client = CurrentUserInfo.getCurrentClient();
            ClientPlatformEnum clientPlatformEnum = ClientPlatformEnum.fromKey(client.getOs());
            switch (clientPlatformEnum) {
                // TODO: 2017/5/25 一网通支付同步回调地址 
                case WX_WEB:
                case WEB:
                    if (payWayEnum.equals(PayWayEnum.ALIPAY_WAP)) {
                        Map<String, String> params = Maps.newTreeMap();
                        ThirdParty thirdParty = DAOFactory.getDAO(ThirdPartyDao.class).get(targetAppId);
                        UserRoleToken urt = UserRoleToken.getCurrent();
                        Patient patient = (Patient) urt.getProperty("patient");
                        params.put("appkey", targetAppId);
                        params.put("tid", platformUserId);
                        params.put("mobile", patient.getMobile());
                        params.put("idcard", patient.getIdcard());
                        params.put("patientName", patient.getPatientName());
                        params.put("signature", thirdParty.signature(packForSignature(params)));
                        params.put("backable", "1");
                        IWXServiceInterface wxService = AppContextHolder.getBean("eh.wxService", IWXServiceInterface.class);
                        alipayWapReturnUrl = wxService.getSinglePageUrlForThirdPlat(targetAppId, busTypeEnum.getCode(), busId, params);
                        logger.info("packIndividualizationParam returnUrl[{}]", alipayWapReturnUrl);
                    }
                    break;
                case ALILIFE:
                    IAliServiceInterface aliService = AppContextHolder.getBean("eh.aliPushMessService", IAliServiceInterface.class);
                    Map<String, String> callbackParams = Maps.newHashMap();
                    alipayWapReturnUrl = aliService.autoPackCallbackUrlForBus(callbackParams, targetAppId, busTypeEnum.getCode(), String.valueOf(busId));
                    break;
                case WEIXIN:
                    IWXPMServiceInterface wxpmService = AppContextHolder.getBean("eh.wxPushMessService", IWXPMServiceInterface.class);
                    Map<String, String> callBackParams = Maps.newHashMap();
                    alipayWapReturnUrl = wxpmService.getPayRedirectUrl(callBackParams, targetAppId, busTypeEnum, String.valueOf(busId));
                    break;
                default:
                    alipayWapReturnUrl = "";
                    break;
            }

        } catch (Exception e) {
            logger.error("packIndividualizationParam error, busType[{}], busId[{}], errorMessage[{}], stackTrace[{}]", busTypeEnum, busId, e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw new DAOException("支付宝网页地址拼装失败");
        }
        payBizParam.setReturnUrl(alipayWapReturnUrl);
    }

    private String packForSignature(Map<String, String> params) {
        StringBuilder s = new StringBuilder();
        for (String k : params.keySet()) {
            String v = params.get(k);
            s.append("&").append(k).append("=").append(StringUtils.isEmpty(v) ? "" : v);
        }
        return s.substring(1);
    }

    private PayBizParam createDocumentAndPackCommonBusParam(String payOrganId, BusTypeEnum busTypeEnum, Integer busId, PayWayEnum payWayEnum) throws Exception {
        logger.info("createDocumentAndPackCommonBusParam start pack, busTypeEnum[{}], busId[{}], payWayEnum[{}]", busTypeEnum, busId, payWayEnum);
        try {
            String serviceName = StringUtils.uncapitalize(busTypeEnum.getCode()) + "BusPayService";
            BusPayService busPayService = AppContextHolder.getBean(serviceName, BusPayService.class);
            SimpleBusObject simpleBusObject = busPayService.obtainBusForOrder(busId, busTypeEnum.getCode());
            if (ValidateUtil.nullOrZeroDouble(simpleBusObject.getActualPrice())) {
                String outTradeNo = produceApplyNo(busTypeEnum, simpleBusObject.getOrganId(), payWayEnum.getPayType());
                busPayService.handleBusWhenNoNeedPay(busId, outTradeNo,new HashMap<String,Object>());
                throw new PriceZeroException(LocalStringUtil.format("该[{}]单价格为0", busTypeEnum.getDesc()));
            }
            PayBizParam payBizParam = fullfillPayBizForPayApply(busTypeEnum, payWayEnum, simpleBusObject);
            Order order = saveOrder(busId, busTypeEnum, payBizParam.getApplyNo(), payWayEnum, payOrganId, payBizParam.getAmount().doubleValue());
            busPayService.onOrder(order);
            logger.info("createDocumentAndPackCommonBusParam pack success, busTypeEnum[{}], busId[{}], payWayEnum[{}]", busTypeEnum, busId, payWayEnum);
            return payBizParam;
        } catch (Exception e) {
            logger.error("createDocumentAndPackCommonBusParam pack exception, busTypeEnum[{}], busId[{}], payWayEnum[{}], errorMessage[{}], stackTrace[{}]", busTypeEnum, busId, payWayEnum, e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw e;
        }
    }

    private Order saveOrder(Integer busId, BusTypeEnum busTypeEnum, String applyNo, PayWayEnum payWayEnum, String payOrganId, Double price) {
        OrderDao orderDao = DAOFactory.getDAO(OrderDao.class);
        Order order = new Order();
        order.setBusId(busId);
        order.setBusType(busTypeEnum.getCode());
        order.setOutTradeNo(applyNo);
        order.setPayway(payWayEnum.getCode());
        order.setPayOrganId(payOrganId);
        order.setPayFlag(PayConstant.PAY_FLAG_NOT_PAY);
        order.setCreateTime(new Date());
        order.setUpdateTime(new Date());
        order.setPrice(price);
        order.setStatus(0);
        return orderDao.save(order);
    }

    /**
     * 咸阳版本定下交易流水规则：业务类型_机构编码_时间戳+2位的随机数 from huangjh
     * 说明：因微信限制流水号最长不能超过32位，故机构编码最长不能超过12位，若超过则截取
     *
     * @param busTypeEnum
     * @param targetOrgan
     * @return
     */
    public String produceApplyNo(BusTypeEnum busTypeEnum, Integer targetOrgan, Integer payType) {
        char separatorChar = '_';
        Organ organ = DAOFactory.getDAO(OrganDAO.class).getByOrganId(targetOrgan);
        String organizeCode = organ.getOrganizeCode();
        StringBuffer sb = new StringBuffer();
        // TODO: 2017/5/26  一网通支付机构配置
        if (payType == 3) {
            return RandomStringGenerator.getRandomNumByLength(10);//上海龙华一网通支付，交易流水号长度为10位
        } else {
            sb.append(busTypeEnum.applyPrefix());
            sb.append(separatorChar);
            if (organizeCode != null && organizeCode.length() > 12) {
                logger.info("warning, the organ's organizeCode length is bigger than 12, targetOrgan[{}]", targetOrgan);
                organizeCode = organizeCode.substring(0, 12);
            }
            sb.append(organizeCode);
            sb.append(separatorChar);
            sb.append(busTypeEnum.getSuffix());
            return sb.toString();
        }
    }

    private PayBizParam fullfillPayBizForPayApply(BusTypeEnum busTypeEnum, PayWayEnum payWayEnum, SimpleBusObject simpleBusObject) throws Exception {
        if (simpleBusObject == null) {
            String errorMsg = LocalStringUtil.format("该{}业务记录不存在", busTypeEnum.getName());
            throw new Exception(errorMsg);
        }
        //病人信息
        Patient patient = DAOFactory.getDAO(PatientDAO.class).getByMpiId(simpleBusObject.getMpiId());
        if (patient == null) {
            String errorMsg = "查无此人";
            throw new Exception(errorMsg);
        }
        //判断支付状态
        if (simpleBusObject.getPayFlag() != null && simpleBusObject.getPayFlag() == 1) {//已付费
            String errorMsg = LocalStringUtil.format("该{}单已付费", busTypeEnum.getName());
            throw new Exception(errorMsg);
        }
        if (simpleBusObject.getPayFlag() != null && simpleBusObject.getPayFlag() > 1) {//已付费
            String errorMsg = LocalStringUtil.format("该{}单已结束", busTypeEnum.getName());
            throw new Exception(errorMsg);
        }
        String payName = "";
        UserRoleToken token = UserRoleToken.getCurrent();
        if (token != null) {
            if (StringUtils.isBlank(token.getUserName())) {
                payName = patient.getPatientName();
            } else {
                payName = token.getUserName();
            }
        } else {
            payName = patient.getPatientName();
        }
        String applyNo = produceApplyNo(busTypeEnum, simpleBusObject.getOrganId(), payWayEnum.getPayType());
        String busDesc = LocalStringUtil.format("{}费用", busTypeEnum.getName());
        PayBizParam payBizParam = new PayBizParam();
        payBizParam.setReqOrganId(StringUtils.defaultString(String.valueOf(simpleBusObject.getOrganId()), ""));
        payBizParam.setFinalOrganId("");
        payBizParam.setApplyNo(applyNo);
        payBizParam.setPayName(payName);
        payBizParam.setPatientName(patient.getPatientName());
        payBizParam.setMrn(simpleBusObject.getMpiId());// TODO: 2017/4/24  用户病历号获取
        payBizParam.setSubject(busDesc);
        payBizParam.setPayWay(payWayEnum.getPayWay());
        payBizParam.setConsumeType((byte) busTypeEnum.getId());
        payBizParam.setItbPay(300);// TODO: 2017/4/24 超时自动关闭时间
        payBizParam.setAmount(BigDecimal.valueOf(simpleBusObject.getActualPrice()));

        return payBizParam;
    }

    @RpcService
    public Map<String, Object> payQuery(String busIdString, String busType) {
        logger.info("payQuery start in, busId[{}], busType[{}]", busIdString, busType);
        if (ValidateUtil.blankString(busIdString) || ValidateUtil.blankString(busType)) {
            logger.error("payQuery necessary param null, busId[{}], busType[{}]", busIdString, busType);
            throw new DAOException("必填参数为空");
        }
        Map<String, Object> resultMap = new HashMap<String, Object>();
        try {
            BusTypeEnum busTypeEnum = BusTypeEnum.fromCode(busType);
            Integer busId = Integer.valueOf(busIdString);

            String serviceName = StringUtils.uncapitalize(busTypeEnum.getCode()) + "BusPayService";
            BusPayService busPayService = AppContextHolder.getBean(serviceName, BusPayService.class);
            SimpleBusObject simpleBusObject = busPayService.obtainBusForOrder(busId, busType);
            PayWayEnum payWayEnum = PayWayEnum.fromCode(simpleBusObject.getPayWay());

            OrderQueryParam orderQueryParam = new OrderQueryParam();
            orderQueryParam.setApplyNo(simpleBusObject.getOutTradeNo());
            orderQueryParam.setTradeNo(simpleBusObject.getTradeNo());

            //调用支付平台服务
            String result = commonPayRequestService.payCommon(simpleBusObject.getPayOrganId(), payWayEnum.getPayType(), orderQueryParam, PayServiceConstant.ORDER_QUERY);
            JSONObject jsonObject = JSONObject.parseObject(result);
            String code = (String) jsonObject.get("code");
            String msg = (String) jsonObject.get("msg");
            if (!code.isEmpty() && code.equals("200")) {
                JSONObject data = jsonObject.getJSONObject("data");
                String tradeStatus = (String) data.get("trade_status");
                if (PayConstant.RESULT_SUCCESS.equals(tradeStatus)) {
                    String applyNo = (String) data.get("apply_no");
                    String tradeNo = (String) data.get("trade_no");
                    Date gmtPayment = DateConversion.getCurrentDate(StringUtils.defaultString((String) data.get("gmt_payment"), ""), DateConversion.DEFAULT_DATE_TIME);
                    PayNotifyServiceImpl payNotifyService = AppContextHolder.getBean("payNotify", PayNotifyServiceImpl.class);
                    payNotifyService.updateBusinessPayStatus(applyNo, tradeNo, gmtPayment, busTypeEnum);
                    resultMap.put("code", PayConstant.RESULT_SUCCESS);
                    // TODO: 2017/4/26 查询的返回结果需要哪些参数待确定
                } else {
                    resultMap.put("code", PayConstant.RESULT_FAIL);
                    resultMap.put("msg", msg);
                }
            }
        } catch (Exception e) {
            logger.error("【云平台订单查询】异常: errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            if (resultMap == null || resultMap.isEmpty()) {
                throw new DAOException(609, "云平台订单查询");
            }
            resultMap.put("code", PayConstant.RESULT_FAIL);
            resultMap.put("msg", e.getMessage());
        }
        logger.info("【云平台订单查询】云平台订单查询返回数据:" + JSONUtils.toString(resultMap));
        return resultMap;
    }

}
