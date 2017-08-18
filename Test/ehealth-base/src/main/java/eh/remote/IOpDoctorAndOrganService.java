package eh.remote;

import ctd.util.annotation.RpcService;

/**
 * Created by zxx on 2017/7/19 0019.
 */
public interface IOpDoctorAndOrganService {
    @RpcService
    public void updateStatusByDoctorId(Integer status, Integer doctorId);

    @RpcService
    public void updateStatusByOrganId(Integer status, Integer organId);
}
