package eh.remote;

import ctd.util.annotation.RpcService;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface IRTMService {

    @RpcService
    Map<String, Collection<Map<String, Object>>> getConnectClients(List<String> userIds);




}
