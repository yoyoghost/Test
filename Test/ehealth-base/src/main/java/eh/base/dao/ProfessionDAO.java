package eh.base.dao;

import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.dictionary.DictionaryItem;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.persistence.support.impl.dictionary.DBDictionaryItemLoader;
import eh.entity.base.Profession;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by luf on 2016/10/6.
 */

public abstract class ProfessionDAO extends HibernateSupportDelegateDAO<Profession>
        implements DBDictionaryItemLoader<Profession> {

    private final static String dicName = "eh.base.dictionary.Profession";

    public ProfessionDAO() {
        super();
        this.setEntityName(Profession.class.getName());
        this.setKeyField("key");
    }

    @DAOMethod(sql = " from Profession order by leaf,orderNum desc,key asc")
    public abstract List<Profession> findAllProfession(@DAOParam(pageLimit = true) int limit, @DAOParam(pageStart = true) int start);

    @Override
    public List<DictionaryItem> findAllDictionaryItem(int start, int limit) {
        List<Profession> list = this.findAllProfession(limit, start);
        if (list == null || list.isEmpty()) {
            return null;
        }
        List<DictionaryItem> items = new ArrayList<DictionaryItem>();
        for (Profession profession : list) {
            String key = profession.getKey();
            DictionaryItem dictionaryItem = new DictionaryItem(key, profession.getText());
            dictionaryItem.setLeaf(profession.getLeaf());
            dictionaryItem.setParent(profession.getParent());
            items.add(dictionaryItem);
        }
        return items;
    }

    @Override
    public DictionaryItem getDictionaryItem(Object key) {
        String strKey = (String) key;
        Profession profession = this.getByKey(strKey);
        DictionaryItem dictionaryItem = new DictionaryItem(key, profession.getText());
        dictionaryItem.setLeaf(profession.getLeaf());
        dictionaryItem.setParent(profession.getParent());
        return dictionaryItem;
    }

    /**
     * 根据文字反查key值
     *
     * @param text
     * @return
     */
    public List<String> findKeysByText(final String text) {
        HibernateStatelessResultAction<List<String>> action = new AbstractHibernateStatelessResultAction<List<String>>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                StringBuffer hql = new StringBuffer("select key from Profession where text like :text");
                Query q = statelessSession.createQuery(hql.toString());
                q.setParameter("text", "%" + text + "%");
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    @DAOMethod(sql = " from Profession where key = :key")
    public abstract Profession getByKey(@DAOParam("key") String key);

    @DAOMethod(sql = " from Profession where key like :key")
    public abstract List<Profession> findLikeKey(@DAOParam("key") String key);


    @DAOMethod(sql = " update Profession set leaf=:leaf where key = :key")
    public abstract void updateLeafByKey(@DAOParam("key") String key, @DAOParam("leaf") Boolean leaf);

    public void reloadDictionary() {
        try {
            DictionaryController.instance().getUpdater().reload(dicName);
        } catch (ControllerException e) {
            throw new DAOException("专科字典刷新失败：" + e.getMessage());
        }
    }
}
