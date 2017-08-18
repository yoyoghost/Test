package eh.bus.dao;

import ctd.dictionary.DictionaryItem;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.persistence.support.impl.dictionary.DBDictionaryItemLoader;
import ctd.util.annotation.RpcService;
import eh.entity.bus.AppointDepartClass;
import eh.entity.bus.AppointDepartFeature;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public abstract class AppointDepartClassDAO extends
		HibernateSupportDelegateDAO<AppointDepartClass> implements
		DBDictionaryItemLoader<AppointDepartClass> {
	public AppointDepartClassDAO() {
		super();
		this.setEntityName(AppointDepartClass.class.getName());
		this.setKeyField("departClassId");
	}

	/**
	 * @author xyf
	 */
	@Override
	@DAOMethod(sql = "select new ctd.dictionary.DictionaryItem( departClassId,appointDepartName) from AppointDepartClass order by departClassId")
	public abstract List<DictionaryItem> findAllDictionaryItem(
			@DAOParam(pageStart = true) int start,
			@DAOParam(pageLimit = true) int limit);

	/**
	 * @author xyf
	 */
	@DAOMethod(sql = "select new ctd.dictionary.DictionaryItem( departClassId,appointDepartName) from AppointDepartClass where departClassId=:departClassId")
	public abstract DictionaryItem getDictionaryItem(@DAOParam("departClassId") Object key);

	/**
	 * 服务名：获取医院挂号科室列表服务
	 * 
	 * @author xyf
	 * @param ...
	 * @return
	 * @throws DAOException
	 */
	@RpcService
	public List<HashMap<String,Object>> findByOrganIDAndDepartClass(
			final int organID,final String branchId, final int departClass) {
		HibernateStatelessResultAction<List<HashMap<String,Object>>> action = new AbstractHibernateStatelessResultAction<List<HashMap<String,Object>>>() {
			@SuppressWarnings("unchecked")
			@Override
			public void execute(StatelessSession ss) throws Exception {
				
				Query q=null;
				String hql=null;
				if(departClass==0){
					hql = new String(
							"from AppointDepartClass where OrganId=:organID and BranchID=:branchId order by Sort");
					q = ss.createQuery(hql);
				}else{
					hql = new String(
							"from AppointDepartClass where OrganId=:organID and BranchID=:branchId and DepartClass=:departClass order by Sort");
					q = ss.createQuery(hql);
					q.setInteger("departClass", departClass);
				}
				q.setInteger("organID", organID);
				q.setParameter("branchId", branchId);
				List<AppointDepartClass> list = q.list();
				
				List<HashMap<String,Object>> departList =new ArrayList<HashMap<String,Object>>();
				
				for(AppointDepartClass appDepart:list){
					HashMap<String,Object> cell = new HashMap<String,Object>();
					cell.put("dp_id", appDepart.getAppointDepartCode());
					cell.put("dp_name", appDepart.getAppointDepartName());
					cell.put("haveCommon", appDepart.getHaveCommon());
					cell.put("haveExp", appDepart.getHaveExp());
					cell.put("haveSpecial", appDepart.getHaveSpecial());
					cell.put("departClass", appDepart.getDepartClass());
					cell.put("department", appDepart.getDepartment());
					cell.put("pdr_id", appDepart.getPtDoctorId());
					if(appDepart.getHaveSpecial()==1&&appDepart.getHaveSpecial()!=null){
						List<HashMap<String ,Object>> specialList =findByDepartClassID(appDepart.getDeptClassId());
						cell.put("specialList", specialList);
					}
					departList.add(cell);
				}
				setResult(departList);
			}
		};
		
		HibernateSessionTemplate.instance().executeReadOnly(action);
		return (List<HashMap<String,Object>>) action.getResult();
	}
	
	/**
	 * 服务名：根据科室内部ID查询特殊科室列表
	 * 
	 * @author xyf
	 * @param departClassId
	 * @return
	 */
	@RpcService
	public List<HashMap<String ,Object>> findByDepartClassID(final int deptClassId) {
		HibernateStatelessResultAction<List<HashMap<String ,Object>>> action = new AbstractHibernateStatelessResultAction<List<HashMap<String ,Object>>>() {
			@SuppressWarnings("unchecked")
			@Override
			public void execute(StatelessSession ss) throws Exception {
				String hql = new String(
						"from AppointDepartFeature where DeptClassID=:deptClassId and appointDepartCode<>'' and spDoctorId<>'' order by Sort");
				Query q = ss.createQuery(hql);
				q.setParameter("deptClassId", deptClassId);
				List<AppointDepartFeature> list = q.list();
				List<HashMap<String,Object>> applist =new ArrayList<HashMap<String,Object>>();
				
				for(AppointDepartFeature appDepartFeature: list){
					HashMap<String ,Object> specialDepart=new HashMap<String ,Object>();
					specialDepart.put("specialDepartCode", appDepartFeature.getAppointDepartCode());
					specialDepart.put("specialDepartName", appDepartFeature.getAppointDepartName());
					specialDepart.put("spDoctorId", appDepartFeature.getSpDoctorId());//新增特色门诊医生id
					applist.add(specialDepart);
				}
				setResult(applist);
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		return action.getResult();
	}
	
}
