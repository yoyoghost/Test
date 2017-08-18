package eh.base.service;

import com.alibaba.druid.util.StringUtils;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import ctd.util.event.GlobalEventExecFactory;
import eh.base.constant.ErrorCode;
import eh.base.constant.QRInfoConstant;
import eh.base.dao.DoctorDAO;
import eh.base.dao.MaterialDAO;
import eh.base.dao.QRInfoDAO;
import eh.entity.base.Doctor;
import eh.entity.base.Material;
import eh.entity.base.QRInfo;
import eh.entity.wx.WXConfig;
import eh.op.dao.WXConfigsDAO;
import eh.remote.IWXServiceInterface;
import eh.util.UploadFile;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Date;
import java.util.HashMap;
import java.util.List;


public class QRInfoService {
    private static final Log logger = LogFactory.getLog(QRInfoService.class);
    private static final QRInfoDAO qrDao = DAOFactory.getDAO(QRInfoDAO.class);

    /**
     * 获取相关公众号上的医生二维码
     * @param doctorId
     * @param appId
     */
    public QRInfo getDocQRInfo(Integer doctorId,String appId){
        if(org.apache.commons.lang3.StringUtils.isEmpty(appId)){
            logger.error("公众号["+appId+"]为空，无法获取二维码");
            return null;
        }

        WXConfigsDAO  wxconfigDao=DAOFactory.getDAO(WXConfigsDAO.class);
        WXConfig wxConfig = wxconfigDao.getByAppID(appId);
        Integer wxconfigId=null;

        if (wxConfig!=null) {
            wxconfigId=wxConfig.getId();
        }

        if(wxconfigId==null){
            logger.error("公众号["+appId+"]未配置wxconfig，无法获取二维码");
            return null;
        }

        String qrInfo=doctorId+"";
        Integer qrType=QRInfoConstant.QRTYPE_DOCTOR;

        QRInfo info=null;

        List<QRInfo> infos=qrDao.findQRInfo(wxconfigId,qrInfo,qrType);
        if(infos.size()>0){
            info=infos.get(0);
        }
        return info;

    }

    /**
     * 更换公众号配置的时候，将相关的已上传的二维码设置为失效
     * @param wxconfigId
     */
    @RpcService
    public void invalidQRInfosByWxConfigId(Integer wxconfigId){
        qrDao.updateQRInfoByWxConfigId(wxconfigId);
        logger.info("更新二维码信息记录为无效-"+wxconfigId);
    }

    /**
     * 查询微信有效物料二维码
     * @param wxconfigId
     */
    @RpcService
    public List<QRInfo> findMaterialQRInfo(Integer wxconfigId){
       return qrDao.findMaterialQRInfo(wxconfigId);
    }

    /**
     * 生成医生二维码
     * @param doctorId
     * @param appId
     */
    public void createDocQRInfo(Integer doctorId,String appId){
        logger.info("生成公众号医生二维码doctorId="+doctorId+",appId="+appId);
        if(org.apache.commons.lang3.StringUtils.isEmpty(appId)){
            logger.error("公众号["+appId+"]为空，无法生成二维码");
            return;
        }

        WXConfigsDAO  wxconfigDao=DAOFactory.getDAO(WXConfigsDAO.class);
        WXConfig wxConfig = wxconfigDao.getByAppID(appId);
        Integer wxconfigId=null;

        if (wxConfig!=null) {
            wxconfigId=wxConfig.getId();
        }

        if(wxconfigId==null){
           logger.error("公众号["+appId+"]未配置wxconfig，无法生成二维码");
            return;
        }

        String qrInfo=doctorId+"";
        Integer qrType=QRInfoConstant.QRTYPE_DOCTOR;
        List<QRInfo> infos=qrDao.findQRInfo(wxconfigId,qrInfo,qrType);

        //考虑到患者端分享页面有好几步，不会再短时间内完成，采用异步执行生成二维码并上传，提高效率
        if(infos.size()==0){
            createQRInfoByType(qrInfo,appId,wxconfigId,qrType,QRInfoConstant.ASYNCFALG_YES);
        }

    }

