package eh.push.function;

import ctd.persistence.DAOFactory;
import eh.bus.dao.AppointRecordDAO;
import eh.entity.his.push.callNum.HisExeRegisterReqMsg;
import eh.entity.his.push.callNum.PushRequestModel;
import eh.entity.his.push.callNum.PushResponseModel;
import eh.utils.ValidateUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * 应诊确认推送服务
 * Created by hwg on 2016/10/31.
 */
public class ExeRegisterFunction implements Function{
    private static final Log logger = LogFactory.getLog(ExeRegisterFunction.class);

    @Override
    public PushResponseModel perform(PushRequestModel task) {
        //获取参数
        Object data = task.getData();
        //校验参数
        HisExeRegisterReqMsg exeRegisterReqMsg = (HisExeRegisterReqMsg)data;
        PushResponseModel response = validatePara(exeRegisterReqMsg);
        if (!response.isSuccess()){
            return response;
        }
        AppointRecordDAO dao = DAOFactory.getDAO(AppointRecordDAO.class);
        dao.updateExeRegisteAppointStatus(exeRegisterReqMsg.getAppointRecordId());
        logger.info("成功更新应诊状态");

        return null;
    }

    private PushResponseModel validatePara(HisExeRegisterReqMsg eRRMsg){
        PushResponseModel res = new PushResponseModel();

        if (ValidateUtil.blankString(eRRMsg.getOrganId())){
            res.setMsgCode("1");
            res.setMsg("医院机构编号不能为空");
        }
        else if (ValidateUtil.blankString(eRRMsg.getPatientName())){
            res.setMsgCode("2");
            res.setMsgCode("患者姓名不能为空");
        }
        return res;
    }
}
