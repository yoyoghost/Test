package eh.bus.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcService;
import eh.entity.bus.SourceAllot;

import java.util.List;

/**
 * 
 * 号源规则
 * 
 * @author <a href="mailto:jianghc@easygroup.net.cn">jianghc</a>
 */
public abstract class SourceAllotDAO extends
		HibernateSupportDelegateDAO<SourceAllot> {

	public SourceAllotDAO() {
		super();
		this.setEntityName(SourceAllot.class.getName());
		this.setKeyField("allotId");
	}

	/**
	 * 
	 * 
	 * @Class eh.bus.dao.SourceAllotDAO.java
	 * 
	 * @Title: findByScheduleId 根据排班ID查询号源规则信息
	 * 
	 * @Description: TODO
	 * 
	 * @param @param scheduleId 排班ID
	 * @param @return
	 * 
	 * @author AngryKitty
	 * 
	 * @Date 2015-11-30下午4:39:07
	 * 
	 * @return List<SourceAllot>
	 * 
	 * @throws
	 */
	@RpcService
	@DAOMethod(sql = " from SourceAllot where scheduleId = :scheduleId order by startNum")
	public abstract List<SourceAllot> findByScheduleId(
			@DAOParam("scheduleId") Integer scheduleId);

	/**
	 * 
	 * 
	 * @Class eh.bus.dao.SourceAllotDAO.java
	 * 
	 * @Title: deleteByScheduleId
	 * 
	 * @Description: TODO 根据号源排班ID删除对应的号源规则
	 * 
	 * @param @param scheduleId 排班ID
	 * 
	 * @author AngryKitty
	 * 
	 * @Date 2015-11-30下午4:44:07
	 * 
	 * @return void
	 * 
	 * @throws
	 */
	@RpcService
	@DAOMethod(sql = "delete FROM SourceAllot WHERE scheduleId=:scheduleId")
	public abstract void deleteByScheduleId(
			@DAOParam("scheduleId") Integer scheduleId);

	/**
	 * 
	 * 
	 * @Class eh.bus.dao.SourceAllotDAO.java
	 * 
	 * @Title: deleteByAllotId
	 * 
	 * @Description: TODO 根据号源生成规则ID删除号源生成规则
	 * 
	 * @param @param allotId 号源生成规则ID
	 * 
	 * @author AngryKitty
	 * 
	 * @Date 2015-11-30下午5:08:04
	 * 
	 * @return void
	 * 
	 * @throws
	 */
	@RpcService
	@DAOMethod(sql = "delete FROM SourceAllot WHERE allotId=:allotId")
	public abstract void deleteByAllotId(@DAOParam("allotId") Integer allotId);

	/**
	 * 
	*
	* @Class eh.bus.dao.SourceAllotDAO.java
	*
	* @Title: getSumByScheduleId
	
	* @Description: TODO 
	
	* @param @param scheduleId
	* @param @return    
	
	* @author AngryKitty
	
	* @Date 2015-12-2下午2:38:24 
	
	* @return Integer   
	
	* @throws
	 */
	@RpcService
	@DAOMethod(sql = "select sum(sourceNum) from SourceAllot WHERE scheduleId=:scheduleId")
	public abstract Long getSumByScheduleId(@DAOParam("scheduleId") Integer scheduleId);
}
