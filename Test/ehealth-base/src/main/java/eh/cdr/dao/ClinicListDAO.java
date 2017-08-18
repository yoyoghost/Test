package eh.cdr.dao;


import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcService;
import eh.entity.base.DoctorGroup;
import eh.entity.cdr.ClinicList;

import java.util.Date;
import java.util.List;


public abstract class ClinicListDAO extends HibernateSupportDelegateDAO<ClinicList>{
	
	public ClinicListDAO(){
		super();
		this.setEntityName(ClinicList.class.getName());
		this.setKeyField("clinicId");
	}
	
	@RpcService
	@DAOMethod
	public abstract DoctorGroup getById(String id);
	
	/**
	 * 就诊记录查询服务之情况一(按主索引和就诊时间查询,并根据就诊时间逆向排序)
	 * @param MPIID--病人主索引
	 * @param StartDate--开始时间
	 * @param EndDate--结束时间
	 * @return
	 */
	@RpcService
	@DAOMethod(sql="from ClinicList where mpiid=:MPIID and clinicDate>=:StartDate and clinicDate<:EndDate order by clinicDate desc")
	public abstract List<ClinicList> findByMpiidAndClinicDate(@DAOParam("MPIID") String mpiId,@DAOParam("StartDate") Date startDate,@DAOParam("EndDate") Date endDate);

	/**
	 *  就诊记录查询服务之情况二(按机构就诊序号查询,并根据就诊时间逆向排序)
	 * @param OrganClinicId--机构就诊序号
	 * @return
	 */
	@RpcService
	@DAOMethod(sql="from ClinicList where organClinicId=:OrganClinicId order by clinicDate desc")
	public abstract List<ClinicList> findByOrganClinicId(@DAOParam("OrganClinicId") String organClinicId);
	
	/**
	 * 就诊记录查询服务之情况三(按主索引、就诊机构和就诊时间查询,并根据就诊时间逆向排序)
	 * @param MPIID--病人主索引
	 * @param ClinicOrgan--就诊机构
	 * @param StartDate--开始时间
	 * @param EndDate--结束时间
	 * @return
	 */
	@RpcService
	@DAOMethod(sql="from ClinicList where mpiid=:MPIID and clinicOrgan=:ClinicOrgan and clinicDate>=:StartDate and clinicDate<:EndDate order by clinicDate desc")
	public abstract List<ClinicList> findByMpiidAndClinicOrganAndClinicDate(@DAOParam("MPIID") String mpiId,@DAOParam("ClinicOrgan") int clinicOrgan,@DAOParam("StartDate") Date startDate,@DAOParam("EndDate") Date endDate);

	/**
	 * 就诊记录查询服务之情况四(按主索引、就诊医生和就诊时间查询,并根据就诊时间逆向排序)
	 * @param MPIID--病人主索引
	 * @param ClinicDoctor--就诊医生
	 * @param StartDate--开始时间
	 * @param EndDate--结束时间
	 * @return
	 */
	@RpcService
	@DAOMethod(sql="from ClinicList where mpiid=:MPIID and clinicDoctor=:ClinicDoctor and clinicDate>=:StartDate and clinicDate<:EndDate order by clinicDate desc")
	public abstract List<ClinicList> findByMpiidAndClinicDoctorAndClinicDate(@DAOParam("MPIID") String mpiId,@DAOParam("ClinicDoctor") int clinicDoctor,@DAOParam("StartDate") Date startDate,@DAOParam("EndDate") Date endDate);
	
	/**
	 * 就诊记录查询服务之情况五(按主索引、就诊类别和就诊时间查询,并根据就诊时间逆向排序)
	 * @param MPIID--病人主索引
	 * @param ClinicType--就诊类别
	 * @param StartDate--开始时间
	 * @param EndDate--结束时间
	 * @return
	 */
	@RpcService
	@DAOMethod(sql="from ClinicList where mpiid=:MPIID and clinicType=:ClinicType and clinicDate>=:StartDate and clinicDate<:EndDate order by clinicDate desc")
	public abstract List<ClinicList> findByMpiidAndClinicTypeAndClinicDate(@DAOParam("MPIID") String mpiId,@DAOParam("ClinicType") int clinicType,@DAOParam("StartDate") Date startDate,@DAOParam("EndDate") Date endDate);

	/**
	 * 就诊记录查询服务之情况六(按就诊医生和就诊时间查询,并根据就诊时间逆向排序)
	 * @param ClinicDoctor--就诊医生
	 * @param StartDate--开始时间
	 * @param EndDate--结束时间
	 * @return
	 */
	@RpcService
	@DAOMethod(sql="from ClinicList where clinicDoctor=:ClinicDoctor and clinicDate>=:StartDate and clinicDate<:EndDate order by clinicDate desc")
	public abstract List<ClinicList> findByClinicDoctorAndClinicDate(@DAOParam("ClinicDoctor") int clinicDoctor,@DAOParam("StartDate") Date startDate,@DAOParam("EndDate") Date endDate);
	
	/**
	 * 就诊记录从his导入到平台
	 */
	@RpcService
	public void saveClinicList(List<ClinicList> list){
		for(ClinicList clinicList :list){
			save(clinicList);
		}
	}
}
