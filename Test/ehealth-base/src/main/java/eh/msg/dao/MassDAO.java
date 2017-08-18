package eh.msg.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcService;
import eh.entity.mpi.Patient;
import eh.entity.msg.Mass;

import java.util.List;

public abstract class MassDAO extends HibernateSupportDelegateDAO<Mass> {

	public MassDAO() {
		super();
		this.setEntityName(Mass.class.getName());
		this.setKeyField("id");
	}

	/**
	 * 根据父表主键查询子表数据
	 * 
	 * @param rootId
	 *            主表主键
	 * @return
	 */
	@RpcService
	@DAOMethod(limit = 0)
	public abstract List<Mass> findByRootId(int rootId);

    @RpcService
    public Mass saveMass(Mass mass){
        return super.save(mass);
    }

	/**
	 *获取病人信息
	 * 
	 * @param rootId
	 *            主表ID
	 * @param flag
	 *            信息发送状态
	 * @return
	 */
	@RpcService
	@DAOMethod(sql = "select p from Mass m,Patient p  where m.mpiId = p.mpiId and m.rootId=:rootId and m.flag =:flag", limit = 0)
	public abstract List<Patient> findSumByRootId(@DAOParam("rootId") int rootId,
			@DAOParam("flag") boolean flag);

	/**
	 * 更新子表数据
	 * @param mass
	 *               子表信息
	 */
	@RpcService
	public void updateFlagByMass(Mass mass){
		this.update(mass);
	}
	
}
