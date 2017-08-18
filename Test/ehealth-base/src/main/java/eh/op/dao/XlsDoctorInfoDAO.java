package eh.op.dao;

import ctd.account.UserRoleToken;
import ctd.mvc.upload.FileMetaRecord;
import ctd.mvc.upload.FileService;
import ctd.mvc.upload.exception.FileRegistryException;
import ctd.mvc.upload.exception.FileRepositoryException;
import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import eh.base.dao.DepartmentDAO;
import eh.base.dao.DoctorDAO;
import eh.base.dao.OrganDAO;
import eh.base.dao.UserRolesDAO;
import eh.entity.base.Doctor;
import eh.entity.base.Organ;
import eh.entity.base.UserRoles;
import eh.entity.xls.ImportExcelInfo;
import eh.entity.xls.XlsDoctorInfo;
import eh.util.ChinaIDNumberUtil;
import eh.util.DictionaryUtil;
import eh.util.PoiUtil;
import eh.utils.ValidateUtil;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author jianghc
 * @create 2016-11-23 13:29
 **/
public abstract class XlsDoctorInfoDAO extends HibernateSupportDelegateDAO<XlsDoctorInfo> {
    public static final Logger log = Logger.getLogger(XlsDoctorInfoDAO.class);
    private UserRolesDAO userRolesDAO;

    public XlsDoctorInfoDAO() {
        super();
        this.setEntityName(XlsDoctorInfo.class.getName());
        this.setKeyField("id");
        userRolesDAO = DAOFactory.getDAO(UserRolesDAO.class);
    }

