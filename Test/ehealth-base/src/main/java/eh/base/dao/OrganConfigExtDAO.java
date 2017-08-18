package eh.base.dao;

import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import eh.entity.base.Organ;
import eh.entity.base.OrganConfigExt;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.List;

/**
 * @author jianghc
 * @create 2017-03-23 15:15
 **/
public abstract class OrganConfigExtDAO extends
        HibernateSupportDelegateDAO<OrganConfigExt> {
    private static String CONSULT_SUBSIDY = "0"; //咨询补贴
    private static String CAN_NOT_GIFT = "1"; //不可使用心意

    public OrganConfigExtDAO() {
        super();
        setEntityName(OrganConfigExt.class.getName());
        setKeyField("Id");
    }

    @DAOMethod
    public abstract OrganConfigExt getByExtTypeAndOrganId(String extType, Integer organId);

    @DAOMethod(limit = 0)
    public abstract List<OrganConfigExt> findByOrganId(Integer organId);

    @DAOMethod(limit = 0)
    public abstract List<OrganConfigExt> findByExtType(String extType);

    @DAOMethod(sql = "delete from OrganConfigExt where extType=:extType")
    public abstract void removeByExtType(@DAOParam("extType") String extType);

    @DAOMethod(sql = "delete from OrganConfigExt where extType=:extType and organId not in :o")
    public abstract void removeByExtTypeAndNotInOrganIds(
            @DAOParam("extType") String extType,
            @DAOParam("o") List<Integer> o);

    public OrganConfigExt saveOneExtOrganConfig(OrganConfigExt extOrganConfig) {
        if (extOrganConfig == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "extOrganConfig is require");
        }
        String extType = extOrganConfig.getExtType();
        try {
            if (extType == null || DictionaryController.instance().get("eh.base.dictionary.ExtType").getText(extType) == null) {
                throw new DAOException(DAOException.VALUE_NEEDED, "extType is not exist");
            }
        } catch (ControllerException e) {
            throw new DAOException("extType is not exist");
        }
        Integer organId = extOrganConfig.getOrganId();
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        if (organId == null || !organDAO.exist(organId)) {
            throw new DAOException("organId is not exist");
        }
        if (this.getByExtTypeAndOrganId(extType, organId) != null) {
            throw new DAOException("this config is exist");
        }
        return this.save(extOrganConfig);
    }

    /**
     * 是否可以咨询补贴
     *
     * @param organId
     * @return true 可以 false 不可以
     */
    public Boolean canConsultSubsidy(Integer organId) {
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        if (organId == null || !organDAO.exist(organId)) {
            throw new DAOException("organId is not exist");
        }
        return this.getByExtTypeAndOrganId(this.CONSULT_SUBSIDY, organId) != null ? true : false;
    }

    /**
     * 是否可以心意
     *
     * @param organId
     * @return true 可以 false 不可以
     */
    public Boolean canNotMindGift(Integer organId) {
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        if (organId == null || !organDAO.exist(organId)) {
            throw new DAOException("organId is not exist");
        }
        return this.getByExtTypeAndOrganId(this.CAN_NOT_GIFT, organId) != null ? false : true;
    }


    public QueryResult<Organ> queryOrganForMindGift(boolean canMindGift, final int start, final int limit) {
        StringBuffer sb = new StringBuffer(" from Organ o where 1=1 and o.organId");
        if (canMindGift) {
            sb.append(" not");
        }
        sb.append(" in(SELECT e.organId from OrganConfigExt e where e.extType='")
                .append(this.CAN_NOT_GIFT)
                .append("')");

        final String hql = sb.toString();
        HibernateStatelessResultAction<QueryResult<Organ>> action = new AbstractHibernateStatelessResultAction<QueryResult<Organ>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws Exception {
                Query query = ss.createQuery("SELECT count(o) " + hql);
                long total = (long) query.uniqueResult();//获取总条数
                query = ss.createQuery("select o " + hql + " order by organId");
                query.setFirstResult(start);
                query.setMaxResults(limit);
                setResult(new QueryResult<Organ>(total, start, limit, query.list()));
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }


}
