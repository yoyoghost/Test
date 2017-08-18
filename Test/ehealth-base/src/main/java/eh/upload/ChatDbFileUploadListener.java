package eh.upload;
/********************************************
 * 文件名称: ChatDbFileUploadListener <br/>
 * 系统名称: feature4
 * 功能说明: TODO ADD FUNCTION. <br/>
 * 开发人员:  Chenq
 * 开发时间: 2017/4/1 13:36 
 *********************************************/

import ctd.mvc.upload.FileMetaRecord;
import ctd.mvc.upload.FileServiceListener;
import ctd.persistence.DAOFactory;
import eh.bus.dao.DoctorMsgOssDao;
import eh.entity.bus.DoctorMsgOss;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChatDbFileUploadListener implements FileServiceListener {
    private Logger logger = LoggerFactory.getLogger(ChatDbFileUploadListener.class);

    @Override
    public String getCatalog() {
        return "other-doc";
    }

    @Override
    public void onFileUpload(FileMetaRecord meta) {
        Object uploadType = meta.getProperty("uploadType");

        if (uploadType != null && ((String) uploadType).equalsIgnoreCase("chatLog")) {

            String consultId = meta.getProperty("consultId") != null ? ((String) meta.getProperty("consultId")) : "";
            String doctorId = meta.getProperty("doctorId") != null ? ((String) meta.getProperty("doctorId")) : "";

            if (!consultId.equalsIgnoreCase("") && !doctorId.equalsIgnoreCase("")) {
                DoctorMsgOss doctorMsgOss = new DoctorMsgOss();
                doctorMsgOss.setDoctorId(Integer.parseInt(doctorId));
                doctorMsgOss.setCreateTime(meta.getUploadTime());
                doctorMsgOss.setBusId(Integer.parseInt(consultId));
                doctorMsgOss.setOssId(meta.getFileId());

                DoctorMsgOssDao doctorMsgOssDao = DAOFactory.getDAO(DoctorMsgOssDao.class);
                doctorMsgOssDao.save(doctorMsgOss);
            }
        }


    }

    @Override
    public void onFileLoad(FileMetaRecord r) {

    }

    @Override
    public void onFileDelete(FileMetaRecord r) {

    }
}
