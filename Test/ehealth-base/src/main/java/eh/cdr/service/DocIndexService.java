package eh.cdr.service;

import ctd.account.UserRoleToken;
import ctd.account.user.UserRoleTokenEntity;
import ctd.mvc.upload.FileMetaRecord;
import ctd.mvc.upload.FileService;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.dao.OrganDAO;
import eh.base.service.UrlResourceService;
import eh.cdr.constant.DocClassConstant;
import eh.cdr.constant.DocIndexConstant;
import eh.cdr.constant.OtherdocConstant;
import eh.cdr.dao.DocIndexDAO;
import eh.cdr.dao.OtherDocDAO;
import eh.entity.base.Doctor;
import eh.entity.cdr.DocIndex;
import eh.entity.cdr.Otherdoc;
import eh.entity.his.push.callNum.PushRequestModel;
import eh.entity.his.push.callNum.PushResponseModel;
import eh.mpi.dao.PatientDAO;
import eh.utils.DateConversion;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.*;

/**
 * Created by w on 2016/5/11.
 */
public class DocIndexService {
    private static final Log logger = LogFactory.getLog(DocIndexService.class);

    /**
     * 根据文档索引id获取资源访问地址
     * @param docIndexId 文档索引id
     * @return 资源url地址
     */
    @RpcService
    public  String getDocContentUrl(int docIndexId){
        String url="";
        DocIndexDAO dao=DAOFactory.getDAO(DocIndexDAO.class);

        DocIndex docIndex=dao.get(docIndexId);
        if(docIndex==null){
            return "";
        }
        int docClass=docIndex.getDocClass();
        //根据class 获取资源访问地址
        switch (docClass){
            case DocClassConstant.WINNING_PACS:
                url=getPacsUrl(docIndex);
                break;
            case DocClassConstant.OTHER_DOC:
                url=getOtherDocUrl(docIndex);
                break;
            default:
                url="";
        }
        return url;
    }
    @RpcService
    public String getPacsUrl(DocIndex docIndex){
        return UrlResourceService.getUrlByName("pacsUrl")+docIndex.getOrganDocId();
    }
    @RpcService
    public String getOtherDocUrl(DocIndex docIndex){
        OtherDocDAO otherDocDAO=DAOFactory.getDAO(OtherDocDAO.class);
        Otherdoc doc=otherDocDAO.get(docIndex.getDocId());
        return UrlResourceService.getUrlByName("imgUrl")+doc.getDocContent();
    }

    /**
     * 删除医生自己在电子病历页面上传的文件
     * @param indexIds
     * @return
     */
    @RpcService
    public Integer delSelfDocIndex(List<Integer> indexIds){
        Integer returnNum=0;

        DocIndexDAO indexDAO = DAOFactory.getDAO(DocIndexDAO.class);

        UserRoleTokenEntity ure = (UserRoleTokenEntity) UserRoleToken.getCurrent();
        if(null==ure){
            throw new DAOException(ErrorCode.SERVICE_ERROR,"用户未登陆");
        }

        Doctor doctor = (Doctor) ure.getProperty("doctor");
        Integer doctorId=doctor.getDoctorId();

        for(Integer id:indexIds){
            DocIndex index=indexDAO.get(id);
            Integer createDoctorId=index.getCreateDoctor();

            //文档可删除，且文档是操作医生上传，将记录标记为删除
            if(createDoctorId!=null && DocIndexConstant.DOC_FLAG_CAN_DEL.equals(index.getDocFlag()) && createDoctorId.compareTo(doctorId)==0){
                index.setDocStatus(DocIndexConstant.DOC_STATUS_HAS_DEL);
                index.setLastModify(new Date());
                indexDAO.update(index);
                returnNum++;
            }
        }

        return returnNum;
    }

