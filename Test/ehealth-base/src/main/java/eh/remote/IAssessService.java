package eh.remote;

import ctd.util.annotation.RpcService;

import java.util.Map;

/**
 * Created by Administrator on 2017/7/5 0005.
 */
public interface IAssessService {

    @RpcService
    Map<String, Object> getDefaultAssessUrl(Map<String, Object> param);
}
