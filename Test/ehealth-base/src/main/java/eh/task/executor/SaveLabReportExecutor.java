package eh.task.executor;

import ctd.mvc.upload.FileMetaRecord;
import ctd.mvc.upload.FileService;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.resource.ResourceCenter;
import ctd.util.annotation.RpcService;
import eh.cdr.dao.DocIndexDAO;
import eh.cdr.dao.LabReportDAO;
import eh.cdr.dao.LabReportDetailDAO;
import eh.entity.cdr.LabReport;
import eh.entity.cdr.LabReportDetail;
import eh.entity.mpi.Patient;
import eh.task.ActionExecutor;
import eh.task.ExecutorRegister;
import eh.util.FreemarkerUtil;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

import java.io.*;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
/**
 * 保存检验列表
 * @author hyj
 *
 */
public class SaveLabReportExecutor implements ActionExecutor{
	private static final Logger logger = LoggerFactory.getLogger(SaveLabReportExecutor.class);
	/** 线程池 */
    private static ExecutorService executors = ExecutorRegister.register(Executors.newScheduledThreadPool(2));

    /** 业务参数 */
    private LabReport lab;
    private  List<LabReportDetail> tmpList;
    public SaveLabReportExecutor(LabReport lab,List<LabReportDetail> tmpList){
    	this.lab=lab;
    	this.tmpList=tmpList;
    }
	@Override
	public void execute() throws DAOException {
		executors.execute(new Runnable() {			
			@Override
			public void run() {
				saveLabReports();
			}
		});
	}
	
	@RpcService
	private void saveLabReports() throws DAOException{
		//保存检验报告列表
		LabReportDAO dao =DAOFactory.getDAO(LabReportDAO.class);
		String reportId = lab.getReportId();
		String typeCode = lab.getTypeCode();
		String mpiId = lab.getMpiid();
		Integer organ = lab.getRequireOrgan();
		logger.info("保存检查检验报告单reportId={}, mpiId={}, requireOrgan={},typeCode={}", reportId, mpiId, organ, typeCode);

		LabReport labReport = dao.getByRequreOrganAndReportId(organ, reportId, typeCode, mpiId);
		if(null != labReport){
			logger.info("检查检验报告单已存在,reportId={}, mpiId={}, requireOrgan={},typeCode={}", reportId, mpiId, organ, typeCode);
			return;
		}
		LabReport saveLab = dao.saveLabReport(lab);
		//保存检验报告明细
		LabReportDetailDAO detailDao=DAOFactory.getDAO(LabReportDetailDAO.class);
		for(LabReportDetail labDetail:tmpList){
			labDetail.setLabReportId(saveLab.getLabReportId());
			detailDao.saveLabReportDetail(labDetail);
		}
		DocIndexDAO docIndexDAO = DAOFactory.getDAO(DocIndexDAO.class);
		docIndexDAO.saveLabReportDocIndex(saveLab, saveLab.getTypeCode());
		/*LabReport l=dao.saveLabReport(lab);

		for(LabReportDetail labDetail:tmpList){
			labDetail.setLabReportId(l.getLabReportId());
			logger.info("保存检验报告明细--->"+JSONUtils.toString(labDetail));
			detailDao.saveLabReportDetail(labDetail);
		}
		//保存电子病历文档索引
		DocIndex doc=new DocIndex();
		doc.setDocType("1");
		doc.setMpiid(l.getMpiid());
		doc.setDocClass(99);
		doc.setDocTitle(l.getTypeName()+"("+l.getSampleTypeName()+")");
		doc.setOrganDocId(l.getReportId());
		doc.setDocSummary(l.getTypeName()+"("+l.getSampleTypeName()+")");
		doc.setCreateOrgan(l.getRequireOrgan());
		doc.setCreateDepart(l.getRequireDepartId());
		doc.setDepartName(l.getRequireDepartName());
		doc.setCreateDoctor(l.getRequireDoctorId());
		doc.setDoctorName(l.getRequireDoctorName());
		doc.setCreateDate(new Date());
		doc.setGetDate(new Date());
		DocIndexDAO docIndexDAO=DAOFactory.getDAO(DocIndexDAO.class);
//		logger.info("保存电子病历文档索引--->"+JSONUtils.toString(doc));
//		DocIndex docTarget=docIndexDAO.saveDocIndex(doc);
//
//		PatientDAO patientDAO=DAOFactory.getDAO(PatientDAO.class);
//		Patient p=patientDAO.getByMpiId(l.getMpiid());
//
//		//生成检验数据模板
//		this.createLabReportHtml(l,tmpList,p);
//		logger.info("生成检验数据模板成功");
//		//上传检验数据模板html
//		int fileId=this.uploadHtml(l);
//		logger.info("上传检验数据模板成功,fileId:"+fileId);
//		logger.info("更新云平台文档序号");
//		docIndexDAO.updateDocIdByDocIndexId(fileId, docTarget.getDocIndexId());
		saveLabReportDocIndex*/
	}
	
