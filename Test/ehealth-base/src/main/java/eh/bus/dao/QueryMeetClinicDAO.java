package eh.bus.dao;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.annotation.RpcService;
import eh.entity.bus.MeetClinicAndResult;
import eh.entity.msg.Group;
import eh.msg.dao.GroupDAO;
import eh.utils.DateConversion;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;


public abstract class QueryMeetClinicDAO extends HibernateSupportDelegateDAO<MeetClinicAndResult>{
	
	public QueryMeetClinicDAO(){
		super();
		this.setEntityName(MeetClinicAndResult.class.getName());
		this.setKeyField("meetClinicId");
	}
	
	/**
	 * 查询待处理会诊单服务
	 * @author LF
	 * @param doctorId
	 * @param groupFlag
	 * @return
	 * @throws DAOException
	 */
	@RpcService
	public List<MeetClinicAndResult> queryMeetClinicNew(final int doctorId,final boolean groupFlag) throws DAOException{
		HibernateStatelessResultAction<List<MeetClinicAndResult>> action = new AbstractHibernateStatelessResultAction<List<MeetClinicAndResult>>() {
			@SuppressWarnings("unchecked")
			@Override
			public void execute(StatelessSession ss) throws Exception{
				List<MeetClinicAndResult> list = new ArrayList<MeetClinicAndResult>();
				if(groupFlag){
					String hql = new String(
							"select new eh.entity.bus.MeetClinicAndResult(mc,mr,pt) from MeetClinic mc,MeetClinicResult mr,Patient pt where ((exeStatus<2 and (targetDoctor=:doctorId or exeDoctor=:doctorId)) or (exeStatus<1 and targetDoctor in (select doctorId from DoctorGroup where memberId=:doctorId))) and mc.mpiid=pt.mpiId AND mc.meetClinicId=mr.meetClinicId order by requestTime asc");
					Query q3 = ss.createQuery(hql);
					q3.setParameter("doctorId", doctorId);
					list = q3.list();
				}else {
					String hql = new String(
							"select new eh.entity.bus.MeetClinicAndResult(mc,mr,pt) from MeetClinic mc,MeetClinicResult mr,Patient pt where mc.mpiid = pt.mpiId and mc.meetClinicId = mr.meetClinicId and mr.exeStatus<2 and (mr.targetDoctor =:doctorId or mr.exeDoctor =:doctorId) order by mc.requestTime");
					Query q = ss.createQuery(hql);
					q.setParameter("doctorId", doctorId);
					list = q.list();
				}
				String hql = new String();
				for(int i=0;i<list.size();i++){
					String mpiid = list.get(i).getMc().getMpiid();
					hql = new String(
							"SELECT familyDoctorFlag FROM RelationDoctor WHERE NOW()>startDate AND NOW()<endDate and mpiId=:mpiid and doctorId=:doctorId");
					Query q2 = ss.createQuery(hql);
					q2.setParameter("mpiid", mpiid);
					q2.setParameter("doctorId", doctorId);
					Boolean familyDoctorFlag = (Boolean) (q2.uniqueResult());
					if(familyDoctorFlag==null){
						familyDoctorFlag = false;
					}
					list.get(i).setFamilyDoctorFlag(familyDoctorFlag);
					
					int targetDoctorId = list.get(i).getMr().getTargetDoctor();
					hql = new String(
							"SELECT mobile,teams FROM Doctor WHERE doctorId=:doctorId");
					Query q3 = ss.createQuery(hql);
					q3.setParameter("doctorId", targetDoctorId);
					Object[] objs = (Object[]) q3.uniqueResult();
					String targetMobile = (String) objs[0];
					Boolean targetTeams = (Boolean) objs[1];
					list.get(i).setTargetMobile(targetMobile);
					list.get(i).setTargetTeams(targetTeams);
					//添加聊天群组号
					GroupDAO groupDAO = DAOFactory.getDAO(GroupDAO.class);
					Group group = groupDAO.getByBussTypeAndBussId(2, list.get(i).getMc().getMeetClinicId());//2-会诊
					if(group!=null) {
						list.get(i).setGroupId(group.getGroupId());
					}
				}
				setResult(list);
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		return action.getResult();
	}
	
	/**
	 * 查询待处理会诊单服务(添加分页)[华哥]
	 * @author LF
	 * @param doctorId
	 * @param groupFlag
	 * @param start
	 * @return
	 * @throws DAOException
	 */
	@RpcService
	public List<MeetClinicAndResult> queryMeetClinic(final Integer doctorId,final Boolean groupFlag,final Integer start) {
		return queryMeetClinicStartAndLimit(doctorId, groupFlag, start, 10);
	}
	
	/**
	 * 查询待处理会诊单服务(添加分页)[华姐]
	 * @author LF
	 * @param doctorId
	 * @param groupFlag
	 * @param start
	 * @param limit
	 * @return
	 * @throws DAOException
	 */
	@RpcService
	public List<MeetClinicAndResult> queryMeetClinicStartAndLimit(final Integer doctorId,final Boolean groupFlag,final Integer start,final Integer limit) throws DAOException{
		HibernateStatelessResultAction<List<MeetClinicAndResult>> action = new AbstractHibernateStatelessResultAction<List<MeetClinicAndResult>>() {
			@SuppressWarnings("unchecked")
			@Override
			public void execute(StatelessSession ss) throws Exception{
				List<MeetClinicAndResult> list = new ArrayList<MeetClinicAndResult>();
				if(groupFlag){
					String hql = new String(
							"select new eh.entity.bus.MeetClinicAndResult(mc,mr,pt) from MeetClinic mc,MeetClinicResult mr,Patient pt where ((exeStatus<2 and (targetDoctor=:doctorId or exeDoctor=:doctorId)) or (exeStatus<1 and targetDoctor in (select doctorId from DoctorGroup where memberId=:doctorId))) and mc.mpiid=pt.mpiId AND mc.meetClinicId=mr.meetClinicId order by requestTime asc");
					Query q3 = ss.createQuery(hql);
					q3.setParameter("doctorId", doctorId);
					q3.setFirstResult(start);
					q3.setMaxResults(limit);
					list = q3.list();
				}else {
					String hql = new String(
							"select new eh.entity.bus.MeetClinicAndResult(mc,mr,pt) from MeetClinic mc,MeetClinicResult mr,Patient pt where mc.mpiid = pt.mpiId and mc.meetClinicId = mr.meetClinicId and mr.exeStatus<2 and (mr.targetDoctor =:doctorId or mr.exeDoctor =:doctorId) order by mc.requestTime");
					Query q = ss.createQuery(hql);
					q.setParameter("doctorId", doctorId);
					q.setFirstResult(start);
					q.setMaxResults(limit);
					list = q.list();
				}
				String hql = new String();
				for(int i=0;i<list.size();i++){
					String mpiid = list.get(i).getMc().getMpiid();
					hql = new String(
							"SELECT familyDoctorFlag FROM RelationDoctor WHERE NOW()>startDate AND NOW()<endDate and mpiId=:mpiid and doctorId=:doctorId");
					Query q2 = ss.createQuery(hql);
					q2.setParameter("mpiid", mpiid);
					q2.setParameter("doctorId", doctorId);
					Boolean familyDoctorFlag = (Boolean) (q2.uniqueResult());
					if(familyDoctorFlag==null){
						familyDoctorFlag = false;
					}
					list.get(i).setFamilyDoctorFlag(familyDoctorFlag);
					
					int targetDoctorId = list.get(i).getMr().getTargetDoctor();
					hql = new String(
							"SELECT mobile,teams FROM Doctor WHERE doctorId=:doctorId");
					Query q3 = ss.createQuery(hql);
					q3.setParameter("doctorId", targetDoctorId);
					Object[] objs = (Object[]) q3.uniqueResult();
					String targetMobile = (String) objs[0];
					Boolean targetTeams = (Boolean) objs[1];
					list.get(i).setTargetMobile(targetMobile);
					list.get(i).setTargetTeams(targetTeams);
					//添加聊天群组号
					GroupDAO groupDAO = DAOFactory.getDAO(GroupDAO.class);
					Group group = groupDAO.getByBussTypeAndBussId(2, list.get(i).getMc().getMeetClinicId());//2-会诊
					if(group!=null) {
						list.get(i).setGroupId(group.getGroupId());
					}
					Date requestT = list.get(i).getMc().getRequestTime();
					list.get(i).getMc().setRequestString(DateConversion.convertRequestDateForBuss(requestT));
				}
				setResult(list);
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		return action.getResult();
	}
}
