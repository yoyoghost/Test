package eh.prmt.dao;

import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONObject;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.prmt.PrmtBasicEntity;

/**
 * 活动基本信息DAO
 * @author hexy
 * @since 2017-7-3 9:36
 */
public abstract class PrmtBasicDAO extends HibernateSupportDelegateDAO<PrmtBasicEntity>{
	
	private static final Logger logger = LoggerFactory.getLogger(PrmtBasicDAO.class);
	
    public PrmtBasicDAO() {
        super();
        this.setEntityName(PrmtBasicEntity.class.getName());
        this.setKeyField("id");
    }
    
	/**
	 * 更新活动信息状态
	 * 
	 * @return entity
	 */
	public PrmtBasicEntity updatePrmtBasic(PrmtBasicEntity entity) {
		logger.info("PrmtBasicDAO.updatePrmtBasic() entity:"+JSONObject.toJSONString(entity));
		entity.setUpdateTime(new Date());
		return update(entity);
	}

	/**
	 * 保存活动参与信息
	 *
	 * @param record
	 * @return
	 */
	public PrmtBasicEntity savePrmtBasic(PrmtBasicEntity entity) {
		logger.info("PrmtBasicDAO.savePrmtBasic() entity:"+JSONObject.toJSONString(entity));
		Date date = new Date();
		entity.setActiveFlag(true);
		entity.setUpdateTime(date);
		return save(entity);
	}

	/**
	 * 分页查询活动信息
	 * 
	 * @param prmtCode
	 * @param prmtStatus
	 * @param start
	 * @param limit
	 * @return list entity
	 */
	@DAOMethod(sql = "FROM PrmtBasicEntity WHERE prmtCode = :prmtCode AND prmtStatus = :prmtStatus AND activeFlag = 1 ORDER BY createTime DESC")
	public abstract List<PrmtBasicEntity> findByQueryPages(
			@DAOParam("prmtCode") String prmtCode,
			@DAOParam("prmtStatus") Integer prmtStatus,
			@DAOParam(pageStart = true) int start,
			@DAOParam(pageLimit = true) int limit);

	/**
	 * 根据活动code,活动状态,查询活动信息
	 * 
	 * @param prmtCode
	 * @param prmtStatus
	 * @return list entity
	 */
	@DAOMethod(sql = "FROM PrmtBasicEntity WHERE  prmtCode = :prmtCode AND prmtStatus = :prmtStatus AND activeFlag = 1")
	public abstract List<PrmtBasicEntity> findByQuery(
			@DAOParam("prmtCode") String prmtCode,
			@DAOParam("prmtStatus") Integer prmtStatus);
}