    /**
     * @function 按病人、文档类型查询分页列表,按时间排序(app版)
     * @author zhangx
     * @param mpiId
     *            病人主索引
     * @param docType
     *            文档类型 可为null，即可查询全部类型
     * @param start
     *            从0开始
     * @param limit
     *            每页最大记录数
     * @date 2015-10-30
     * @return List<DocIndex>
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @RpcService
    public List<Map<String, Object>> findByMpiIdAndDocTypeWithPage(final String mpiId,
                                                       final String docType, final int start, final int limit) {
        if (StringUtils.isEmpty(mpiId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "病人主索引不能为空!");
        }

        UserRoleTokenEntity ure = (UserRoleTokenEntity) UserRoleToken.getCurrent();
        if(null==ure){
            throw new DAOException(ErrorCode.SERVICE_ERROR,"用户未登陆");
        }

        Doctor doctor = (Doctor) ure.getProperty("doctor");
        Integer doctorId=doctor.getDoctorId();

        DocIndexDAO indexDAO = DAOFactory.getDAO(DocIndexDAO.class);
        List<DocIndex> list = indexDAO.findDocListByMpiAndDocType(mpiId,docType, start,limit);

        MMap<DocIndex> mMap=new MMap<DocIndex>();
        for(DocIndex index:list){
            Integer createDoctorId=index.getCreateDoctor();
            //2017-5-19 14:15:11 wx2.9版本新增健康档案，患者可自主上传电子病历，自主上传的电子病历除患者本人外不可删除
            if(createDoctorId==null && DocIndexConstant.DOC_FLAG_CAN_DEL.equals(index.getDocFlag())){
                index.setDocFlag(DocIndexConstant.DOC_FLAG_CANNOT_DEL);
            }

            //文档可删除，且文档不是查询医生上传，将是否删除标记置为不可删除
            if(createDoctorId!=null && DocIndexConstant.DOC_FLAG_CAN_DEL.equals(index.getDocFlag()) && createDoctorId.compareTo(doctorId)!=0){
                index.setDocFlag(DocIndexConstant.DOC_FLAG_CANNOT_DEL);
            }
            //将记录按指定格式输出
            mMap.put(DateConversion.getDateFormatter(index.getCreateDate(),"yyyy-MM-dd"),getIndex(index));
        }
        return mMap.getList();
    }

    /**
     * @function 按病人、文档类型查询分页列表,按时间排序(PC版)
     * @author zhangjr yaozh
     * @param mpiId
     *            病人主索引
     * @param docType
     *            文档类型 可为null，即可查询全部类型
     * @param start
     *            从0开始
     * @param limit
     *            每页最大记录数
     * @date 2015-11-04
     * @return HashMap<String,Object>
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @RpcService
    public HashMap<String, Object> findByMpiIdAndDocTypeWithPageForPc(
            final String mpiId, final String docType, final int start,
            final int limit) {

        if (StringUtils.isEmpty(mpiId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "病人主索引不能为空!");
        }

        UserRoleTokenEntity ure = (UserRoleTokenEntity) UserRoleToken.getCurrent();
        if(null==ure){
            throw new DAOException(ErrorCode.SERVICE_ERROR,"用户未登陆");
        }

        Doctor doctor = (Doctor) ure.getProperty("doctor");
        Integer doctorId=doctor.getDoctorId();

        DocIndexDAO indexDAO = DAOFactory.getDAO(DocIndexDAO.class);
        List<DocIndex> list = indexDAO.findDocListByMpiAndDocType(mpiId,docType, start,limit);

        MMap<DocIndex> mMap=new MMap<DocIndex>();
        for(DocIndex index:list){
            Integer createDoctorId=index.getCreateDoctor();
            //2017-5-19 14:15:11 wx2.9版本新增健康档案，患者可自主上传电子病历，自主上传的电子病历除患者本人外不可删除
            if(createDoctorId==null && DocIndexConstant.DOC_FLAG_CAN_DEL.equals(index.getDocFlag())){
                index.setDocFlag(DocIndexConstant.DOC_FLAG_CANNOT_DEL);
            }

            //文档可删除，且文档不是查询医生上传，将是否删除标记置为不可删除
            if(createDoctorId!=null && DocIndexConstant.DOC_FLAG_CAN_DEL.equals(index.getDocFlag()) && createDoctorId.compareTo(doctorId)!=0){
                index.setDocFlag(DocIndexConstant.DOC_FLAG_CANNOT_DEL);
            }
            //将记录按指定格式输出
            mMap.put(DateConversion.getDateFormatter(index.getCreateDate(),"yyyy-MM-dd"),getIndex(index));
        }

        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("recordCount", list.size());
        if (list.size() > 0) {
            map.put("recordList", mMap.getList());
        } else {
            map.put("recordList", null);
        }

        return map;

    }



    /**
     * 获取新的数据
     * @param doc
     * @return
     */
    private DocIndex getIndex(DocIndex doc){
        switch (doc.getDocClass()){
            case DocClassConstant.WINNING_PACS:
                doc.setUrl(getPacsUrl(doc));
                doc.setUrlType(DocIndexConstant.URL_TYPE_HTML);
                break;
            case DocClassConstant.OTHER_DOC:
                doc.setUrl(getOtherDocUrl(doc));
                doc.setUrlType(DocIndexConstant.URL_TYPE_IMG);
                break;
            case DocClassConstant.PDF_DOC:
                doc.setUrl(getOtherDocUrl(doc));//前端获取pdf文件路径同图片
                doc.setUrlType(DocIndexConstant.URL_TYPE_PDF);
                break;
            default:
                doc.setUrl("");
                doc.setUrlType(DocIndexConstant.URL_TYPE_IMG);
        }
        return doc;
    }

