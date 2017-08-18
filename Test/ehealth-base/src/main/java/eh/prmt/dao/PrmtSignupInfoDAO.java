package eh.prmt.dao;

import java.util.Date;
import java.util.List;







import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONObject;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.prmt.PrmtSignupInfoEntity;

/**
 * 活动参与信息DAO
 * 
 * @author hexy
 * @since 2017-7-3 9:36
 */
public abstract class PrmtSignupInfoDAO extends
		HibernateSupportDelegateDAO<PrmtSignupInfoEntity> {

	private static final Logger logger = LoggerFactory.getLogger(PrmtSignupInfoDAO.class);

	public PrmtSignupInfoDAO() {
		super();
		this.setEntityName(PrmtSignupInfoEntity.class.getName());
		this.setKeyField("id");
	}

	/**
	 * 更新活动参与信息表状态
	 * 
	 * @return entity
	 */
	public PrmtSignupInfoEntity updateRequestStatus(PrmtSignupInfoEntity entity) {
		logger.info("PrmtSignupInfoDAO.updateRequestStatus() entity:"+JSONObject.toJSONString(entity));
		return update(entity);
	}

	/**
	 * 保存活动参与信息
	 *
	 * @param record
	 * @return
	 */
	public PrmtSignupInfoEntity savePrmtSignupInfo(PrmtSignupInfoEntity entity) {
		logger.info("PrmtSignupInfoDAO.savePrmtSignupInfo() entity:"+JSONObject.toJSONString(entity));
		Date d = new Date();
		entity.setActiveFlag(true);
		entity.setSignupTime(d);
		return save(entity);
	}

	/**
	 * 分页查询活动参与信息
	 * 
	 * @param prmtCode
	 * @param prmtStatus
	 * @param signupUserId
	 * @param start
	 * @param limit
	 * @return list entity
	 */
	@DAOMethod(sql = "FROM PrmtSignupInfoEntity WHERE prmtCode = :prmtCode AND prmtStatus = :prmtStatus AND signupUserId = :signupUserId AND  activeFlag = 1 ORDER BY signupTime DESC")
	public abstract List<PrmtSignupInfoEntity> getPrmtSignupInfoByQueryVO(
			@DAOParam("prmtCode") String prmtCode,
			@DAOParam("prmtStatus") Integer prmtStatus,
			@DAOParam("signupUserId") String signupUserId,
			@DAOParam(pageStart = true) int start,
			@DAOParam(pageLimit = true) int limit);

	/**
	 * 根据活动code,活动状态,参与人信息查询活动参与信息
	 * 
	 * @param signupUserId
	 * @param prmtCode
	 * @param prmtStatus
	 * @return list entity
	 */
	@DAOMethod(sql = "FROM PrmtSignupInfoEntity WHERE signupUserId = :signupUserId AND prmtCode = :prmtCode AND prmtStatus = :prmtStatus AND activeFlag = 1")
	public abstract List<PrmtSignupInfoEntity> findSignupInfoByQueryVO(
			@DAOParam("signupUserId") String signupUserId,
			@DAOParam("prmtCode") String prmtCode,
			@DAOParam("prmtStatus") Integer prmtStatus);
}
