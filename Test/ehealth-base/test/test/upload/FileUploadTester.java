package test.upload;

import ctd.account.UserRoleToken;
import ctd.mvc.upload.FileMetaRecord;
import ctd.mvc.upload.FileService;
import ctd.mvc.upload.exception.FileRegistryException;
import ctd.mvc.upload.exception.FileRepositoryException;
import eh.base.service.thirdparty.BasicInfoService;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/**
 * Created by w on 2016/5/25.
 */
public class FileUploadTester {
    static {
        ClassPathXmlApplicationContext appContext = new ClassPathXmlApplicationContext("classpath:spring.xml");
    }
    @Test
    public void uploadAdminImage() throws InterruptedException {

        UserRoleToken urt = UserRoleToken.getCurrent();
        String userId = urt==null?null:urt.getUserId();
        String tenantId = urt==null?null:urt.getTenantId();
        String manageUnit = urt==null?null:urt.getManageUnit();
        FileMetaRecord metaRecord = new FileMetaRecord();
        Date nowDate = new Date();
        File f1 =new File("d:\\doctor.jpg");
        // 生成文件名
        String fileName = "doctor.jpg";
        metaRecord.setFileName(fileName);
        metaRecord.setContentType("image/jpeg");
        metaRecord.setTenantId(tenantId);
        metaRecord.setManageUnit(manageUnit);
        metaRecord.setMode(31);
        metaRecord.setOwner(userId);
        metaRecord.setCatalog("doctor-avatar");
        metaRecord.setFileSize(f1.length());
        metaRecord.setUploadTime(nowDate);
        metaRecord.setLastModify(nowDate);
        metaRecord.setProperty("uid","13735891715");
        //metaRecord.setProperty("doctorId","1177");
        //metaRecord.setProperty("photo","");
        metaRecord.setProperty("avatarFiled","");
        //先生成本地图片

        try {
            FileService.instance().upload(metaRecord, f1);
        } catch (FileRepositoryException e) {
            e.printStackTrace();
        } catch (FileRegistryException e) {
            e.printStackTrace();
        }
        TimeUnit.SECONDS.sleep(1000);

    }
}
