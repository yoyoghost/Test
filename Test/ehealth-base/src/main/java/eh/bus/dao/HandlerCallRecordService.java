package eh.bus.dao;

import ctd.mvc.upload.FileMetaRecord;
import ctd.mvc.upload.FileService;
import ctd.mvc.upload.exception.FileRegistryException;
import ctd.mvc.upload.exception.FileRepositoryException;
import ctd.mvc.weixin.support.HttpClientUtils;
import ctd.mvc.weixin.support.HttpStreamResponse;
import ctd.persistence.DAOFactory;
import ctd.util.annotation.RpcService;
import eh.entity.bus.CallRecord;
import eh.entity.bus.InternalSystemMessage;
import eh.op.dao.InternalSystemMessageDAO;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by xuqh on 2016/4/20.
 */
public  class HandlerCallRecordService {

    private static final Log logger = LogFactory.getLog(HandlerCallRecordService.class);

    @RpcService
    public void checkServiceStatus(){
        CallRecordDAO cd = DAOFactory.getDAO(CallRecordDAO.class);
        if (!cd.isCallServiceNormal())
        {
            try {
                InternalSystemMessageDAO isd = DAOFactory.getDAO(InternalSystemMessageDAO.class);
                Integer id = isd.addUrgentMessage("双呼系统异常", "双呼系统异常，请拨打测试，如有问题，及时反馈！","checkServiceStatus", 7);
            }
            catch (Exception e)
            {
                logger.error(e);
            }
        }
    }


    @RpcService
    public void handlerCallRecord(){
        FileOutputStream fileOutputStream = null;
        File file = null;
        InputStream is = null;
        try {
            List <CallRecord>list = new ArrayList<CallRecord>();
            CallRecordDAO dao = DAOFactory.getDAO(CallRecordDAO.class);
            list = dao.findCallOSSISNULL();
            if(list.size()>0){
                for(CallRecord callRecord : list){
                    Integer id=callRecord.getId();
                    String url = callRecord.getUrl();
                    //文件下载上传到OSS服务器
                    String temp[] = url.split("/");
                    String fileName = temp[temp.length - 1];
                    logger.info("fileName=============================="+fileName);
                    HttpStreamResponse strRes = HttpClientUtils.doGetAsInputStream(url);
                    is = strRes.getInputStream();
                    FileMetaRecord meta = new FileMetaRecord();
                    meta.setManageUnit("eh");
                    meta.setOwner(callRecord.getFromMobile());
                    meta.setTenantId("eh");

                    meta.setLastModify(new Date());
                    meta.setUploadTime(new Date());
                    meta.setCatalog("ngarimedia");
                    meta.setContentType("audio/x-wav");
                    meta.setFileName(fileName);
                    meta.setFileSize(strRes.getContentLength());

                    file = new File(fileName);
                    logger.info("file================="+file);
                    byte[] data = new byte[1024];
                    int len = 0;
                    fileOutputStream = new FileOutputStream(file);
                    while((len=is.read(data)) != -1){
                        fileOutputStream.write(data,0,len);
                    }
                    logger.info("upload ================== start");
                    FileService.instance().upload(meta,file);
                    logger.info("upload =================== end");
                    // 更新OSSid时长
                    dao.updateCallOSSID(id,meta.getFileId());
                }
            }


        }catch (FileRepositoryException e) {
            logger.error(e);
        } catch (FileRegistryException e) {
            logger.error(e);
        } catch (IOException e) {
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
    }

    public static String downloadFromUrl(String url,File dir) {
        try {
            URL httpurl = new URL(url);
            String fileName = getFileNameFromUrl(url);
            File f = new File(dir + fileName);
            FileUtils.copyURLToFile(httpurl, f);
        } catch (Exception e) {
            logger.error(e);
            return "Fault!";
        }
        return "Successful!";
    }

    public static String getFileNameFromUrl(String url){
        String name = new Long(System.currentTimeMillis()).toString() + ".X";
        int index = url.lastIndexOf("/");
        if(index > 0){
            name = url.substring(index + 1);
            if(name.trim().length()>0){
                return name;
            }
        }
        return name;
    }


}
