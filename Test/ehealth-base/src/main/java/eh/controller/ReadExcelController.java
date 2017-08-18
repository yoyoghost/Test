package eh.controller;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import ctd.account.UserRoleToken;
import ctd.mvc.controller.OutputSupportMVCController;
import ctd.mvc.controller.util.UserRoleTokenUtils;
import ctd.mvc.upload.FileMetaRecord;
import ctd.mvc.upload.FileService;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.security.exception.SecurityException;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.context.ContextUtils;
import eh.op.dao.DoctorPointXlsDAO;
import eh.op.dao.XlsDoctorInfoDAO;
import eh.op.service.PatientOPService;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 读取Excel文件v
 *
 * @author jianghc
 * @create 2016-10-17 11:07
 **/
@Controller("readExcelController")
public class ReadExcelController extends OutputSupportMVCController {

    private static final Log logger = LogFactory.getLog(ReadExcelController.class);
    private final LoadingCache<Integer, FileMetaRecord> cache;
    private FileService fileService;
    private int maxMemorySize;
    private long maxUploadFileSize;
    private DiskFileItemFactory factory;
    private ServletFileUpload uploader;

    public ReadExcelController() {
        this.cache = CacheBuilder.newBuilder().maximumSize(1000L).expireAfterAccess(30L, TimeUnit.MINUTES).build(new CacheLoader<Integer, FileMetaRecord>() {
            public FileMetaRecord load(Integer fileId) throws Exception {
                return ReadExcelController.this.fileService.load(fileId.intValue());
            }
        });
        this.maxMemorySize = 524288;
        this.maxUploadFileSize = 2097152L;
        this.factory = new DiskFileItemFactory();
        this.factory.setSizeThreshold(this.maxMemorySize);
        this.uploader = new ServletFileUpload(this.factory);
        this.uploader.setSizeMax(this.maxUploadFileSize);
    }


    /**
     * 读取Excel文件
     * 医生积分批量导入
     *
     * @param request
     * @param response
     */
    @RequestMapping(value = "api/doctorpoint", method = RequestMethod.POST)
    public void readDoctorPointExcel(HttpServletRequest request,
                                     HttpServletResponse response) {

        String fileName = null;
        Long fileSize = null;
        FileItem item = null;
        try {
            request.setCharacterEncoding("UTF-8");
            UserRoleToken e = null;
            e = UserRoleTokenUtils.getUserRoleToken(request);
            ContextUtils.put("$ur", e);
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("X-Coded-JSON-Message","true");
            List items = this.uploader.parseRequest(request);
            Iterator gzip = items.iterator();
            if (gzip.hasNext()) {
                item = (FileItem) gzip.next();
                fileName = item.getName();
                fileSize = item.getSize();
            }
        } catch (FileUploadException e) {
            logger.error(e);
        } catch (IOException e) {
            logger.error(e);
        } catch (SecurityException e1) {
            logger.error(e1);
        }
        if (item == null) {
            throw new DAOException("无上传文件");
        }
        DoctorPointXlsDAO doctorPointXlsDAO = DAOFactory.getDAO(DoctorPointXlsDAO.class);
        try {
            String json = null;
            try {
                json = JSONUtils.toString(doctorPointXlsDAO.readXlsToDoctorPointXls(item, fileName, fileSize));
            } catch (Exception e) {
                json = "{\"code\":500,\"msg\":\""+e.getMessage()+"\"}";
            }
            Writer writer = response.getWriter();
            writer.write(json);
            writer.flush();
            writer.close();
        } catch (IOException e) {
            logger.error(e);
        }
    }

