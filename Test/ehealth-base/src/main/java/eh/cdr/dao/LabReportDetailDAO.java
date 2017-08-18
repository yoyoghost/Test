package eh.cdr.dao;


import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.JSONUtils;
import eh.entity.cdr.LabReportDetail;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;


public abstract class LabReportDetailDAO extends HibernateSupportDelegateDAO<LabReportDetail>{
	private static final Log logger = LogFactory.getLog(LabReportDetailDAO.class);
	
	public LabReportDetailDAO(){
		super();
		this.setEntityName(LabReportDetail.class.getName());
		this.setKeyField("labReportDetailId");
	}
	
	/**
	 * 检验报告列表明细保存服务
	 * @author hyj
	 * @param lab
	 */
	public void saveLabReportDetail(LabReportDetail lab){
		logger.info(" 检验报告列表明细保存服务:"+JSONUtils.toString(lab));
		save(lab);
	}
	
	@DAOMethod
	public abstract List<LabReportDetail> findByLabReportId(Integer labReportId);
	
}
