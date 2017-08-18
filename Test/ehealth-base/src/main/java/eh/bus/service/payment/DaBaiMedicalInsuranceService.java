package eh.bus.service.payment;

import com.alibaba.fastjson.JSONObject;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.util.HttpHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Created by Administrator on 2016/10/30 0030.
 * 杭州大白医快付申请支付、结果通知、查询结果等接口
 */
public class DaBaiMedicalInsuranceService {
    private static final Logger log = LoggerFactory.getLogger(DaBaiMedicalInsuranceService.class);


    @RpcService
    public PayResult applyDaBaiPay(Map<String, Object> requestMap){
        log.info("applyDaBaiPay start, params: requestMap[{}]", JSONObject.toJSONString(requestMap));
        try {
            String requestParamJsonString = JSONObject.toJSONString(requestMap);
            String payResult = HttpHelper.sendPostRequest(DaBaiMedicalInsuranceEnum.APPLY.getUrl(), DaBaiMedicalInsuranceEnum.APPLY.getHeaderMap(), requestParamJsonString);
            log.info("applyDaBaiPay result: [{}]", payResult);
            PayResult pr = JSONObject.parseObject(payResult, PayResult.class);
            return pr;
        }catch (Exception e){
            log.error("applyDaBaiPay error, params: requestMap[{}], errorMessage[{}], stackTrace[{}]", JSONObject.toJSONString(requestMap), e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw new DAOException(ErrorCode.SERVICE_ERROR, "医快付下单失败");
        }
    }


    /**
     * TODO 不需要
     */
    @RpcService
    public Object queryForPay(){

        return null;
    }




    public static class PayResult{
        private String code;
        private String message;
        private Data data;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Data getData() {
            return data;
        }

        public void setData(Data data) {
            this.data = data;
        }
    }

    public static class Data{
        private String trade_no;

        public String getTrade_no() {
            return trade_no;
        }

        public void setTrade_no(String trade_no) {
            this.trade_no = trade_no;
        }
    }
}
