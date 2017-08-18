package eh.mpi.dao;

import ctd.controller.exception.ControllerException;
import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.base.constant.ErrorCode;
import eh.base.dao.RelationLabelDAO;
import eh.entity.mpi.RelationDoctor;
import eh.entity.mpi.SignPatientLabel;
import eh.mpi.constant.SignInitiatorConstant;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;

/**
 * Created by dingding on 2016/10/10.
 * 签约记录居民类型关系DAO
 */
public abstract class SignPatientLabelDAO extends HibernateSupportDelegateDAO<SignPatientLabel>{
    private final Log logger = LogFactory.getLog(SignPatientLabelDAO.class);

    public SignPatientLabelDAO(){
        super();
        this.setEntityName(SignPatientLabel.class.getName());
        this.setKeyField("splId");
    }

    /**
     * 根据签约记录表主键查询患者标签
     * @param signRecordId
     * @return
     */
    @DAOMethod(sql = "SELECT splLabel FROM SignPatientLabel WHERE signRecordId=:signRecordId")
    public abstract List<Integer> findSplLabelBySignRecordId(@DAOParam("signRecordId") Integer signRecordId);

    /**
     *医生发起上门签约的时候或者患者端申请签约的时候，插入签约记录居民类型关系表数据(此方法需要在事务里面)
     * @param signRecordId 签约记录表ID
     * @param doctorId 签约医生ID
     * @param MpiId 患者MpiId
     * @param patientLabel 居民类型key（是个集合）
     * @param signInitiator 业务发起者 （1 医生APP发起上门签约，2 微信端患者发起签约申请，3医生PC端诊间签约）
     * @return
     * @throws ControllerException
     */
    public Boolean saveSignResidentType(Integer signRecordId, Integer doctorId, String MpiId,
                                        List<String> patientLabel, Integer signInitiator){

            SignPatientLabelDAO signPatientLabelDAO = DAOFactory.getDAO(SignPatientLabelDAO.class);

            //校验传入数据
            if (signRecordId > 0){

            } else {
                throw new DAOException(DAOException.VALUE_NEEDED, "signRecordId is required");
            }
            if (patientLabel != null && patientLabel.size() > 0){
                if (signInitiator != null && (signInitiator.equals(SignInitiatorConstant.DROP_IN_SIGN)
                        || signInitiator.equals(SignInitiatorConstant.CLINIC_SIGN)
                        || signInitiator.equals(SignInitiatorConstant.PATIENT_SIGN) )){

                    for (int i=0; i < patientLabel.size(); i++){

                        SignPatientLabel signPatientLabel = new SignPatientLabel(); //签约记录居民类型关系
                        signPatientLabel.setSignRecordId(signRecordId);

                        Integer patientLabelInt = 0; //患者标签

                        //判断是否是数字
                        if (patientLabel.get(i).matches("[\\d]+")){
                            patientLabelInt = Integer.valueOf(patientLabel.get(i));
                            signPatientLabel.setSplLabel(patientLabelInt);
                        } else {
                            logger.error("发起签约申请插入居民类型的时候居民类型入参不合法,patientLabel="+patientLabel);
                            throw new DAOException(DAOException.VALIDATE_FALIED, "居民类型不合法！");
                        }

                        SignPatientLabel signPatientLabelTemp = signPatientLabelDAO.save(signPatientLabel);

                        //如果插入签约患者类型成功根据发起方判断是否直接执行签约成功后：将居民类型插入患者标签表
                        if (signPatientLabelTemp != null && signPatientLabelTemp.getSplId() > 0){

                            //医生APP上门签约或者PC端诊间签约直接将居民类型作为患者标签
                            if (signInitiator.equals(SignInitiatorConstant.DROP_IN_SIGN)
                                    || signInitiator.equals(SignInitiatorConstant.CLINIC_SIGN)){

                                RelationLabelDAO relationLabelDAO = DAOFactory.getDAO(RelationLabelDAO.class);
                                RelationDoctorDAO relationDoctorDAO = DAOFactory.getDAO(RelationDoctorDAO.class);

                                //获取签约记录优先查询relationType为2的，如果没有再查为0的
                                RelationDoctor relationDoctor = relationDoctorDAO.getSignByMpiAndDocAndType(MpiId, doctorId, 2);
                                if (null == relationDoctor){
                                    relationDoctor = relationDoctorDAO.getSignByMpiAndDocAndType(MpiId, doctorId, 0);
                                }
                                if (null == relationDoctor) {
                                	 relationDoctor = relationDoctorDAO.getSignByMpiAndDocAndType(MpiId, doctorId, 0);
                                }
                                //将居民类型保存到患者标签表
                                if (null != relationDoctor){
                                    Integer relationDoctorId = relationDoctor.getRelationDoctorId(); //医生关注内码
                                    Boolean spl = relationLabelDAO.savePatientLabel(relationDoctorId, patientLabelInt);
                                    if (!spl){
                                        throw new DAOException(ErrorCode.SERVICE_ERROR, "居民类型插入患者标签表失败！");
                                    }
                                }
                            }
                        } else {
                            throw new DAOException(ErrorCode.SERVICE_ERROR, "插入居民类型失败！");
                        }
                    }
                    logger.info("插入居民类型成功！");
                    return true;

                } else {
                    logger.error("申请签约的时候插入居民类型业务发起者不正确！signInitiator="+signInitiator);
                    throw new DAOException(ErrorCode.SERVICE_ERROR, "申请签约的时候插入居民类型业务发起者不正确！");
                }
            } else {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "居民类型不能为空");
            }

    }

}
