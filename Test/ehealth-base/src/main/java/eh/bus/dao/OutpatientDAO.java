package eh.bus.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.annotation.RpcService;
import eh.entity.bus.Outpatient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.List;

public abstract class OutpatientDAO extends HibernateSupportDelegateDAO<Outpatient> {
    private static final Log logger = LogFactory.getLog(OutpatientDAO.class);

    public static final String MsgTitle = "纳里医生";

    public OutpatientDAO() {
        super();
        setEntityName(Outpatient.class.getName());
        setKeyField("id");
    }


    @RpcService
    @DAOMethod
    public abstract Outpatient getById(Integer id);

    @DAOMethod
    public abstract Outpatient getByOutTradeNo(String outTradeNo);

    @DAOMethod
    public abstract Outpatient getByTradeNo(String tradeNo);

    public int updateToRefundSuccessForOffline(final String outTradeNo, final int offlineRefundMode){
        HibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                String hql = "UPDATE Outpatient SET payFlag=3, offlineRefund=:offlineRefund WHERE outTradeNo=:outTradeNo AND payFlag=1";
                Query q = statelessSession.createQuery(hql);
                q.setParameter("outTradeNo", outTradeNo);
                q.setParameter("offlineRefund", offlineRefundMode);
                int num = q.executeUpdate();
                setResult(num);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    public int updateForOffline(final String outTradeNo, final int offlineRefundMode) {
        HibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                String hql = "UPDATE Outpatient SET offlineRefund=:offlineRefund WHERE outTradeNo=:outTradeNo AND payFlag=1";
                Query q = statelessSession.createQuery(hql);
                q.setParameter("outTradeNo", outTradeNo);
                q.setParameter("offlineRefund", offlineRefundMode);
                int num = q.executeUpdate();
                setResult(num);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    @DAOMethod(sql="from Outpatient where statementNo=:statementNo order by paymentDate desc")
    public abstract List<Outpatient> findByStatementNo(@DAOParam("statementNo")String statementNo);

    @DAOMethod(sql="from Outpatient where tradeNo=:tradeNo order by paymentDate desc")
    public abstract List<Outpatient> findByTradeNo(@DAOParam("tradeNo")String tradeNo);
}
