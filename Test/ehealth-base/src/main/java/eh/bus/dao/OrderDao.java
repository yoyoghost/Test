package eh.bus.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import eh.entity.bus.Order;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

/**
 * Created by Administrator on 2017/4/23 0023.
 */
public abstract class OrderDao extends HibernateSupportDelegateDAO<Order> {
    private static final Logger log = LoggerFactory.getLogger(OrderDao.class);

    public OrderDao(){
        super();
        this.setEntityName(Order.class.getName());
        this.setKeyField("id");
    }

    @DAOMethod(sql = "UPDATE Order SET payFlag=1, paymentDate=:paymentDate, tradeNo=:tradeNo, updateTime=NOW() where outTradeNo=:outTradeNo")
    public abstract void updateOrderWithOutTradeNoForCallbackSuccess(@DAOParam("tradeNo") String tradeNo,
    @DAOParam("paymentDate") Date paymentDate, @DAOParam("outTradeNo") String outTradeNo);

    @DAOMethod(sql = "UPDATE Order SET payFlag=:payFlag, refundTime=NOW(), updateTime=NOW() where outTradeNo=:outTradeNo AND payFlag!=:payFlag")
    public abstract void updateOrderWithOutTradeNoForRefundResult(@DAOParam("payFlag") int payFlag, @DAOParam("outTradeNo") String outTradeNo);

    @DAOMethod
    public abstract Order getByTradeNo(String tradeNo);

    @DAOMethod
    public abstract Order getByOutTradeNo(String outTradeNo);

    public int updateOrderRefundSuccessForOffline(final String outTradeNo, final int offlineRefundMode) {
        HibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                String hql = "UPDATE Order SET payFlag=3, refundMode=:refundMode, updateTime=NOW() WHERE outTradeNo=:outTradeNo AND payFlag=1";
                Query q = statelessSession.createQuery(hql);
                q.setParameter("outTradeNo", outTradeNo);
                q.setParameter("refundMode", offlineRefundMode);
                int num = q.executeUpdate();
                setResult(num);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    public int updateOrderForOffline(final String outTradeNo, final int offlineRefundMode) {
        HibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                String hql = "UPDATE Order SET refundMode=:refundMode WHERE outTradeNo=:outTradeNo AND payFlag=1";
                Query q = statelessSession.createQuery(hql);
                q.setParameter("outTradeNo", outTradeNo);
                q.setParameter("refundMode", offlineRefundMode);
                int num = q.executeUpdate();
                setResult(num);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    @DAOMethod(sql = "FROM Order WHERE busId=:busId AND busType=:busType ORDER BY id DESC")
    public abstract List<Order> findOrderListByBusId(@DAOParam("busId") Integer busId, @DAOParam("busType") String code);
}
