package eh.alipay.service;

import eh.wxpay.service.PayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class AliPayService extends PayService{
    private static final Logger log = LoggerFactory.getLogger(AliPayService.class);


    /**
     * 支付下单——封装个性化参数
     * @param payWayEnum
     * @param platformUserId
     *//*
    @Override
    protected void packIndividualizationParam(String targetAppId, PayBizParam payBizParam, PayWayEnum payWayEnum, String platformUserId, Integer busId, BusTypeEnum busTypeEnum) {
        String alipayWapReturnUrl = "";
        try {
            Client client = CurrentUserInfo.getCurrentClient();
            ClientPlatformEnum clientPlatformEnum = ClientPlatformEnum.fromKey(client.getOs());
            switch (clientPlatformEnum){
                case WX_WEB:
                case WEB:
                    if(payWayEnum.equals(PayWayEnum.ALIPAY_WAP)){
                        Map<String, String> params = Maps.newTreeMap();
                        ThirdParty thirdParty = DAOFactory.getDAO(ThirdPartyDao.class).get(targetAppId);
                        UserRoleToken urt = UserRoleToken.getCurrent();
                        Patient patient = (Patient)urt.getProperty("patient");
                        params.put("appkey",targetAppId);
                        params.put("tid",platformUserId);
                        params.put("mobile",patient.getMobile());
                        params.put("idcard",patient.getIdcard());
                        params.put("patientName",patient.getPatientName());
                        params.put("signature", thirdParty.signature(packForSignature(params)));
                        params.put("backable", "1");
                        IWXServiceInterface wxService = AppContextHolder.getBean("eh.wxService", IWXServiceInterface.class);
                        alipayWapReturnUrl = wxService.getSinglePageUrlForThirdPlat(targetAppId, busTypeEnum.getCode(), busId, params);
                        log.info("fullfillOtherInfoForAlipay returnUrl[{}]", alipayWapReturnUrl);
                    }
                    break;
                case ALILIFE:
                    IAliServiceInterface aliService = AppContextHolder.getBean("eh.aliPushMessService", IAliServiceInterface.class);
                    Map<String, String> callbackParams = Maps.newHashMap();
                    alipayWapReturnUrl = aliService.autoPackCallbackUrlForBus(callbackParams, targetAppId, busTypeEnum.getCode(), String.valueOf(busId));
                    break;
            }

        }catch (Exception e){
            log.error("fullfillOtherInfoForAlipay error, busType[{}], busId[{}], errorMessage[{}], stackTrace[{}]", busTypeEnum, busId, e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw new DAOException("支付宝网页地址拼装失败");
        }
        payBizParam.setReturnUrl(alipayWapReturnUrl);

        *//*root.addElement("return_url").setText(alipayWapReturnUrl);
        root.addElement("merchant_url").setText("");
        root.addElement("pay_channel").setText(AliPayConstant.PayChannel.NGARIHEALTH);
        root.addElement("agreementno").setText("");
        root.addElement("agreement_info").setText("");
        root.addElement("payservice").setText(AliPayConstant.PayService.ALIPAY);
        root.addElement("card_no").setText("");
        root.addElement("trade_voucher_no").setText("");
        root.addElement("It_b_pay").setText("");
        root.addElement("branchid").setText("0000");
        root.addElement("userid").setText(ValidateUtil.blankString(platformUserId)?"":platformUserId);//关注该商户服务窗后支付宝分配（服务窗环境支付需传）相当于openid
        root.addElement("auth_code").setText("");//支付授权码，条码支付、声波支付时不可空*//*
    }

    private String packForSignature(Map<String, String> params) {
        StringBuilder s = new StringBuilder();
        for(String k:params.keySet()){
            String v = params.get(k);
            s.append("&").append(k).append("=").append(StringUtils.isEmpty(v)?"":v);
        }
        return s.substring(1);
    }*/

}
