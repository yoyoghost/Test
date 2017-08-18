package eh.base.dao;

import ctd.dictionary.DictionaryItem;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.impl.dictionary.DBDictionaryItemLoader;
import eh.entity.base.ProTitle;

import java.util.List;

/**
 * @author jianghc
 * @create 2017-07-13 10:10
 **/
public abstract class ProTitleDAO extends HibernateSupportDelegateDAO<ProTitle>
        implements DBDictionaryItemLoader<ProTitle> {
    public ProTitleDAO() {
        super();
        setEntityName(ProTitle.class.getName());
        setKeyField("id");
    }

    @Override
    @DAOMethod(sql = "select new ctd.dictionary.DictionaryItem(id,text) from ProTitle order by orderNum desc")
    public abstract List<DictionaryItem> findAllDictionaryItem(int start, int limit);

    @Override
    @DAOMethod(sql = "select new ctd.dictionary.DictionaryItem(id,text) from ProTitle where id=:id")
    public abstract DictionaryItem getDictionaryItem(@DAOParam("id") Object key);

    @DAOMethod(sql = " from ProTitle order by orderNum desc ")
    public abstract List<ProTitle> findAllProTitle();
}
