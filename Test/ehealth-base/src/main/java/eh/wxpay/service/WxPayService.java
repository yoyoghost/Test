package eh.wxpay.service;

import com.alibaba.fastjson.JSONObject;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.bus.service.common.CurrentUserInfo;
import eh.entity.bus.msg.SimpleWxAccount;
import eh.utils.ValidateUtil;
import eh.wxpay.constant.PayConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class WxPayService extends PayService{
    private static final Logger logger = LoggerFactory.getLogger(WxPayService.class);

    /**
     * @param payway  支付方式  40微信网页支付 41微信app支付
     * @param busId   业务id
     * @param busType 业务类型:转诊transfer,咨询consult，处方recipe；预约挂号appoint，门诊缴费outpatient，住院预交prepay， 签约sign
     * @return String
     * @function 返回页面支付请求参数
     * @author zhangjr
     * @date 2015-12-16
     */
    @RpcService
    public Map<String, Object> payApply(String payway, String busType, String busId) {
        logger.info("微信网页支付下单进入...params: payway[{}], busType[{}], busId[{}]", payway, busType, busId);
        Map<String, Object> resultMap = new HashMap<String, Object>();
        if(ValidateUtil.blankString(payway) || ValidateUtil.blankString(busType) || ValidateUtil.blankString(busId)){
            logger.info("payApply necessary params null, params: payway[{}], busType[{}], busId[{}]", payway, busType, busId);
            resultMap.put("code", PayConstant.RESULT_FAIL);
            resultMap.put("msg", "必填参数为空");
            return resultMap;
        }
        try {
            SimpleWxAccount wxAccount = CurrentUserInfo.getSimpleWxAccount();
            NgariPayService payService = AppContextHolder.getBean("ngariPayService", NgariPayService.class);
            resultMap = payService.actualDoPayApply(wxAccount.getAppId(), payway, busType, busId, wxAccount.getOpenId(), null); //TODO
        }catch (Exception e){
            logger.info("payApply exception, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            resultMap.put("code", PayConstant.RESULT_FAIL);
            resultMap.put("msg", e.getMessage());
        }
        return resultMap;
    }

    /**
     * 支付下单——封装个性化参数
     * @param payWayEnum
     * @param platformUserId
     */
    /*@Override
    protected void packIndividualizationParam(String targetAppId, PayBizParam payBizParam, PayWayEnum payWayEnum, String platformUserId, Integer busId, BusTypeEnum busTypeEnum) {
        *//*root.addElement("spbill_create_ip").setText("");
        root.addElement("device_info").setText(payWayEnum.getDeviceInfo());
        if(payWayEnum.equals(PayWayEnum.WEIXIN_WAP)) {
            logger.info("packIndividualizationParam platformUserId[{}]", platformUserId);
            root.addElement("openid").setText(platformUserId);
            root.addElement("appid").setText(targetAppId);
        }
        root.addElement("trade_type").setText(payWayEnum.getTradeType());
        root.addElement("attach").setText("");
        root.addElement("goods_tag").setText("");*//*

//        payBizParam.setOpenId(platformUserId);
    }*/

}
