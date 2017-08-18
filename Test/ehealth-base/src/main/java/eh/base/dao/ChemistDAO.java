package eh.base.dao;

import ctd.dictionary.DictionaryItem;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.persistence.support.impl.dictionary.DBDictionaryItemLoader;
import eh.entity.base.Chemist;
import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Created by zhongzx on 2016/5/23 0023.
 */
public abstract class ChemistDAO extends HibernateSupportDelegateDAO<Chemist>
        implements DBDictionaryItemLoader<Chemist> {

    public static final Logger log = Logger.getLogger(ChemistDAO.class);

    public ChemistDAO() {
        super();
        this.setEntityName(Chemist.class.getName());
        this.setKeyField("chemistId");
    }

    @Override
    @DAOMethod(sql = "select new ctd.dictionary.DictionaryItem(chemistId,name) from Chemist order by chemistId")
    public abstract List<DictionaryItem> findAllDictionaryItem(
            @DAOParam(pageStart = true) int start,
            @DAOParam(pageLimit = true) int limit);

    @DAOMethod(sql = "select new ctd.dictionary.DictionaryItem(chemistId,name) from Chemist where chemistId=:id")
    public abstract DictionaryItem getDictionaryItem(@DAOParam("id") Object key);

    @DAOMethod
    public abstract Chemist getByMobile(String mobile);

    @DAOMethod
    public abstract Chemist getByIdNumber(String idNumber);

    @DAOMethod
    public abstract Chemist getByChemistId(int chemistId);

    /**
     * 更新药师照片字段，供updatePhotoByChemistId使用
     * @param photo
     * @param chemistId
     */
    @DAOMethod(sql = "update Chemist set photo=:photo where chemistId =:chemistId")
    public abstract void updatePhotoByChemistId(@DAOParam("photo") int photo, @DAOParam("chemistId") Integer chemistId);

    /**
     * 根据手机号 查询 除了自己以外的药师集合（更新药师信息--手机号 时用到）
     *
     * @return
     */
    @DAOMethod(sql = "from Chemist where mobile=:mobile and chemistId<>:chemistId ")
    public abstract List<Chemist> findByMobile(@DAOParam("mobile") String mobile, @DAOParam("chemistId") Integer chemistId);

    @DAOMethod
    public abstract Chemist getByLoginId(String loginId);


    /**
     * 运营平台药师查询
     *
     * @param keyword 关键字
     * @param status  状态
     * @param start
     * @param limit
     * @return
     */
    public QueryResult<Chemist> queryChemistByKeywordAndStatus(final String keyword,
                                                               final Integer status,
                                                               final int start,
                                                               final int limit) {
        HibernateStatelessResultAction<QueryResult<Chemist>> action = new AbstractHibernateStatelessResultAction<QueryResult<Chemist>>() {

            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder("from Chemist where 1=1");
                if (StringUtils.hasText(keyword)) {
                    hql.append(" and (name like :keyword or mobile like :keyword or idNumber like :keyword or loginId like :keyword)");
                }
                if (null != status) {
                    hql.append(" and status = :status");
                }
                hql.append(" order by chemistId desc");
                Query query = ss.createQuery("select count(*) " + hql.toString());
                if (StringUtils.hasText(keyword)) {
                    query.setParameter("keyword", "%" + keyword + "%");
                }
                if (null != status) {
                    query.setParameter("status", status);
                }
                Long total = (Long) query.uniqueResult();

                query = ss.createQuery(hql.toString());
                if (StringUtils.hasText(keyword)) {
                    query.setParameter("keyword", "%" + keyword + "%");
                }
                if (null != status) {
                    query.setParameter("status", status);
                }
                query.setFirstResult(start);
                query.setMaxResults(limit);
                setResult(new QueryResult<Chemist>(total, query.getFirstResult(), query.getMaxResults(), query.list()));
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }


}
