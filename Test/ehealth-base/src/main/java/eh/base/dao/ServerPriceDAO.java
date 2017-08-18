package eh.base.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcService;
import eh.entity.base.ServerPrice;

/**
 * 服务价格
 */
public abstract class ServerPriceDAO extends
		HibernateSupportDelegateDAO<ServerPrice>{

	public ServerPriceDAO() {
		super();
		this.setEntityName(ServerPrice.class.getName());
		this.setKeyField("serverId");
	}
	
	/**
	 * 根据主键查服务价格
	 * @author ZX
	 * @date 2015-4-26  下午5:21:38
	 * @param serverId
	 * @return
	 */
	@RpcService
	@DAOMethod
	public abstract ServerPrice getByServerId(int serverId);
}
