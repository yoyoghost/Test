package eh.esign;


import ctd.account.UserRoleToken;
import ctd.mvc.upload.FileMetaRecord;
import ctd.mvc.upload.FileService;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.dao.DoctorDAO;
import eh.base.dao.DoctorExtendDAO;
import eh.base.service.doctor.UpdateDoctorService;
import eh.bus.constant.MeetClinicConstant;
import eh.bus.dao.AppointRecordDAO;
import eh.bus.dao.MeetClinicDAO;
import eh.bus.dao.MeetClinicResultDAO;
import eh.bus.dao.TransferDAO;
import eh.cdr.dao.RecipeDAO;
import eh.entity.base.Doctor;
import eh.entity.base.DoctorExtend;
import eh.entity.bus.AppointRecord;
import eh.entity.bus.MeetClinic;
import eh.entity.bus.Transfer;
import eh.entity.cdr.EsignPerson;
import eh.remote.IESignService;
import eh.utils.MapValueUtil;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Created by zhongzx on 2016/12/1 0001.
 */
public class ESignService {

    private static IESignService eSignService = AppContextHolder.getBean("esign.esignService", IESignService.class);

    private static Logger log = LoggerFactory.getLogger(ESignService.class);

    /**
     * 给医生获取e签宝签名账号
     * @param doctorId
     * @return
     */
    public Map<String, String> getAccountAndSeal(Integer doctorId) {

        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doctor = doctorDAO.get(doctorId);
        if (null == doctor) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "[getAccount] doctorId 找不到改医生");
        }
        //E签宝账号
        String account = doctor.getESignId();
        if (StringUtils.isEmpty(account)) {
            EsignPerson person = new EsignPerson();
            person.setName(doctor.getName());
            person.setMobile(doctor.getMobile());
            person.setIdNumber(doctor.getIdNumber());
            try {
                account = eSignService.addPerson(person);
            } catch (Exception e) {
                log.error("doctorId=" + doctorId + "用户创建e签宝账号失败：" + e.getMessage());
                throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
            }
            doctor.setESignId(account);
            doctorDAO.update(doctor);
        }
        Map<String, String> map = new HashMap<>();
        map.put("account", account);
        DoctorExtendDAO doctorExtendDAO = DAOFactory.getDAO(DoctorExtendDAO.class);
        DoctorExtend doctorExtend = doctorExtendDAO.getByDoctorId(doctorId);
        if(null != doctorExtend) {
            map.put("sealData", doctorExtend.getSealData());
        }
        return map;
    }

    /**
     * 远程联合门诊单签名
     * @param paramMap
     * @return
     */
    @RpcService
    public Map<String, Object> signForTelClinic(Map<String, Object> paramMap) {
        Map<String, Object> resMap = new HashMap<>();
        boolean bl = true;
        Integer doctorId = MapValueUtil.getInteger(paramMap, "doctorId");
        String telClinicId = MapValueUtil.getString(paramMap, "telClinicId");
        AppointRecordDAO recordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
        AppointRecord record = recordDAO.getByTelClinicIdAndClinicObject(telClinicId, 2);
        if(null == record){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "telClinicId:" + telClinicId +"没有找到对应的远程门诊记录");
        }
        if (StringUtils.isEmpty(telClinicId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "telClinicId is needed");
        }
        if (null == doctorId) {
            throw new DAOException(DAOException.VALUE_NEEDED, "doctorId is needed");
        }
        Map<String, String> map = getAccountAndSeal(doctorId);
        String account = MapValueUtil.getString(map, "account");
        String sealData = MapValueUtil.getString(map, "sealData");
        byte[] data = null;
        String fileName = "telClinicId" + telClinicId + ".pdf";
        //签名操作
        try {
            paramMap.put("userId", account);
            paramMap.put("fileName", fileName);
            paramMap.put("sealData", sealData);
            data = eSignService.signForTelClinic(paramMap);
        } catch (Exception e) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }
        //上传阿里云
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        Integer fileId = recipeDAO.uploadRecipeFile(data, fileName);
        if (null == fileId) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "上传oss失败");
        }
        //签署文件保存到出诊方医生的记录里
        record.setFileId(fileId);
        recordDAO.update(record);
        log.info("[signForTelClinic] 签名成功 appointRecordId=[{}], fileId=[{}]", record.getAppointRecordId(), fileId);
        resMap.put("fileId", fileId);
        resMap.put("result", bl);
        return resMap;
    }

    /**对字节数组字符串进行Base64解码并生成图片
     * zhongzx
     * @param imgStr
     * @param doctorId
     * @return
     */
    public Integer generateImage(String imgStr, Integer doctorId) {
        UpdateDoctorService service = AppContextHolder.getBean("updateDoctorService", UpdateDoctorService.class);
        //前端传过来 是有编码头的
        imgStr = imgStr.substring(imgStr.lastIndexOf(",") + 1);
        try {
            //Base64解码
            byte[] b = Base64.decodeBase64(imgStr);
            for (int i = 0; i < b.length; ++i) {
                if (b[i] < 0) {//调整异常数据
                    b[i] += 256;
                }
            }
            //生成jpeg图片
            String fileName = doctorId + "-signImage" + ".jpg";
            File file = new File(fileName);
            OutputStream out = new FileOutputStream(file);
            out.write(b);
            out.flush();
            out.close();

            FileMetaRecord meta = new FileMetaRecord();
            UserRoleToken token = UserRoleToken.getCurrent();
            if(token == null){
                throw new DAOException(ErrorCode.SERVICE_ERROR, "userRoleToken is null");
            }
            meta.setManageUnit(token.getManageUnit());
            meta.setOwner(token.getUserId());
            meta.setLastModify(new Date());
            meta.setUploadTime(new Date());
            meta.setMode(0);
            meta.setCatalog("other-doc"); // 测试
            meta.setContentType("image/jpeg");
            meta.setFileName(fileName);
            meta.setFileSize(file.length());
            FileService.instance().upload(meta, file);
            file.delete();
            Integer fileId = meta.getFileId();
            service.updateSignImage(doctorId, fileId);
            return fileId;
        } catch (Exception e) {
            log.error("doctorId=[{}]医生的签章图片生成上传oss失败", doctorId);
            throw new DAOException(ErrorCode.SERVICE_ERROR, "生成签章失败");
        }
    }

    /**
     * 创建或者更新个人印章
     * @return
     */
    @RpcService
    public Map<String, Object> createOrUpdatePersonalSeal(Integer doctorId, String imgB64) {
        if (StringUtils.isEmpty(imgB64)) { //图像数据为空
            throw new DAOException(DAOException.VALUE_NEEDED, "签名内容不能为空");
        }
        Map<String, Object> resultInfo = new HashMap<>();
        boolean resultFlag = false;
        String sealData = imgB64.substring(imgB64.lastIndexOf(",") + 1);
        if(StringUtils.isNotEmpty(sealData)){
            DoctorExtendDAO doctorExtendDAO = DAOFactory.getDAO(DoctorExtendDAO.class);
            //把img64转成图片文件存到oss 供前段调用 签章图片Id更新到医生表
            Integer fileId = generateImage(imgB64, doctorId);
            if(null != fileId && fileId > 0){
                resultFlag = true;
                resultInfo.put("fileId", fileId);
                //sealData更新到医生扩展表
                doctorExtendDAO.saveOrUpdateSealData(sealData, doctorId);
                log.info("医生 doctorId=" + doctorId + " 创建个人印章成功");
            }
        }
        resultInfo.put("resultFlag", resultFlag);
        return resultInfo;
    }


    /**
     * 会诊单签名
     * @param paramMap
     * @return
     */
    @RpcService
    public Map<String, Object> signForMeetClinic(Map<String, Object> paramMap){
        List<Map<String, Object>> detailInfoList = MapValueUtil.getList(paramMap, "detailInfoList");
        if(null == detailInfoList || 0 == detailInfoList.size()){
            throw new DAOException(DAOException.VALUE_NEEDED, "detailInfoList is null or size == 0");
        }
        Integer meetClinicId = MapValueUtil.getInteger(paramMap, "meetClinicId");
        MeetClinicDAO meetClinicDAO = DAOFactory.getDAO(MeetClinicDAO.class);
        MeetClinic meetClinic = meetClinicDAO.get(meetClinicId);
        if(null == meetClinic){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "meetClinicId 找不到会诊单");
        }
        Integer meetClinicResultId = MapValueUtil.getInteger(paramMap, "meetClinicResultId");
        if (meetClinicResultId != null && meetClinicResultId > 0) {
            MeetClinicResultDAO resultDAO = DAOFactory.getDAO(MeetClinicResultDAO.class);
            Integer effeStatus = resultDAO.getEffectiveStatusByMeetClinicResultId(meetClinicResultId);
            if (effeStatus != null && MeetClinicConstant.EFFECTIVESTATUS_INVALID.equals(effeStatus)) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "抱歉，您已被移出该会诊~");
            }
        }
        Map<String, Object> resMap = new HashMap<>();
        boolean bl = true;
        for (Map<String, Object> detailInfo : detailInfoList) {
            Integer doctorId = MapValueUtil.getInteger(detailInfo, "doctorId");
            Map<String, String> map = getAccountAndSeal(doctorId);
            String account = MapValueUtil.getString(map, "account");
            String sealData = MapValueUtil.getString(map, "sealData");
            detailInfo.put("sealData", sealData);
            detailInfo.put("account", account);
        }
        byte[] data = null;
        String fileName = "meetClinic" + meetClinicId + ".pdf";
        try {
            data = eSignService.signForMeetClinic(paramMap);
        } catch (Exception e) {
            log.error("[signForMeetClinic] error:" + e.getMessage());
            throw new DAOException(ErrorCode.SERVICE_ERROR, "会诊单签署失败");
        }
        //上传阿里云
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        Integer fileId = recipeDAO.uploadRecipeFile(data, fileName);
        if (null == fileId) {
            log.error("[signForMeetClinic] 签名文件上传阿里云返回 文件Id 为 null");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "上传oss失败");
        }
        //签署文件保存到会诊单
        meetClinic.setFileId(fileId);
        meetClinicDAO.update(meetClinic);
        log.info("[signForMeetClinic] 签名成功 meetClinicId = [{}], fileId = [{}]", meetClinicId, fileId);
        resMap.put("fileId", fileId);
        resMap.put("result", bl);
        return resMap;
    }

    /**
     * 生成转诊单pdf
     * @param map
     * @return
     */
    @RpcService
    public Map<String, Object> generateTransferPdf(Map<String, Object> map){
        Integer transferId = MapValueUtil.getInteger(map, "transferId");
        if(null == transferId){
            throw new DAOException(DAOException.VALUE_NEEDED, "transferId is needed");
        }
        TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
        Transfer transfer = transferDAO.getById(transferId);
        if(null == transfer){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "找不到对应的转诊单");
        }
        Integer fileId = transfer.getFileId();
        boolean bl = true;
        Map<String, Object> resMap = new HashMap<>();
        if(null != fileId){
            resMap.put("fileId", fileId);
        }else{
            byte[] data = null;
            String fileName = "transfer" + transfer + ".pdf";
            try {
                data = eSignService.createTransferPdf(map);
            } catch (Exception e){
                log.error("生成pdf失败 error = [{}]", e.getMessage());
                throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
            }
            //上传阿里云
            RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
            fileId = recipeDAO.uploadRecipeFile(data, fileName);
            if (null == fileId) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "上传oss失败");
            }
            transfer.setFileId(fileId);
            transferDAO.update(transfer);
            resMap.put("fileId", fileId);
        }
        resMap.put("result", bl);
        return resMap;
    }

    /**
     * 把有个性签章的医生的 旧的个性签章信息 更新成新的签章信息 保存到扩展表里
     * zhongzx
     * @return
     */
    @RpcService
    public Map<String, Object> updateSealDataFromOldForDoctor(List<Integer> doctorIds){
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        FileService fileService = AppContextHolder.getBean("fileService", FileService.class);
        List<Doctor> doctorList = doctorDAO.findDoctorListHasSignImage(doctorIds);
        Map<String, Object> resMap = new HashMap<>();
        resMap.put("total", doctorList.size());
        int i=0;
        for(Doctor doctor:doctorList){
            InputStream is = null;
            BufferedInputStream bis = null;
            ByteArrayOutputStream out = null;
            byte[] byteData = null;
            try {
                FileMetaRecord fileMetaRecord = fileService.getRegistry().load(doctor.getSignImage());
                if(null != fileMetaRecord) {
                    is = fileService.getRepository().readAsStream(fileMetaRecord);
                    bis = new BufferedInputStream(is);
                }else{
                    log.error("fileMetaRecord is null, fileId=[{}]", doctor.getSignImage());
                }
                if(null != bis){
                    byte[] byteArray = new byte[1024];
                    int len = 0;
                    out = new ByteArrayOutputStream();
                    while((len=bis.read(byteArray))!=-1){
                        out.write(byteArray, 0, len);
                    }
                    byteData = out.toByteArray();
                    String sealData = Base64.encodeBase64String(byteData);
                    DoctorExtendDAO doctorExtendDAO = DAOFactory.getDAO(DoctorExtendDAO.class);
                    doctorExtendDAO.saveOrUpdateSealData(sealData, doctor.getDoctorId());
                    i++;
                }
            }catch (Exception e){
                log.error("从oss上下载个性签章图片转成base64数据失败 fileId=[{}], reason=[{}]", doctor.getSignImage(), e.getMessage());
            } finally {
                if (null != bis) {
                    try {
                        bis.close();
                    } catch (IOException e) {
                        log.error("bis close fail:[{}]", e.getMessage());
                    }
                }
                if (null != is) {
                    try {
                        is.close();
                    } catch (IOException e) {
                        log.error("is close fail:[{}]", e.getMessage());
                    }
                }
                if (null != out) {
                    try {
                        out.close();
                    } catch (IOException e) {
                        log.error("out close fail:[{}]", e.getMessage());
                    }
                }
            }
        }
        resMap.put("complete", i);
        return resMap;
    }
}
