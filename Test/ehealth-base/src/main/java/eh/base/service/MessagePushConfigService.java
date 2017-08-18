package eh.base.service;

import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.alibaba.fastjson.JSONObject;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.base.dao.MessagePushConfigDAO;
import eh.entity.base.MessagePushConfigEntity;

/**
 * 
 * @author hexy
 *
 */
public class MessagePushConfigService {
	   private static final Log logger = LogFactory.getLog(MessagePushConfigService.class);
	 
	   private MessagePushConfigDAO messagePushConfigDAO = DAOFactory.getDAO(MessagePushConfigDAO.class);
	 
	 
		/**
		 * save MessagePushConfigEntity object
		 * @return
		 */
	    @RpcService
		public MessagePushConfigEntity saveConfig(MessagePushConfigEntity entity) {
	    	logger.info("messagePushConfigDAO.saveConfig () param:"+JSONObject.toJSONString(entity)); 
			return messagePushConfigDAO.saveConfig(entity);
		}
		
	   	 
		/**
		 * update MessagePushConfigEntity object
		 * @param entity
		 * @return
		 */
		@RpcService
		public MessagePushConfigEntity updateConfig(MessagePushConfigEntity entity){
			logger.info("messagePushConfigDAO.updateConfig () param:"+JSONObject.toJSONString(entity)); 
			if (null == entity) {
				throw new DAOException(DAOException.VALUE_NEEDED, "entity is null.");
			}
			
			if (null == entity.getId()) {
				throw new DAOException(DAOException.VALUE_NEEDED, "entity id is null.");
			}
			return messagePushConfigDAO.updateConfig(entity);
		}
	 
	    /**
	     * 根据条件查询消息推送配置信息
	     *
	     * @param deviceType
	     * @param accountType
	     * @param userType
	     * @return list
	     */
	    @RpcService
	    public MessagePushConfigEntity findByQuery(Integer deviceType,Integer accountType,Integer userType) {
	    	logger.info("messagePushConfigDAO.findByQuery () param:{deviceType:"+deviceType+",accountType:"+accountType+"}");
	    	List<MessagePushConfigEntity> returnList = messagePushConfigDAO.findByQuery(deviceType, accountType,userType);
	    	if (CollectionUtils.isNotEmpty(returnList)) {
	    		return returnList.get(0);
	    	}
			return null;
	    }
	    
	    /**
	     * 根据code查询唯一配置信息
	     * @return list
	     */
	    @RpcService
	    public MessagePushConfigEntity findConfigByCode() {
	    	List<MessagePushConfigEntity> result = messagePushConfigDAO.findConfigByCode("0001");
	    	if (CollectionUtils.isNotEmpty(result)) {
	    		return result.get(0);
	    	}
			return null;
	    }
	    
	    /**
	     * 查询所有有效的配置信息
	     * @return entity
	     */
	    @RpcService
	    public List<MessagePushConfigEntity> findAllEffective() {
			return messagePushConfigDAO.findAllEffective();
	    }

}
