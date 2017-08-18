package eh.bus.dao;

import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.annotation.RpcService;
import eh.entity.bus.MeetClinic;
import org.hibernate.Query;
import org.hibernate.StatelessSession;


public abstract class UnMeetClinicNumDAO extends HibernateSupportDelegateDAO<MeetClinic>{
	
	public UnMeetClinicNumDAO(){
		super();
		this.setEntityName(MeetClinic.class.getName());
		this.setKeyField("meetClinicId");
	}
	
	/**
	 * 获取待处理会诊单数服务
	 * @param doctorId
	 * @param groupFlag
	 * @return
	 * @throws DAOException
	 */
	@RpcService
	public int getUnMeetClinicNum(final int doctorId,final boolean groupFlag) throws DAOException{
		HibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
			@Override
			public void execute(StatelessSession ss) throws Exception{
				int allCount = 0;
				int count1 = 0,count2 = 0;
				String hql1 = new String(
						"select count(*) from MeetClinic mc,MeetClinicResult mr where mc.meetClinicId = mr.meetClinicId and mr.exeStatus<2 and (mr.targetDoctor =:doctorId or mr.exeDoctor =:doctorId)");
				Query q1 = ss.createQuery(hql1);
				q1.setParameter("doctorId", doctorId);
				count1 = ((Number)q1.uniqueResult()).intValue();
				if(groupFlag){
					String hql = new String(
							"select count(*) from MeetClinic mc,MeetClinicResult mr,DoctorGroup dg where mc.meetClinicId = mr.meetClinicId and mr.exeStatus<1 and mr.targetDoctor=dg.doctorId and memberId =:doctorId");
					Query q = ss.createQuery(hql);
					q.setParameter("doctorId", doctorId);
					count2 = ((Number)q.uniqueResult()).intValue();
				}
				else{
					count2 = 0;
				}
				allCount = count1 + count2;
				setResult(allCount);
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		return ((Number)action.getResult()).intValue();
	}	
}