    /**
     * 同步生成物料[易拉宝]二维码
     * @param materialId
     * @param wxConfigId
     */
    @RpcService
    public Integer  createMaterialQRInfo(Integer materialId,Integer wxConfigId){
        //查询物料信息
        MaterialDAO materialDAO=DAOFactory.getDAO(MaterialDAO.class);
        Material material=materialDAO.get(materialId);
        if(material==null || material.getMaterialStatus().intValue()==0){
            logger.info("物料["+materialId+"]未配置，或配置无效,无法生成二维码");
            throw new DAOException(ErrorCode.SERVICE_ERROR,"物料未配置，或配置无效,无法生成二维码");
        }

        //查询公众号信息
        WXConfigsDAO wxConfigsDAO=DAOFactory.getDAO(WXConfigsDAO.class);
        WXConfig wxConfig=wxConfigsDAO.get(wxConfigId);
        if(wxConfig==null){
            logger.info("公众号["+wxConfigId+"]未配置，或配置无效,无法生成二维码");
            throw new DAOException(ErrorCode.SERVICE_ERROR,"公众号未配置，或配置无效,无法生成二维码");
        }
        String appId=wxConfig.getAppID();
        if(StringUtils.isEmpty(appId)){
            logger.info("公众号["+appId+"]配置无效,无法生成二维码");
            throw new DAOException(ErrorCode.SERVICE_ERROR,"公众号未配置，或配置无效,无法生成二维码");
        }

        Integer qrType=QRInfoConstant.QRTYPE_MATERIA;
        String sceneStr=qrType+"_"+materialId;

        Integer fileId=null;

        //公众号[物料]二维码实时生成
        List<QRInfo> infos=qrDao.findQRInfo(wxConfigId,sceneStr,qrType);
        if(infos.size()==0){
            fileId=createQRInfoByType(sceneStr,appId,wxConfigId,qrType,QRInfoConstant.ASYNCFALG_NO);
        }else{
            QRInfo qr=infos.get(0);
            fileId=qr.getQrCode();
        }
        return fileId;
    }

    /**
     * 同步生成公众号二维码
     * @param materialId
     * @param wxConfigId
     */
    @RpcService
    public Integer  createPublicQRInfo(String appId){
        logger.info("生成公众号二维码appId="+appId);
        if(org.apache.commons.lang3.StringUtils.isEmpty(appId)){
            logger.error("公众号["+appId+"]为空，无法生成二维码");
            return 0;
        }

        WXConfigsDAO  wxconfigDao=DAOFactory.getDAO(WXConfigsDAO.class);
        WXConfig wxConfig = wxconfigDao.getByAppID(appId);
        Integer wxConfigId=null;

        if (wxConfig!=null) {
            wxConfigId=wxConfig.getId();
        }

        if(wxConfigId==null){
            logger.error("公众号["+appId+"]未配置wxconfig，无法生成二维码");
            return 0;
        }

        //自定义二维码，type+主键
        Integer qrType=QRInfoConstant.QRTYPE_PUBLIC;
        String sceneStr=qrType+"_"+wxConfigId;//微信事件里面识别的时候，识别信息为空的情况下，默认为公众号二维码

        Integer fileId=null;

        //公众号二维码实时生成
        List<QRInfo> infos=qrDao.findQRInfo(wxConfigId,sceneStr,qrType);
        if(infos.size()==0){
            fileId=createQRInfoByType(sceneStr,appId,wxConfigId,qrType,QRInfoConstant.ASYNCFALG_NO);
        }else{
            QRInfo qr=infos.get(0);
            fileId=qr.getQrCode();
        }
        return fileId;
    }


