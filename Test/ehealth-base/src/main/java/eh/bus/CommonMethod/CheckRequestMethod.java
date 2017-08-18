package eh.bus.CommonMethod;

import ctd.persistence.DAOFactory;
import eh.base.dao.DoctorDAO;
import eh.base.dao.OrganDAO;
import eh.entity.base.Doctor;
import eh.entity.base.Organ;
import eh.entity.bus.CheckRequest;
import eh.entity.his.TaskQueue;
import eh.entity.his.hisCommonModule.HisResponse;
import eh.utils.ValidateUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;

/**
 * Created by dingding on 2016/11/3.
 */
public class CheckRequestMethod {

    private static final Log logger = LogFactory.getLog(CheckRequestMethod.class);


    public static HisResponse validateCheckRequest(CheckRequest checkRequest, List<String> list){
        HisResponse hisResponse = new HisResponse();
        try {
            OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
            DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);

            if (checkRequest != null){

                Organ organ;
                if (checkRequest.getOrganId() != null && checkRequest.getOrganId() > 0) {
                    organ = organDAO.getByOrganId(checkRequest.getOrganId());
                } else {
                    hisResponse.setMsgCode("0");
                    hisResponse.setMsg("医院OrganId 不能为空！");
                    logger.info("纳里平台向远程影像诊断中心进行远程影像诊断申请的时候入参错误：医院OrganId 不能为空！");
                    return hisResponse;
                }

                Doctor requestDoctor; //申请医生
                if (checkRequest.getRequestDoctorId() != null && checkRequest.getRequestDoctorId() > 0){
                    requestDoctor = doctorDAO.getByDoctorId(checkRequest.getRequestDoctorId()); //申请医生
                } else {

//                    hisResponse.setMsgCode("0");
//                    hisResponse.setMsg("申请医生ID RequestDoctorId不能为空！");
//                    logger.info("纳里平台向远程影像诊断中心进行远程影像诊断申请的时候入参错误：申请医生ID RequestDoctorId不能为空！");
//                    return hisResponse;
                }

                if (list != null && list.size() > 0){
                } else {
                    hisResponse.setMsgCode("0");
                    hisResponse.setMsg("影像序列list不能为空！");
                    logger.info("纳里平台向远程影像诊断中心进行远程影像诊断申请的时候入参错误：影像序列list不能为空！");
                    return hisResponse;
                }

                if (ValidateUtil.notBlankString(checkRequest.getPatientName())){
                } else {
                    hisResponse.setMsgCode("0");
                    hisResponse.setMsg("患者姓名PatientName不能为空！");
                    logger.info("纳里平台向远程影像诊断中心进行远程影像诊断申请的时候入参错误：患者姓名PatientName不能为空！");
                    return hisResponse;
                }

                if (ValidateUtil.notBlankString(checkRequest.getPatientSex())){
                } else {
                    hisResponse.setMsgCode("0");
                    hisResponse.setMsg("患者性别PatientSex不能为空！");
                    logger.info("纳里平台向远程影像诊断中心进行远程影像诊断申请的时候入参错误：患者性别PatientSex不能为空！");
                    return hisResponse;
                }

                if (ValidateUtil.notBlankString(checkRequest.getPatientType())){
                } else {
                    checkRequest.setPatientType("1");
//                    hisResponse.setMsgCode("0");
//                    hisResponse.setMsg("患者类型PatientType不能为空！");
//                    logger.info("纳里平台向远程影像诊断中心进行远程影像诊断申请的时候入参错误：患者类型PatientType不能为空！");
//                    return hisResponse;
                }

                if (ValidateUtil.notBlankString(checkRequest.getCertId())){
                } else {
                    hisResponse.setMsgCode("0");
                    hisResponse.setMsg("患者身份证号CertId为空！");
                    logger.info("纳里平台向远程影像诊断中心进行远程影像诊断申请的时候入参错误：患者身份证号CertId不能为空！");
                    return hisResponse;
                }

                if (ValidateUtil.notBlankString(checkRequest.getMobile())){
                } else {
                    checkRequest.setMobile("");
//                    hisResponse.setMsgCode("0");
//                    hisResponse.setMsg("患者手机号Mobile不能为空！");
//                    logger.info("纳里平台向远程影像诊断中心进行远程影像诊断申请的时候入参错误：患者手机号Mobile不能为空！");
                    return hisResponse;
                }

                if (checkRequest.getOrganId() > 0){
                } else {
                    hisResponse.setMsgCode("0");
                    hisResponse.setMsg("申请机构编号OrganId不正确！");
                    logger.info("纳里平台向远程影像诊断中心进行远程影像诊断申请的时候入参错误：申请机构编号OrganId不正确！");
                    return hisResponse;
                }

                if (ValidateUtil.notBlankString(organ.getOrganizeCode())){
                } else {
                    hisResponse.setMsgCode("0");
                    hisResponse.setMsg("此医院没有OrganizeCode！");
                    logger.info("纳里平台向远程影像诊断中心进行远程影像诊断申请的时候入参错误：此医院没有OrganizeCode！");
                    return hisResponse;
                }

                if (ValidateUtil.notBlankString(checkRequest.getRequestDoctorName())){
                } else {
                    hisResponse.setMsgCode("0");
                    hisResponse.setMsg("申请医生姓名requestDoctorName不能为空！");
                    logger.info("纳里平台向远程影像诊断中心进行远程影像诊断申请的时候入参错误：申请医生姓名requestDoctorName不能为空！");
                    return hisResponse;
                }

                if (checkRequest.getCheckDate() != null){
                } else {
                    hisResponse.setMsgCode("0");
                    hisResponse.setMsg("影像检查日期CheckDate不能为空！");
                    logger.info("纳里平台向远程影像诊断中心进行远程影像诊断申请的时候入参错误：影像检查日期CheckDate不能为空！");
                    return hisResponse;
                }

                if (checkRequest.getRequestDate() != null){
                } else {
                    hisResponse.setMsgCode("0");
                    hisResponse.setMsg("影像诊断申请日期RequestDate不能为空！");
                    logger.info("纳里平台向远程影像诊断中心进行远程影像诊断申请的时候入参错误：影像诊断申请日期RequestDate不能为空！");
                    return hisResponse;
                }

                if (ValidateUtil.notBlankString(checkRequest.getOrganRequestNo())){
                } else {
                    hisResponse.setMsgCode("0");
                    hisResponse.setMsg("影像诊断申请ID OrganRequestNo不能为空！");
                    logger.info("纳里平台向远程影像诊断中心进行远程影像诊断申请的时候入参错误：影像诊断申请ID OrganRequestNo不能为空！");
                    return hisResponse;
                }

                if (ValidateUtil.notBlankString(checkRequest.getDisease())){
                } else {
                    hisResponse.setMsgCode("0");
                    hisResponse.setMsg("门诊初步诊断Disease不能为空！");
                    logger.info("纳里平台向远程影像诊断中心进行远程影像诊断申请的时候入参错误：门诊初步诊断Disease不能为空！");
                    return hisResponse;
                }

                if (ValidateUtil.notBlankString(checkRequest.getDiseasesHistory())){

                } else {
                    hisResponse.setMsgCode("0");
                    hisResponse.setMsg("病情描述DiseasesHistory不能为空！");
                    logger.info("纳里平台向远程影像诊断中心进行远程影像诊断申请的时候入参错误：病情描述DiseasesHistory不能为空！");
                    return hisResponse;
                }

                if (ValidateUtil.notBlankString(checkRequest.getPurpose())){
                } else {
                    hisResponse.setMsgCode("0");
                    hisResponse.setMsg("检查目的Purpose不能为空！");
                    logger.info("纳里平台向远程影像诊断中心进行远程影像诊断申请的时候入参错误：检查目的Purpose不能为空！");
                    return hisResponse;
                }

                if (ValidateUtil.notBlankString(checkRequest.getCheckType())){
                } else {
                    hisResponse.setMsgCode("0");
                    hisResponse.setMsg("检查项目类型CheckType不能为空！");
                    logger.info("纳里平台向远程影像诊断中心进行远程影像诊断申请的时候入参错误：检查项目类型CheckType不能为空！");
                    return hisResponse;
                }

                if (ValidateUtil.notBlankString(checkRequest.getCheckItemName())){
                } else {
                    hisResponse.setMsgCode("0");
                    hisResponse.setMsg("检查项目类型名字CheckItemName不能为空！");
                    logger.info("纳里平台向远程影像诊断中心进行远程影像诊断申请的时候入参错误：检查项目类型名字CheckItemName不能为空！");
                    return hisResponse;
                }

            } else {
                hisResponse.setMsgCode("0");
                hisResponse.setMsg("传入数据checkRequest不能为空！");
                logger.info("纳里平台向远程影像诊断中心进行远程影像诊断申请时传入数据checkRequest为空！");
            }

        } catch (Exception e){
            logger.error("validateCheckRequest() error : "+e);
        }