    public Map readXlsToDoctorInfo(FileItem file, String fileName, Long fileSize) {
        if (file == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "file is required");
        }
        InputStream is = null;
        try {
            is = file.getInputStream();
        } catch (IOException e) {
            log.error(e);
        }
        if (is == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "InputStream is required");
        }
        //获得用户上传工作簿
        Workbook workbook = null;
        try {
            workbook = new HSSFWorkbook(is);//尝试使用2003版本读取
        } catch (Exception e) {
            throw new DAOException(DAOException.VALIDATE_FALIED,"上传文件格式有问题，请使用Excel2003版本");
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
        List<XlsDoctorInfo> doctors = new ArrayList<XlsDoctorInfo>();
        List<XlsDoctorInfo> errDoctors = new ArrayList<XlsDoctorInfo>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        DepartmentDAO departmentDAO = DAOFactory.getDAO(DepartmentDAO.class);
        HashMap<String, Integer> hashID = new HashMap<String, Integer>();
        HashMap<String, Integer> hashMobile = new HashMap<String, Integer>();
        for (int rowIndex = 1; rowIndex <= total; rowIndex++) {
            XlsDoctorInfo info = new XlsDoctorInfo();
            StringBuilder errMsg = new StringBuilder();
            Row row = sheet.getRow(rowIndex);//循环获得每个行
            String cell_organ = null;//所属机构
            try {
                cell_organ = PoiUtil.getStrFromCell(row.getCell(0));
                if (cell_organ != null) {
                    Organ organ = organDAO.getValidOrganByName(cell_organ);
                    if (organ != null) {
                        info.setOrganId(organ.getOrganId());
                    }
                }
            } catch (Exception e) {
                log.error(e.getMessage());
            }
            try {
                //工号
                info.setJobNumber(PoiUtil.getStrFromCell(row.getCell(1)));
            } catch (Exception e) {
                //log.error(e.getMessage());
            }
            try {
                //姓名
                info.setName(PoiUtil.getStrFromCell(row.getCell(2)));
            } catch (Exception e) {
                //log.error(e.getMessage());
            }
            try {
                //性别
                String cell_gender = PoiUtil.getStrFromCell(row.getCell(3));
                if (cell_gender != null && !StringUtils.isEmpty(cell_gender.trim())) {
                    info.setGender(DictionaryUtil.getKeyByValue("eh.base.dictionary.Gender", cell_gender));
                }
            } catch (Exception e) {
                //log.error(e.getMessage());
            }
            try {
                //出生日期
                info.setBirthDay(sdf.parse(PoiUtil.getStrFromCell(row.getCell(4))));
            } catch (Exception e) {
                //log.error(e.getMessage());
            }
            try {
                //身份证号
                info.setIdNumber(PoiUtil.getStrFromCell(row.getCell(5)));
            } catch (Exception e) {
                //log.error(e.getMessage());
            }
            try {
                //手机号
                info.setMobile(PoiUtil.getStrFromCell(row.getCell(6)));
            } catch (Exception e) {
             //   log.error(e.getMessage());
            }
            try {
                //人员类别
                String cell_userType = PoiUtil.getStrFromCell(row.getCell(7));
                if (cell_userType != null && !StringUtils.isEmpty(cell_userType.trim())) {
                    String utype = DictionaryUtil.getKeyByValue("eh.base.dictionary.DoctorType", cell_userType);
                    if (utype != null) {
                        info.setUserType(Integer.parseInt(utype));
                    }
                }
            } catch (Exception e) {
                //log.error(e.getMessage());
            }
            try {
                //科室
                if (info.getOrganId() != null) {
                    info.setDepartment(departmentDAO.getEffByNameAndOrgan(PoiUtil.getStrFromCell(row.getCell(8)), info.getOrganId()).getDeptId());
                }
            } catch (Exception e) {
                log.error(e.getMessage());
            }
            try {
                //专科
                String cell_profession = PoiUtil.getStrFromCell(row.getCell(9));
                if (cell_profession != null && !StringUtils.isEmpty(cell_profession.trim())) {
                    info.setProfession(DictionaryUtil.getKeyByValue("eh.base.dictionary.Profession", cell_profession));
                }
            } catch (Exception e) {
                //log.error(e.getMessage());
            }
            try {
                //邮箱
                info.setEmail(PoiUtil.getStrFromCell(row.getCell(10)));
            } catch (Exception e) {
                errMsg.append("邮箱信息有误：").append(e.getMessage()).append(";");
            }
            try {
                //微信
                info.setWeChat(PoiUtil.getStrFromCell(row.getCell(11)));
            } catch (Exception e) {
                errMsg.append("微信信息有误：").append(e.getMessage()).append(";");
            }
            try {
                //个人介绍
                info.setIntroduce(PoiUtil.getStrFromCell(row.getCell(12)));
            } catch (Exception e) {
                errMsg.append("个人介绍信息有误：").append(e.getMessage()).append(";");
            }
            try {
                //擅长领域
                info.setDomain(PoiUtil.getStrFromCell(row.getCell(13)));
            } catch (Exception e) {
                errMsg.append("擅长领域信息有误：").append(e.getMessage()).append(";");
            }
            try {
                //成果荣誉
                info.setHonour(PoiUtil.getStrFromCell(row.getCell(14)));
            } catch (Exception e) {
                errMsg.append("成果荣誉信息有误：").append(e.getMessage()).append(";");
            }
            try {
                //职称
                String cell_porTitle = PoiUtil.getStrFromCell(row.getCell(15));
                if (cell_porTitle != null && !StringUtils.isEmpty(cell_porTitle)) {
                    String pro = DictionaryUtil.getKeyByValue("eh.base.dictionary.ProTitle", cell_porTitle);
                    if (pro != null && !StringUtils.isEmpty(pro)) {
                        info.setProTitle(pro);
                    } else {
                        errMsg.append("职称信息有误：不存在该职务;");
                    }
                }
            } catch (Exception e) {
                errMsg.append("职称信息有误:").append(e.getMessage()).append(";");
            }
            try {
                //职务
                String cell_jobTitle = PoiUtil.getStrFromCell(row.getCell(16));
                if (!StringUtils.isEmpty(cell_jobTitle)) {
                    String job = DictionaryUtil.getKeyByValue("eh.base.dictionary.JobTitle", cell_jobTitle);
                    if (job != null && !StringUtils.isEmpty(job)) {
                        info.setJobTitle(job);
                    } else {
                        errMsg.append("职务信息有误：不存在该职务;");
                    }
                }
            } catch (Exception e) {
                errMsg.append("职务信息有误:").append(e.getMessage()).append(";");
            }
            try {
                //学历
                String cell_education = PoiUtil.getStrFromCell(row.getCell(17));
                if (!StringUtils.isEmpty(cell_education)) {
                    String education = DictionaryUtil.getKeyByValue("eh.base.dictionary.Education", cell_education);
                    if (education != null && !StringUtils.isEmpty(education)) {
                        info.setEducation(education);
                    } else {
                        errMsg.append("学历信息有误：不存在该职务;");
                    }
                }
            } catch (Exception e) {
                errMsg.append("学历信息有误:").append(e.getMessage()).append(";");
            }
            String strMsg = valideDoctorRegistration(info);
            if (strMsg != null && strMsg.length() > 1) {
                errMsg.append(strMsg);
            }
            String mobile = info.getMobile();
            String ID = info.getIdNumber();
            if (mobile != null && !StringUtils.isEmpty(mobile.trim())) {
                if (hashMobile.get(mobile.trim()) == null || hashMobile.get(mobile.trim()) != 1) {
                    hashMobile.put(mobile, 1);
                } else {
                    errMsg.append("该手机号已存在Excel;");
                }
            }
            if (ID != null && !StringUtils.isEmpty(ID.trim())) {
                if (hashID.get(ID.trim()) == null || hashID.get(ID.trim()) != 1) {
                    hashID.put(ID, 1);
                } else {
                    errMsg.append("该身份证号已存在Excel;");
                }
            }
            if (errMsg.length() > 1) {
                info.setErrMsg("【第" + rowIndex + "行】" + errMsg.substring(0, errMsg.length() - 1));
                errDoctors.add(info);
            }
            doctors.add(info);
        }
        ImportExcelInfo excelInfo = new ImportExcelInfo();
        excelInfo.setFileName(fileName);
        excelInfo.setTotal(doctors.size());
        excelInfo.setSuccess(doctors.size() - errDoctors.size());
        if (doctors.size()>0&&errDoctors.size() <= 0) {//不包含错误信息
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
            excelInfo.setExcelType(2);//医生注册
            ImportExcelInfoDAO importExcelInfoDAO = DAOFactory.getDAO(ImportExcelInfoDAO.class);
            excelInfo = importExcelInfoDAO.save(excelInfo);
            for (XlsDoctorInfo item : doctors) {
                item.setXlsId(excelInfo.getId());
                item.setStatus(0);
                save(item);
                // errPoints.add(save(item));
            }
        }
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("excelInfo", excelInfo);
        map.put("detail", errDoctors);
        return map;
    }


    private String valideDoctorRegistration(XlsDoctorInfo d) {
        if (d == null) {
            return null;
        }
        StringBuilder errorMessage = new StringBuilder("");
        if (StringUtils.isEmpty(d.getIdNumber())) {
            errorMessage.append("身份证信息有误;");
        }
        if (StringUtils.isEmpty(d.getName())) {
            errorMessage.append("姓名信息有误;");
        }
        if (ValidateUtil.nullOrZeroInteger(d.getOrganId())) {
            errorMessage.append("机构信息有误;");
        }
        if (d.getMobile() == null || StringUtils.isEmpty(d.getMobile().trim()) || !ValidateUtil.isMobile(d.getMobile())) {
            errorMessage.append("手机号信息有误;");
            // 手机号码不正确
        } else {
            List<UserRoles> urs = userRolesDAO.findByUserIdAndRoleId(d.getMobile(), "doctor");
            if (urs != null && urs.size() > 0) {
                errorMessage.append("已存在该用户名的用户;");
            }
        }
        if (StringUtils.isEmpty(d.getGender())) {
            errorMessage.append("性别信息有误;");
        }
        if (d.getUserType() == null) {
            errorMessage.append("用户类型信息有误;");
        }
        if (d.getBirthDay() == null) {
            errorMessage.append("生日信息有误;");
        }
        if (StringUtils.isEmpty(d.getProfession())) {
            errorMessage.append("专科信息有误;");
        }

        String idNumber = d.getIdNumber();
        if (idNumber == null || (idNumber.length() != 15 && idNumber.length() != 18)) {
            errorMessage.append("身份证号不合法;");
        } else {
            String sex = "";
            String idCard18;
            Date brithDay = null;
            try {
                idCard18 = ChinaIDNumberUtil.convert15To18(idNumber);
                sex = ChinaIDNumberUtil.getSexFromIDNumber(idCard18);
                brithDay = ChinaIDNumberUtil.getBirthFromIDNumber(idCard18);
            } catch (Exception e) {
                errorMessage.append("身份证号不合法;");
            }
            if (!sex.equals(d.getGender())) {
                errorMessage.append("输入的性别和身份证号获取的性别不一致;");
            }
            if (brithDay == null || !brithDay.equals(d.getBirthDay())) {
                errorMessage.append("输入的生日和身份证号获取的生日不一致;");
            }
        }
        if (d.getJobNumber() == null || StringUtils.isEmpty(d.getJobNumber().trim())) {
            errorMessage.append("工号信息有误;");
        }
        if (ValidateUtil.nullOrZeroInteger(d.getDepartment())) {
            errorMessage.append("科室信息有误;");
        }
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doctor = doctorDAO.getByMobile(d.getMobile());
        if (doctor != null) {
            errorMessage.append("该医生已存在，请勿重复添加;");
        }
        Doctor doctorIDNumber = doctorDAO.getByIdNumber(d.getIdNumber());
        if (doctorIDNumber != null) {
            errorMessage.append("该医生已存在，请勿重复添加;");
        }
        if (errorMessage.toString().length() > 0) {
            //验证失败时，用Introduce来作为错误信息变量。
            return errorMessage.toString();
        } else {
            return null;
        }
    }


    @DAOMethod(limit = 0)
    public abstract List<XlsDoctorInfo> findByXlsIdAndStatus(Integer xlsId, Integer status);

    public QueryResult<Object> queryDoctorPointXls(final Integer xlsId , final int start , final int limit){

        if(xlsId==null){
            throw new DAOException(DAOException.VALUE_NEEDED,"xlsId is require");
        }
        HibernateStatelessResultAction<QueryResult<Object>> action = new AbstractHibernateStatelessResultAction<QueryResult<Object>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(" from XlsDoctorInfo where xlsId="+xlsId);
                Query countQuery = ss.createQuery("select count(*) "+hql.toString());
                long total = (long) countQuery.uniqueResult();//获取总条数
                Query query = ss.createQuery(hql.toString()+" order by id asc");
                query.setMaxResults(limit);
                query.setFirstResult(start);
                List<Object> list = query.list();
                if(list==null){
                    list = new ArrayList<Object>();
                }
                setResult(new QueryResult<Object>(total,start,list.size(),list));
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);

        return action.getResult();
    }

}

