package eh.bus.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcService;
import eh.entity.bus.OrganNetManager;

import java.util.List;

public abstract class OrganNetManagerDAO extends HibernateSupportDelegateDAO<OrganNetManager> {
	public OrganNetManagerDAO() {
		super();
		this.setEntityName(OrganNetManager.class.getName());
		this.setKeyField("id");
	}

	/**
	 * 根据状态查询网络监听管理员OLD  默认邵逸夫
	 * @param status
	 * @return
     */
	@RpcService
	@DAOMethod(sql="from OrganNetManager where status=:status and organ=1")
	public abstract List<OrganNetManager> findByStatus(@DAOParam("status") int status);

	/**
	 * 根据机构查询网络监听管理员
	 * @param organ
	 * @return
	 */
	@RpcService
	@DAOMethod(sql="from OrganNetManager where status=1 and organ=:organ")
	public abstract List<OrganNetManager> findByOrgan(@DAOParam("organ") int organ);
}
