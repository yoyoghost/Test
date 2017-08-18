package eh.op.service;

import ctd.account.UserRoleToken;
import ctd.mvc.upload.FileMetaRecord;
import ctd.mvc.upload.FileService;
import ctd.mvc.upload.exception.FileRegistryException;
import ctd.mvc.upload.exception.FileRepositoryException;
import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.schema.exception.ValidateException;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.dao.DoctorDAO;
import eh.base.dao.OrganDAO;
import eh.base.service.BusActionLogService;
import eh.entity.base.Doctor;
import eh.entity.base.Organ;
import eh.entity.mpi.Patient;
import eh.entity.mpi.RelationDoctor;
import eh.entity.xls.ImportExcelInfo;
import eh.entity.xls.XlsRelationPatient;
import eh.mpi.dao.PatientDAO;
import eh.mpi.dao.RelationDoctorDAO;
import eh.op.dao.ImportExcelInfoDAO;
import eh.op.dao.XlsRelationPatientDAO;
import eh.util.ChinaIDNumberUtil;
import eh.util.PoiUtil;
import eh.utils.ValidateUtil;
import org.apache.axis.utils.StringUtils;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author jianghc
 * @create 2016-11-28 13:59
 **/
public class PatientOPService {

    private static final Log logger = LogFactory.getLog(PatientOPService.class);

