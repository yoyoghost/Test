package eh.mpi.service.sign;

import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.base.dao.RelationPatientDAO;
import eh.bus.dao.ConsultSetDAO;
import eh.entity.bus.ConsultSet;
import eh.entity.mpi.Patient;
import eh.entity.mpi.RelationDoctor;
import eh.entity.mpi.SignRecord;
import eh.mpi.constant.SignRecordConstant;
import eh.mpi.dao.PatientDAO;
import eh.mpi.dao.SignPatientLabelDAO;
import eh.mpi.dao.SignRecordDAO;
import eh.msg.dao.SessionMemberDAO;
import eh.push.SmsPushService;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class SignRecordService {
    private static final Log logger = LogFactory.getLog(SignRecordService.class);

    /**
     * 获取医生端签约申请列表
     *
     * @param doctorId
     * @return
     */
    @RpcService
    public List<HashMap<String, Object>> getSignRecordByDoctor(Integer doctorId, Integer start) {
        return getSignRecordByDoctorPages(doctorId, start, 10);
    }

    /**
     * 获取医生端签约申请列表(分页)
     *
     * @param doctorId
     * @return
     */
    @RpcService
    public List<HashMap<String, Object>> getSignRecordByDoctorPages(Integer doctorId, Integer start, Integer limit) {
        SignRecordDAO signDao = DAOFactory.getDAO(SignRecordDAO.class);
        PatientDAO patientDao = DAOFactory.getDAO(PatientDAO.class);
        ConsultSetDAO consultSetDAO = DAOFactory.getDAO(ConsultSetDAO.class);
        RelationPatientDAO relationPatientDAO = DAOFactory.getDAO(RelationPatientDAO.class);

        ConsultSet consultSet1 = consultSetDAO.getById(doctorId);
        ConsultSet consultSet2 = new ConsultSet();
        consultSet2.setDoctorId(consultSet1.getDoctorId());
        consultSet2.setSignPrice(consultSet1.getSignPrice());
        consultSet2.setSignTime(consultSet1.getSignTime());

        List<SignRecord> signList = signDao.findByDoctorAndRecordStatusPages(doctorId, start, limit);

        List<HashMap<String, Object>> returnList = new ArrayList<HashMap<String, Object>>();
        if (signList.size() > 0) {
            for (SignRecord sign : signList) {
                String requestMpi = sign.getRequestMpiId();
                Patient patient = patientDao.getPatientPartInfoV2(requestMpi);
                RelationDoctor relationDoctor = relationPatientDAO.getByMpiidAndDoctorId(requestMpi,doctorId);

                if (relationDoctor != null) {
                    patient.setRelationFlag(true);

                }else{
                    patient.setRelationFlag(false);
                    logger.info("获取医生端签约申请列表时mpi_relationdoctor为空！");
                }

                SignRecord reocrd = new SignRecord();
                reocrd.setSignRecordId(sign.getSignRecordId());
                reocrd.setRecordStatus(sign.getRecordStatus());
                reocrd.setRequestMpiId(requestMpi);
                reocrd.setDoctor(sign.getDoctor());
                reocrd.setSignTime(sign.getSignTime());
                reocrd.setRequestDate(sign.getRequestDate());
                reocrd.setSignPrice(sign.getSignPrice());

                //将患者标签封装到reocrd
                Integer signRecordId = sign.getSignRecordId(); //签约记录表主键
                if (signRecordId != null && signRecordId > 0){
                   reocrd.setPatientLabel(getSignPatientLabel(signRecordId));
                } else {
                    logger.info("获取医生端签约申请列表时签约记录为空！");
                }

                HashMap<String, Object> map = new HashMap<String, Object>();

                map.put("patient", patient);
                map.put("signRecord", reocrd);
                map.put("consultSet", consultSet2);
                returnList.add(map);
            }
        } else {
            logger.info("获取医生端签约申请列表时签约记录为空！");
        }
        updateUnRead();
        System.err.println(JSONObject.toJSONString(returnList));
        return returnList;
    }

    /**
     * 根据signRecordId获取患者标签
     * @param signRecordId
     * @return
     */
    public List<String> getSignPatientLabel(Integer signRecordId){

        List<String> signPatientLabelList = new ArrayList<>();

        try {

            SignPatientLabelDAO signPatientLabelDAO = DAOFactory.getDAO(SignPatientLabelDAO.class);

            //获取患者标签列表
            List<Integer> signPatientLabelKeyList = signPatientLabelDAO.findSplLabelBySignRecordId(signRecordId);
            if (signPatientLabelKeyList != null && signPatientLabelKeyList.size() > 0){
                //得到患者标签Name
                for (int i = 0; i < signPatientLabelKeyList.size(); i++){
                    String labelName = DictionaryController.instance()
                            .get("eh.mpi.dictionary.PatientLabel")
                            .getText(signPatientLabelKeyList.get(i));
                    signPatientLabelList.add(labelName);
                }
            } else {
                logger.info("获取医生端签约申请列表时患者标签列表为空！");
            }
        } catch (Exception e){
            logger.error("获取医生端签约申请列表时出错：" + e.getMessage());
        }
        logger.info("获取医生端签约申请列表,根据signRecordId获取患者标签成功！");
        return signPatientLabelList;
    }

    /**
     * 签约消息设为已读
     */
    public void updateUnRead(){
        SessionMemberDAO memberDAO = DAOFactory.getDAO(SessionMemberDAO.class);
        Integer memberType = 1; //1医生端
        Integer publisherId = 9;//9签约消息
        memberDAO.updateUnReadByPublisherIdAndMemberType(publisherId, memberType);
    }

    /**
     * 签约状态修改
     *
     * @param signRecord
     * @return
     */
    @RpcService
    public SignRecord updateSignRecordByRecordStatus(final SignRecord signRecord) {
        if (null == signRecord || null == signRecord.getRecordStatus() || null == signRecord.getSignRecordId()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "signRecord Object or recordStatus is null or signRecordId is null");
        }
        SignRecordDAO signDao = DAOFactory.getDAO(SignRecordDAO.class);
        SignRecord signRecordResult = signDao.updateRequestStatus(signRecord);
        //签约成功状态推送消息
        if (null != signRecordResult && signRecordResult.getRecordStatus() == SignRecordConstant.RECORD_STATUS_AGREE) {
        //推送消息
        	 AppContextHolder.getBean("eh.smsPushService", SmsPushService.class).pushMsgData2Ons(signRecord.getSignRecordId(), signRecord.getOrgan(), "SignMessage", "", 0);
        }
        return signRecordResult;
    }
    /**
     * 
     * @param patientID
     * @param doctor
     * @return
     */
    @RpcService
    public SignRecord updateSignRecordWithHIS(final SignRecord signRecord) {
        if (null == signRecord) {
        	throw new DAOException(DAOException.VALUE_NEEDED, "signRecord Object is null.");
        }
        if (StringUtils.isBlank(signRecord.getRequestMpiId())) {
        	throw new DAOException(DAOException.VALUE_NEEDED, "requestMpiId is blank.");
        }
        if (null == signRecord.getDoctor()){
        	throw new DAOException(DAOException.VALUE_NEEDED, "doctorId is null.");
        }
        SignRecordDAO signDao = DAOFactory.getDAO(SignRecordDAO.class);
        List<SignRecord> signRecordList = signDao.findDoctorAndMpiIdWithHIS(signRecord.getDoctor(), signRecord.getRequestMpiId());
        if (CollectionUtils.isEmpty(signRecordList)) {
        	throw new DAOException(DAOException.VALUE_NEEDED, "Change to record does not exist please check it.");
        }
        SignRecord entity = signRecordList.get(0);
        entity.setRecordStatus(signRecord.getRecordStatus());
        SignRecord signRecordResult = signDao.updateRequestStatus(entity);
        //签约成功状态推送消息
        if (null != signRecordResult && signRecordResult.getRecordStatus() == SignRecordConstant.RECORD_STATUS_AGREE) {
        //推送消息
        	 AppContextHolder.getBean("eh.smsPushService", SmsPushService.class).pushMsgData2Ons(signRecord.getSignRecordId(), signRecord.getOrgan(), "SignMessage", "", 0);
        }
        return signRecordResult;
    }
    

    @RpcService
    public SignRecord getSignRecordById(Integer signId){
        return DAOFactory.getDAO(SignRecordDAO.class).get(signId);
    }
}
