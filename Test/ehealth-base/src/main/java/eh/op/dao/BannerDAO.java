package eh.op.dao;

import ctd.account.UserRoleToken;
import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.BeanUtils;
import ctd.util.annotation.RpcService;
import eh.base.dao.DoctorDAO;
import eh.entity.msg.Banner;
import eh.entity.msg.BannerAndOrgans;
import eh.entity.msg.BannerTarget;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import org.springframework.util.ObjectUtils;

import java.util.Date;
import java.util.List;

/**
 * Created by houxr on 2016/6/20.
 * 载入页|Banner 服务
 */
public class BannerDAO extends HibernateSupportDelegateDAO<Banner> {

    public static final Logger log = Logger.getLogger(BannerDAO.class);

    public BannerDAO() {
        super();
        this.setEntityName(Banner.class.getName());
        this.setKeyField("bannerId");
    }

    /**
     * 保存Banner记录
     *
     * @param banner
     * @return
     * @author houxr
     */
    public Banner save(Banner banner) {
        if (StringUtils.isEmpty(banner.getBannerName())) {
            new DAOException(DAOException.VALUE_NEEDED, "bannerName is required!");
        }

        UserRoleToken urt = UserRoleToken.getCurrent();
        String name = urt.getUserName();//创建人姓名
        Integer urtIs = urt.getId();//userrolesId
        banner.setUserRolesId(urtIs);
        banner.setUserRolesName(name);
        banner.setCreateTime(new Date());
        banner.setStatus(1);
        super.save(banner);
        if (banner.getBannerId() > 0) {
            return banner;
        }
        return null;
    }

