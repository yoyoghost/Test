package eh.unifiedpay.service;

import ctd.util.AppContextHolder;
import easypay.entity.vo.param.CommonParam;
import easypay.entity.vo.param.NgariParam;
import easypay.entity.vo.param.OrderQueryParam;
import eh.remote.IEasyPayServiceInterface;
import eh.unifiedpay.constant.PayServiceConstant;
import net.sf.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by IntelliJ IDEA.
 * Description:
 * User: xiangyf
 * Date: 2017-04-28 16:51.
 */
public class CommonPayRequestService {

    private static final Logger logger = LoggerFactory.getLogger(CommonPayRequestService.class);

    public static String payCommon(String payOrganId, Integer payType, NgariParam bizParam , String service){

        CommonParam commonParam = new CommonParam();
        commonParam.setOrganId(payOrganId);
        commonParam.setPayType(payType);//1 支付宝；2 微信；3 一网通
        commonParam.setService(service);
        commonParam.setVersion("1.0");
        commonParam.setToken("");
        commonParam.setUserId("");
        commonParam.setClientId("");
        commonParam.setSign("");
        commonParam.setBizParam(JSONObject.fromObject(bizParam));
        String result ="";
        try {
            logger.info("向支付平台发起申请：commonParam:"+commonParam.toString());
            IEasyPayServiceInterface payService = AppContextHolder.getBean("easypay.payService", IEasyPayServiceInterface.class);
            result = payService.gateWay(commonParam);   //String) ServiceAdapter.invoke("easypay.payService","gateWay",commonParam);
            logger.info("支付平台返回结果：result:"+result);
        } catch (Exception e) {
            // TODO: 2017/4/26 捕获调用超时异常
            logger.error("请求支付平台服务异常！", e);
        }

        return result;
    }

    public static void main(String[] args) {
//        String bizParam = "{\"amount\":1.0,\"applyNo\":\"cst_470003265_149337107076110\",\"consumeType\":3,\"finalOrganId\":\"\",\"itbPay\":300,\"mrn\":\"2c9081825b8fdcba015b8ff5e43c0000\",\"notifyUrl\":\"client=ehealth-base\",\"openId\":\"147\",\"patientName\":\"??\",\"payName\":\"??\",\"payWay\":\"WAP\",\"reqOrganId\":\"1\",\"returnUrl\":\"\",\"subject\":\"咨询费用\"}";
        OrderQueryParam orderQueryParam = new OrderQueryParam();
        orderQueryParam.setApplyNo("cst_470003265_149337107076110");
        payCommon("100038",1,orderQueryParam, PayServiceConstant.ORDER_QUERY);

    }
}
