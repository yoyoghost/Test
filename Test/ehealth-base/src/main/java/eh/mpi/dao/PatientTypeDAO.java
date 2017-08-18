package eh.mpi.dao;

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
import ctd.util.annotation.RpcService;
import eh.entity.mpi.PatientType;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.*;

/**
 * Created by luf on 2016/5/5.
 */

public abstract class PatientTypeDAO extends HibernateSupportDelegateDAO<PatientType>
        implements DBDictionaryItemLoader<PatientType> {

    public PatientTypeDAO() {
        super();
        this.setEntityName(PatientType.class.getName());
        this.setKeyField("key");
    }

    @Override
    public List<DictionaryItem> findAllDictionaryItem(final int start, final int limit) {
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        " from PatientType");
                Query query = ss.createQuery(hql.toString());
                query.setFirstResult(start);
                query.setMaxResults(limit);
                List<PatientType> list = query.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<PatientType> typeList = (List<PatientType>) action.getResult();

        List<DictionaryItem> dicList = new ArrayList<DictionaryItem>();
        for (PatientType type : typeList) {
            DictionaryItem item = new DictionaryItem();

            Boolean inputCardNo = type.getInputCardNo();

            if (inputCardNo != null && inputCardNo) {
                HashMap<String, Object> properties = new HashMap<String, Object>();
                properties.put("inputCardNo", inputCardNo);
                item.setProperties(properties);
            }

            item.setKey(type.getKey());
            item.setText(type.getText());

            dicList.add(item);
        }
        return dicList;
    }

    @Override
    public DictionaryItem getDictionaryItem(Object key) {
        PatientType type = get(key);

        DictionaryItem item = new DictionaryItem();

        HashMap<String, Object> properties = new HashMap<String, Object>();
        properties.put("inputCardNo", type.getInputCardNo());

        item.setKey(type.getKey());
        item.setText(type.getText());
        item.setProperties(properties);

        return item;
    }


    @DAOMethod
    public abstract List<PatientType> findByAddrAreaLike(String addr);

    @DAOMethod
    public abstract List<PatientType> findByAddrArea(String addr);

    /**
     * 此方法不适用，不能用get方法获取区域
     *
     * @param addrArea
     * @return
     * @author luf:2016-10-28
     */
//    @DAOMethod
//    public abstract PatientType getByAddrArea(String addr);
    @DAOMethod(sql = "select key from PatientType where addrArea=:addrArea")
    public abstract String getKeyByAddrArea(@DAOParam("addrArea") String addrArea);

    /**
     * 根据地区获取医保类型
     *
     * @param addr
     * @return
     * @author luf
     */
    @RpcService
    public List findTypeByAddr(String addr) {
        List<PatientType> results = this.findByAddrAreaLike(addr);
        if (results == null || results.isEmpty()) {
            results = new ArrayList<PatientType>();
        }
        int length = addr.length();
        while (addr.length() > 1) {
            length--;
            addr = addr.substring(0, length);
            List<PatientType> types = this.findByAddrArea(addr);
            for (PatientType type : types) {
                if (type != null && !StringUtils.isEmpty(type.getKey())) {
                    //2016-12-15 17:49:04 zhangx wx2.7-2422 后台开发：所有医保类型都要填医保卡号,医生端暂时不做修改
                    //2017-1-19 09:18:15 zhangx wx2.7-2728 后台开发：医保类型修改-去掉所有医保都要填医保卡号
//                    type.setInputCardNo(true);
                    results.add(type);
                }
            }
        }
        Collections.sort(results, new Comparator<PatientType>() {
            public int compare(PatientType arg0, PatientType arg1) {
                return arg0.getKey().compareTo(arg1.getKey());
            }
        });
        return results;
    }


    public QueryResult<PatientType> queryByStartAndLimit(final int start, final int limit) {
        HibernateStatelessResultAction<QueryResult<PatientType>> action = new AbstractHibernateStatelessResultAction<QueryResult<PatientType>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                long total = 0;
                StringBuilder hql = new StringBuilder("FROM PatientType  WHERE 1=1 ");
                Query query = ss.createQuery("SELECT count(*) " + hql.toString());
                total = (long) query.uniqueResult();//获取总条数
                query = ss.createQuery( hql.toString());
                query.setFirstResult(start);
                query.setMaxResults(limit);
                setResult(new QueryResult<PatientType>(total, query.getFirstResult(), query.getMaxResults(), query.list()));
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }





}
