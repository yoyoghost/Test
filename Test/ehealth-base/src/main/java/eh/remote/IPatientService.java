package eh.remote;

import ctd.util.annotation.RpcService;
import eh.entity.his.PatientQueryRequest;
import eh.entity.his.hisCommonModule.HisResponse;

/**
 * Created by luf on 2016/9/11.
 */

public interface IPatientService {

    /**
     * 查询医院是否有改患者记录
     *
     * @param req
     * @return
     */
    @RpcService
    public HisResponse queryPatient(PatientQueryRequest req);

    /**
     * 新建档案
     *
     * @param req
     * @return
     */
    @RpcService
    public PatientQueryRequest createPatient(PatientQueryRequest req);

    /**
     * 绑卡
     *
     * @param req
     * @return
     */
    @RpcService
    public PatientQueryRequest bandCard(PatientQueryRequest req);

    /**
     * 获取条形码
     *
     * @return Str
     */
    @RpcService
    public  String getPatientCode(String pname,String certId,String mobile,String patientType);
}
