package eh.util;

import ctd.account.UserRoleToken;
import ctd.mvc.upload.FileMetaRecord;
import ctd.mvc.upload.FileService;
import ctd.mvc.weixin.support.HttpClientUtils;
import ctd.mvc.weixin.support.HttpStreamResponse;
import ctd.persistence.exception.DAOException;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Date;

/**
 * Created by zhongzixuan on 2016/4/20 0020.
 * 上传微信二维码到阿里云
 */
public class UploadFile {
    private static final Logger logger = Logger.getLogger(UploadFile.class);

    /**上传二维码到阿里云
     * zhongzx
     * @param url
     * @param doctorId
     * @return
     */
    public static Integer uploadImage(String url,Integer doctorId){
        if(StringUtils.isEmpty(url)){
            throw new DAOException(DAOException.VALUE_NEEDED,"url is needed");
        }
        logger.info(url);
        FileOutputStream fileOutputStream = null;
        InputStream is = null;
        File file = null;
        try {
            HttpStreamResponse res = HttpClientUtils.doGetAsInputStream(url);
            Long size = res.getContentLength();
            // 获取文件类型
            String contentType = res.getContentType();
            logger.info("contentType = " + contentType);
            // 获取输入字节流
            is = res.getInputStream();
            if(contentType.equals("text/plain")){
                throw new DAOException(609,"下载二维码出错");
            }
            UserRoleToken urt = UserRoleToken.getCurrent();
            String userId = urt==null?null:urt.getUserId();
            String tenantId = urt==null?null:urt.getTenantId();
            String manageUnit = urt==null?null:urt.getManageUnit();
            FileMetaRecord metaRecord = new FileMetaRecord();
            Date nowDate = new Date();
            // 生成文件名
            String fileName = "doctor"+doctorId.toString()+".jpg";
            metaRecord.setFileName(fileName);
            metaRecord.setContentType(contentType);
            metaRecord.setTenantId(tenantId);
            metaRecord.setManageUnit(manageUnit);
            metaRecord.setMode(31);
            metaRecord.setOwner(userId);
            metaRecord.setCatalog("other-doc");
            metaRecord.setFileSize(size);
            metaRecord.setUploadTime(nowDate);
            metaRecord.setLastModify(nowDate);
            //先生成本地图片
            file = new File(fileName);
            byte[] data = new byte[1024];
            int len = 0;
            fileOutputStream = new FileOutputStream(file);
            while ((len = is.read(data)) != -1) {
                fileOutputStream.write(data, 0, len);
            }
            logger.info("upload start ...... ");
            FileService.instance().upload(metaRecord, file);
            logger.info("upload end ...... ");
            file.delete();//删除本地图片
            Integer fileId = metaRecord.getFileId();
            logger.info("返回fileId:" + fileId==null?"":String.valueOf(fileId));
            return fileId;
        }catch (Exception e){
            logger.error(e);
        }
        finally{
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    logger.error(e);
                }
            }
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    logger.error(e);
                }
            }
        }
        return null;
    }

    public static Integer uploadQRInfoImage(String url,String sceneStr,String owner){
        if(StringUtils.isEmpty(url)){
            throw new DAOException(DAOException.VALUE_NEEDED,"url is needed");
        }
        if(StringUtils.isEmpty(owner)){
            owner="eh";
        }

        try {
            URL urlObject = new URL(url);
            URLConnection connection = urlObject.openConnection();
            InputStream is = connection.getInputStream();
            String contentType=connection.getContentType();
            int size = connection.getContentLength();

            if(contentType.equals("text/plain")){
                throw new DAOException(609,"下载二维码出错");
            }
            UserRoleToken urt = UserRoleToken.getCurrent();
            String userId = urt==null?owner:urt.getUserId();
            String tenantId = urt==null?"eh":urt.getTenantId();
            String manageUnit = urt==null?"eh":urt.getManageUnit();
            FileMetaRecord metaRecord = new FileMetaRecord();
            Date nowDate = new Date();

            // 生成文件名
            String fileName = sceneStr.toString()+".jpg";
            metaRecord.setFileName(fileName);
            metaRecord.setContentType(contentType);
            metaRecord.setTenantId(tenantId);
            metaRecord.setManageUnit(manageUnit);
            metaRecord.setMode(31);
            metaRecord.setOwner(userId);
            metaRecord.setCatalog("other-doc");
            metaRecord.setFileSize(size);
            metaRecord.setUploadTime(nowDate);
            metaRecord.setLastModify(nowDate);

            FileService.instance().upload(metaRecord, is);

            Integer fileId = metaRecord.getFileId();
            logger.info("返回fileId:" + fileId==null?"":String.valueOf(fileId));
            return fileId;
        }catch (Exception e){
            logger.error(e.getMessage());
        }
        return 0;
    }
}
