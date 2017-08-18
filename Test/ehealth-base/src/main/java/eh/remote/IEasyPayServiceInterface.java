package eh.remote;

import ctd.util.annotation.RpcService;
import easypay.entity.vo.param.CommonParam;

/**
 * Created by IntelliJ IDEA.
 * Description:
 * User: xiangyf
 * Date: 2017-05-03 19:09.
 */
public interface IEasyPayServiceInterface {

    @RpcService
    public String gateWay(CommonParam commonParam);
}
