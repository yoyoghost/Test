package eh.upload;

import ctd.account.user.User;
import ctd.account.user.UserController;
import ctd.mvc.upload.FileMetaRecord;
import ctd.mvc.upload.FileServiceListener;
import ctd.net.rpc.util.ServiceAdapter;
import eh.entity.base.Doctor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 头像上传事件
 *
 * @author wnw
 */
public class DoctorImageUploadListener implements FileServiceListener {
    private static final Logger log = LoggerFactory.getLogger(DoctorImageUploadListener.class);
    @Override
    public String getCatalog() {
        return "doctor-avatar";
    }

    @Override
    public void onFileDelete(FileMetaRecord arg0) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onFileLoad(FileMetaRecord arg0) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onFileUpload(FileMetaRecord arg0) {
        Map<String, Object> properties = arg0.getProperties();
        if (properties == null){
            log.error("missing properties by fileId[{}]", arg0.getFileId());
            return;
        }
        try {
            if (properties.containsKey("uid")) {// user头像照片
                String uid = (String) arg0.getProperty("uid");
                //更新user 表 avatarFiled 字段//uid 传管理员userid
                User user = UserController.instance().get(uid);
                user.setAvatarFileId(arg0.getFileId());
                UserController.instance().getUpdater().update(user);
            } else if (properties.containsKey("chemistId")) { //药师个人照片
                Integer chemistId = Integer.parseInt((String) arg0.getProperty("chemistId"));
                ServiceAdapter.invoke("eh.chemistService", "updatePhotoByChemistId", arg0.getFileId(), chemistId);
                log.info("chemist[{}] photo updated by {}", chemistId, arg0.getFileId());
            } else if(properties.containsKey("doctorId")) {
                Integer doctorId = Integer.parseInt((String) arg0.getProperty("doctorId"));
                if (properties.containsKey("photo")) { // 医生个人照片
                    ServiceAdapter.invoke("eh.doctor", "updatePhotoByDoctorId", doctorId, arg0.getFileId());
                    log.info("doctor[{}] photo updated by {}", doctorId, arg0.getFileId());
                } else if (properties.containsKey("doctorCertImage")) { // 医生执业证书照片1
                    ServiceAdapter.invoke("eh.doctor", "updateCertImageByDoctorId", doctorId, arg0.getFileId());
                    log.info("doctor[{}] doctorCertImage updated by {}", doctorId, arg0.getFileId());
                } else if (properties.containsKey("doctorCertImage2")) {// 医生执业证书照片2
                    ServiceAdapter.invoke("eh.doctor", "updateCertImage2ByDoctorId", doctorId, arg0.getFileId());
                    log.info("doctor[{}] doctorCertImage2 updated by {}", doctorId, arg0.getFileId());
                } else if (properties.containsKey("proTitleImage")) { // 医生职称照片
                    ServiceAdapter.invoke("eh.doctor", "updateProTitleImageByDoctorId", doctorId, arg0.getFileId());
                    log.info("doctor[{}] proTitleImage updated by {}", doctorId, arg0.getFileId());
                } else if (properties.containsKey("workCardImage")) { // 医生手持工牌照
                    ServiceAdapter.invoke("eh.doctor", "updateWorkCardImageByDoctorId", arg0.getFileId(), doctorId);
                    log.info("doctor[{}] workCardImage updated by {}", doctorId, arg0.getFileId());
                } else {
                    log.error("missing properties by fileId[{}]", arg0.getFileId());
                }
                Doctor doctor = (Doctor) ServiceAdapter.invoke("eh.doctor", "getByDoctorId", doctorId);
                if (doctor != null) {
                    ServiceAdapter.invoke("eh.doctor", "updateDoctorByDoctorId", doctor);
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }
}
