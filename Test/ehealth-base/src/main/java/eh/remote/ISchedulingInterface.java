package eh.remote;

import ctd.util.annotation.RpcService;
import eh.entity.bus.AppointSchedule;

import java.util.List;

/**
 * Created by xuqh on 2016/6/1.
 * 良渚排班信息
 */
public interface ISchedulingInterface {

    /**
     * 良渚排班信息
     * @param
     */
    @RpcService
    public void setDoctorSchedu(List<AppointSchedule> listAppointSchedule);

}
