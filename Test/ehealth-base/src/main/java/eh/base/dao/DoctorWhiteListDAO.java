package eh.base.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import eh.entity.base.Doctor;
import eh.entity.base.DoctorWhiteList;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jianghc
 * @create 2017-02-17 16:49
 **/
public abstract class DoctorWhiteListDAO extends HibernateSupportDelegateDAO<DoctorWhiteList> {
    public DoctorWhiteListDAO() {
        super();
        setEntityName(DoctorWhiteList.class.getName());
        setKeyField("Id");
    }

    @DAOMethod
    public abstract DoctorWhiteList getByTypeAndDoctorId(Integer type,Integer DoctorId);


    public QueryResult<Doctor> queryByType( final Integer type,final Integer start,final Integer  limit){

        HibernateStatelessResultAction<QueryResult<Doctor>> action = new AbstractHibernateStatelessResultAction<QueryResult<Doctor>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        " from Doctor a,DoctorWhiteList b where a.doctorId=b.doctorId ");
                if (type!=null){
                    hql.append(" And b.type=").append(type);
                }
                Query countQuery = ss.createQuery(" select count (*) "+hql.toString());
                Long total = (Long) countQuery.uniqueResult();
                Query query = ss.createQuery(hql.toString()+" order by b.type");
                query.setFirstResult(start);
                query.setMaxResults(limit);
                List<Doctor> list = query.list();
                if(list==null){
                    list = new ArrayList<Doctor>();
                }
                setResult(new QueryResult<Doctor>(total,start,limit,list));
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }






}
