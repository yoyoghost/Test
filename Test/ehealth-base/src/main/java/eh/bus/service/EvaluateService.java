package eh.bus.service;

import ctd.account.UserRoleToken;
import ctd.persistence.DAOFactory;
import ctd.util.annotation.RpcService;
import ctd.util.context.Context;
import ctd.util.context.ContextUtils;
import eh.bus.constant.EvaluateConstant;
import eh.bus.dao.EvaluateDAO;
import eh.bus.dao.MeetClinicDAO;
import eh.bus.service.common.CurrentUserInfo;
import eh.entity.base.Doctor;
import eh.entity.bus.Evaluate;
import eh.entity.bus.MeetClinic;
import eh.entity.bus.msg.SimpleWxAccount;
import eh.entity.wx.WXConfig;
import eh.op.dao.WXConfigsDAO;
import eh.op.dao.WxAppPropsDAO;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by zhangsl on 2017/6/2.
 */
public class EvaluateService {
    public static final Logger logger = LoggerFactory.getLogger(EvaluateService.class);

    /**
     * 根据业务类型和id发布评价
     * @param bussType
     * @param bussId
     * @param evaluates
     * @return
     */
    @RpcService
    public Boolean evaluateByBussType(int bussType, int bussId, List<Evaluate> evaluates) {
        EvaluateDAO evaluateDAO = DAOFactory.getDAO(EvaluateDAO.class);
        if (evaluateDAO.isEvaluate(bussType, bussId)||evaluates==null||evaluates.isEmpty()) {
            return false;
        }
        switch (bussType) {
            case EvaluateConstant.BUSSTYPE_MEETCLINIC:
                return this.evaluateMeetClinic(bussId, evaluates);
            default:
                return false;
        }
    }

    /**
     * 会诊单评价
     * @param bussId
     * @param evaluates
     * @return
     */
    public Boolean evaluateMeetClinic(int bussId, final List<Evaluate> evaluates) {
        MeetClinicDAO meetClinicDAO = DAOFactory.getDAO(MeetClinicDAO.class);
        MeetClinic mc = meetClinicDAO.getByMeetClinicId(bussId);
        UserRoleToken ur = (UserRoleToken) ContextUtils
                .get(Context.USER_ROLE_TOKEN);
        Doctor doctor = (Doctor) ur.getProperty("doctor");
        if (mc == null || doctor == null
                || !mc.getRequestDoctor().equals(doctor.getDoctorId())
                //|| mc.getMeetClinicStatus() != MeetClinicConstant.MEETSTATUS_COMPLETE
        ) {
            return false;
        }
        EvaluateDAO evaluateDAO = DAOFactory.getDAO(EvaluateDAO.class);
        List<Evaluate> es=new ArrayList<Evaluate>();
        for(Evaluate e:evaluates){
            e.setBussType(EvaluateConstant.BUSSTYPE_MEETCLINIC);
            e.setBussId(bussId);
            if(e.getEvaluateTitleType()!=null&& StringUtils.isNotBlank(e.getEvaluateContent())){
                es.add(e);
            }
        }
        return evaluateDAO.addEvaluate(es);
    }



}
