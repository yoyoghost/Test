package eh.cdr.dao;


import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcService;
import eh.entity.cdr.LabReport;
import eh.entity.cdr.LabReportDetail;
import eh.entity.mpi.HealthCard;
import eh.entity.mpi.Patient;
import eh.mpi.dao.HealthCardDAO;
import eh.mpi.dao.PatientDAO;
import eh.task.executor.LabReportSendExecutor;
import eh.task.executor.SaveLabReportExecutor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Date;
import java.util.List;


public abstract class LabReportDAO extends HibernateSupportDelegateDAO<LabReport>{
	private static final Log logger = LogFactory.getLog(LabReportDAO.class);
	
	public LabReportDAO(){
		super();
		this.setEntityName(LabReport.class.getName());
		this.setKeyField("labReportId");
	}
	
	@RpcService
	@DAOMethod
	public abstract LabReport getByLabReportId(int labReportId);

	/**
	 * 查询报告列表
	 * @param requireOrgan 检查医院代码
	 * @param reportId    报告单号
     * @return
     */
	@RpcService
	@DAOMethod(sql="from LabReport where requireOrgan=:requireOrgan and reportId=:reportId and typeCode=:typeCode and mpiid=:mpiId")
	public abstract LabReport getByRequreOrganAndReportId(@DAOParam("requireOrgan") Integer requireOrgan, @DAOParam("reportId") String reportId, @DAOParam("typeCode") String typeCode, @DAOParam("mpiId") String mpiId);


	/**
	 * 调用his服务--获取检验报告列表服务
	 * @param mpiid
	 * @param organ
	 */
	public void getLabReport(String mpiid,Integer organ){
		PatientDAO dao=DAOFactory.getDAO(PatientDAO.class);
		Patient p=dao.getByMpiId(mpiid);
		
		HealthCardDAO healthCardDAO=DAOFactory.getDAO(HealthCardDAO.class);
		List<HealthCard> healthCards=healthCardDAO.findByCardOrganAndMpiId(organ,mpiid);
		p.setHealthCards(healthCards);
		LabReportSendExecutor executor=new LabReportSendExecutor(p, organ);
		executor.execute();
	}
	
	@RpcService
	public void saveLabReports(LabReport lab,List<LabReportDetail> tmpList) throws DAOException{
		SaveLabReportExecutor labReport=new SaveLabReportExecutor(lab,tmpList);
		labReport.execute();
	}
	
	/**
	 * 检验报告列表保存服务
	 * @author hyj
	 * @param lab
	 * @return
	 */
	@RpcService
	public LabReport saveLabReport(LabReport lab){
		 if(StringUtils.isEmpty(lab.getMpiid())){
			throw new DAOException(DAOException.VALUE_NEEDED,"mpiid is required");
		 }
		 if(StringUtils.isEmpty(lab.getTypeCode())){
			throw new DAOException(DAOException.VALUE_NEEDED,"typeCode is required");
		 }
		 if(null == lab.getRequireOrgan()){
			throw new DAOException(DAOException.VALUE_NEEDED,"requireOrgan is required");
		 }
		 if(StringUtils.isEmpty(lab.getReportId())){
			throw new DAOException(DAOException.VALUE_NEEDED,"reportId is required");
		 }
		 lab.setCreateTime(new Date());
		 return save(lab);
	}

	@DAOMethod
	public abstract List<LabReport> findByPhyId(Integer phyId);
}
