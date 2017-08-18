package eh.op.service;

import ctd.account.UserRoleToken;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.base.dao.DoctorDAO;
import eh.base.service.BusActionLogService;
import eh.entity.base.Doctor;
import eh.entity.xls.ImportExcelInfo;
import eh.entity.xls.XlsDoctorInfo;
import eh.op.dao.ImportExcelInfoDAO;
import eh.op.dao.XlsDoctorInfoDAO;

import java.util.*;

/**
 * @author jianghc
 * @create 2016-12-02 10:22
 **/
public class XlsDoctorInfoService {

    private byte[] mLock = new byte[0];
    @RpcService
    public Map createDoctorByExcel(Integer xlsId) {
        if (xlsId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, " xlsId is require");
        }
        ImportExcelInfoDAO importExcelInfoDAO = DAOFactory.getDAO(ImportExcelInfoDAO.class);
        ImportExcelInfo excelInfo = importExcelInfoDAO.getById(xlsId);
        if (excelInfo == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "xlsId is not exist");
        }
        if(excelInfo.getStatus().intValue()!=0){
            throw new DAOException("Excel已导入或已取消");
        }
        excelInfo.setStatus(1);
        importExcelInfoDAO.update(excelInfo);
        XlsDoctorInfoDAO xlsDoctorInfoDAO = DAOFactory.getDAO(XlsDoctorInfoDAO.class);
        List<XlsDoctorInfo> xlss = xlsDoctorInfoDAO.findByXlsIdAndStatus(xlsId, 0);
        if (xlss == null) {
            throw new DAOException(" data is not found ");
        }
        DoctorOpService doctorOpService = AppContextHolder.getBean("doctorOpService", DoctorOpService.class);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        List<XlsDoctorInfo> errInfo = new ArrayList<XlsDoctorInfo>();
        for (XlsDoctorInfo info : xlss) {
            Doctor doc = getDoctorByXlsDoctorInfo(info);
            info.setStatus(1);
            try {
                Integer docId =0;
                synchronized(mLock){
                    docId = doctorOpService.importDoctor(doc, info.getDepartment(), info.getJobNumber());
                }
                String IDCard = doc.getIdNumber();
                doctorDAO.createDoctorUser(docId,IDCard.substring(IDCard.length()-6,IDCard.length()));//开户，默认密码为身份证后6位
                info.setDoctorId(docId);

            } catch (Exception e) {
                info.setStatus(-1);
                info.setErrMsg(e.getMessage());
            }
            XlsDoctorInfo rInfo = xlsDoctorInfoDAO.update(info);
            if (info.getStatus() < 0) {
                errInfo.add(info);
            }
        }
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
        BusActionLogService.recordBusinessLog("医生批量导入", excelInfo.getId()+"", "ImportExcelInfo",
                "导入医生"+excelInfo.getTotal()+"条，成功"+excelInfo.getSuccess()+"条。");
        return map;
    }


    private Doctor getDoctorByXlsDoctorInfo(XlsDoctorInfo info) {
        Doctor doctor = new Doctor();
        doctor.setOrgan(info.getOrganId());
        doctor.setName(info.getName());
        doctor.setGender(info.getGender());
        doctor.setUserType(info.getUserType());
        doctor.setBirthDay(info.getBirthDay());
        doctor.setIdNumber(info.getIdNumber());
        doctor.setProfession(info.getProfession());
        doctor.setMobile(info.getMobile());
        doctor.setEmail(info.getEmail());
        doctor.setWeiXin(info.getWeChat());
        doctor.setIntroduce(info.getIntroduce());
        doctor.setDomain(info.getDomain());
        doctor.setHonour(info.getHonour());
        doctor.setSpecificSign(info.getSpecificSign());
        doctor.setProTitle(info.getProTitle());
        doctor.setJobTitle(info.getJobTitle());
        doctor.setEducation(info.getEducation());
        doctor.setGroupType(0);
        doctor.setSource(2);//来源为批量导入
        return doctor;
    }

}
