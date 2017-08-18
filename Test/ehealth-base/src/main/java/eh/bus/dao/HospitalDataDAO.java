package eh.bus.dao;

import com.alibaba.fastjson.JSONObject;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import eh.entity.bus.HospitalData;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

/**
 * Created by Administrator on 2017/7/1 0001.
 */
public abstract class HospitalDataDAO extends HibernateSupportDelegateDAO<HospitalData> {
    private static final Logger log = LoggerFactory.getLogger(HospitalDataDAO.class);

    public HospitalDataDAO() {
        super();
        setEntityName(HospitalData.class.getName());
        setKeyField("id");
    }

    public void batchSave(final List<HospitalData> hospitalDataList) {
        for (HospitalData hd : hospitalDataList) {
            try {
                save(hd);
            } catch (Exception e) {
                log.error("save hd[{}] error, errorMessage[{}], stackTrace[{}]", JSONObject.toJSONString(hd), e.getStackTrace(), JSONObject.toJSONString(e.getStackTrace()));
            }
        }
    }

    @DAOMethod(sql = "SELECT COUNT(1) FROM HospitalData WHERE createTime>=:createTime ")
    public abstract Long getHospitalDataCount(@DAOParam("createTime") Date startImportTime);

    public List<HospitalData> findHospitalData(final Date startImportTime,
                                               final int start,
                                               final int size){
        AbstractHibernateStatelessResultAction<List<HospitalData>> action = new AbstractHibernateStatelessResultAction<List<HospitalData>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String sql = "FROM HospitalData WHERE createTime>=:createTime AND jlzt=1 ORDER BY cjsj DESC";
                Query query = ss.createQuery(sql);
                query.setParameter("createTime", startImportTime);
                query.setFirstResult(start);
                query.setMaxResults(size);
                setResult(query.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }
}
