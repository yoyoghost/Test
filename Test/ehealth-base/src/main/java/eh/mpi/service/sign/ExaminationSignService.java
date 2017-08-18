package eh.mpi.service.sign;

import com.alibaba.fastjson.JSONObject;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.dao.DoctorDAO;
import eh.base.dao.HisServiceConfigDAO;
import eh.bus.dao.ConsultSetDAO;
import eh.entity.bus.ConsultSet;
import eh.entity.mpi.Patient;
import eh.entity.mpi.RelationDoctor;
import eh.entity.mpi.SignRecord;
import eh.mpi.dao.PatientDAO;
import eh.mpi.dao.RelationDoctorDAO;
import eh.mpi.dao.SignPatientLabelDAO;
import eh.mpi.dao.SignRecordDAO;
import eh.utils.DateConversion;
import eh.utils.LocalStringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author renzh
 * @date 2016/9/30 0030 下午 14:23
 */
public class ExaminationSignService {

    private static final Logger logger = LoggerFactory.getLogger(ExaminationSignService.class);
    private ConsultSetDAO consultSetDAO;
    private HisServiceConfigDAO hisServiceConfigDao;
    private DoctorDAO doctorDAO;
    private SignRecordDAO signRecordDAO;
    private PatientDAO patientDAO;
    private RelationDoctorDAO relationDoctorDAO;
    private SignPatientLabelDAO signPatientLabelDAO;

    public ExaminationSignService(){
        consultSetDAO = DAOFactory.getDAO(ConsultSetDAO.class);
        hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
        doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        signRecordDAO = DAOFactory.getDAO(SignRecordDAO.class);
        patientDAO = DAOFactory.getDAO(PatientDAO.class);
        relationDoctorDAO = DAOFactory.getDAO(RelationDoctorDAO.class);
        signPatientLabelDAO = DAOFactory.getDAO(SignPatientLabelDAO.class);
    }

    /**
     * （判断该医生签约权限）PC端点击创建诊间签约按钮
     * @param doctorId
     * @return
     */
    @RpcService
    public Boolean canExaSign(Integer doctorId){
        Boolean flag = false;
        ConsultSet consultSet = consultSetDAO.getById(doctorId);
        if(consultSet!=null){
            if(consultSet.getCanSign()!=false){
                if(consultSet.getSignStatus()!=false){
                    flag = true;
                }
            }else{
                throw new DAOException(ErrorCode.SERVICE_ERROR, "您还没有签约权限，暂时无法使用签约功能。可向机构申请开通权限。");
            }
        }
        return flag;
    }

    /**
     * 判断是否允许签约（通过his）
     * @return
     */
    @RpcService
    public Boolean checkSignPatientInfoByHis(Integer doctorId,String mpiId,String patientName,String patientType,String certID,String cardType,String cardId,String mobile){
        try {
            Boolean flag = true;
            PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
            Patient patient = patientDAO.getPatientByMpiId(mpiId);
            if (!"1".equals(patientType)) {
                String idCard = patient.getIdcard();
                if (cardId != null && cardId.trim().length() > 0 && idCard.substring(idCard.length() - 12, idCard.length()).equals(cardId)) {
                    flag = false;
                }
            }
            return flag;
        }catch (Exception e){
            logger.error("checkSignPatientInfoByHis exception");
            e.printStackTrace();
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }

    }

    /**
     * 查某条签约记录
     * @param recordId
     * @return
     */
    @RpcService
    public Map getBySignRecordId(Integer recordId){
        Map map = new HashMap();
        List<String> signPatientLabelList = new ArrayList<>();
        SignRecord signRecord = signRecordDAO.get(recordId);
        if (signRecord==null){
            throw new DAOException(DAOException.ENTITIY_NOT_FOUND, " SignRecord not exist");
        }
        List<Integer> signLables = signPatientLabelDAO.findSplLabelBySignRecordId(recordId);
        if (signLables != null && signLables.size() > 0){
            for (int i = 0; i < signLables.size(); i++){
                String labelName;
                try {
                    labelName = DictionaryController.instance().get("eh.mpi.dictionary.PatientLabel").getText(signLables.get(i));
                    signPatientLabelList.add(labelName);
                    signRecord.setPatientLabel(signPatientLabelList);
                } catch (ControllerException e) {
                    logger.error(LocalStringUtil.format("error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace())));
                }
            }
        }
        Patient patient = patientDAO.getByMpiId(signRecord.getRequestMpiId());
        map.put("patient",patient);
        map.put("signRecord",signRecord);
        List<RelationDoctor> relationDoctorList = relationDoctorDAO.findSignByMpi(signRecord.getRequestMpiId());
        if(relationDoctorList.size()>0){
            map.put("signFlag",true);
        }else{
            map.put("signFlag",false);
        }
        //前端显示倒计时 大于24小时显示天数+小时
        Integer preSign = signRecord.getPreSign();
        Integer status = signRecord.getRecordStatus();
        String leftTime = "";
        if (0 == status) {
            int hour = 0;
            if (null != preSign && 1 == preSign) {
                hour = DateConversion.getHoursDiffer(signRecord.getRequestDate(), new Date(), 7*24);
            } else {
                hour = DateConversion.getHoursDiffer(signRecord.getRequestDate(), new Date(), 2*24);
            }
            if(hour > 24){
                int day = hour/24;
                hour = hour%24;
                if(0 == hour){
                    leftTime = day + "天";
                }else {
                    leftTime = day + "天" + hour + "小时";
                }
            }else{
                leftTime = hour+"小时";
            }
        }
        map.put("leftTimeForDoc", leftTime);
        map.put("leftTime", DateConversion.getHoursDiffer(signRecord.getRequestDate(),new Date(),24));
        return map;
    }

}
