package eh.op.service;

import com.alibaba.druid.util.StringUtils;
import ctd.account.thirdparty.ThirdParty;
import ctd.account.thirdparty.ThirdPartyProps;
import ctd.mvc.controller.support.LogonManager;
import ctd.mvc.controller.util.ThirdPartyProvider;
import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.persistence.support.impl.thirdparty.ThirdPartyDao;
import ctd.persistence.support.impl.thirdparty.ThirdPartyPropsDao;
import ctd.util.BeanUtils;
import ctd.util.annotation.RpcService;
import ctd.util.converter.ConversionUtils;
import eh.base.dao.ClientConfigDAO;
import eh.base.service.BusActionLogService;
import eh.entity.pay.PaymentClient;
import eh.entity.wx.WXConfig;
import eh.pay.dao.PaymentClientDAO;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author jianghc
 * @create 2017-04-18 16:16
 **/
public class ThirdPartyOpService {

    @RpcService
    public QueryResult<ThirdParty> queryThirdPartysWithPage(final int start, final int limit) {
        ThirdPartyPropsDao thirdPartyPropsDao = DAOFactory.getDAO(ThirdPartyPropsDao.class);
        AbstractHibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            public void execute(StatelessSession ss) throws Exception {
                StringBuffer hqlSum = new StringBuffer("SELECT count(*) FROM ThirdParty WHERE 1=1 ");
                StringBuffer hql = new StringBuffer("FROM ThirdParty WHERE 1=1 ");
                Query querySum = ss.createQuery(hqlSum.toString());
                Query query = ss.createQuery(hql.toString());
                query.setFirstResult(start);
                query.setMaxResults(limit);
                QueryResult queryResult = new QueryResult(start, limit);
                queryResult.setTotal((long) ((Integer) ConversionUtils.convert(querySum.uniqueResult(), Integer.TYPE)).intValue());
                queryResult.setItems(query.list());
                this.setResult(queryResult);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        QueryResult queryResult = (QueryResult) action.getResult();
        List appList = queryResult.getItems();
        if (appList != null) {
            Iterator var8 = appList.iterator();
            while (var8.hasNext()) {
                ThirdParty app = (ThirdParty) var8.next();
                List<ThirdPartyProps> propss = thirdPartyPropsDao.findByAppkey(app.getAppkey());
                Map<String, Object> map = new HashMap<String, Object>();
                Iterator<ThirdPartyProps> iterator = propss.iterator();
                while (iterator.hasNext()) {
                    ThirdPartyProps props = (ThirdPartyProps) iterator.next();
                    if (props != null) {
                        map.put(props.getPropName(), props.getPropValue());
                    }
                }
                app.setProps(map);
            }
        }
        return queryResult;
    }


    @RpcService
    public ThirdParty saveOrUpdateThirdParty(ThirdParty thirdParty) {
        if (thirdParty == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, " ThirdParty is require");
        }
        String appKey = thirdParty.getAppkey();
        if (StringUtils.isEmpty(appKey)) {
            throw new DAOException(DAOException.VALUE_NEEDED, " ThirdParty.appKey is require");
        }
        String name = thirdParty.getName();
        if (StringUtils.isEmpty(name)) {
            throw new DAOException(DAOException.VALUE_NEEDED, " ThirdParty.name is require");
        }
        ThirdPartyDao thirdPartyDao = DAOFactory.getDAO(ThirdPartyDao.class);
        ThirdPartyPropsDao thirdPartyPropsDao = DAOFactory.getDAO(ThirdPartyPropsDao.class);
        ThirdPartyProvider provider = LogonManager.instance().getThirdPartyProvider();
        ThirdParty tp = provider.get(appKey);
        Map<String, Object> map = thirdParty.getProps();
        if (tp == null) {//create
            thirdParty.setStatus("1");
            thirdParty = thirdPartyDao.save(thirdParty);
            Map<String, Object> newMap = new HashMap<String, Object>();
            if (map != null) {
                Iterator iterator = map.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry entry = (Map.Entry) iterator.next();
                    ThirdPartyProps partyProps = new ThirdPartyProps();
                    partyProps.setAppkey(appKey);
                    String key = (String) entry.getKey();
                    String value = (String) entry.getValue();
                    partyProps.setPropName(key);
                    partyProps.setPropValue(value);
                    newMap.put(key, value);
                    thirdPartyPropsDao.save(partyProps);
                    thirdParty.setProps(newMap);
                }
            }
            BusActionLogService.recordBusinessLog("第三方管理", appKey, "ThirdParty", "新增第三放应用【" + name + "】，key【" + appKey + "】");
        } else {//update
            Map<String, Object> tpMap = tp.getProps();
            if (map == null || map.isEmpty()) {
                this.deleteThirdPartyPropsByAppKey(appKey);
            } else {
                Iterator iterator = map.entrySet().iterator();
                Iterator iterator2 = tpMap.entrySet().iterator();
                while (iterator2.hasNext()) {
                    Map.Entry entry = (Map.Entry) iterator2.next();
                    String key = (String) entry.getKey();
                    if (map.get(key) == null) {
                        this.deleteThirdPartyPropsByAppKeyAndPropName(appKey, key);
                    }
                }
                while (iterator.hasNext()) {//需要保存
                    Map.Entry entry = (Map.Entry) iterator.next();
                    String key = (String) entry.getKey();
                    String value = (String) entry.getValue();
                    if (tpMap.get(key) == null) {
                        ThirdPartyProps partyProps = new ThirdPartyProps();
                        partyProps.setAppkey(appKey);
                        partyProps.setPropName(key);
                        partyProps.setPropValue(value);
                        thirdPartyPropsDao.save(partyProps);
                    } else {
                        this.updateThirdPartyProps(appKey, key, value);
                    }
                }
                BeanUtils.map(thirdParty, tp);
                thirdPartyDao.update(tp);
            }
            BusActionLogService.recordBusinessLog("第三方管理", appKey, "ThirdParty", "更新第三放应用【" + name + "】，key【" + appKey + "】");
        }
        provider.reload(appKey);
       // DAOFactory.getDAO(ClientConfigDAO.class).addOneClient(6, ThirdParty.class.getName(), thirdParty.getId(), thirdParty.getName());
        return thirdParty;
    }

    private Integer deleteThirdPartyPropsByAppKey(final String appKey) {
        HibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuffer hql = new StringBuffer("delete from ThirdPartyProps where appkey =:appkey");
                Query query = ss.createQuery(hql.toString());
                query.setParameter("appkey", appKey);
                setResult(query.executeUpdate());
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    private Integer deleteThirdPartyPropsByAppKeyAndPropName(final String appKey, final String propName) {
        HibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuffer hql = new StringBuffer("delete from ThirdPartyProps where appkey =:appkey and propName=:propName");
                Query query = ss.createQuery(hql.toString());
                query.setParameter("appkey", appKey);
                query.setParameter("propName", propName);
                setResult(query.executeUpdate());
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }


    private Boolean updateThirdPartyProps(final String appKey, final String name, final String value) {
        HibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuffer hql = new StringBuffer("update ThirdPartyProps set propValue =:propValue where appkey =:appkey and propName=:propName");
                Query query = ss.createQuery(hql.toString());
                query.setParameter("propValue", value);
                query.setParameter("appkey", appKey);
                query.setParameter("propName", name);
                query.executeUpdate();
                setResult(true);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

}

