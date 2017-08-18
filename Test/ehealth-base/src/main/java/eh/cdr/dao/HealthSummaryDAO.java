package eh.cdr.dao;


import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcService;
import eh.entity.cdr.HealthSummary;

import java.util.List;


public abstract class HealthSummaryDAO extends HibernateSupportDelegateDAO<HealthSummary>{
	
	public HealthSummaryDAO(){
		super();
		this.setEntityName(HealthSummary.class.getName());
		this.setKeyField("summaryId");
	}
	
	/**
	 * 根据健康摘要序号查询
	 * @param summaryId
	 * @return
	 */
	@RpcService
	@DAOMethod
	public abstract HealthSummary getBySummaryId(Integer summaryId);
	
	/**
	 * 根据病人主索引查询
	 * @param mpiId
	 * @return
	 */
	@RpcService
	@DAOMethod
	public abstract List<HealthSummary> findByMpiId(String mpiId);

	/**
	 * 健康摘要信息服务(按摘要类别合并与排序。)
	 * @param mpiId 主索引
	 * @return
	 */
	@RpcService
	@DAOMethod(sql="from HealthSummary where mpiId=:mpiId  order by summaryType")
	public abstract List<Integer>  findHealthSummaryByMpiId(@DAOParam("mpiId") String mpiId);
}
