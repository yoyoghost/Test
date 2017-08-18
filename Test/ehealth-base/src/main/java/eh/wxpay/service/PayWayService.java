package eh.wxpay.service;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Maps;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.service.PayConfigService;
import eh.entity.base.AliPayConfig;
import eh.entity.bus.pay.BusTypeEnum;
import eh.entity.bus.pay.OpReturnPayParams;
import eh.entity.wx.WXConfig;
import eh.remote.IWXServiceInterface;
import eh.unifiedpay.constant.PayWayEnum;
import eh.utils.LocalStringUtil;
import eh.utils.ValidateUtil;
import eh.wxpay.constant.PayConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * PayWayService
 *
 * @author FE
 * @date 2016/10/10
 */
public class PayWayService {

    private static final Logger log = LoggerFactory.getLogger(PayWayService.class);


    /**
     * 获取支付目标机构信息
     * @param appId   当前公众号appId或者当前app在不同支付平台申请到的appKey
     * @param payway  支付方式，参见PayWayEnum的code值
     * @param busType 业务类型，参见BusTypeEnum的code值，需要注意的是实际传往运营的值不是code而是os_businessTypeKey的值
     * @param callbackParams   拼接微信网页跨公众号支付所需参数，当为微信网页支付时为必填参数，其余情况可为空
     * @return
     */
    @RpcService
    public OpReturnPayParams fetchPayTargetInfo(String appId, Integer organId, String payway, String busType, Map<String, String> callbackParams){
        log.info("fetchPayTargetInfo start in, appId[{}], organId[{}], payway[{}], busType[{}], callbackParams[{}]", appId, organId, payway, busType, callbackParams);
        try {
            if(ValidateUtil.blankString(appId) || ValidateUtil.blankString(payway) || ValidateUtil.blankString(busType) || ValidateUtil.nullOrZeroInteger(organId)){
                log.error("fetchPayTargetInfo necessary params is null, please check, appId[{}], organId[{}], payway[{}], busType[{}], callbackParams[{}]", appId, organId, payway, busType, callbackParams);
                throw new DAOException("必填参数为空");
            }
            PayWayEnum payWayEnum = PayWayEnum.fromCode(payway);
            BusTypeEnum busTypeEnum = BusTypeEnum.fromCode(busType);
            OpReturnPayParams opReturnPayParams = null;
            switch (payWayEnum.getPayType()){
                case 1:
                    opReturnPayParams = fullfillOtherInfoForAlipay(appId, busTypeEnum, organId, payWayEnum);
                    break;
                case 2:
                    opReturnPayParams = fullfillOtherInfoForWeixin(appId, busTypeEnum, organId, payWayEnum, callbackParams);
                    break;
                case 3:
                    opReturnPayParams = fullfillOtherInfoForThirdPart(appId, busTypeEnum, organId, payWayEnum);
                    break;
                default:
                    log.error("not support payway, please check! payWay[{}]", payway);
                    break;
            }
            log.info("fetchPayTargetInfo opReturnPayParams[{}]", opReturnPayParams);
            return opReturnPayParams;
        }catch (Exception e){
            log.info("fetchPayTargetInfo error, appId[{}], payway[{}], busType[{}], errorMessage[{}], stackTrace[{}]", appId, payway, busType, e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw new DAOException(ErrorCode.SERVICE_ERROR, PayConstant.ORGAN_NOT_OPEN_ONLINE_PAY_MSG);
        }
    }

    /**
     * 当支付方式为支付宝时，封装
     * @param currentAppId
     * @param busTypeEnum
     * @param organId
     * @param payWayEnum
     * @return
     */
    private OpReturnPayParams fullfillOtherInfoForAlipay(String currentAppId, BusTypeEnum busTypeEnum, Integer organId, PayWayEnum payWayEnum) {
        OpReturnPayParams opReturnPayParams = new OpReturnPayParams();
        PayConfigService payConfigService = AppContextHolder.getBean("payConfigService", PayConfigService.class);
        AliPayConfig aliPayConfig = (AliPayConfig)payConfigService.getConfigByInfo(currentAppId, busTypeEnum.getOsBusinessTypeKey(), organId,  payWayEnum.getPayType());
        if (aliPayConfig == null || ValidateUtil.blankString(aliPayConfig.getPaymentId()) || ValidateUtil.blankString(aliPayConfig.getAppID())) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, PayConstant.ORGAN_NOT_OPEN_ONLINE_PAY_MSG);
        }
        opReturnPayParams.setPayOrganId(aliPayConfig.getPaymentId());
        opReturnPayParams.setTargetAppId(aliPayConfig.getAppID());
        if(ValidateUtil.notBlankString(currentAppId) && ValidateUtil.notBlankString(opReturnPayParams.getTargetAppId()) && !currentAppId.equals(opReturnPayParams.getTargetAppId())){
            opReturnPayParams.setStepOverMerchant(true);
        }
        /*if(payWayEnum.equals(PayWayEnum.ALIPAY_WAP)){
            try {
                Map<String, String> params = Maps.newHashMap();
                ThirdParty thirdParty = DAOFactory.getDAO(ThirdPartyDao.class).get(opReturnPayParams.getTargetAppId());
                params.put("appkey", opReturnPayParams.getTargetAppId());
                params.put("appsecret", thirdParty.getAppsecret());
                params.put("tid", CurrentUserInfo.getSimpleWxAccount().getOpenId());
                IWXServiceInterface wxService = AppContextHolder.getBean("eh.wxService", IWXServiceInterface.class);
                String returnUrl = wxService.getSinglePageUrlForThirdPlat(opReturnPayParams.getTargetAppId(), busTypeEnum.getCode(), busId, params);
                log.info("fullfillOtherInfoForAlipay returnUrl[{}]", returnUrl);
                opReturnPayParams.setWeChatAuthUrl(returnUrl);
            }catch (Exception e){
                log.error("fullfillOtherInfoForAlipay error, busType[{}], busId[{}], errorMessage[{}], stackTrace[{}]", busTypeEnum, busId, e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            }
        }*/
        return opReturnPayParams;
    }

    private OpReturnPayParams fullfillOtherInfoForWeixin(String currentAppId, BusTypeEnum busTypeEnum, Integer organId, PayWayEnum payWayEnum, Map<String, String> callbackParams) {
        OpReturnPayParams opReturnPayParams = new OpReturnPayParams();
        PayConfigService payConfigService = AppContextHolder.getBean("payConfigService", PayConfigService.class);
        WXConfig wXConfig = (WXConfig)payConfigService.getConfigByInfo(currentAppId, busTypeEnum.getOsBusinessTypeKey(), organId,  payWayEnum.getPayType());
        if (wXConfig == null || ValidateUtil.blankString(wXConfig.getPaymentId())) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, PayConstant.ORGAN_NOT_OPEN_ONLINE_PAY_MSG);
        }
        opReturnPayParams.setPayOrganId(wXConfig.getPaymentId());
        opReturnPayParams.setTargetAppId(wXConfig.getAppID());
        if(ValidateUtil.notBlankString(currentAppId) && ValidateUtil.notBlankString(opReturnPayParams.getTargetAppId()) && !currentAppId.equals(opReturnPayParams.getTargetAppId()) && ValidateUtil.nullOrZeroInteger(wXConfig.getParentId())){
            opReturnPayParams.setStepOverMerchant(true);
        }
        if(PayWayEnum.WEIXIN_WAP.equals(payWayEnum) && opReturnPayParams.isStepOverMerchant()){
            //根据目标appId，调用微信服务接口获取对应的授权地址
            if(callbackParams==null){
                callbackParams = Maps.newHashMap();
            }
            callbackParams.put("poId", wXConfig.getPaymentId());
            if(callbackParams.containsKey("price")){
                callbackParams.put("price", LocalStringUtil.removeRedundantZeroForFloatNumber(callbackParams.get("price")));
            }
            IWXServiceInterface wxService = AppContextHolder.getBean("eh.wxService", IWXServiceInterface.class);
            String weChatAuthUrl = wxService.getWxPageAuthorizeUrl(wXConfig.getAppID(), callbackParams);
            opReturnPayParams.setWeChatAuthUrl(weChatAuthUrl);
        }
        return opReturnPayParams;
    }


    private OpReturnPayParams fullfillOtherInfoForThirdPart(String currentAppId, BusTypeEnum busTypeEnum, Integer organId, PayWayEnum payWayEnum) {
        OpReturnPayParams opReturnPayParams = new OpReturnPayParams();
        opReturnPayParams.setPayOrganId("100050");//龙华一网通支付机构 默认测试机构
        opReturnPayParams.setTargetAppId(currentAppId);//目标机构
        opReturnPayParams.setStepOverMerchant(false);
        log.info("一网通支付机构、当前机构号："+currentAppId);
        return opReturnPayParams;
    }
}