    /**
     * 【异步】患者端生成二维码医生/运营平台生成公众号二维码
     * @param sceneStr
     * @param appId
     */
    private Integer createQRInfoByType(final String sceneStr,final String appId,
                                    final Integer wxconfgId,final Integer qrType,final boolean asyncFalg){

        String ownerStr=null;
        if(QRInfoConstant.QRTYPE_DOCTOR==qrType){
            DoctorDAO doctorDAO=DAOFactory.getDAO(DoctorDAO.class);
            Doctor doc=doctorDAO.get(Integer.parseInt(sceneStr));
            ownerStr=doc.getMobile();

            if(StringUtils.isEmpty(ownerStr)){
                ownerStr=doc.getDoctorId()+"";
            }
        }

        if(QRInfoConstant.QRTYPE_PUBLIC==qrType||QRInfoConstant.QRTYPE_MATERIA==qrType){
            ownerStr=appId;
        }

        final String owner=ownerStr;

        Integer fileId=null;

        //是否异步生成二维码
        if(asyncFalg){
            GlobalEventExecFactory.instance().getExecutor().submit(new Runnable() {
                @Override
                public void run() {
                    createAndSaveQRInfo(sceneStr,appId,wxconfgId,qrType,owner);
                }
            });
        }else {
            fileId=createAndSaveQRInfo(sceneStr,appId,wxconfgId,qrType,owner);
        }

        return fileId;
    }

    /**
     * 返回二维码ossID
     * @param sceneStr
     * @param appId
     * @param wxconfgId
     * @param qrType
     * @param owner
     * @return
     */
    private Integer createAndSaveQRInfo(String sceneStr,String appId,Integer wxconfgId,Integer qrType,String owner){
        Integer fileId=null;
        try	{

            IWXServiceInterface wxService = AppContextHolder.getBean("eh.wxService", IWXServiceInterface.class);
            HashMap<String, String> reMap = wxService.getTicketAndUrlByAppId(sceneStr,appId);
            String qrUrl = reMap.get("url");
            String ticket = reMap.get("ticket");
            fileId = UploadFile.uploadQRInfoImage(ticket, sceneStr,owner);

            QRInfo info=new QRInfo();
            info.setQrCode(fileId);
            info.setQrUrl(qrUrl);
            info.setQrInfo(sceneStr);
            info.setQrType(qrType);
            info.setWxConfigId(wxconfgId);
            QRInfo savedInfo=qrDao.saveQRInfo(info);
            logger.info("二维码生成并上传成功,二维码记录ID="+savedInfo.getId());

        } catch (Exception e) {
            logger.error(
                    "user[{}] QRInfo image load from Weixin falied.", e);
        }finally {
            return fileId;
        }
    }


    /**
     * 运营平台/医生主页生成医生二维码的时候，保存相关二维码记录
     * @param reMap
     */
    public void saveDocQRInfo(HashMap<String, String> reMap){
        String qrUrl = reMap.get("url");
        String ticket = reMap.get("ticket");
        String sceneStr = reMap.get("scene_str");
        String appId=reMap.get("appId");

        WXConfigsDAO  wxconfigDao=DAOFactory.getDAO(WXConfigsDAO.class);
        WXConfig wxConfig = wxconfigDao.getByAppID(appId);
        Integer wxconfigId=null;

        if (wxConfig!=null) {
            wxconfigId=wxConfig.getId();
        }

        if(wxconfigId==null){
            logger.error("公众号未配置wxconfig，无法保存二维码数据"+ JSONUtils.toString(reMap));
            return;
        }

        Integer wxconfgId=wxConfig.getId();

        Integer qrCode=Integer.parseInt(reMap.get("qrCode"));

        QRInfo info=new QRInfo();
        info.setQrCode(qrCode);
        info.setQrUrl(qrUrl);
        info.setQrInfo(sceneStr);
        info.setQrType(QRInfoConstant.QRTYPE_DOCTOR);
        info.setWxConfigId(wxconfgId);
        info.setCreateDate(new Date());
        info.setQrStatus(QRInfoConstant.QRSTATUS_EFFECTIVE);
        qrDao.save(info);
    }





}