    /**
     * 创建 载入页 或 Banner
     *
     * @param banner
     * @param organs
     * @author houxr
     * @date 2016-06-21 15:30:01
     */
    @RpcService
    public Integer createBannerAndOrgans(Banner banner, List<String> organs) {
        BannerTargetDAO bannerTargetDAO = DAOFactory.getDAO(BannerTargetDAO.class);
        if (banner == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "banner is required!");
        }
        if (banner.getBannerId() != null) {
            throw new DAOException("banner is exist!");
        }
        this.save(banner);
        if (organs != null) {
            for (String organId : organs) {
                BannerTarget bannerTarget = new BannerTarget();
                bannerTarget.setBannerId(banner.getBannerId());
                bannerTarget.setOrganId(organId);
                bannerTargetDAO.save(bannerTarget);
            }
        }
        log.info("create Banner success,bannerId:" + banner.getBannerId());
        return banner.getBannerId();
    }

    @RpcService
    public Boolean updateBannerAndOrgans(Banner banner, List<String> organs) {
        if (banner == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "banner is required!");
        }
        if (banner.getBannerId() == null) {
            throw  new DAOException("bannerId is null!");
        }

        UserRoleToken urt = UserRoleToken.getCurrent();
        String name = urt.getUserName();//修改人姓名
        Integer urtIs = urt.getId();//userrolesId

        Banner target = super.get(banner.getBannerId());
        BeanUtils.map(banner, target);
        target.setLastModify(new Date());
        target.setLastModifyUserRoles(urtIs);
        target.setLastModifyUserName(name);
        super.update(target);

        BannerTargetDAO bannerTargetDAO = DAOFactory.getDAO(BannerTargetDAO.class);
        bannerTargetDAO.removeBannerTargetByBannerId(banner.getBannerId());
        if (organs != null) {
            for (String organId : organs) {
                BannerTarget bannerTarget = new BannerTarget();
                bannerTarget.setBannerId(banner.getBannerId());
                bannerTarget.setOrganId(organId);
                bannerTargetDAO.save(bannerTarget);
            }
        }
        log.info("update Banner success,bannerId:" + banner.getBannerId());
        return true;
    }

    @RpcService
    public QueryResult<BannerAndOrgans> queryBannerByOrganIdAndStatusAndType(final List<String> organs,
                                                                             final Integer status,
                                                                             final String bannerType,
                                                                             final int start,
                                                                             final int limit) {
        if (StringUtils.isEmpty(bannerType)) {
            new DAOException(DAOException.VALUE_NEEDED, "bannerType is required!");
        }
        final BannerTargetDAO bannerTargetDAO = DAOFactory.getDAO(BannerTargetDAO.class);
        HibernateStatelessResultAction<QueryResult<BannerAndOrgans>> action =
                new AbstractHibernateStatelessResultAction<QueryResult<BannerAndOrgans>>() {
                    @SuppressWarnings("unchecked")
                    public void execute(StatelessSession ss) throws DAOException {
                        StringBuilder hql = new StringBuilder(
                                "from Banner b where b.bannerType=:bannerType and b.status<>9");
                        if (organs != null && organs.size() > 0) {
                            hql.append(" and bannerId in ( select t.bannerId from BannerTarget t where t.organId in (:organs))");
                        }
                        if (ObjectUtils.nullSafeEquals(status, 0)) {
                            hql.append(" and b.startDate > now()");
                        } else if (ObjectUtils.nullSafeEquals(status, 1)) {
                            hql.append(" and b.startDate <= now() and b.endDate >= now()");
                        } else if (ObjectUtils.nullSafeEquals(status, 2)) {
                            hql.append(" and b.endDate < now()");
                        }
                        hql.append(" order by b.bannerId desc ");
                        Query countQuery = ss.createQuery("select count(*) " + hql.toString());
                        countQuery.setParameter("bannerType", bannerType);
                        if (organs != null && organs.size() > 0) {
                            countQuery.setParameterList("organs", organs);
                        }
                        Long total = (Long) countQuery.uniqueResult();

                        Query query = ss.createQuery("select new eh.entity.msg.BannerAndOrgans(b) " + hql.toString());
                        query.setParameter("bannerType", bannerType);
                        if (organs != null && organs.size() > 0) {
                            query.setParameterList("organs", organs);
                        }
                        query.setFirstResult(start);
                        query.setMaxResults(limit);

                        List<BannerAndOrgans> list = query.list();

                        for (BannerAndOrgans bao : list) {
                            bao.setTargets(bannerTargetDAO.findBannerTargetByBannerId(bao.getBanner().getBannerId()));
                        }

                        setResult(new QueryResult<BannerAndOrgans>(total, query.getFirstResult(), query.getMaxResults(), list));
                    }
                };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 根据医生内码查询banner或加载页
     *
     * @param doctorId
     * @param bannerType
     * @return
     */
    @RpcService
    public List<Banner> findBannerByDoctorIdAndTypeAndSta(final Integer doctorId, final String bannerType) {
        if (StringUtils.isEmpty(bannerType)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "bannerType is required!");
        }
        if (doctorId == null || doctorId == 0) {
            throw new DAOException(DAOException.VALUE_NEEDED, "doctorId is required!");
        }
        final DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        final Integer organId = doctorDAO.getByDoctorId(doctorId).getOrgan();
        if (organId == null || organId == 0) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organ not exist!");
        }
        HibernateStatelessResultAction<List<Banner>> action = new AbstractHibernateStatelessResultAction<List<Banner>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                StringBuilder hql = new StringBuilder(
                        "from Banner b where b.bannerType=:bannerType and b.status<> 9 and b.startDate <= now() and b.endDate >= now() ");
                hql.append(" and (bannerId in ( select t.bannerId from BannerTarget t where t.organId =:organId) " +
                        " or not exists ( from BannerTarget t where t.bannerId = b.bannerId)) ");
                hql.append(" ORDER BY b.orderNum asc,b.createTime desc ");
                Query query = ss.createQuery(hql.toString());
                query.setParameter("bannerType", bannerType);
                query.setParameter("organId", organId.toString());
                List<Banner> list = query.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List<Banner>) action.getResult();
    }


    /**
     * 根据医生内码查询banner或加载页
     *
     * @param doctorId
     * @param bannerType
     * @return
     */
    @RpcService
    public List<Banner> findBannerByDoctorIdAndType(final Integer doctorId, final String bannerType, final Integer status) {
        if (StringUtils.isEmpty(bannerType)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "bannerType is required!");
        }
        if (null == doctorId || doctorId == 0) {
            throw new DAOException(DAOException.VALUE_NEEDED, "doctorId is required!");
        }
        final DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        final Integer organId = doctorDAO.getByDoctorId(doctorId).getOrgan();
        if (null == organId || organId.equals(0) ) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organ not exist!");
        }
        return  findBannerByPlatform(organId.toString(),bannerType,status);
    }


    /**
     * 根据医生内码查询banner或加载页
     *
     * @param wxConfigId 对应的wxConfig的主键
     * @param bannerType 2启动页 3wxbanner
     * @return
     */
    @RpcService
    public List<Banner> findWxBannerByOrganIdAndTypeAndSta(final Integer wxConfigId, final String bannerType) {
        if (StringUtils.isEmpty(bannerType)) {
           throw new DAOException(DAOException.VALUE_NEEDED, "bannerType is required!");
        }

        HibernateStatelessResultAction<List<Banner>> action = new AbstractHibernateStatelessResultAction<List<Banner>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                StringBuilder hql1 = new StringBuilder(
                        " from Banner b where b.bannerType=:bannerType and b.status<> 9 and b.startDate <= now() and b.endDate >= now() ");
                hql1.append(" and (bannerId in ( select t.bannerId from BannerTarget t where t.organId =:organId) " +
                        " or not exists ( from BannerTarget t where t.bannerId = b.bannerId)) ");
                hql1.append(" ORDER BY b.orderNum asc,b.createTime desc ");

                StringBuilder hql2 = new StringBuilder(
                        " from Banner b where b.bannerType=:bannerType and b.status<> 9 and b.startDate <= now() and b.endDate >= now() ");
                hql2.append(" and (bannerId in ( select t.bannerId from BannerTarget t ) " +
                        " or not exists ( from BannerTarget t where t.bannerId = b.bannerId)) ");
                hql2.append(" ORDER BY b.orderNum asc,b.createTime desc ");

                Query query = ss.createQuery(!ObjectUtils.isEmpty(wxConfigId) ? hql1.toString() : hql2.toString());
                query.setParameter("bannerType", bannerType);

                if (!ObjectUtils.isEmpty(wxConfigId)) {
                    query.setParameter("organId", wxConfigId.toString());
                }
                List<Banner> list = query.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List<Banner>) action.getResult();
    }



    public List<Banner> findBannerByPlatform(final String configId, final String bannerType, final Integer status) {

        if(StringUtils.isEmpty(configId)){
            throw new DAOException(DAOException.VALUE_NEEDED,"configId is require");
        }
        if(StringUtils.isEmpty(bannerType)){
            throw new DAOException(DAOException.VALUE_NEEDED,"bannerType is require");
        }
        if(status==null){
            throw new DAOException(DAOException.VALUE_NEEDED,"status is require");
        }
        HibernateStatelessResultAction<List<Banner>> action = new AbstractHibernateStatelessResultAction<List<Banner>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                StringBuilder hql = new StringBuilder(
                        "from Banner b where b.bannerType=:bannerType and b.status<> 9 ");
                hql.append(" and (bannerId in ( select t.bannerId from BannerTarget t where t.organId =:organId) " +
                        " or not exists ( from BannerTarget t where t.bannerId = b.bannerId)) ");
                if (ObjectUtils.nullSafeEquals(status, 0)) {//未使用
                    hql.append(" and b.startDate > now() ");
                } else if (ObjectUtils.nullSafeEquals(status, 1)) {//在使用
                    hql.append(" and b.startDate <= now() and b.endDate >= now() ");
                } else if (ObjectUtils.nullSafeEquals(status, 2)) {//已过期
                    hql.append(" and b.endDate < now() ");
                }
                hql.append(" ORDER BY b.orderNum asc,b.createTime desc ");
                Query query = ss.createQuery(hql.toString());
                query.setParameter("bannerType", bannerType);
                query.setParameter("organId", configId);
                List<Banner> list = query.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
        return action.getResult();
    }



}
