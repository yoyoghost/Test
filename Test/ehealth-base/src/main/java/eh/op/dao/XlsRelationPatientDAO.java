package eh.op.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import eh.entity.xls.XlsRelationPatient;
import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.ArrayList;
import java.util.List;


/**
 * @author jianghc
 * @create 2016-12-23 16:40
 **/
public abstract class XlsRelationPatientDAO extends HibernateSupportDelegateDAO<XlsRelationPatient> {
    public static final Logger log = Logger.getLogger(XlsRelationPatientDAO.class);

    public XlsRelationPatientDAO() {
        super();
        this.setEntityName(XlsRelationPatient.class.getName());
        this.setKeyField("id");
    }

    @DAOMethod(limit = 0)
    public abstract List<XlsRelationPatient> findByXlsId(Integer xlsId);

    public QueryResult<Object> queryRelationPatientXls(final Integer xlsId , final int start , final int limit){

        if(xlsId==null){
            throw new DAOException(DAOException.VALUE_NEEDED,"xlsId is require");
        }
        HibernateStatelessResultAction<QueryResult<Object>> action = new AbstractHibernateStatelessResultAction<QueryResult<Object>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(" from XlsRelationPatient where xlsId="+xlsId);
                Query countQuery = ss.createQuery("select count(*) "+hql.toString());
                long total = (long) countQuery.uniqueResult();//获取总条数
                Query query = ss.createQuery(hql.toString()+" order by id asc");
                query.setMaxResults(limit);
                query.setFirstResult(start);
                List<Object> list = query.list();
                if(list==null){
                    list = new ArrayList<Object>();
                }
                setResult(new QueryResult<Object>(total,start,list.size(),list));
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);

        return action.getResult();
    }

}