    /**
     * 读取Excel文件
     * 医生批量导入（注册）
     *
     * @param request
     * @param response
     */
    @RequestMapping(value = "api/doctor", method = RequestMethod.POST)
    public void readDoctorExcel(HttpServletRequest request,
                                HttpServletResponse response) {
        String fileName = null;
        Long fileSize = null;
        FileItem item = null;
        try {
            request.setCharacterEncoding("UTF-8");
            UserRoleToken e = null;
            e = UserRoleTokenUtils.getUserRoleToken(request);
            ContextUtils.put("$ur", e);
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("X-Coded-JSON-Message","true");
            List items = this.uploader.parseRequest(request);
            Iterator gzip = items.iterator();
            if (gzip.hasNext()) {
                item = (FileItem) gzip.next();
                fileSize = item.getSize();
                fileName = item.getName();
            }
        } catch (FileUploadException e) {
            logger.error(e);
        } catch (IOException e) {
            logger.error(e);
        } catch (SecurityException e1) {
            logger.error(e1);
        }
        if (item == null) {
            throw new DAOException("无上传文件");
        }
        XlsDoctorInfoDAO xlsDoctorInfoDAO = DAOFactory.getDAO(XlsDoctorInfoDAO.class);
        try {
            String json = null;

            try {
                json = JSONUtils.toString(xlsDoctorInfoDAO.readXlsToDoctorInfo(item, fileName, fileSize));
            } catch (Exception e) {
                json = "{\"code\":500,\"msg\":\""+e.getMessage()+"\"}";
            }
            Writer writer = response.getWriter();
            writer.write(json);
            writer.flush();
            writer.close();
        } catch (IOException e) {
            logger.error(e);
        }


    }

    /**
     * 读取Excel文件
     * 签约患者批量导入（注册）
     *
     * @param request
     * @param response
     */
    @RequestMapping(value = "api/relationpatient/{organ}", method = RequestMethod.POST)
    public void readRelationpatientExcel(@PathVariable("organ") int organ, HttpServletRequest request,
                                         HttpServletResponse response) {
        String fileName = null;
        Long fileSize = null;
        FileItem item = null;
        try {
            request.setCharacterEncoding("UTF-8");
            UserRoleToken e = null;
            e = UserRoleTokenUtils.getUserRoleToken(request);
            ContextUtils.put("$ur", e);
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("X-Coded-JSON-Message","true");
            List items = this.uploader.parseRequest(request);
            Iterator gzip = items.iterator();
            if (gzip.hasNext()) {
                item = (FileItem) gzip.next();
                fileSize = item.getSize();
                fileName = item.getName();
            }
        } catch (FileUploadException e) {
           logger.error(e);
        } catch (IOException e) {
            logger.error(e);
        } catch (SecurityException e1) {
            logger.error(e1);
        }
        if (item == null) {
            throw new DAOException("无上传文件");
        }
        try {
            Writer writer = response.getWriter();
            String json = null;
            try {
                PatientOPService patientOPService = AppContextHolder.getBean("patientOPService", PatientOPService.class);
                Map<String,Object> map=patientOPService.readRelationPation(item, fileName, fileSize,organ);
                json = JSONUtils.toString(map);
            } catch (Exception e) {
                json = "{\"code\":500,\"msg\":\""+e.getMessage()+"\"}";
            }
            writer.write(json);
            writer.flush();
            writer.close();
        } catch (IOException e) {
           logger.error(e);
        }

    }

    public FileService getFileService() {
        return fileService;
    }

    public void setFileService(FileService fileService) {
        this.fileService = fileService;
    }

    public long getMaxUploadFileSize() {
        return maxUploadFileSize;
    }

    public void setMaxUploadFileSize(long maxUploadFileSize) {
        this.maxUploadFileSize = maxUploadFileSize;
        this.uploader.setSizeMax(maxUploadFileSize);
        logger.info("uploadFileSize setNewValue:" + maxUploadFileSize);
    }

    public int getMaxMemorySize() {
        return maxMemorySize;
    }

    public void setMaxMemorySize(int maxMemorySize) {
        this.maxMemorySize = maxMemorySize;
        this.factory.setSizeThreshold(maxMemorySize);
    }
}

