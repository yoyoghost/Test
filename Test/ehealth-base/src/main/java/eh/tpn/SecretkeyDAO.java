package eh.tpn;

import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONObject;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.base.SecretkeyEntity;

/**
 * tpn相关秘钥
 * @author hexy
 * @since 2017-7-17 9:36
 */
public abstract class SecretkeyDAO extends HibernateSupportDelegateDAO<SecretkeyEntity>{
	
	private static final Logger logger = LoggerFactory.getLogger(SecretkeyDAO.class);
	
    public SecretkeyDAO() {
        super();
        this.setEntityName(SecretkeyEntity.class.getName());
        this.setKeyField("id");
    }
    
	/**
	 * 更新秘钥状态
	 * 
	 * @return entity
	 */
	public SecretkeyEntity updateSecretkey(SecretkeyEntity entity) {
		logger.info("SecretkeyDAO.updateSecretkey() entity:"+JSONObject.toJSONString(entity));
		entity.setUpdateTime(new Date());
		return update(entity);
	}

	/**
	 * 保存秘钥
	 *
	 * @param record
	 * @return
	 */
	public SecretkeyEntity saveSecretkey(SecretkeyEntity entity) {
		logger.info("SecretkeyDAO.savePrSecretkey() entity:"+JSONObject.toJSONString(entity));
		Date date = new Date();
		entity.setActiveFlag(true);
		entity.setCreateTime(date);
		return save(entity);
	}

	/**
	 * 根据秘钥查询具体信息
	 * 
	 * @param secretKey
	 * @return list entity
	 */
	@DAOMethod(sql = "FROM SecretkeyEntity WHERE  secretKey = :secretKey  AND status = 0 AND activeFlag = 1")
	public abstract List<SecretkeyEntity> findByKey(@DAOParam("secretKey") String secretKey);
}