    /**
     * 将搜索出来的数据按规则进行组装
     * @param <T>
     */
    static class MMap<T>{
        private List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();

        public void put(String key, T t){
            boolean exists = false;
            for(Map<String, Object> map : list){
                if(map.get("createDate").equals(key)){
                    ((List<T>)map.get("docList")).add(t);
                    exists = true;
                }
            }
            if(!exists){
                Map<String, Object> map = new HashMap<String, Object>();
                List<T> subList = new ArrayList<T>();
                subList.add(t);
                map.put("createDate", key);
                map.put("docList", subList);
                list.add(map);
            }
        }

        public List<Map<String, Object>> getList() {
            return list;
        }
    }

    @RpcService
    public PushResponseModel uploadCDRFile(PushRequestModel req){
        logger.info("开始处理pdf。。。");
        PushResponseModel responseModel = new PushResponseModel();
        try {
            Object dataObj = req.getData();
            if(dataObj==null){
                return responseModel.setError("-1","dataObj id null");
            }
            Map dataMap = (Map)dataObj;

            //文件名称
            String fileName = dataMap.get("fileName").toString();
            //his传过来的文件byte
            byte[] data = (byte[]) dataMap.get("fileByte");
//            String  certID = (String) dataMap.get("certID");
//            int  organID = (int) dataMap.get("organID");
//            dataMap.get("rePortDepartName");
//            dataMap.get("rePortDoctorName");
            String cdrID = (String)dataMap.get("cdrID");
            // 校验文件重复
            logger.info("校验文件重复。。。");
            DocIndexDAO docIndexService = DAOFactory.getDAO(DocIndexDAO.class);
            boolean isExist = docIndexService.isFileExist(req);
            if(isExist){
                logger.info("file is Exist "+cdrID);
                return responseModel.setSuccess("已存在");
            }
            logger.info("开始文件上传。。。");
            Integer fileID = uploadFile(data, fileName);
            if(fileID==null){
                return responseModel.setError("-1","上传服文件务器失败");
            }
            logger.info("upload File success "+fileName +"  fileID:"+fileID);
            responseModel =  docIndexService.saveDocIndexForCdrFile(fileID,req);
            return  responseModel;
        }catch (Exception e){
            return responseModel.setError("-1","异常"+e.getMessage());

        }
    }

