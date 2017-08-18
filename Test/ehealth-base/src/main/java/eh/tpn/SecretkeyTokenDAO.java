package eh.tpn;

import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONObject;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.base.SecretkeyTokenEntity;

/**
 * 秘钥令牌关系
 * @author hexy
 * @since 2017-7-17 9:36
 */
public abstract class SecretkeyTokenDAO extends HibernateSupportDelegateDAO<SecretkeyTokenEntity>{
	
	private static final Logger logger = LoggerFactory.getLogger(SecretkeyTokenDAO.class);
	
    public SecretkeyTokenDAO() {
        super();
        this.setEntityName(SecretkeyTokenEntity.class.getName());
        this.setKeyField("id");
    }
    
	
	public SecretkeyTokenEntity updateSecretkeyToken(SecretkeyTokenEntity entity) {
		logger.info("SecretkeyTokenDAO.updateSecretkeyToken() entity:"+JSONObject.toJSONString(entity));
		entity.setUpdateTime(new Date());
		return update(entity);
	}

	
	public SecretkeyTokenEntity saveSecretkeyToken(SecretkeyTokenEntity entity) {
		logger.info("SecretkeyTokenDAO.savePrSecretkeyToken() entity:"+JSONObject.toJSONString(entity));
		Date date = new Date();
		entity.setActiveFlag(true);
		entity.setCreateTime(date);
		return save(entity);
	}

	
	@DAOMethod(sql = "FROM SecretkeyTokenEntity WHERE  token = :token AND status = 1 AND activeFlag = 1 ORDER BY createTime DESC ")
	public abstract List<SecretkeyTokenEntity> findByKey(@DAOParam("token") String token);
}
