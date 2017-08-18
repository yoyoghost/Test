package eh.mpi.dao;

import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONObject;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.mpi.FollowModuleLimit;

/**
 * 模板权限DAO
 * @author hexy
 * @since 2017-7-7 9:36
 */
public abstract class FollwModuleLimitDAO extends HibernateSupportDelegateDAO<FollowModuleLimit>{
	
	private static final Logger logger = LoggerFactory.getLogger(FollowModuleLimit.class);
	
    public FollwModuleLimitDAO() {
        super();
        this.setEntityName(FollowModuleLimit.class.getName());
        this.setKeyField("id");
    }
    
	/**
	 * 更新模板权限
	 * 
	 * @return entity
	 */
	public FollowModuleLimit updateFollowModuleLimit(FollowModuleLimit entity) {
		logger.info("FollwModuleLimitDAO.updateFollowModuleLimit() entity:"+JSONObject.toJSONString(entity));
		entity.setUpdateTime(new Date());
		return update(entity);
	}

	/**
	 * 模板权限信息
	 *
	 * @param entity
	 * @return
	 */
	public FollowModuleLimit saveFollowModuleLimit(FollowModuleLimit entity) {
		logger.info("FollwModuleLimitDAO.saveFollowModuleLimit() entity:"+JSONObject.toJSONString(entity));
		Date date = new Date();
		entity.setActiveFlag(true);
		entity.setUpdateTime(date);
		return save(entity);
	}

	@DAOMethod()
	public abstract void deleteByMid(Integer mid);

	@DAOMethod()
	public abstract FollowModuleLimit getByMidAndOrganId(Integer mid,Integer organId);

	@DAOMethod()
	public abstract FollowModuleLimit getByMidAndDeptId(Integer mid,Integer deptId);

	@DAOMethod()
	public abstract List<FollowModuleLimit> findByMid(Integer mid);


	/**
	 * 模板权限查询
	 * 
	 * @param mid
	 * @param organId
	 * @param deptId
	 * @return list entity
	 */
	@DAOMethod(sql = "FROM FollowModuleLimit WHERE  mid = :mid AND organId = :organId AND deptId = :deptId AND activeFlag = 1")
	public abstract List<FollowModuleLimit> findByQuery(
			@DAOParam("mid") Long mid,
			@DAOParam("organId") Long organId,
			@DAOParam("deptId") Long deptId);
}
