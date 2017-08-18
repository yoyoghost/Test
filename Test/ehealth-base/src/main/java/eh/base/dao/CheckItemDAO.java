package eh.base.dao;

import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.BeanUtils;
import ctd.util.annotation.RpcService;
import eh.bus.dao.CheckSourceDAO;
import eh.entity.base.CheckItem;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.message.BasicNameValuePair;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.*;

public abstract class CheckItemDAO extends HibernateSupportDelegateDAO<CheckItem> {


    public CheckItemDAO() {
        super();
        this.setEntityName(CheckItem.class.getName());
        this.setKeyField("checkItemId");
    }

	/*private static ClassPathXmlApplicationContext appContext;
    static {
		appContext = new ClassPathXmlApplicationContext("spring.xml");
	}
	private static CheckItemDAO dao = appContext.getBean("checkItem",
			CheckItemDAO.class);

	public static void main(String[] args) {
		CheckItemDAO dao = new CheckItemDAO() {
		};
		List<CheckItem> res = dao.findByCheckClass("001");
		System.out.println(JSONUtils.toString(res));
	}*/


    /**
     * 根据字典查询检查项目
     *
     * @param checkClass 检查类型
     * @return
     */
    @RpcService
    public List<CheckItem> findByCheckClass(@DAOParam("checkClass") final String checkClass) {
        HibernateStatelessResultAction<List<CheckItem>> action = new AbstractHibernateStatelessResultAction<List<CheckItem>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = new String("from CheckItem where checkClass = :checkClass ");
                Query q = ss.createQuery(hql);
                q.setParameter("checkClass", checkClass);
                @SuppressWarnings("unchecked")
                List<CheckItem> list = q.list();
//				for(CheckItem item:list){
//					String bodyName = DictionaryController.instance()
//							.get("eh.base.dictionary.Body").getText(item.getCheckBody());
//					item.setBodyName(bodyName);
//				}

                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 获取检查项目，对授权关系进行过滤
     *
     * @param checkClass
     * @param organId
     * @return
     */
    @RpcService
    public List<CheckItem> findByCheckClassIgnoreEmpty(String checkClass, Integer organId) {
        List<CheckItem> dbList = this.findByCheckClass(checkClass);
        List<CheckItem> showList = new ArrayList<>(0);
        //过滤没有授权机构的项目
        CheckSourceDAO checkSourceDAO = DAOFactory.getDAO(CheckSourceDAO.class);
        boolean isNotEmpty;
        for (CheckItem item : dbList) {
            isNotEmpty = checkSourceDAO.checkSourcesByCheckItemIdIsNotEmpty(item.getCheckItemId(), organId);
            if (isNotEmpty) {
                showList.add(item);
            }
        }

        return showList;
    }

    /**
     * 数据校验
     *
     * @param checkItem
     * @throws DAOException
     */
    protected void validateCheckItem(CheckItem checkItem) throws DAOException {
        if (StringUtils.isEmpty(checkItem.getCheckItemName()))
            throw new DAOException(DAOException.VALUE_NEEDED, "checkItemName is required.");
        if (StringUtils.isEmpty(checkItem.getCheckClass()))
            throw new DAOException(DAOException.VALUE_NEEDED, "checkClass is required.");
        //if (StringUtils.isEmpty(checkItem.getCheckBody()))
        //	throw new DAOException(DAOException.VALUE_NEEDED, "checkBody is required.");
    }

    /**
     * 新建检查队列
     *
     * @param checkItem
     * @return
     */
    @RpcService
    public CheckItem addCheckItem(CheckItem checkItem) {
        if (checkItem == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "checkItem is null.");
        }
        validateCheckItem(checkItem);
        checkItem.setCheckItemId(null);
        return save(checkItem);
    }

    /**
     * 更新 检查队列
     *
     * @param checkItem
     * @return
     */
    @RpcService
    public CheckItem updateCheckItem(final CheckItem checkItem) {
        if (null == checkItem) {
            throw new DAOException(DAOException.VALUE_NEEDED, "checkItem is null.");
        }
        if (null == checkItem.getCheckItemId()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "checkItemId is null.");
        }
        CheckItem target = get(checkItem.getCheckItemId());
        if (null == target) {
            throw new DAOException(DAOException.ENTITIY_NOT_FOUND, "checkItem:[" + checkItem.getCheckItemId() + "] is not exist.");
        }
        BeanUtils.map(checkItem, target);
        validateCheckItem(target);
        return update(target);
    }

    /**
     * 运营平台调用 查询检查队列
     *
     * @param checkClass
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    @DAOMethod
    public abstract QueryResult<CheckItem> queryByCheckClass(String checkClass,
                                                             @DAOParam(pageStart = true) long start,
                                                             @DAOParam(pageLimit = true) int limit);


    @RpcService
    @DAOMethod
    public abstract List<CheckItem> findByCheckItemName(String checkItemName);


    @RpcService
    public QueryResult<CheckItem> queryCheckItemForOp(final String checkClass, final String checkItemName,
                                                      final int start,final int limit) {
        HibernateStatelessResultAction<QueryResult<CheckItem>> action = new AbstractHibernateStatelessResultAction<QueryResult<CheckItem>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuffer sb = new StringBuffer(" from CheckItem where 1=1");
                Map<String, Object> map = new HashMap<String, Object>();
                if (!StringUtils.isEmpty(checkClass)) {
                    sb.append(" and checkClass=:checkClass");
                    map.put("checkClass", checkClass);
                }
                if (!StringUtils.isEmpty(checkItemName)) {
                    sb.append(" and checkItemName like:checkItemName");
                    map.put("checkItemName", "%"+checkItemName+"%");
                }
                Query cQuery = ss.createQuery("select count(*) "+sb.toString());
                Query query = ss.createQuery(sb.toString());
                query.setFirstResult(start);
                query.setMaxResults(limit);
                Iterator<Map.Entry<String, Object>> it = map.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, Object> entry = it.next();
                    cQuery.setParameter(entry.getKey(),entry.getValue());
                    query.setParameter(entry.getKey(),entry.getValue());
                }
                setResult(new QueryResult<CheckItem>((long)cQuery.uniqueResult(),start,limit,query.list()));
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

}
