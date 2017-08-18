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
import ctd.schema.exception.ValidateException;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.base.dao.DoctorDAO;
import eh.base.dao.OrganDAO;
import eh.base.service.BusActionLogService;
import eh.entity.base.Doctor;
import eh.entity.base.Organ;
import eh.entity.xls.DoctorPointXls;
import eh.entity.xls.ImportExcelInfo;
import eh.op.service.DoctorOpService;
import eh.util.ChinaIDNumberUtil;
import org.apache.axis.utils.StringUtils;
import org.apache.commons.fileupload.FileItem;
import org.apache.log4j.Logger;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;

/**
 * @author jianghc
 * @create 2016-11-17 15:16
 **/
public abstract class DoctorPointXlsDAO extends HibernateSupportDelegateDAO<DoctorPointXls> {
    public static final Logger log = Logger.getLogger(DoctorPointXlsDAO.class);
    public DoctorPointXlsDAO() {
        super();
        this.setEntityName(DoctorPointXls.class.getName());
        this.setKeyField("id");
    }


    public Map readXlsToDoctorPointXls(FileItem file, String fileName, Long fileSize) {
        if(file==null){
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
            throw new DAOException("上传文件格式有问题，请使用Excel2003版本");
        }

        Sheet sheet = workbook.getSheetAt(0);
        Integer total = sheet.getLastRowNum();
        if (total == null || total <= 0) {
            throw new DAOException(DAOException.VALUE_NEEDED, "data is required");
        }

        List<DoctorPointXls> points = new ArrayList<DoctorPointXls>();
        List<DoctorPointXls> errPoints = new ArrayList<DoctorPointXls>();
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        for (int rowIndex = 1; rowIndex <= total; rowIndex++) {
            DoctorPointXls doctorPointXls = new DoctorPointXls();
            StringBuilder sbErrMsg = new StringBuilder();
            Row row = sheet.getRow(rowIndex);//循环获得每个行
            boolean flag = false;
            Cell cell_idCard = row.getCell(2);//身份证号
            if (cell_idCard != null) {
                String idCard = cell_idCard.getStringCellValue();
                if (idCard == null || StringUtils.isEmpty(idCard.trim())) {
                    sbErrMsg.append("身份证号缺失，");
                    flag = true;
                } else {
                    doctorPointXls.setIdCard(idCard.trim());
                    try {
                        ChinaIDNumberUtil.convert15To18(idCard);
                        Doctor doctor = doctorDAO.getByIdNumberAndStatus(idCard,1);
                        if (doctor == null) {
                            sbErrMsg.append("未找到该身份证号的医生，");
                            flag = true;
                        } else {
                            doctorPointXls.setDoctorName(doctor.getName());
                            doctorPointXls.setDoctorId(doctor.getDoctorId());
                            doctorPointXls.setOrganId(doctor.getOrgan());
                        }
                    } catch (ValidateException e) {
                        sbErrMsg.append("身份证号不合法，");
                        flag = true;
                    }
                }
            } else {
                sbErrMsg.append("身份证号缺失，");
                flag = true;
            }
            if (!flag) {//身份证不合法
                Cell cell_Organ = row.getCell(0);//所属机构
                if (cell_Organ != null) {
                    String organName = cell_Organ.getStringCellValue();
                    if (organName == null || StringUtils.isEmpty(organName.trim())) {
                        sbErrMsg.append("机构名称缺失，");
                        flag = true;
                    } else {
                        Organ organ = organDAO.getValidOrganByName(organName);
                        doctorPointXls.setOrganName(organName.trim());
                        if (organ == null) {
                            sbErrMsg.append("未找到该名称的机构，");
                            flag = true;
                        } else if (organ.getOrganId().intValue() != doctorPointXls.getOrganId().intValue()) {
                            sbErrMsg.append("该医生与机构不匹配，");
                            flag = true;
                        }
                    }
                } else {
                    sbErrMsg.append("机构名称缺失，");
                    flag = true;
                }
                Cell cell_doctor = row.getCell(1);//医生姓名
                if (cell_doctor != null) {
                    String docytorName = cell_doctor.getStringCellValue();
                    if (docytorName == null || StringUtils.isEmpty(docytorName.trim())) {
                        doctorPointXls.setDoctorName(null);
                        sbErrMsg.append("医生姓名缺失，");
                        flag = true;
                    } else if (!docytorName.trim().equals(doctorPointXls.getDoctorName().trim())) {
                        doctorPointXls.setDoctorName(docytorName.trim());
                        sbErrMsg.append("医生姓名有误，");
                        flag = true;
                    }
                } else {
                    doctorPointXls.setDoctorName(null);
                    sbErrMsg.append("医生姓名缺失，");
                    flag = true;
                }

                Cell cell_point = row.getCell(3);//医生积分
                if (cell_point != null) {
                    Double point = null;
                    if (cell_point.getCellType() == Cell.CELL_TYPE_NUMERIC) {
                        point = cell_point.getNumericCellValue();
                    } else {
                        try {
                            point = Double.parseDouble(cell_point.getStringCellValue());
                        } catch (NumberFormatException e) {
                            sbErrMsg.append("医生积分格式有误，");
                            flag = true;
                        }
                    }
                    if (!flag) {
                        if (point == null) {
                            sbErrMsg.append("医生积分缺失，");
                            flag = true;
                        } else {
                            try {
                                doctorPointXls.setInCome(new BigDecimal(point));
                            } catch (Exception e) {
                                sbErrMsg.append("医生积分格式有误，");
                                flag = true;
                            }
                        }
                    }
                } else {
                    sbErrMsg.append("医生积分缺失，");
                    flag = true;
                }
            }
            if (flag) {
                doctorPointXls.setOrganId(null);
                doctorPointXls.setDoctorId(null);
                doctorPointXls.setErrMsg("【第" + rowIndex + "行】" + sbErrMsg.substring(0, sbErrMsg.length() - 1));
                errPoints.add(doctorPointXls);
            }
            points.add(doctorPointXls);
        }
        ImportExcelInfo excelInfo = new ImportExcelInfo();
        excelInfo.setFileName(fileName);
        excelInfo.setTotal(points.size());
        excelInfo.setSuccess(points.size() - errPoints.size());

        if (errPoints.size() <= 0) {//不包含错误信息
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
            }catch (IOException e) {
                throw new DAOException("文件上传失败");
            }
            excelInfo.setOssId(meta.getFileId());
            excelInfo.setSuccess(0);
            excelInfo.setStatus(0);
            excelInfo.setExcelType(1);//医生积分
            ImportExcelInfoDAO importExcelInfoDAO = DAOFactory.getDAO(ImportExcelInfoDAO.class);
            excelInfo = importExcelInfoDAO.save(excelInfo);
            for (DoctorPointXls item : points) {
                item.setXlsId(excelInfo.getId());
                item.setStatus(0);
                save(item);
                // errPoints.add(save(item));
            }
        }
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("excelInfo", excelInfo);
        map.put("detail", errPoints);
        return map;
    }

    @DAOMethod(limit = 0)
    public abstract List<DoctorPointXls> findByXlsId(Integer xlsId);


    @RpcService
    public Map importDoctorPoint(Integer xlsId) {
        if (xlsId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "xlsId is required");
        }
        ImportExcelInfoDAO importExcelInfoDAO = DAOFactory.getDAO(ImportExcelInfoDAO.class);
        ImportExcelInfo excelInfo = importExcelInfoDAO.getById(xlsId);
        if (excelInfo == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "xlsId is not exist");
        }

        List<DoctorPointXls> list = findByXlsId(xlsId);
        if (list == null || list.size() <= 0) {
            throw new DAOException(DAOException.VALUE_NEEDED, "data is not exist");
        }
        DoctorOpService service = AppContextHolder.getBean("doctorOpService", DoctorOpService.class);
        List<DoctorPointXls> errXls = new ArrayList<DoctorPointXls>();
        for (DoctorPointXls xls : list) {
            xls.setStatus(1);
            try {
                service.addDoctorIncomeByDoctorIdForImport(xls.getDoctorId(), xls.getInCome(), xlsId);
            } catch (DAOException e) {
                xls.setStatus(-1);
                xls.setErrMsg(e.getMessage());
                errXls.add(xls);
                BusActionLogService.recordBusinessLog("批量添加医生积分", xls.getDoctorId() + "", "", "添加积分失败(错误信息：" + e.getMessage() + "),批次号：" + xlsId + "，给[" + xls.getOrganName() + "]的[" + xls.getDoctorName() + "](" + xls.getDoctorId() + ")医生增加" + xls.getInCome() + "积分");
            }
            update(xls);
        }
        UserRoleToken urt = UserRoleToken.getCurrent();
        excelInfo.setSuccess(excelInfo.getTotal() - errXls.size());
        excelInfo.setStatus(1);
        excelInfo.setExecuter(urt.getId());
        excelInfo.setExecuterName(urt.getUserName());
        excelInfo.setExecuteDate(new Date());
        excelInfo = importExcelInfoDAO.update(excelInfo);
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("excelInfo", excelInfo);
        map.put("detail", errXls);
        return map;
    }


    public QueryResult<Object> queryDoctorPointXls(final Integer xlsId ,final int start , final int limit){

        if(xlsId==null){
            throw new DAOException(DAOException.VALUE_NEEDED,"xlsId is require");
        }
        HibernateStatelessResultAction<QueryResult<Object>> action = new AbstractHibernateStatelessResultAction<QueryResult<Object>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(" from DoctorPointXls where xlsId="+xlsId);
                Query countQuery = ss.createQuery("select count(*) "+hql.toString());
                long total = (long) countQuery.uniqueResult();//获取总条数
                Query query = ss.createQuery(hql.toString()+" order by status asc,id asc");
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
