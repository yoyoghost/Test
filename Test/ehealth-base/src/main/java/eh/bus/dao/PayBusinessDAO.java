package eh.bus.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.annotation.RpcService;
import eh.entity.bus.PayBusiness;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.Date;
import java.util.List;

public abstract class PayBusinessDAO extends HibernateSupportDelegateDAO<PayBusiness> {
	public PayBusinessDAO() {
		super();
		this.setEntityName(PayBusiness.class.getName());
		this.setKeyField("Id");
	}

	/**
	 * 保存意见
	 * @author ZX
	 * @date 2015-5-19  下午10:35:55
	 * @param payBusiness
	 */
	public void savePayBusiness(PayBusiness payBusiness){
		payBusiness.setCreateTime(new Date());
		payBusiness.setUpdateTime(new Date());
		payBusiness.setPayflag(0);

		this.save(payBusiness);
	}

	@RpcService
	@DAOMethod
	public abstract PayBusiness getById(Integer id);

//	@DAOMethod
//	public abstract PayBusiness getByBusId(String busId);

	@DAOMethod
	public abstract PayBusiness getByOutTradeNo(String outTradeNo);

	@DAOMethod(sql = "update PayBusiness set payflag=:payflag where outTradeNo=:outTradeNo")
	public abstract void updateSinglePayFlagByOutTradeNo(
			@DAOParam("payflag") int payflag,
			@DAOParam("outTradeNo") String outTradeNo);
	
	@DAOMethod(sql = "update PayBusiness set payflag=:payflag where Id=:Id")
	public abstract void updatePayFlagByID(@DAOParam("payflag") int payflag,@DAOParam("Id") Integer Id);

	@DAOMethod(sql="from PayBusiness where MPIID=:MPIID and organId=:organId and payflag=:payflag and busType=:busType order by createTime desc")
	public abstract List<PayBusiness>  findListByMpiIdAndOrganID(@DAOParam("MPIID") String MPIID, @DAOParam("organId") Integer organId,@DAOParam("payflag") Integer payflag, @DAOParam("busType") String busType);

	@DAOMethod(sql = "update PayBusiness set hisbackcode=:hisbackcode where outTradeNo=:outTradeNo")
	public abstract void updateHisbackCode(@DAOParam("hisbackcode") String hisbackcode, @DAOParam("outTradeNo") String outTradeNo);

	@DAOMethod
	public abstract PayBusiness getByTradeNo(String tradeNo);

	public int updateToRefundSuccessForOffline(final String outTradeNo, final int offlineRefundMode){
		HibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
			@Override
			public void execute(StatelessSession statelessSession) throws Exception {
				String hql = "UPDATE PayBusiness SET payFlag=3, offlineRefund=:offlineRefund WHERE outTradeNo=:outTradeNo AND payFlag=1";
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

	public int updateForOffline(final String outTradeNo, final int offlineRefundMode){
		HibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
			@Override
			public void execute(StatelessSession statelessSession) throws Exception {
				String hql = "UPDATE PayBusiness SET offlineRefund=:offlineRefund WHERE outTradeNo=:outTradeNo AND payFlag=1";
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

	@DAOMethod(sql="from PayBusiness where busType=:busType and tradeNo=:tradeNo order by paymentDate desc")
	public abstract List<PayBusiness>  findByBusTypeAndTradeNo(@DAOParam("busType") String busType,@DAOParam("tradeNo") String tradeNo);
}
