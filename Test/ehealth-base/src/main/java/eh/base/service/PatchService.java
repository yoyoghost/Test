package eh.base.service;

import com.google.common.collect.Maps;
import ctd.account.UserRoleToken;
import ctd.mvc.upload.FileMetaRecord;
import ctd.mvc.upload.FileService;
import ctd.mvc.upload.exception.FileRegistryException;
import ctd.mvc.upload.exception.FileRepositoryException;
import ctd.mvc.weixin.support.HttpClientUtils;
import ctd.mvc.weixin.support.HttpStreamResponse;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.dao.PatchDAO;
import eh.entity.base.Patch;
import eh.utils.DateConversion;
import eh.utils.FileUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Created by yaozh on 2017/1/6.
 */
public class PatchService {
    public static final Logger log = Logger.getLogger(PatchService.class);

    private static final String VIDEO_APP = "videoApp.zip";
    private static final String VIDEO_APP_FULL = "videoAppFull.zip";
    private static final String MAIN = "main.zip";
    private static final String MAIN_FULL = "mainFull.zip";


    private PatchDAO patchDAO;

    public PatchService() {
        patchDAO = DAOFactory.getDAO(PatchDAO.class);
    }

    @RpcService
    public Map<String, Object> getXYPatchFile(String version) {
        return getPatchFile(VIDEO_APP, version);
    }

    /**
     * yaozh
     * 获取批量更新文件
     *
     * @param pcPatchName
     * @param version
     * @return
     */
    @RpcService
    public Map<String, Object> getPatchFile(String pcPatchName, String version) {
        Map<String, Object> data = Maps.newHashMap();
        data.put("fileId", 0);
        List<Patch> fullPkg = patchDAO.findByFileNameAndIsPatchIsNull(pcPatchName);
        if (fullPkg.isEmpty()) {
            return data;
        }
        Patch lastFullPkg = fullPkg.get(0);
        Patch lastSecondFullPkg = fullPkg.size() > 1 ? fullPkg.get(1) : null;
        if (StringUtils.isEmpty(version)) {
            data.put("fileId", lastFullPkg.getFileId());
            data.put("md5", lastFullPkg.getFileMD5());
            data.put("version", lastFullPkg.getFileVersion());
            return data;
        }
        int compare = version.compareTo(lastFullPkg.getFileVersion());
        if (compare == 0) {
            return data;
        }
        if (compare < 0) {
            if (lastSecondFullPkg == null) {
                data.put("fileId", lastFullPkg.getFileId());
                data.put("md5", lastFullPkg.getFileMD5());
                data.put("version", lastFullPkg.getFileVersion());
                return data;
            } else {
                if (version.compareTo(lastSecondFullPkg.getFileVersion()) == 0) {
                    Patch p = patchDAO.getByFileNameAndFileVersionAndIsPatchIs1(pcPatchName, lastFullPkg.getFileVersion());
                    if (p != null) {
                        data.put("fileId", p.getFileId());
                        data.put("md5", p.getFileMD5());
                        data.put("version", p.getFileVersion());
                        return data;
                    }
                } else {
                    data.put("fileId", lastFullPkg.getFileId());
                    data.put("md5", lastFullPkg.getFileMD5());
                    data.put("version", lastFullPkg.getFileVersion());
                    return data;
                }
            }
        }
        return data;
    }

    @RpcService
    public void updateAndUploadProgram(String url) {
        if (url == null || StringUtils.isEmpty(url.trim())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "url is require");
        }
        UserRoleToken urt = UserRoleToken.getCurrent();
        if (urt == null) {
            throw new DAOException("UserRoleToken is null");
        }
        log.info("版本升级====>" + JSONUtils.toString(url));
        url = url.trim();
        int i = url.lastIndexOf("/") + 1;
        String fileName = url.substring(i, url.length());
        String fileDescription = "";
        boolean ql = false;
        switch (fileName) {
            case MAIN:
                fileDescription = "PC版本升级增量压缩包";
                break;
            case MAIN_FULL:
                fileDescription = "PC版本升级全量压缩包";
                ql = true;
                fileName = MAIN;
                break;
            case VIDEO_APP:
                fileDescription = "小鱼版本升级增量压缩包";
                break;
            case VIDEO_APP_FULL:
                fileDescription = "小鱼版本升级全量压缩包";
                ql = true;
                fileName = VIDEO_APP;
                break;
            default:
                throw new DAOException(" fileName is not allow");
        }
        List<Patch> patches = patchDAO.findByFileNameAndIsPatchIsNull(fileName);
        Patch lastPkg = null;
        if (patches != null) {
            lastPkg = patches.get(0);
        }
        if (lastPkg == null) {
            throw new DAOException(" has no Default Version");
        }
        String version = lastPkg.getFileVersion();
        if (ql) {
            String strNow = DateConversion.getDateFormatter(new Date(), "yyyyMMdd");
            version = strNow + "01";
            String v = lastPkg.getFileVersion();
            if (v == null) {
                throw new DAOException("FileVersion is null");
            }
            if (v.substring(0, 8).equals(strNow)) {
                int vi = Integer.parseInt(v.substring(8, 10));
                vi++;
                version = strNow + (vi < 10 ? "0" + vi : vi);
            }
        }
        uploadProgram(url, fileName, fileDescription, version, urt, ql);
    }


    private Patch uploadProgram(String filPath, String fileName, String fileDescription, String fileVersion, UserRoleToken urt, boolean ql) {
        Patch patch = new Patch();
        if (!ql) {
            patch.setIsPatch("1");
        }
        HttpStreamResponse strRes = null;
        InputStream is = null;
        FileOutputStream fileOutputStream = null;
        try {
            strRes = HttpClientUtils.doGetAsInputStream(filPath);
            is = strRes.getInputStream();

            FileMetaRecord meta = new FileMetaRecord();
            meta.setManageUnit("eh");
            meta.setTenantId("eh");
            meta.setLastModify(new Date());
            meta.setUploadTime(new Date());
            meta.setCatalog("other-doc");
            meta.setContentType("application/zip");
            meta.setFileName(fileName);
            meta.setMode(31);
            meta.setOwner(urt.getUserId());
            meta.setFileSize(strRes.getContentLength());
            File file = new File(fileName);
            byte[] data = new byte[1024];
            int len = 0;
            fileOutputStream = new FileOutputStream(file);
            while ((len = is.read(data)) != -1) {
                fileOutputStream.write(data, 0, len);
            }
            log.info("uploadProgram ================== start");
            FileService.instance().upload(meta, file);
            log.info("uploadProgram =================== end");
            patch.setFileId(meta.getFileId());
            patch.setFileMD5(FileUtil.getMd5Code(file));
            patch.setFileName(fileName);
            patch.setFileDescription(fileDescription);
            patch.setFileVersion(fileVersion);
            patch = patchDAO.save(patch);
        } catch (IOException e) {
            throw new DAOException(e.getMessage());
        } catch (FileRepositoryException e) {
            throw new DAOException(e.getMessage());
        } catch (FileRegistryException e) {
            throw new DAOException(e.getMessage());
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    log.error("uploadProgram() error : " + e);
                }
            }
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    log.error("uploadProgram() error : " + e);
                }
            }
        }
        return patch;
    }


}
