package eh.base.dao;

import ctd.dictionary.DictionaryItem;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.persistence.support.impl.dictionary.DBDictionaryItemLoader;
import eh.entity.base.AddrArea;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jianghc
 * @create 2017-02-13 09:49
 **/
public abstract  class AddrAreaDAO extends HibernateSupportDelegateDAO<AddrArea>
        implements DBDictionaryItemLoader<AddrArea> {

    public AddrAreaDAO() {
        super();
        setEntityName(AddrArea.class.getName());
        setKeyField("Id");
    }

    @Override
    public List<DictionaryItem> findAllDictionaryItem(int start, int limit) {
        List<AddrArea> list = this.findAllAddrArea(start, limit);
        if (list == null) {
            return null;
        }
        List<DictionaryItem> dicList = new ArrayList<DictionaryItem>();
        for (AddrArea addrArea : list) {
            dicList.add(this.changeType(addrArea));
        }
        return dicList;
    }

    @Override
    public DictionaryItem getDictionaryItem(Object id) {
        AddrArea addrArea = this.get(id);
        return this.changeType(addrArea);
    }

    private DictionaryItem changeType(AddrArea addrArea){
        if (addrArea == null) {
            return null;
        }
        DictionaryItem dictionaryItem = new DictionaryItem();
        String key = addrArea.getId();
        dictionaryItem.setKey(key);
        dictionaryItem.setText(addrArea.getText());
        int length = key.trim().length();
        if ( length> 2) {
            dictionaryItem.setParent(key.substring(0, key.length() - 2));
        }
        dictionaryItem.setLeaf(addrArea.getLeaf());
        return dictionaryItem;
    }

    @DAOMethod(sql = " from AddrArea")
    public abstract List<AddrArea> findAllAddrArea(@DAOParam(pageStart = true) int start,
                                                   @DAOParam(pageLimit = true) int limit);

    @DAOMethod(sql = " from AddrArea where id like :id")
    public abstract List<AddrArea> findChildById(@DAOParam("id") String id);

    @DAOMethod(sql = " from AddrArea where text=:text")
    public abstract List<AddrArea> findAreaByText(@DAOParam("text") String text);

    @DAOMethod(sql = " from AddrArea where text=:text and id like :id")
    public abstract List<AddrArea> findAreaByTextWithLike(@DAOParam("text") String text, @DAOParam("id") String id);



    public QueryResult<AddrArea> queryByStartAndLimit(final int start, final int limit) {
        HibernateStatelessResultAction<QueryResult<AddrArea>> action = new AbstractHibernateStatelessResultAction<QueryResult<AddrArea>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                long total = 0;
                StringBuilder hql = new StringBuilder("FROM AddrArea  WHERE 1=1 ");
                Query query = ss.createQuery("SELECT count(*) " + hql.toString());
                total = (long) query.uniqueResult();//获取总条数
                query = ss.createQuery(hql.toString());
                query.setFirstResult(start);
                query.setMaxResults(limit);
                setResult(new QueryResult<AddrArea>(total, query.getFirstResult(), query.getMaxResults(), query.list()));
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }



}