    /**
     * 获取有效患者（身份证或手机号一致）
     *
     * @param idCard
     * @param mobile
     * @return
     */
    @RpcService
    public List<Patient> findPatientByIdCardOrMobile(String idCard, String mobile) {
        if (idCard == null || StringUtils.isEmpty(idCard.trim())) {
            throw new DAOException(DAOException.VALUE_NEEDED, " idCard is require");
        }
        if (mobile == null || StringUtils.isEmpty(mobile.trim())) {
            throw new DAOException(DAOException.VALUE_NEEDED, " mobile is require");
        }
        String idCard18;
        if (idCard.trim().length() > 15) {
            idCard18 = idCard;
        } else {
            try {
                idCard18 = ChinaIDNumberUtil.convert15To18(idCard.toUpperCase());
            } catch (ValidateException e) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "身份证不正确");
            }
        }
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        List<Patient> patients = patientDAO.findUsefulPatientsByIdCardOrLoginId(idCard18, mobile);
        if (patients == null) {
            return null;
        }
       /* if(patients.size()!=2){
            throw new DAOException(patients.size()+"");
        }*/
        //RelationDoctorDAO.findSignByMpi()
        RelationDoctorDAO relationDoctorDAO = DAOFactory.getDAO(RelationDoctorDAO.class);
        List<Patient> returnList = new ArrayList<Patient>();
        for (Patient patient : patients) {
            List<RelationDoctor> relations = relationDoctorDAO.findSignByMpi(patient.getMpiId());//查询是否有有效的签约记录
            if (relations != null && relations.size() > 0) {
                patient.setSignFlag(true);
            }
            returnList.add(patient);
        }
        return returnList;
    }

    /**
     * 患者绑定身份证和手机号
     *
     * @param idCard
     * @param mobile
     * @param mpiid
     */
    @RpcService
    public void bindPatientByIdCardOrMobile(String idCard, String mobile, String mpiid) {
        if (idCard == null || StringUtils.isEmpty(idCard.trim())) {
            throw new DAOException(DAOException.VALUE_NEEDED, " idCard is require");
        }
        if (mobile == null || StringUtils.isEmpty(mobile.trim())) {
            throw new DAOException(DAOException.VALUE_NEEDED, " mobile is require");
        }
        if (mpiid == null || StringUtils.isEmpty(mpiid.trim())) {
            throw new DAOException(DAOException.VALUE_NEEDED, " mpiid is require");
        }
        String idCard18;
        if (idCard.trim().length() > 15) {
            idCard18 = idCard;
        } else {
            try {
                idCard18 = ChinaIDNumberUtil.convert15To18(idCard.toUpperCase());
            } catch (ValidateException e) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "身份证号码不正确");
            }
        }

        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        List<Patient> patients = patientDAO.findUsefulPatientsByIdCardOrLoginId(idCard18, mobile);
        if (patients == null) {
            return;
        }
        StringBuilder sbPatient = new StringBuilder(" ");
        Patient cp = null;
        for (Patient patient : patients) {
            if (mpiid.equals(patient.getMpiId().trim())) {
                patient.setLoginId(mobile);
                patient.setMobile(mobile);
                patient.setIdcard(idCard18);
                patient.setRawIdcard(idCard);
                try {
                    patient.setBirthday(ChinaIDNumberUtil.getBirthFromIDNumber(idCard18));
                    patient.setPatientSex(ChinaIDNumberUtil.getSexFromIDNumber(idCard18));
                } catch (ValidateException e) {
                    logger.error(e);
                }
                cp = patient;
            } else {
                patient.setLoginId(null);
                patient.setMobile(null);
                if (patient.getIdcard() != null && idCard18.equals(patient.getIdcard().trim())) {
                    patient.setIdcard("ZF" + patient.getIdcard());
                    patient.setRawIdcard("ZF" + idCard);
                }
                patient.setStatus(9);//注销
                sbPatient.append(patient.getMpiId()).append(",");
                patientDAO.update(patient);
            }
        }
        if (cp != null) {
            patientDAO.update(cp);
        }
        BusActionLogService.recordBusinessLog("患者信息绑定", mpiid, "Patient", "将患者ID为：" + mpiid + "的手机号设置为" + mobile + ",身份证号设置为：" + idCard18 + ";并注销以下账号【" + sbPatient.substring(0, sbPatient.length() - 1).trim() + "】");
    }

    @RpcService
    public Patient updateNameAndMobileAndCardIdByMpiId(String mpi, String name, String mobile, String cardID) {
        if (mpi == null || StringUtils.isEmpty(mpi.trim())) {
            throw new DAOException(DAOException.VALUE_NEEDED, " mpi is require");
        }
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        Patient patient = patientDAO.getByMpiId(mpi);
        if (patient == null) {
            throw new DAOException(" 患者不存在 ");
        }
        if (name == null || StringUtils.isEmpty(name.trim())) {
            throw new DAOException(DAOException.VALUE_NEEDED, " name is require");
        }
        if (mobile == null || !ValidateUtil.isMobile(mobile)) {
            throw new DAOException(" 手机号错误");
        }

        if (cardID == null || (cardID.length() != 15 && cardID.length() != 18)) {
            throw new DAOException(" 身份证号错误");
        } else {
            String sex = "";
            String idCard18 = "";
            Date brithDay = null;
            try {
                idCard18 = ChinaIDNumberUtil.convert15To18(cardID);
                sex = ChinaIDNumberUtil.getSexFromIDNumber(idCard18);
                brithDay = ChinaIDNumberUtil.getBirthFromIDNumber(idCard18);
            } catch (Exception e) {
                throw new DAOException(" 身份证号错误");
            }
            Patient old = patientDAO.getByIdCard(idCard18);
            if (old == null || old.getMpiId().trim().equals(mpi.trim())) {
                patient.setPatientName(name);
                patient.setIdcard(idCard18);
                patient.setRawIdcard(cardID);
                patient.setMobile(mobile);
                patient.setPatientSex(sex);
                patient.setBirthday(brithDay);
                patient = patientDAO.update(patient);
            } else {
                throw new DAOException(" 该身份证号已经存在");
            }
        }
        BusActionLogService.recordBusinessLog("患者管理", mpi, "Patient",
                "患者:" + patient.getPatientName() + ",将姓名更新为" + name + ",将身份证号更新为" + patient.getIdcard() + ",将手机号更新为" + mobile);
        return patient;
    }


    /**
     * 运营平台 批量导入 签约医生患者
     *
     * @param patient
     * @param relationDoctor
     * @return
     * @author AndyWang 2016-12-22
     */
    @RpcService
    public void importPatientAndSignDoctor(Patient patient, Integer doctorId, RelationDoctor relationDoctor) {
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
//        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        RelationDoctorDAO relationDoctorDAO = DAOFactory.getDAO(RelationDoctorDAO.class);
        try {
            Patient patientTarget = patientDAO.getByIdCard(patient.getCardId());
            if (patientTarget == null) {
                patientTarget = patientDAO.createWXPatientUser(patient);
            }
            relationDoctorDAO.addFamilyDoctor(patientTarget.getMpiId(), doctorId,
                    relationDoctor.getRelationDate(), relationDoctor.getStartDate(), relationDoctor.getEndDate());
           // List<RelationDoctor> relationDoctors = relationDoctorDAO.findDoctorAttentionToPatient(doctorId, patientTarget.getMpiId());
        } catch (ValidateException e) {
            throw new DAOException(e.getMessage());
        }
    }

    public List<XlsRelationPatient> importRelationPatients(List<XlsRelationPatient> relationPatients) {
        List<XlsRelationPatient> failedRelationPatients = new ArrayList<XlsRelationPatient>();
        Iterator<XlsRelationPatient> iter = relationPatients.iterator();
        XlsRelationPatientDAO xlsRelationPatientDAO = DAOFactory.getDAO(XlsRelationPatientDAO.class);
        while (iter.hasNext()) {
            XlsRelationPatient r = iter.next();
            Patient patient = new Patient();
           // Doctor doctor = new Doctor();
            RelationDoctor relationDoctor = new RelationDoctor();
            //患者 信息
            patient.setPatientName(r.getPatientName());
            patient.setAge(r.getAge());//年龄
            patient.setRawIdcard(r.getPatRawIdcard());
            patient.setIdcard(r.getPatIdcard());
            patient.setBirthday(r.getBirthday());
            patient.setPatientSex(r.getPatientSex());
            patient.setMobile(r.getMobile());
            patient.setAddress(r.getAddress());
            patient.setCreateDate(new Date());
            patient.setLastModify(new Date());
            patient.setPatientType("1");// 1：自费
            patient.setLastSummary("ngarihealth");//标记本次导入的患者
            relationDoctor.setStartDate(r.getStartDate());//签约开始日期
            relationDoctor.setEndDate(r.getEndDate());//签约结束日期
            relationDoctor.setRelationDate(r.getRelationDate());//签约关系创建时间
            relationDoctor.setRelationType(0);//类型 为0 签
            r.setStatus(1);
            try{
                this.importPatientAndSignDoctor(patient, r.getDoctorId(), relationDoctor);
            }catch (Exception e){
                r.setStatus(-1);
                r.setErrMsg(e.getMessage());
                failedRelationPatients.add(r);
            }
            xlsRelationPatientDAO.update(r);
        }
        return failedRelationPatients;
    }

    @RpcService
    public Map importRelationPatientsByXlsId(Integer xlsId) {
        if (xlsId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "xlsId is require");
        }
        ImportExcelInfoDAO importExcelInfoDAO = DAOFactory.getDAO(ImportExcelInfoDAO.class);
        ImportExcelInfo excelInfo = importExcelInfoDAO.getById(xlsId);
        if (excelInfo == null || excelInfo.getStatus() != 0) {
            throw new DAOException("xlsId is error");
        }
        XlsRelationPatientDAO xlsRelationPatientDAO = DAOFactory.getDAO(XlsRelationPatientDAO.class);
        List<XlsRelationPatient> errInfo = this.importRelationPatients(xlsRelationPatientDAO.findByXlsId(xlsId));
        UserRoleToken urt = UserRoleToken.getCurrent();
        excelInfo.setSuccess(excelInfo.getTotal() - errInfo.size());
        excelInfo.setStatus(1);
        excelInfo.setExecuter(urt.getId());
        excelInfo.setExecuterName(urt.getUserName());
        excelInfo.setExecuteDate(new Date());
        excelInfo = importExcelInfoDAO.update(excelInfo);
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("excelInfo", excelInfo);
        map.put("detail", errInfo);
        BusActionLogService.recordBusinessLog("签约患者批量导入", excelInfo.getId() + "", "ImportExcelInfo",
                "导入签约患者" + excelInfo.getTotal() + "条，成功" + excelInfo.getSuccess() + "条。");
        return map;
    }


    public List<XlsRelationPatient> validateRelationPatients(List<XlsRelationPatient> relationPatients) {
        List<XlsRelationPatient> failedRelationPatients = new ArrayList<XlsRelationPatient>();
        Iterator<XlsRelationPatient> iter = relationPatients.iterator();
        while (iter.hasNext()) {
            XlsRelationPatient failedRelationPatient = this.valideRelationPatientRegistration(iter.next());
            if (failedRelationPatient != null) {
                //有非法数据
                failedRelationPatients.add(failedRelationPatient);
            }
        }
        return failedRelationPatients;
    }

    private XlsRelationPatient valideRelationPatientRegistration(XlsRelationPatient r) {
        if (r == null) {
            return null;
        }
        StringBuilder errorMessage = new StringBuilder("");
        if (StringUtils.isEmpty(r.getPatIdcard())) {
            errorMessage.append("患者身份证不能为空;");
        }
        if (StringUtils.isEmpty(r.getMobile())) {
            errorMessage.append("患者手机号不能为空;");
        }

        if (StringUtils.isEmpty(r.getPatientName())) {
            errorMessage.append("患者姓名不能为空;");
        }
        if (r.getRelationDate() == null) {
            errorMessage.append("签约时间有误;");
        }
        if (r.getStartDate() == null) {
            errorMessage.append("开始时间有误;");
        }
        if (r.getEndDate() == null) {
            errorMessage.append("结束时间有误;");
        }
        if (r.getStartDate() != null && r.getEndDate() != null) {
            if (r.getStartDate().after(r.getEndDate())) {
                errorMessage.append("开始时间不能大于结束时间;");
            }
        }
        if (r.getStartDate() != null && r.getRelationDate() != null) {
            if (r.getRelationDate().after(r.getStartDate())) {
                errorMessage.append("签约时间不能大于开始时间;");
            }
        }
        /*if (StringUtils.isEmpty(r.getAddress())) {
            errorMessage.append("地址不能为空;");
        }*/
        if (ValidateUtil.nullOrZeroInteger(r.getDoctorId())) {
            errorMessage.append("医生信息有误;");
        }
        if (!ValidateUtil.isMobile(r.getMobile())) {
            errorMessage.append("手机号码不正确;");
            // 手机号码不正确
        }
        if (StringUtils.isEmpty(r.getPatientSex())) {
            errorMessage.append("性别不能为空;");
        }
        if (errorMessage.toString().length() > 0) {
            //验证失败时，用Introduce来作为错误信息变量。
            r.setErrMsg(r.getErrMsg() + errorMessage.toString());

        }
        return r;

    }

    public Map readRelationPation(FileItem file, String fileName, Long fileSize, int organId) {
        InputStream is = null;
        try {
            is = file.getInputStream();
        } catch (IOException e) {
            logger.error(e);
        }
        if (is == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "InputStream is required");
        }
        //获得用户上传工作簿
        Workbook workbook = null;
        try {
            workbook = new HSSFWorkbook(is);//尝试使用2003版本读取
        } catch (Exception e) {
            throw new DAOException("上传文件格式有问题，请使用Excel2003版本");
        }
        Sheet sheet = workbook.getSheetAt(0);
        if (sheet == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "sheet is required");
        }
        Integer total = sheet.getLastRowNum();
        if (total == null || total <= 0) {
            throw new DAOException(DAOException.VALUE_NEEDED, "data is required");
        }
        if (total > 1000) {
            throw new DAOException("上传数据量过大");
        }
        List<XlsRelationPatient> rps = new ArrayList<XlsRelationPatient>();
        List<XlsRelationPatient> errRps = new ArrayList<XlsRelationPatient>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        Organ organ = organDAO.getByOrganId(organId);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        if (organ == null) {
            throw new DAOException("organId is not exist");
        }
        String organName = organ.getName();
        HashMap<String, Integer> hmDoc = new HashMap<String, Integer>();
        for (int rowIndex = 1; rowIndex <= total; rowIndex++) {
            XlsRelationPatient info = new XlsRelationPatient();
            info.setOrganId(organId);
            info.setOrganName(organName);
            StringBuilder errMsg = new StringBuilder("");
            Row row = sheet.getRow(rowIndex);//循环获得每个行
            String cell_docName = null;//医生名称
            String cell_docCard = null;//医生身份证
            String cell_patName = null;
            String cell_patCard = null;
            String cell_patMobile = null;
            String cell_patAdress = null;
            String cell_signDate = null;
            String cell_startDate = null;
            String cell_endDate = null;
            try {
                cell_docName = PoiUtil.getStrFromCell(row.getCell(0));
                cell_docCard = PoiUtil.getStrFromCell(row.getCell(1));
                if (cell_docName != null && !StringUtils.isEmpty(cell_docName.trim())) {
                    cell_docName = cell_docName.trim();
                    info.setDoctorName(cell_docName);
                    Integer hmDocId = hmDoc.get(cell_docName);
                    if (hmDocId == null) {
                        Doctor doctor = null;
                        if (cell_docCard != null && !StringUtils.isEmpty(cell_docCard.trim())) {
                            info.setDocIdCard(cell_docCard);
                            String idCard18 = ChinaIDNumberUtil.convert15To18(cell_docCard);
                            doctor = doctorDAO.getByIdNumber(idCard18);
                        }
                        if (doctor == null) {
                            try {
                                doctor = doctorDAO.getByNameAndOrgan(cell_docName, organId);
                            } catch (Exception e) {
                                errMsg.append("医生姓名不唯一;");
                            }
                        }
                        if (doctor != null && doctor.getName().equals(cell_docName)) {
                            hmDoc.put(cell_docName, doctor.getDoctorId());
                            info.setDoctorId(doctor.getDoctorId());
                            info.setDocIdCard(doctor.getIdNumber());
                        } else {
                            hmDoc.put(cell_docName, 0);
                        }
                    } else {
                        if (hmDocId > 0) {
                            info.setDoctorId(hmDocId);
                        }
                    }
                }
            } catch (Exception e) {
                throw new DAOException("医生姓名/身份证单元格格式有误");
            }
            try {
                cell_patName = PoiUtil.getStrFromCell(row.getCell(2));
                info.setPatientName(cell_patName);
            } catch (Exception e) {
                throw new DAOException("患者姓名单元格格式有误");
            }
            try {
                cell_patCard = PoiUtil.getStrFromCell(row.getCell(3));
                info.setPatRawIdcard(cell_patCard);
                if (cell_patCard != null) {
                    cell_patCard = cell_patCard.trim();
                }
                if (cell_patCard == null ||( cell_patCard.length() != 15 && cell_patCard.length() != 18)) {
                    errMsg.append("患者身份证号不合法;");
                } else {
                    String idCard18;
                    try {
                        idCard18 = ChinaIDNumberUtil.convert15To18(cell_patCard);
                        info.setPatIdcard(idCard18);
                        info.setPatientSex(ChinaIDNumberUtil.getSexFromIDNumber(idCard18));
                        info.setBirthday(ChinaIDNumberUtil.getBirthFromIDNumber(idCard18));
                        info.setAge(ChinaIDNumberUtil.getAgeFromIDNumber(idCard18));
                        Patient patient = patientDAO.getByIdCard(idCard18);
                        if (patient != null) {
                            if (patient.getPatientName().trim().equals(cell_patName == null ? "" : cell_patName.trim())) {
                                info.setMpiId(patient.getMpiId());
                            } else {
                                errMsg.append("患者姓名与身份证不匹配;");
                            }
                        }

                    } catch (Exception e) {
                        errMsg.append("患者身份证号不合法;");
                    }
                }
            } catch (Exception e) {
                throw new DAOException("患者身份证号单元格格式有误");
            }
            try {
                cell_patMobile = PoiUtil.getStrFromCell(row.getCell(4));
                info.setMobile(cell_patMobile);
            } catch (Exception e) {
                throw new DAOException("患者手机号单元格格式有误");
            }
            try {
                cell_patAdress = PoiUtil.getStrFromCell(row.getCell(5));
                info.setAddress(cell_patAdress);
            } catch (Exception e) {
                throw new DAOException("患者地址单元格格式有误");
            }
            try {
                cell_signDate = PoiUtil.getStrFromCell(row.getCell(6));
                if (cell_signDate != null && !StringUtils.isEmpty(cell_signDate.trim())) {
                    info.setRelationDate(sdf.parse(cell_signDate));
                }
            } catch (ParseException e) {
                throw new DAOException("签约时间格式有误");
            }

            try {
                cell_startDate = PoiUtil.getStrFromCell(row.getCell(7));
                if (cell_startDate != null && !StringUtils.isEmpty(cell_startDate.trim())) {
                    info.setStartDate(sdf.parse(cell_startDate));
                }
            } catch (ParseException e) {
                throw new DAOException("签约开始格式有误");
            }
            try {
                cell_endDate = PoiUtil.getStrFromCell(row.getCell(8));
                if (cell_endDate != null && !StringUtils.isEmpty(cell_endDate.trim())) {
                    info.setEndDate(sdf.parse(cell_endDate));
                }
            } catch (ParseException e) {
                throw new DAOException("签约结束格式有误");
            }
            info.setErrMsg(errMsg.toString());
            info = this.valideRelationPatientRegistration(info);
            if (info.getErrMsg() != null && info.getErrMsg().length() > 5) {
                info.setErrMsg("【第" + rowIndex + "行】:" + info.getErrMsg().substring(0, info.getErrMsg().length() - 1));
                errRps.add(info);
            }
            rps.add(info);
        }
        ImportExcelInfo excelInfo = new ImportExcelInfo();
        excelInfo.setFileName(fileName);
        excelInfo.setTotal(rps.size());
        excelInfo.setSuccess(rps.size() - errRps.size());
        Map<String, Object> map = new HashMap<String, Object>();
        if (errRps.size() <= 0) {//不包含错误信息
            UserRoleToken urt = UserRoleToken.getCurrent();
            excelInfo.setUploader(urt.getId());
            excelInfo.setUploaderName(urt.getUserName());
            excelInfo.setUploadDate(new Date());
            FileMetaRecord meta = new FileMetaRecord();
            meta.setManageUnit(urt.getManageUnit());
            meta.setOwner(urt.getUserId());
            meta.setLastModify(new Date());
            meta.setUploadTime(new Date());
            meta.setCatalog("other-doc");
            meta.setContentType("application/vnd.ms-excel");
            meta.setFileName(fileName);
            meta.setFileSize(fileSize);
            try {
                is = file.getInputStream();
                FileService.instance().upload(meta, is);
            } catch (FileRepositoryException e) {
                throw new DAOException("文件上传失败");
            } catch (FileRegistryException e) {
                throw new DAOException("文件上传失败");
            } catch (IOException e) {
                throw new DAOException("文件上传失败");
            }
            excelInfo.setOssId(meta.getFileId());
            excelInfo.setSuccess(0);
            excelInfo.setStatus(0);
            excelInfo.setExcelType(3);//签约患者
            ImportExcelInfoDAO importExcelInfoDAO = DAOFactory.getDAO(ImportExcelInfoDAO.class);
            excelInfo = importExcelInfoDAO.save(excelInfo);
            XlsRelationPatientDAO xlsRelationPatientDAO = DAOFactory.getDAO(XlsRelationPatientDAO.class);
            for (XlsRelationPatient item : rps) {
                item.setXlsId(excelInfo.getId());
                item.setStatus(0);
                xlsRelationPatientDAO.save(item);
            }
        } else {
            map.put("detail", errRps);
        }
        map.put("excelInfo", excelInfo);
        return map;
    }

    @RpcService
    public QueryResult<Patient> queryPatient(final Date startDate, final Date endDate, final String patientName,
                                             final String idcard, final Integer sex, final Integer minAge, final Integer maxAge,
                                             final String mobile, final Integer start, final Integer limit, final Boolean hasRegister) {
        PatientDAO pdao = DAOFactory.getDAO(PatientDAO.class);
        QueryResult<Patient> patientList = pdao.findPatient(startDate, endDate, patientName, idcard, sex, minAge, maxAge, mobile, start, limit, hasRegister);
        return patientList;
    }

    /**
     * 查询患者（手机号，姓名，身份证）
     *
     * @return
     * @author Andywang 2017-03-08
     */
    @RpcService
    public List<Patient> findPatientByKeywords(final String keywords) {
        HibernateStatelessResultAction<List<Patient>> action = new AbstractHibernateStatelessResultAction<List<Patient>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws Exception {
                StringBuffer buffer = new StringBuffer("From Patient where 1=1 ");
                buffer.append(" and (mobile = :keywords or patientName = :keywords or idcard = :keywords) ");
                Query q = ss.createQuery(buffer.toString());
                q.setParameter("keywords", keywords);
                List<Patient> patients = q.list();
                setResult(patients);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }


    /**
     * 查询拥有用户的患者（手机号，姓名，身份证）
     * @return
     * @author Andywang 2017-03-08
     */
    @RpcService
    public List<Patient> findPatientWithLoginIdByKeywords(final String keywords) {
        HibernateStatelessResultAction<List<Patient>> action = new AbstractHibernateStatelessResultAction<List<Patient>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws Exception {
                StringBuffer buffer = new StringBuffer("From Patient where loginId is not null ");
                buffer.append(" and (mobile = :keywords or patientName = :keywords or idcard = :keywords) ");
                Query q = ss.createQuery(buffer.toString());
                q.setParameter("keywords", keywords);
                List<Patient> patients = q.list();
                setResult(patients);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

}
