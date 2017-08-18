package eh.base.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcService;
import eh.entity.base.OrganMedicalItem;

import java.util.List;

/**
 *  机构医疗项目
 * @author ZX
 */
public abstract class OrganMedicalItemDAO extends HibernateSupportDelegateDAO<OrganMedicalItem>{

	public OrganMedicalItemDAO() {
		super();
		this.setEntityName(OrganMedicalItem.class.getName());
		this.setKeyField("oraganItemId");
	}
	
	/**
	 * 科室常用项目列表查询服务
	 * 备注：住院转诊确认时，选择检查项目时使用
	 * @author ZX
	 * @date 2015-4-14  下午3:24:19
	 * @param organId 机构序号
	 * @param departId 科室序号
	 * @param useType 项目使用类别（1入院检查项目2特殊检查项目）
	 * @return
	 */
	@RpcService
	@DAOMethod(sql = "select o from OrganMedicalItem o,DepartMedicalItem d where o.organItemId=d.organItemId and o.organId=:organId and d.departId=:departId and d.itemUseType=:useType")
	public abstract List<OrganMedicalItem> findByOrganIdAndDepartIdAndUseType(@DAOParam("organId") int organId,@DAOParam("departId") int departId,@DAOParam("useType") int useType);
	
	/**
	 * 项目名称查询服务
	 * @author ZX
	 * @date 2015-4-14  下午3:24:19
 	 * @param organItemCode 项目代码
	 * @param organId 机构序号
	 * @param departId 科室序号
	 * @param useType 项目使用类别（1入院检查项目2特殊检查项目）
	 * @return
	 */
	@RpcService
	@DAOMethod(sql = "select o.organItemName from OrganMedicalItem o,DepartMedicalItem d where o.organItemId=d.organItemId and o.organId=:organId and d.departId=:departId and d.itemUseType=:useType and o.organItemCode=:organItemCode")
	public abstract String getOrganItemNameByOrganItemCode( @DAOParam("organItemCode") String organItemCode,@DAOParam("organId") int organId,@DAOParam("departId") int departId,@DAOParam("useType") int useType);
}