        return hisResponse;
    }

    /**
     * 将checkRequest，影像序列list信息封装到taskQueue中
     * @param checkRequest
     * @return
     */
    public static TaskQueue packCRtoTaskqueue(CheckRequest checkRequest, List<String> list){


        TaskQueue taskQueue = new TaskQueue();

        try {
            OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);

            if (checkRequest != null && list != null && list.size() > 0){
                Organ organ = organDAO.getByOrganId(checkRequest.getOrganId());

                taskQueue.setRequireType("1");
                taskQueue.setMpi(checkRequest.getMpiid());
                taskQueue.setPatientid("");
                taskQueue.setPatientName(checkRequest.getPatientName());
                taskQueue.setPatientSex(checkRequest.getPatientSex());
                taskQueue.setPatientType("1");
                taskQueue.setBirthday(null);
                taskQueue.setCredentialsType("1");
                taskQueue.setCertno(checkRequest.getCertId());
                taskQueue.setMobile(checkRequest.getMobile() == null? "":checkRequest.getMobile());
                taskQueue.setOrganid(checkRequest.getOrganId());
                taskQueue.setOrganizedCode(organ.getOrganizeCode()); //医院机构编号
                taskQueue.setDepartCode("");
                taskQueue.setDepartName("挂号科室名称");
                taskQueue.setDoctorID("");
                taskQueue.setDoctorName(checkRequest.getRequestDoctorName());
                taskQueue.setDotorMobile("");
                taskQueue.setExamDate(checkRequest.getCheckDate());
                taskQueue.setRequireDate(checkRequest.getRequestDate());
                taskQueue.setRequireID(checkRequest.getReportId());
                taskQueue.setDiseaseCode(checkRequest.getDiseaseCode());
                taskQueue.setDisease(checkRequest.getDisease());
                taskQueue.setDiseasesHistory(checkRequest.getDiseasesHistory());
                taskQueue.setPurpose(checkRequest.getPurpose());
                taskQueue.setExamType(checkRequest.getCheckType());
                taskQueue.setExamItemName(checkRequest.getCheckItemName());
                taskQueue.setExamDisplay(checkRequest.getExaminationDisplay() == null
                        ? "":checkRequest.getExaminationDisplay());
                taskQueue.setExamResult(checkRequest.getExaminationResult() == null
                        ? "":checkRequest.getExaminationResult());
                taskQueue.setStudyUID(list);

            } else {
                logger.info("纳里平台向远程影像诊断中心进行远程影像诊断申请时传入数据checkRequest或影像序列为空！");
            }

        } catch (Exception e){
            logger.error("packCRtoTaskqueue() error : "+e);
        }

        return taskQueue;
    }
}
