package eh.msg.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.annotation.RpcService;
import eh.entity.msg.SmsInfo;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

public abstract class SmsInfoDAO extends HibernateSupportDelegateDAO<SmsInfo> {

	public SmsInfoDAO() {
		super();
		this.setEntityName(SmsInfo.class.getName());
		this.setKeyField("id");
	}
	
	/**
	 * 根据id更新status
	 * @author wnw
	 * @param status
	 * @param id
	 */
	@RpcService
	@DAOMethod
	public abstract void updateStatusById(Integer status,Integer id);
	
	@RpcService
	@DAOMethod(sql = "update SmsInfo set status=:status,modifyTime=now() where id = :id")
	public abstract void updateStatusAndModifyTimebyId(@DAOParam("status") Integer status,@DAOParam("id")Integer id);

	@RpcService
	public int updateStatusTo2(final Integer id){
		HibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
			@Override
			public void execute(StatelessSession ss) throws Exception {
				Query q = ss.createQuery("update SmsInfo set status=2 where id=:id and status !=2");
				q.setInteger("id", id);
				setResult(q.executeUpdate());
			}
		};
		HibernateSessionTemplate.instance().execute(action);
		return action.getResult();
	}
}
