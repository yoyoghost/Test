/**
 * 
 */
package eh.base.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.annotation.RpcService;
import eh.entity.base.DictProfession;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Eric
 * 
 */
public abstract class DictProfessionDAO extends
		HibernateSupportDelegateDAO<DictProfession> {

	public DictProfessionDAO() {
		super();
	}

	/**
	 * 查询所有的一级专科信息
	 * 
	 * @return
	 */
	@RpcService
	public List<DictProfession> findAllDictProfession() {
		String hql = new String(
				"from DictProfession ppt where ppt.preProfession is null");
		return getDictProfessionByCon(hql, null);

	}

	private List<DictProfession> getDictProfessionByCon(final String hql,
			final Map<String, Object> params) {
		List<DictProfession> list = null;
		HibernateStatelessResultAction<List<DictProfession>> action = new AbstractHibernateStatelessResultAction<List<DictProfession>>() {
			List<DictProfession> list = null;

			@SuppressWarnings("unchecked")
			@Override
			public void execute(StatelessSession ss) throws Exception {
				Query q = ss.createQuery(hql);
				if (params != null && !params.isEmpty()) {
					for (String key : params.keySet()) {
						q.setParameter(key, params.get(key));
					}
				}
				list = q.list();
				setResult(list);
			}
		};
		HibernateSessionTemplate.instance().execute(action);
		list = action.getResult();
		return list;
	}

	/**
	 * 根据专科编码获取该节点下的所有DictProfession信息
	 * 
	 * @param parentId
	 * @return
	 */
	@RpcService
	public List<DictProfession> findChildrenByParentId(String parentId) {
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("parentId", parentId);
		String hql = "from DictProfession ppt where ppt.preProfession=:parentId";
		return getDictProfessionByCon(hql, params);
	}

	/**
	 * 查询专科编码为professionId的专科信息
	 * 
	 * @param professionId
	 * @return
	 */
	@RpcService
	@DAOMethod
	public abstract DictProfession getByProfessiontId(String professionId);
}