    public Integer uploadFile(byte[] data, String fileName) {
        if(null == data){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "byte[] 为空");
        }
        OutputStream fileOutputStream = null;
        File file = null;
        try{
            //先生成本地文件
            file = new File(fileName);
            fileOutputStream = new FileOutputStream(file);
            if (data.length > 0) {
                fileOutputStream.write(data, 0, data.length);
                fileOutputStream.flush();
                fileOutputStream.close();
            }

            FileMetaRecord meta = new FileMetaRecord();
//            UserRoleToken token = UserRoleToken.getCurrent();
//            meta.setManageUnit(token.getManageUnit());
//            meta.setOwner(token.getUserId());
            meta.setLastModify(new Date());
            meta.setUploadTime(new Date());
            meta.setMode(0);
            meta.setCatalog("other-doc"); // 测试
            meta.setContentType("application/pdf");
            meta.setFileName(fileName);
            meta.setFileSize(file.length());
            FileService.instance().upload(meta, file);
            file.delete();
            return meta.getFileId();
        }catch (Exception e){
//            logger.error("uploadFile exception:"+e.getMessage());
        }
        return null;
    }



    /**
     * 健康管家根据病历id获取病历详情
     */
    @RpcService
    public DocIndex getPatientDocIndexById(int docIndexId) {
        DocIndexDAO docIndexDAO = DAOFactory.getDAO(DocIndexDAO.class);
        OtherDocDAO otherDocDAO = DAOFactory.getDAO(OtherDocDAO.class);
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        //根据病历id获取病历详情
        DocIndex docIndex = docIndexDAO.get(docIndexId);
        if (docIndex == null) {
            throw new DAOException(609, "数据不存在");
        }
        if(docIndex.getOrganNameByUser()==null&&docIndex.getCreateOrgan()!=null){
           docIndex.setOrganNameByUser(organDAO.getNameById(docIndex.getCreateOrgan()));
        }
        docIndex.setUrl(getDocContentUrl(docIndex.getDocIndexId()));
        docIndex.setClinicPersonName(patientDAO.getNameByMpiId(docIndex.getMpiid()));
        //不是患者本人上传的健康档案不可删除
        if ((docIndex.getDocId()!=null)&&otherDocDAO.get(docIndex.getDocId()).getClinicType() != OtherdocConstant.CLINIC_TYPE_UPLOAD_USER) {
            docIndex.setDocFlag(DocIndexConstant.DOC_FLAG_CANNOT_DEL);

        }
        return docIndex;
    }

    /**
     * 健康管家患者删除自己上传病历
     */
    @RpcService
    public int delPatientSelfDocIndex(List<Integer> ids) {
        int delNum = 0;
        DocIndexDAO docIndexDAO = DAOFactory.getDAO(DocIndexDAO.class);
        OtherDocDAO otherDocDAO = DAOFactory.getDAO(OtherDocDAO.class);
        for (Integer id : ids) {
            DocIndex docIndex = docIndexDAO.get(id);
            //没有删除且可以删除的时候删除
            if (docIndex!=null&&docIndex.getDocStatus() == DocIndexConstant.DOC_STATUS_NO_DEL
                    && docIndex.getDocFlag() == DocIndexConstant.DOC_FLAG_CAN_DEL) {
                docIndex.setDocStatus(DocIndexConstant.DOC_STATUS_HAS_DEL);
                docIndex.setLastModify(new Date());
                docIndexDAO.update(docIndex);
            }
            delNum++;
        }

        return delNum;
    }

    /**
     * 健康管家查看患者病历列表
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @RpcService
    public List<Map<String, Object>> findByMpiIdAndDocTypeWithPageForPatient(
            final String mpiId, final String docType, final int start, final int limit) {

        if (StringUtils.isEmpty(mpiId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "病人主索引不能为空!");
        }
        DocIndexDAO indexDAO = DAOFactory.getDAO(DocIndexDAO.class);
        OtherDocDAO otherDocDAO = DAOFactory.getDAO(OtherDocDAO.class);
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        //list返回病历列表(只显示患者上传的健康档案和医生上传的，所有删除的也均显示)
        List<DocIndex> list = indexDAO.findHealthProfileDocListByMpiAndDocType(mpiId, docType, start, limit);
        MMap<DocIndex> mMap = new MMap<DocIndex>();
        for (DocIndex index : list) {
            //不是患者自己上传的健康档案病历不能删除
            if (index.getDocId()!=null && otherDocDAO.get(index.getDocId()).getClinicType() != OtherdocConstant.CLINIC_TYPE_UPLOAD_USER) {
                index.setDocFlag(DocIndexConstant.DOC_FLAG_CANNOT_DEL);
            }
            //显示患者端医生上传的病例时，就诊医院名称为创建医院名称。前端显示就诊医院的字段为OrganNameByUser
            if(index.getOrganNameByUser()==null&&index.getCreateOrgan()!=null){
                String createOrganText = organDAO.getNameById(index.getCreateOrgan());
                index.setOrganNameByUser(createOrganText);
            }
            //将记录按指定格式输出
            if(index.getDocId()!=null){
                mMap.put(DateConversion.getDateFormatter(index.getCreateDate(), "yyyy-MM-dd"), getIndex(index));
            }

        }
        return mMap.getList();
    }

}