	/**
	 * 生成简历数据模板服务
	 * @author hyj
	 */
	private void createLabReportHtml(LabReport l,List<LabReportDetail> tmpList,Patient p){
		Template tp=FreemarkerUtil.getTemplate("report.ftl");
		Writer wt=FreemarkerUtil.getWriter(l.getLabReportId()+".html");
		HashMap<String, Object> map=new HashMap<String, Object>();
		map.put("labReport", l);
		map.put("labReportDetails", tmpList);
		map.put("itemName", l.getTypeName()+"("+l.getSampleTypeName()+")");
		map.put("patient", p);
		map.put("age", convertIdcard(p.getIdcard()));
		try {
			tp.process(map, wt);
		} catch (TemplateException e) {
			logger.error("createLabReportHtml-->"+e);
		} catch (IOException e) {
			logger.error("createLabReportHtml-->"+e);
		}
	}
	
	/**
	 * 根据身份证获取出生年月
	 * @author hyj
	 * @param idcard
	 * @param d
	 * @return
	 */
	public int convertIdcard(String idcard){
		Date birthday=null;
		if(idcard.length()==15){
			String idcardbirthday="19"+idcard.substring(6,8)+"-"+idcard.substring(8,10)+"-"+idcard.substring(10,12);
			DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");  
			try {
				birthday=sdf.parse(idcardbirthday);
			} catch (ParseException e) {
				logger.error("convertIdcard-->"+e);
			}
		}else{
			String idcardbirthday=idcard.substring(6,10)+"-"+idcard.substring(10,12)+"-"+idcard.substring(12,14);
			DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");  
			try {
				birthday=sdf.parse(idcardbirthday);
			} catch (ParseException e) {
				logger.error("convertIdcard-->"+e);
			}
		}
		if(birthday == null){
			throw new DAOException(609,"idcard 格式不正确");
		}
		int age=calcAge(birthday);
		return age;
	}
	
	/**
	 * 根据出生日期计算年龄
	 * @author hyj
	 * @param birthDate
	 * @return
	 */
	public static int calcAge(Date birthDate){  
        Date nowDate= new Date();  
        Calendar flightCal= Calendar.getInstance();  
        flightCal.setTime(nowDate);  
        Calendar birthCal= Calendar.getInstance();  
        birthCal.setTime(birthDate);  
          
        int y= flightCal.get(Calendar.YEAR)-birthCal.get(Calendar.YEAR);  
        int m= flightCal.get(Calendar.MONTH)-birthCal.get(Calendar.MONTH);  
        int d= flightCal.get(Calendar.DATE)-birthCal.get(Calendar.DATE);  
        if(y<0){  
            throw new RuntimeException("您老还没出生");  
        }  
          
        if(m<0){  
            y--;  
        }  
        if(m>=0&&d<0){  
            y--;  
        }  
  
        return y;  
    }
	
	/**
	 * 上传检验数据模板html
	 * @author hyj
	 * @param l
	 * @return
	 */
	public int uploadHtml(LabReport l){
		String fileName=l.getLabReportId()+".html";
		FileMetaRecord meta = new FileMetaRecord();
		try {
			Resource r = ResourceCenter.load("classpath:","eh/cdr/htmlmodel/outhtml/"+fileName);
			File htmlFile = r.getFile();
			
			InputStream is = new FileInputStream(htmlFile);
//			UserRoleToken token = UserRoleToken.getCurrent();
			meta.setManageUnit("eh");
			meta.setOwner("13735891715");
			meta.setLastModify(new Date());
			meta.setUploadTime(new Date());
			meta.setCatalog("organ-avatar");
			meta.setContentType("text/html");
			meta.setFileName(fileName);
			meta.setFileSize(htmlFile.length());
			FileService.instance().upload(meta, is);
			
			htmlFile.delete();
			return meta.getFileId();
		} catch (Exception e) {
			logger.error("uploadHtml-->"+e);
		}
		return meta.getFileId();
	}

}
