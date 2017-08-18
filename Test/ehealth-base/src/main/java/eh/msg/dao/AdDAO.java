package eh.msg.dao;

import ctd.account.UserRoleToken;
import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.annotation.RpcService;
import ctd.util.context.Context;
import ctd.util.context.ContextUtils;
import eh.base.dao.OrganDAO;
import eh.bus.service.common.CurrentUserInfo;
import eh.entity.base.Employment;
import eh.entity.msg.Ad;
import eh.utils.ValidateUtil;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public abstract class AdDAO extends HibernateSupportDelegateDAO<Ad> {

	public AdDAO() {
		super();
		this.setEntityName(Ad.class.getName());
		this.setKeyField("id");
	}

	/**
	 * 保存ad记录
	 * @author LF
	 * @param ad
	 * @return
	 */
	@RpcService
	public Integer saveAd(Ad ad) {
		if(StringUtils.isEmpty(ad.getTitle())) {
			new DAOException(DAOException.VALUE_NEEDED,"title is required!");
		}
		ad.setCreateTime(new Date());
		ad.setStatus(1);
		save(ad);
		if(ad.getId()>0) {
			Integer id = ad.getId();
			return id;
		}
		return null;
	}

	/**
	 * 查询状态为1的所有ad
	 * @author LF
	 * @param status
	 * @return
	 */
	@RpcService
	@DAOMethod
	public abstract List<Ad> findByStatus(Integer status);

	/**
	 * 供 根据层级编码和机构内码查询有效广告列表 调用
	 * @author LF
	 * @param organId
	 * @param status
	 * @return
	 */
	@RpcService
	@DAOMethod(sql="From Ad where organId=:organId and status=:status order by orderNum")
	public abstract List<Ad> findAdByOrIdAndSta(@DAOParam("organId")Integer organId,@DAOParam("status")Integer status);

	/**
	 * 供 根据层级编码和机构内码查询有效广告列表 调用
	 * @author LF
	 * @return
	 */
	@DAOMethod(sql="From Ad where organId=null and status=1 order by orderNum")
	public abstract List<Ad> findAdByOrganIdAndStatus();

	/**
	 * 根据层级编码和机构内码查询有效广告列表
	 * @author LF
	 * @param manageUnit
	 * @param organId
	 * @return
	 */
	@RpcService
	public List<Ad> findAdBymanageUnitAndOrganId(String manageUnit,Integer organId) {
		List<Ad> ads = new ArrayList<Ad>();
		if(organId!=null) {
			ads = findAdByOrIdAndSta(organId, 1);
		}

		if(organId==null || ads.size()<=0) {
			ads = findAdByOrganIdAndStatus();
		}
		return ads;
	}

	/**
	 * 供 三条件查询有效广告列表服务调用
	 * @author LF
	 * @param organId
	 * @param status
	 * @param roleId
	 * @return
	 */
	@DAOMethod(sql="From Ad where organId=:organId and status=:status and roleId=:roleId order by orderNum")
	public abstract List<Ad> findAdByOrganIdAndStatusAndRoleId(
			@DAOParam("organId")Integer organId,@DAOParam("status")Integer status,@DAOParam("roleId")Integer roleId);

	/**
	 * 供 三条件查询有效广告列表服务调用
	 * @author LF
	 * @param status
	 * @param roleId
	 * @return
	 */
	@DAOMethod(sql="From Ad where organId=null and status=:status and roleId=:roleId order by orderNum")
	public abstract List<Ad> findAdByOrganIdNullAndStautsAndRoleId(
			@DAOParam("status")Integer status,@DAOParam("roleId")Integer roleId);

	/**
	 * 三条件查询有效广告列表
	 * @author LF
	 * @param manageUnit
	 * @param organId
	 * @param roleId
	 * @return
	 */
	@RpcService
	public List<Ad> findAdBymanageUnitAndOrganIdAndRoleId(String manageUnit,Integer organId,Integer roleId) {
		List<Ad> ads = new ArrayList<Ad>();
		if(organId==null) {
			UserRoleToken ur = (UserRoleToken) ContextUtils.get(Context.USER_ROLE_TOKEN);
			if(ur!=null){
				Employment employment = (Employment) ur.getProperty("employment");
				organId = employment.getOrganId();
			}

		}
		if(organId!=null) {
			ads = findAdByOrganIdAndStatusAndRoleId(organId, 1, roleId);
		}
		if(organId==null||ads.size()<=0) {
			ads = findAdByOrganIdNullAndStautsAndRoleId(1, roleId);
		}
		return ads;
	}

	/**
	 * 根据id更新photo
	 * @author LF
	 * @param photo
	 * @param id
	 */
	@RpcService
	@DAOMethod
	public abstract void updatePhotoById(String photo,Integer id);

	/**
	 * 根据id更新content
	 * @author LF
	 * @param content
	 * @param id
	 */
	@RpcService
	@DAOMethod
	public abstract void updateContentById(String content,Integer id);

	/**
	 * 广告维护工具1
	 * @author luf
	 */
	public void addOrganAdOnlyOneBanner() {
		@SuppressWarnings("rawtypes")
		HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
			@SuppressWarnings("unchecked")
			public void execute(StatelessSession ss) throws DAOException{
//				String hql = "SELECT DISTINCT organ from Doctor";
//				Query q = ss.createQuery(hql);
//				List<Integer> os = q.list();
				String hql = "SELECT organId,COUNT(*) from Ad where content=\'http://ehealth.easygroup.net.cn/ehealth-base/upload/8061\' GROUP BY organId";
				Query q = ss.createQuery(hql);//膏方节
				List<Object[]> os = q.list();

				String hqlString = "From Ad where content=\'http://ehealth.easygroup.net.cn/ehealth-base/upload/8059\' and organId is null";
				Query query = ss.createQuery(hqlString);
				List<Ad> ads = query.list();

				for(Object[] o:os) {
					Integer organId = (Integer) o[0];
					Long count = (Long) o[1];
					if(organId==null) {
						continue;
					}
					if(count==1) {
						for(Ad ad:ads) {
							ad.setId(null);
							ad.setOrganId(organId);
							ad.setCreateTime(new Date());
							save(ad);
						}
					}
				}
			}
		};
		HibernateSessionTemplate.instance().executeTrans(action);
		action.getResult();
	}

	/**
	 * 为所有医院添加一条banner
	 * @author luf
	 */
	public void addOneAdForAllOrgan() {
		@SuppressWarnings("rawtypes")
		HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
			@SuppressWarnings("unchecked")
			public void execute(StatelessSession ss) throws DAOException{
				String hql = "select organId From Organ where status=1";
				Query q = ss.createQuery(hql);
				List<Integer> is= q.list();

				Ad ad = get(421);
				for(Integer i:is) {
					ad.setId(null);
					ad.setOrganId(i);
					save(ad);
				}
			}
		};
		HibernateSessionTemplate.instance().executeTrans(action);
		action.getResult();
	}

	/**
	 * 微信端banner个性化
	 * @return
	 */
	@RpcService
	public List<Ad> findAdForHealth() {
		String type = "0";//0 全国 1 当前管理单元，包括当前管理单元以下的，搜索按%	2 只限当前管理单元 搜索按=
		String manageUnitId = "eh";
		Map<String, String> wxAppProperties = CurrentUserInfo.getCurrentWxProperties();
		if(wxAppProperties!=null && ValidateUtil.notBlankString(wxAppProperties.get("manageUnitId"))){
			manageUnitId = wxAppProperties.get("manageUnitId");
		}
		OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
		List<Integer> organs = organDAO.findEffOrgansUnitLike(manageUnitId+"%");
		if(organs==null || organs.size()<=0) {
			return new ArrayList<Ad>();
		}
		return findAdBymanageUnitAndOrganIdAndRoleId(null,organs.get(0),2);
	}


	@DAOMethod(sql = " update Ad set manageUnit=:manageUnit where manageUnit=:oldManageUnit")
	public abstract void updateManageUnit(@DAOParam("oldManageUnit")String oldManageUnit,@DAOParam("manageUnit")String manageUnit);

}
