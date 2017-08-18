package eh.base.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.impl.dictionary.DBDictionaryItemLoader;
import ctd.util.annotation.RpcService;
import eh.entity.base.UserRoles;

import java.util.List;

public abstract class UserRolesDAO extends
		HibernateSupportDelegateDAO<UserRoles> implements
		DBDictionaryItemLoader<UserRoles> {

	public UserRolesDAO() {
		super();
		this.setEntityName(UserRoles.class.getName());
		this.setKeyField("id");
	}

	/**
	 * 根据机构层级编码获取用户角色（供 OrganDAO updateOrgan使用）
	 * 
	 * @author luf
	 * @param manageUnit
	 *            -- 机构层级编码
	 * @return List<UserRoles>
	 */
	@RpcService
	@DAOMethod(limit = 10)
	public abstract List<UserRoles> findByManageUnit(String manageUnit);

	/**
	 * 根据手机号获取urt列表
	 * @param userId
	 * @return
     */
	@RpcService
	@DAOMethod(sql = "FROM UserRoles where userId = :userId " )
	public abstract List<UserRoles> findUrtByUserId(@DAOParam("userId") String userId);

    @DAOMethod(sql = " FROM UserRoles where userId = :userId and roleId=:roleId ")
	public abstract List<UserRoles> findByUserIdAndRoleId(@DAOParam("userId")String userId,@DAOParam("roleId")String roleId);

	@RpcService
	@DAOMethod
	public abstract UserRoles getByUserIdAndRoleId(String userId,String RoleId);

	@DAOMethod(limit = 0,sql = "select DISTINCT userId FROM UserRoles where manageUnit =:manageUnit")
	public abstract List<String> findAllByManageUnit(@DAOParam("manageUnit")String manageUnit);


	@DAOMethod(sql = " update UserRoles set manageUnit =:manageUnit  where manageUnit =:oldManageUnit")
	public abstract void updateManageUnit(@DAOParam("oldManageUnit")String oldManageUnit,@DAOParam("manageUnit")String manageUnit);

	@DAOMethod(sql = "select doctorId from Doctor where mobile in ( select userId from UserRoles where id in :DoctorIds and roleId = 'doctor' )")
	public abstract List<Integer> findDoctorListByUrtIdList(@DAOParam("DoctorIds")List<Integer> DoctorIds);
}
