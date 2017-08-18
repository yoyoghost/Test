package eh.push;

import ctd.util.annotation.RpcService;
import eh.entity.his.push.callNum.PushRequestModel;
import eh.entity.his.push.callNum.PushResponseModel;
import eh.push.function.Function;
import eh.push.function.FunctionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by zxq on 2016-10-27.
 * 纳里云平台推送服务入口
 * 参数格式参照标准接口文档
 */
public class NgariService {

    /** logger */
    private static final Logger logger = LoggerFactory.getLogger(NgariService.class);

    @RpcService
    public PushResponseModel reciveHisMsg(PushRequestModel req){
        String serviceID = req.getServiceId();
        Function function = FunctionFactory.instance().createFunction(serviceID);
        return function.perform(req);

    }

}
