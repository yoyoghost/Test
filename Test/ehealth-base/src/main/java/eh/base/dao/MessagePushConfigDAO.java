package eh.base.dao;

import com.alibaba.fastjson.JSONObject;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import eh.entity.base.MessagePushConfigEntity;
import org.hibernate.StatelessSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

/**
 * 活动基本信息DAO
 * @author hexy
 * @since 2017-7-3 9:36
 */
public abstract class MessagePushConfigDAO extends HibernateSupportDelegateDAO<MessagePushConfigEntity>{
	
	private static final Logger logger = LoggerFactory.getLogger(MessagePushConfigDAO.class);
	
    public MessagePushConfigDAO() {
        super();
        this.setEntityName(MessagePushConfigEntity.class.getName());
        this.setKeyField("id");
    }
    
	/**
	 * 更新配置信息
	 * 
	 * @return entity
	 */
	public MessagePushConfigEntity updateConfig(MessagePushConfigEntity entity) {
		logger.info("MessagePushConfigDAO.updateConfig() entity:"+JSONObject.toJSONString(entity));
		entity.setUpdateTime(new Date());
		return update(entity);
	}

	/**
	 * 保存配置信息
	 *
	 * @param record
	 * @return
	 */
	public MessagePushConfigEntity saveConfig(final MessagePushConfigEntity entity) {
		logger.info("MessagePushConfigDAO.saveConfig() entity:"+JSONObject.toJSONString(entity));
		HibernateStatelessResultAction<MessagePushConfigEntity> action = new AbstractHibernateStatelessResultAction<MessagePushConfigEntity>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
            	Date date = new Date();
        		entity.setActiveFlag(true);
        		entity.setUpdateTime(date);
        		MessagePushConfigEntity oldEntity = save(entity);
        		entity.setConfigCode("000"+oldEntity.getId());
        		update(oldEntity);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
        return action.getResult();
	}
	
	/**
	 * 查询所有有效的配置信息
	 */
	@DAOMethod(sql = "FROM MessagePushConfigEntity WHERE activeFlag = 1 AND configCode != 0001")
	public abstract List<MessagePushConfigEntity> findAllEffective();
	
	/**
	 * 根据code查询唯一配置信息
	 */
	@DAOMethod(sql = "FROM MessagePushConfigEntity WHERE  configCode = :configCode AND activeFlag = 1")
	public abstract List<MessagePushConfigEntity> findConfigByCode(@DAOParam("configCode") String configCode);

	/**
	 * 根据条件查询消息推送配置信息
	 * 
	 * @param deviceType
	 * @param accountType
	 * @return list entity
	 */
	@DAOMethod(sql = "FROM MessagePushConfigEntity WHERE deviceType = :deviceType AND accountType = :accountType AND userType = :userType AND activeFlag = 1 ORDER BY createTime DESC")
	public abstract List<MessagePushConfigEntity> findByQuery(
			@DAOParam("deviceType") Integer deviceType,
			@DAOParam("accountType") Integer accountType,
			@DAOParam("userType") Integer userType);

}
