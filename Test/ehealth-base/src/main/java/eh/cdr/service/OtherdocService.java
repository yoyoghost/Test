package eh.cdr.service;

import ctd.account.UserRoleToken;
import ctd.account.user.UserRoleTokenEntity;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.dao.DepartmentDAO;
import eh.base.dao.EmploymentDAO;
import eh.cdr.constant.DocClassConstant;
import eh.cdr.constant.DocIndexConstant;
import eh.cdr.constant.OtherdocConstant;
import eh.cdr.dao.CdrOtherdocDAO;
import eh.cdr.dao.DocIndexDAO;
import eh.cdr.dao.OtherDocDAO;
import eh.entity.base.Department;
import eh.entity.base.Doctor;
import eh.entity.base.Employment;
import eh.entity.cdr.DocIndex;
import eh.entity.cdr.Otherdoc;
import eh.mpi.dao.PatientDAO;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.StatelessSession;

import java.util.Date;
import java.util.List;

/**
 * Created by w on 2016/5/11.
 */
public class OtherdocService {
    private static final Log logger = LogFactory.getLog(OtherdocService.class);


    /**
     * 医生在患者的电子病历页面上传电子病历
     * @param otherdocs
     * @return
     */
    @RpcService
    public Integer uploadDocIndexs(List<Otherdoc> otherdocs){
        Integer saveNum=0;

        DepartmentDAO deptDAO = DAOFactory.getDAO(DepartmentDAO.class);
        EmploymentDAO empDAO = DAOFactory.getDAO(EmploymentDAO.class);
        final DocIndexDAO indexDAO = DAOFactory.getDAO(DocIndexDAO.class);
        final CdrOtherdocDAO otherdocDAO = DAOFactory.getDAO(CdrOtherdocDAO.class);

        //获取用户缓存信息
        UserRoleTokenEntity ure = (UserRoleTokenEntity) UserRoleToken.getCurrent();
        if(null==ure){
            throw new DAOException(ErrorCode.SERVICE_ERROR,"用户未登陆");
        }
        //根据缓存获取doctorid
        Doctor doctor = (Doctor) ure.getProperty("doctor");
        Integer doctorId=doctor.getDoctorId();

        //根据doctorId获取第一职业点信息(包括机构/科室)
        Employment employment=empDAO.getPrimaryEmpByDoctorId(doctorId);
        Integer organId=employment.getOrganId();
        Integer deptId=employment.getDepartment();
        Department dept = deptDAO.getById(deptId);

        Date now=new Date();

        for(Otherdoc doc:otherdocs){
            try {
                isValidUploadDocIndexsData(doc);
            }catch (Exception e){
                logger.error(JSONUtils.toString(doc)+"记录保存数据异常:"+e.getMessage());
                continue;
            }

            doc.setClinicType(OtherdocConstant.CLINIC_TYPE_UPLOAD_SELF);
            doc.setCreateDate(now);

            //设置上传参数
            DocIndex docIndex=new DocIndex();
            docIndex.setMpiid(doc.getMpiid());

            docIndex.setCreateDoctor(doctorId);
            docIndex.setCreateDepart(deptId);
            docIndex.setCreateOrgan(organId);
            docIndex.setDepartName(dept.getName());
            docIndex.setDoctorName(doctor.getName());

            docIndex.setCreateDate(now);
            docIndex.setDocClass(DocClassConstant.OTHER_DOC);// 未说明，直接定死
            docIndex.setDocFlag(DocIndexConstant.DOC_FLAG_CAN_DEL);//默认值为可删除
            docIndex.setDocStatus(DocIndexConstant.DOC_STATUS_NO_DEL);//默认为未删除
            docIndex.setLastModify(now);

            String docType=doc.getDocType();
            String docTypeText="";
            try {
                docTypeText = DictionaryController.instance()
                        .get("eh.cdr.dictionary.DocType")
                        .getText(docType);
            } catch (ControllerException e) {
                logger.error("医生自主上传电子病历取文档类型出错"+e.getMessage());
                continue;
            }
            docIndex.setDocType(docType);
            docIndex.setDocTitle(docTypeText);
            docIndex.setDocSummary(docTypeText);
            docIndex.setDoctypeName(docTypeText);

            final DocIndex saveDocIndex=docIndex;
            final Otherdoc saveDoc=doc;

            AbstractHibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
                @Override
                public void execute(StatelessSession ss) throws Exception {
                    Boolean flag=true;

                    // 保存其他资料
                    Otherdoc d = otherdocDAO.save(saveDoc);
                    if (d.getOtherDocId() == null) {
                        flag=false;
                        logger.error("saveOtherDocFailed:"
                                + JSONUtils.toString(saveDoc));
                    }else{

                        // 获取主键值
                        Integer otherDocId = d.getOtherDocId();
                        saveDocIndex.setDocId(otherDocId);

                        // 保存电子病历文档索引
                        DocIndex docIndex2 = indexDAO.save(saveDocIndex);

                        if (docIndex2 == null) {
                            flag=false;
                            logger.error("saveDocIndexFailed: docIndex2 = null");
                        }
                    }


                    setResult(flag);
                }
            };
            HibernateSessionTemplate.instance().executeTrans(action);

            Boolean flag=action.getResult();
            if(flag){
                saveNum++;
            }

        }

