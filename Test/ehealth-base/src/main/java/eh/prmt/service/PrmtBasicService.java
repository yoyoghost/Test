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
import eh.entity.prmt.PrmtBasicEntity;
import eh.entity.prmt.vo.PrmtQueryVO;
import eh.prmt.dao.PrmtBasicDAO;

/**
 * 
 * @author hexy
 *
 */
public class PrmtBasicService {
	   private static final Log logger = LogFactory.getLog(PrmtBasicService.class);
	 
	   @Resource
	   private PrmtBasicDAO prmtBasicDAO = DAOFactory.getDAO(PrmtBasicDAO.class);
	 
	 
		/**
		 * save prmt object
		 * @param record
		 * @return
		 */
	    @RpcService
		public PrmtBasicEntity savePrmtBasic(PrmtBasicEntity entity) {
			return prmtBasicDAO.savePrmtBasic(entity);
		}
		
	   	 
		/**
		 * update prmt object
		 * @param record
		 * @return
		 */
		@RpcService
		public PrmtBasicEntity updatePrmtBasic(PrmtBasicEntity entity){
			if (null == entity) {
				throw new DAOException(DAOException.VALUE_NEEDED, "entity is null.");
			}
			
			if (null == entity.getId()) {
				throw new DAOException(DAOException.VALUE_NEEDED, "entity id is null.");
			}
			return prmtBasicDAO.updatePrmtBasic(entity);
		}
	 
	    /**
	     * 分页查询活动列表
	     *
	     * @param doctorId
	     * @return
	     */
	    @RpcService
	    public List<HashMap<String, Object>> findByQueryPages(PrmtQueryVO queryVO,Integer start) {
	    	logger.info("PrmtBasicService.findByQueryPages() start "+ queryVO.toString()); 
	    	checkParam(queryVO);
	    	
	    	List<PrmtBasicEntity> result = prmtBasicDAO.findByQueryPages(queryVO.getPrmtCode(), queryVO.getPrmtStatus(), start, 10);
	    	//return object
			List<HashMap<String, Object>> returnList = new ArrayList<HashMap<String, Object>>();
			HashMap<String, Object> map = new HashMap<>();
			map.put("prmtSignupInfoList", result);
			returnList.add(map);
			
	        return returnList;
	    }
	    
	    /**
	     * 查询活动
	     *
	     * @param doctorId
	     * @return
	     */
	    @RpcService
	    public PrmtBasicEntity findByQuery(PrmtQueryVO queryVO) {
	    	logger.info("PrmtBasicService.findByQuery() start "+ queryVO.toString()); 
	    	checkParam(queryVO);
	    	List<PrmtBasicEntity> resultList = prmtBasicDAO.findByQuery(queryVO.getPrmtCode(), queryVO.getPrmtStatus());
	    	
	    	PrmtBasicEntity returnObj = null;
	    	if (CollectionUtils.isNotEmpty(resultList)) {
				returnObj = resultList.get(0);
			}
			logger.info("PrmtBasicService.findByQuery end returnObj:"+JSONObject.toJSONString(returnObj));
			return returnObj;
	    }
	    
	    
	    
		private void checkParam(PrmtQueryVO queryVO) {
			if (null == queryVO) {
				throw new DAOException(DAOException.VALUE_NEEDED, "PrmtQueryVO is null.");
			}
			if (StringUtils.isBlank(queryVO.getPrmtCode())) {
				throw new DAOException(DAOException.VALUE_NEEDED, "prmtCode is blank.");
			}
			if (null == queryVO.getPrmtStatus()) {
				throw new DAOException(DAOException.VALUE_NEEDED, "prmtStatus is null.");
			}
		}

}
