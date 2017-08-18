package eh.prmt.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.annotation.Resource;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.alibaba.fastjson.JSONObject;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.entity.prmt.PrmtSignupInfoEntity;
import eh.entity.prmt.vo.PrmtQueryVO;
import eh.prmt.dao.PrmtSignupInfoDAO;

/**
 * @author hexy
 */
public class PrmtSignupInfoService {
	
	    private static final Log logger = LogFactory.getLog(PrmtSignupInfoService.class);
	    
	    @Resource
	    private PrmtSignupInfoDAO prmtSignupInfoDAO = DAOFactory.getDAO(PrmtSignupInfoDAO.class);
	 	   	 
		/**
		 * save prmt object
		 * @param record
		 * @return
		 */
	    @RpcService
		public PrmtSignupInfoEntity savePrmtSignupInfo(PrmtSignupInfoEntity entity) {
			if (null == entity) {
				throw new DAOException(DAOException.VALUE_NEEDED, "entity is null.");
			}
			List<PrmtSignupInfoEntity> vo = prmtSignupInfoDAO.findSignupInfoByQueryVO(entity.getSignupUserId(), entity.getPrmtCode(), entity.getPrmtStatus());
			if (CollectionUtils.isNotEmpty(vo)) {
				throw new DAOException(DAOException.VALUE_NEEDED, "您已报名,请勿重复报名!");
			}
			return prmtSignupInfoDAO.savePrmtSignupInfo(entity);
		}
		
	   	 
		/**
		 * update prmt object
		 * @param record
		 * @return
		 */
		@RpcService
		public PrmtSignupInfoEntity updatePrmtSignupInfo(PrmtSignupInfoEntity entity){
			if (null == entity) {
				throw new DAOException(DAOException.VALUE_NEEDED, "entity is null.");
			}
			
			if (null == entity.getId()) {
				throw new DAOException(DAOException.VALUE_NEEDED, "entity id is null.");
			}
			return prmtSignupInfoDAO.update(entity);
		}
		
		/**
		 * query prmtSignUpInfo 
		 * @param queryVO
		 * @return
		 */
		@RpcService
		public PrmtSignupInfoEntity findSignupInfoByQueryVO(PrmtQueryVO queryVO) {
			logger.info("PrmtSignupInfoService.findSignupInfoByQueryVO start param:"+queryVO.toString());
			// check param 
			checkParam(queryVO);
			
			//begin query 
			String signupUserId = StringUtils.isBlank(queryVO.getDoctorId())? queryVO.getMpiId() : queryVO.getDoctorId();
			List<PrmtSignupInfoEntity> resultList = prmtSignupInfoDAO.findSignupInfoByQueryVO(signupUserId, queryVO.getPrmtCode(), queryVO.getPrmtStatus());
			
			//return object
			PrmtSignupInfoEntity returnObj = null;
			
			if (CollectionUtils.isNotEmpty(resultList)) {
				returnObj = resultList.get(0);
			}
			logger.info("PrmtSignupInfoService.findSignupInfoByQueryVO end returnObj:"+JSONObject.toJSONString(returnObj));
			return returnObj;
		}
	 
	    /**
	     * Paging query prmt list
	     *
	     * @param doctorId
	     * @return
	     */
	    @RpcService
	    public List<HashMap<String, Object>> getPrmtSignupInfoByQueryVO(PrmtQueryVO queryVO,Integer start) {
	    	logger.info("PrmtSignupInfoService.getPrmtBasicByQueryVO start param"+queryVO.toString());
	    	List<HashMap<String, Object>> result = getPrmtSignupInfoByQueryVO(queryVO, start, 10);
	    	logger.info("PrmtSignupInfoService.getPrmtBasicByQueryVO end result.size = "+JSONObject.toJSONString(result.size()));
	        return result;
	    }

		private List<HashMap<String, Object>> getPrmtSignupInfoByQueryVO(PrmtQueryVO queryVO, Integer start, int i) {
			// check param 
			checkParam(queryVO);
			
			//begin query 
			String signupUserId = StringUtils.isBlank(queryVO.getDoctorId())? queryVO.getMpiId() : queryVO.getDoctorId();
			List<PrmtSignupInfoEntity> result = prmtSignupInfoDAO.getPrmtSignupInfoByQueryVO(queryVO.getPrmtCode(), queryVO.getPrmtStatus(), signupUserId, start, i);
			
			//return object
			List<HashMap<String, Object>> returnList = new ArrayList<HashMap<String, Object>>();
			HashMap<String, Object> map = new HashMap<>();
			map.put("prmtSignupInfoList", result);
			returnList.add(map);
			return returnList;
		}

		private void checkParam(PrmtQueryVO queryVO) {
			if (null == queryVO) {
				throw new DAOException(DAOException.VALUE_NEEDED, "PrmtQueryVO is null.");
			}
			if (StringUtils.isBlank(queryVO.getPrmtCode())) {
				throw new DAOException(DAOException.VALUE_NEEDED, "prmtCode is blank.");
			}
			if (StringUtils.isBlank(queryVO.getMpiId()) && StringUtils.isBlank(queryVO.getDoctorId())) {
				throw new DAOException(DAOException.VALUE_NEEDED, "doctorId and mpiId must pass one.");
			}
			if (null == queryVO.getPrmtStatus()) {
				throw new DAOException(DAOException.VALUE_NEEDED, "prmtStatus is null.");
			}
		}
}