        return saveNum;
    }


    /**
     * 不同上传者类型 上传电子病历资料
     * @param uploadType
     * @param otherdocs
     * uploadType(1转诊；2会诊；3咨询；4其他;5在线云门诊;6医生自主上传;7患者注册上传病历;8患者用户健康档案自主上传类型
     * 9-随访会话患者自主上传
     * busId 业务Id 没有业务传0
     */
    @RpcService
    public void uploadDocIndexsWithType(Integer uploadType, Integer busId, List<Otherdoc> otherdocs){
        CdrOtherdocDAO cdrOtherdocDAO = DAOFactory.getDAO(CdrOtherdocDAO.class);
        cdrOtherdocDAO.saveOtherDocList(uploadType, busId, otherdocs);
    }

    /**
     * 检验UploadDocIndexs()数据的完整性
     * @param doc
     */
    private void isValidUploadDocIndexsData(Otherdoc doc) {
        String mpiid=doc.getMpiid();
        if (StringUtils.isEmpty(mpiid)) {
//            logger.error("mpiid is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "mpiid is required");
        }

        PatientDAO patDao=DAOFactory.getDAO(PatientDAO.class);
        if(patDao.get(mpiid)==null){
//            logger.error("不存在患者["+mpiid+"]");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "mpiid is required");
        }

        if (StringUtils.isEmpty(doc.getDocType())) {
//            logger.error("docType is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "docType is required");
        }

        if (StringUtils.isEmpty(doc.getDocName())) {
//            logger.error("docName is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "docName is required");
        }

        if (doc.getDocContent() == null) {
//            logger.error("docContent is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "docContent is required");
        }

        if (StringUtils.isEmpty(doc.getDocFormat())) {
//            logger.error("docFormat is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "docFormat is required");
        }

    }

    /**
     * 患者在健康档案页面上传病历
     * geyin
     */
    @RpcService
    public Integer uploadPatientDocIndexs(DocIndex updateDoc,List<Otherdoc> otherdocs){
        Integer saveNum=0;
        final DocIndexDAO indexDAO = DAOFactory.getDAO(DocIndexDAO.class);
        final OtherDocDAO otherdocDAO = DAOFactory.getDAO(OtherDocDAO.class);
        final PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        Date now=new Date();
        for(Otherdoc doc:otherdocs){
            try {
                isValidUploadDocIndexsData(doc);
            }catch (Exception e){
                logger.error(JSONUtils.toString(doc)+"记录保存数据异常:"+e.getMessage());
                continue;
            }
            doc.setClinicType(OtherdocConstant.CLINIC_TYPE_UPLOAD_USER);//患者自主上传健康档案类型
            //设置上传参数
            DocIndex docIndex=new DocIndex();
            docIndex.setMpiid(doc.getMpiid());

            docIndex.setDocType(doc.getDocType());
            docIndex.setDocTitle(doc.getDocName());
            docIndex.setDocClass(DocClassConstant.OTHER_DOC);//默认为其他
            docIndex.setDocFlag(DocIndexConstant.DOC_FLAG_CAN_DEL);//默认值为可删除
            docIndex.setDocStatus(DocIndexConstant.DOC_STATUS_NO_DEL);//默认为未删除

            docIndex.setOrganNameByUser(updateDoc.getOrganNameByUser());
            docIndex.setDepartName(updateDoc.getDepartName());
            docIndex.setDoctorName(updateDoc.getDoctorName());
            docIndex.setClinicPersonName(patientDAO.getNameByMpiId(doc.getMpiid()));
            docIndex.setGetDate(updateDoc.getCreateDate());//采集时间
            docIndex.setCreateDate(now);//创建时间
            docIndex.setFileSize(updateDoc.getFileSize());

            String docType=doc.getDocType();
            String docTypeText="";
            try {
                docTypeText = DictionaryController.instance()
                        .get("eh.cdr.dictionary.DocType")
                        .getText(docType);
            } catch (ControllerException e) {
                logger.error("患者自主上传健康档案病历取文档类型出错"+e.getMessage());
                continue;
            }
            docIndex.setDocType(docType);
            docIndex.setDocTitle(docTypeText);

            docIndex.setLastModify(now);

            docIndex.setDocSummary(updateDoc.getDocSummary());
            docIndex.setDoctypeName(docTypeText);

            final DocIndex saveDocIndex=docIndex;
            final Otherdoc saveDoc=doc;

            AbstractHibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
                @Override
                public void execute(StatelessSession ss) throws Exception {
                    Boolean flag=true;

                    // 保存其他资料
                    Otherdoc d = otherdocDAO.save(saveDoc);
                    if (d.getOtherDocId() == null) {
                        flag=false;
                        logger.error("uploadPatientDocIndexs otherDocId is null");
                    }else{

                        // 获取主键值
                        Integer otherDocId = d.getOtherDocId();
                        saveDocIndex.setDocId(otherDocId);

                        // 保存电子病历文档索引
                        DocIndex docIndex2 = indexDAO.save(saveDocIndex);

                        if (docIndex2 == null) {
                            flag=false;
                            logger.error("uploadPatientDocIndexs: docIndex2 is null");
                        }
                    }

                    setResult(flag);
                }
            };
            HibernateSessionTemplate.instance().executeTrans(action);

            Boolean flag=action.getResult();
            if(flag){
                saveNum++;
            }

        }

        return saveNum;
    }
    
}
