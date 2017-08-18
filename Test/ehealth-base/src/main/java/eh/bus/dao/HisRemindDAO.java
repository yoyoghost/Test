package eh.bus.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.annotation.RpcService;
import eh.bus.constant.HisRemindConstant;
import eh.entity.bus.HisRemind;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.Date;
import java.util.List;

public abstract class HisRemindDAO extends HibernateSupportDelegateDAO<HisRemind> {
	public HisRemindDAO() {
		super();
		this.setEntityName(HisRemind.class.getName());
		this.setKeyField("remindId");
	}

	@RpcService
	@DAOMethod
	public abstract HisRemind getByRemindId(Integer remindId);

	@DAOMethod(sql="select distinct(operateUser) from HisRemind where maintainOrgan=:maintainOrgan and remindStatus=0")
	public abstract List<String> findUnRemindUsersByMaintainOrgan( @DAOParam("maintainOrgan") Integer maintainOrgan);

	@DAOMethod(sql="from HisRemind where operateUser=:operateUser and maintainOrgan=:maintainOrgan and remindStatus=0")
	public abstract List<HisRemind> findUnRemindRecordsByUsersAndMaintainOrgan(@DAOParam("operateUser") String operateUser, @DAOParam("maintainOrgan") Integer maintainOrgan);


	/**
	 * 更新记录为已经提醒过
	 * @param remindId
	 * @return
     */
	public Integer beReminded(final String user,final Integer organ){
		HibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
			@Override
			public void execute(StatelessSession ss) throws Exception {
				String hql = "update HisRemind set remindStatus=:remindStatus,lastModify=:lastModify where operateUser=:operateUser and" +
						" maintainOrgan=:maintainOrgan and remindStatus=:oldRemindStatus";
				Query query = ss.createQuery(hql);
				query.setInteger("remindStatus",HisRemindConstant.REMIND_STATUS_HASREMIND);
				query.setParameter("lastModify",new Date());
				query.setParameter("operateUser",user);
				query.setParameter("maintainOrgan",organ);
				query.setParameter("oldRemindStatus",HisRemindConstant.REMIND_STATUS_NOREMIND);
				setResult(query.executeUpdate());
			}
		};
		HibernateSessionTemplate.instance().execute(action);
		return action.getResult();
	}

	/**
	 *保存
     */
	public HisRemind saveRemind(HisRemind remind){
		Date now=new Date();
		remind.setOperateDate(now);
		remind.setLastModify(new Date());
		remind.setRemindStatus(HisRemindConstant.REMIND_STATUS_NOREMIND);
		return save(remind);
	}
}
