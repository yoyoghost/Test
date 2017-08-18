package eh.bus.service.payment;

import com.google.common.collect.ImmutableMap;
import eh.wxpay.util.PayUtil;

import java.util.Map;

/**
 * Created by Administrator on 2016/10/31 0031.
 */
public enum DaBaiMedicalInsuranceEnum {
    APPLY("/pres/payApply.json"),
    QUERY("/pres/queryResult.json");

    private String path;

    DaBaiMedicalInsuranceEnum(String path){
        this.path = path;
    }

    public String getUrl(){
        return PayUtil.getDabaiServiceHost() + this.path;
    }

    public Map<String, String> getHeaderMap(){
        Map<String, String> headerMap = ImmutableMap.of("accessToken",PayUtil.getDabaiAccessToken(), "partner", PayUtil.getDabaiPartnerCode());
        return headerMap;
    }

}
