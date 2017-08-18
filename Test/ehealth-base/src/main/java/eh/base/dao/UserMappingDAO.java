package eh.base.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcService;
import eh.entity.base.UserMapping;

public abstract class UserMappingDAO extends HibernateSupportDelegateDAO<UserMapping>{
	
	public UserMappingDAO(){
		super();
		this.setEntityName(UserMapping.class.getName());
		this.setKeyField("id");
	}
	
	/**
	 * 获取有效的记录
	 * @author zhangx
	 * @date 2015-12-2 下午9:19:16
	 * @param openId
	 * @return
	 */
	@RpcService
	@DAOMethod(sql="from UserMapping where openId=:openId and mappingType=:mappingType and status=1")
	public abstract UserMapping getByEffectiveUserMapping(@DAOParam("openId") String openId,@DAOParam("mappingType") Integer mappingType);
	
}
