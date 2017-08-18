package eh.bus.dao;

import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.annotation.RpcService;
import eh.entity.bus.MeetClinic;
import eh.entity.bus.MeetClinicAndResult;
import eh.entity.bus.MeetClinicResult;
import eh.entity.msg.Group;
import eh.msg.dao.GroupDAO;
import eh.utils.DateConversion;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import org.springframework.util.StringUtils;

import java.util.*;

public abstract class MeetClinicRecordDAO extends
		HibernateSupportDelegateDAO<MeetClinicAndResult> {

	public MeetClinicRecordDAO() {
		super();
		this.setEntityName(MeetClinic.class.getName());
		this.setKeyField("meetClinicId");
	}

	/**
	 * 查询会诊申请单列表服务
	 * 
	 * @author LF
	 * @param doctorId
	 * @return
	 * @throws DAOException
	 */
	@SuppressWarnings("rawtypes")
	@RpcService
	public List getMeetClinicRecord(final int doctorId) throws DAOException {
		List list1 = null;
		HibernateStatelessResultAction<List> action = new AbstractHibernateStatelessResultAction<List>() {
			@SuppressWarnings("unchecked")
			public void execute(StatelessSession ss) throws Exception {
				List result = new ArrayList<>();
				String hql = new String(
						"select new eh.entity.bus.MeetClinicAndResult(mc,mr,pt) from MeetClinicResult mr,MeetClinic mc,Patient pt where mc.meetClinicStatus<2 and mr.meetClinicId=mc.meetClinicId and mc.requestDoctor =:doctorId and mc.mpiid = pt.mpiId ORDER BY mc.requestTime desc");
				Query q = ss.createQuery(hql);
				q.setParameter("doctorId", doctorId);
				List<MeetClinicAndResult> list = q.list();
				for (int i = 0; i < list.size(); i++) {
					Map map = new HashMap();
					List<MeetClinicResult> mr = new ArrayList<>();
					String mpiid = list.get(i).getMc().getMpiid();
					hql = new String(
							"SELECT familyDoctorFlag FROM RelationDoctor WHERE NOW()>startDate AND NOW()<endDate and mpiId=:mpiid and doctorId=:doctorId");
					Query q2 = ss.createQuery(hql);
					q2.setParameter("mpiid", mpiid);
					q2.setParameter("doctorId", doctorId);
					Boolean familyDoctorFlag = (Boolean) (q2.uniqueResult());
					if (familyDoctorFlag == null) {
						familyDoctorFlag = false;
					}

					int targetDoctorId = list.get(i).getMr().getTargetDoctor();
					hql = new String(
							"SELECT mobile,teams FROM Doctor WHERE doctorId=:doctorId");
					Query q3 = ss.createQuery(hql);
					q3.setParameter("doctorId", targetDoctorId);
					Object[] objs = (Object[]) q3.uniqueResult();
					String targetMobile = (String) objs[0];
					Boolean targetTeams = (Boolean) objs[1];

					// if(result.size()>0 && result!=null){
					int getMeetClinicId = list.get(i).getMc().getMeetClinicId();
					boolean hasOrNot = false;
					for (int j = 0; j < result.size(); j++) {
						Map m = (Map) result.get(j);
						int meetClinicId = ((MeetClinic) m.get("mc"))
								.getMeetClinicId();
						if (getMeetClinicId == meetClinicId) {
							list.get(i).getMr().setTargetMobile(targetMobile);
							list.get(i).getMr().setTargetTeams(targetTeams);
							((List<MeetClinicResult>) m.get("mr")).add(list
									.get(i).getMr());
							hasOrNot = true;
							break;
						}
					}
					if (!hasOrNot) {
						map.put("mc", list.get(i).getMc());
						list.get(i).getMr().setTargetMobile(targetMobile);
						list.get(i).getMr().setTargetTeams(targetTeams);
						mr.add(list.get(i).getMr());
						map.put("mr", mr);
						map.put("pt", list.get(i).getPt());
						map.put("familyDoctorFlag", familyDoctorFlag);
						// 添加聊天群组号
						GroupDAO groupDAO = DAOFactory.getDAO(GroupDAO.class);
						Group group = groupDAO.getByBussTypeAndBussId(2, list
								.get(i).getMc().getMeetClinicId());// 2-会诊
						if (group != null) {
							map.put("groupId", group.getGroupId());
						}
						result.add(map);
					}
					// }else{
					// map.put("mc", list.get(i).getMc());
					// list.get(i).getMr().setTargetMobile(targetMobile);
					// list.get(i).getMr().setTargetTeams(targetTeams);
					// mr.add(list.get(i).getMr());
					// map.put("mr", mr);
					// map.put("pt", list.get(i).getPt());
					// map.put("familyDoctorFlag", familyDoctorFlag);
					// result.add(map);
					// }
				}
				setResult(result);
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		list1 = (List) action.getResult();
		return list1;
	}

	/**
	 * 查询会诊申请单列表服务(分页)[华哥]
	 * 
	 * @author LF
	 * @param doctorId
	 * @param start
	 * @return
	 * @throws DAOException
	 */
	@RpcService
	public List<MeetClinicAndResult> findMeetClinicRecordStart(
			final Integer doctorId, final Integer start) {
		return findMeetClinicRecordStartAndLimit(doctorId, start, 10);
	}

	/**
	 * 查询会诊申请单列表服务(分页)[华姐]
	 * 
	 * @author LF
	 * @param doctorId
	 * @param start
	 * @param limit
	 * @return
	 * @throws DAOException
	 */
	@RpcService
	public List<MeetClinicAndResult> findMeetClinicRecordStartAndLimit(
			final Integer doctorId, final Integer start, final Integer limit)
			throws DAOException {
		HibernateStatelessResultAction<List<MeetClinicAndResult>> action = new AbstractHibernateStatelessResultAction<List<MeetClinicAndResult>>() {
			public void execute(StatelessSession ss) throws Exception {
				// 会诊申请单和病人表联查
				String hql = new String(
						"select new eh.entity.bus.MeetClinicAndResult(mc,pt) from MeetClinic mc,Patient pt where mc.meetClinicStatus<2 and mc.requestDoctor =:doctorId and mc.mpiid = pt.mpiId ORDER BY mc.requestTime");
				Query q = ss.createQuery(hql);
				q.setParameter("doctorId", doctorId);
				q.setFirstResult(start);
				q.setMaxResults(limit);
				@SuppressWarnings("unchecked")
				List<MeetClinicAndResult> list = q.list();
				EndMeetClinicDAO endMeetClinicDAO = DAOFactory
						.getDAO(EndMeetClinicDAO.class);
				// for循环添加相应执行单和医生相应部分信息
				for (int i = 0; i < list.size(); i++) {
					// 获取执行单列表
					List<MeetClinicResult> meetClinicResults = endMeetClinicDAO
							.findByMeetClinicId(list.get(i).getMc()
									.getMeetClinicId());
					// 获取签约医生标志
					String mpiid = list.get(i).getMc().getMpiid();
					hql = new String(
							"SELECT familyDoctorFlag FROM RelationDoctor WHERE NOW()>startDate AND NOW()<endDate and mpiId=:mpiid and doctorId=:doctorId");
					Query q2 = ss.createQuery(hql);
					q2.setParameter("mpiid", mpiid);
					q2.setParameter("doctorId", doctorId);
					Boolean familyDoctorFlag = (Boolean) (q2.uniqueResult());
					if (familyDoctorFlag == null) {
						familyDoctorFlag = false;
					}
					list.get(i).setFamilyDoctorFlag(familyDoctorFlag);
					// 获取目标医生手机号和团队标志
					for (int j = 0; j < meetClinicResults.size(); j++) {
						int targetDoctorId = meetClinicResults.get(j)
								.getTargetDoctor();
						hql = new String(
								"SELECT mobile,teams FROM Doctor WHERE doctorId=:doctorId");
						Query q3 = ss.createQuery(hql);
						q3.setParameter("doctorId", targetDoctorId);
						Object[] objs = (Object[]) q3.uniqueResult();
						String targetMobile = (String) objs[0];
						Boolean targetTeams = (Boolean) objs[1];
						if (StringUtils.isEmpty(targetMobile)) {
							targetMobile = " ";
						}
						if (targetTeams == null) {
							targetTeams = false;
						}
						meetClinicResults.get(j).setTargetMobile(targetMobile);
						meetClinicResults.get(j).setTargetTeams(targetTeams);
						// 添加聊天群组号
						GroupDAO groupDAO = DAOFactory.getDAO(GroupDAO.class);
						Group group = groupDAO.getByBussTypeAndBussId(2, list
								.get(i).getMc().getMeetClinicId());// 2-会诊
						if (group != null) {
							list.get(i).setGroupId(group.getGroupId());
						}
					}
					list.get(i).setMeetClinicResults(meetClinicResults);
					Date requestT = list.get(i).getMc().getRequestTime();
					list.get(i).getMc().setRequestString(DateConversion.convertRequestDateForBuss(requestT));
				}
				setResult(list);
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		return action.getResult();
	}

	/**
	 * 根据会诊执行单编号查找会诊执行单
	 * 
	 * @author qichengjian
	 * @param meetClinicResultId
	 * @return
	 */
	@RpcService
	@DAOMethod(sql = "from MeetClinicResult where meetClinicResultId=:meetClinicResultId")
	public abstract MeetClinicResult getByMeetClinicResultId(
			@DAOParam("meetClinicResultId") int meetClinicResultId);

	/**
	 * 根据会诊申请单编号查找会诊执行单集合
	 * 
	 * @author qichengjian
	 */
	@RpcService
	@DAOMethod(sql = "from MeetClinicResult where meetClinicId=:meetClinicId")
	public abstract List<MeetClinicResult> findByMeetClinicId(
			@DAOParam("meetClinicId") Integer meetClinicId);

	/**
	 * 导出会诊业务数据
	 * 
	 * @author LF
	 * @param startTime
	 * @param endTime
	 * @return
	 */
	@RpcService
	public List<MeetClinicAndResult> exportExcelMeet(final Date startTime,
			final Date endTime) {
		HibernateStatelessResultAction<List<MeetClinicAndResult>> action = new AbstractHibernateStatelessResultAction<List<MeetClinicAndResult>>() {
			@SuppressWarnings("unchecked")
			public void execute(StatelessSession ss) {
				String sql = "select new eh.entity.bus.MeetClinicAndResult(c,r) from MeetClinic c,MeetClinicResult r where c.requestTime>=:startTime and c.requestTime<=:endTime and c.meetClinicId=r.meetClinicId order by c.requestTime desc";
				Query q = ss.createQuery(sql);
				q.setParameter("startTime", startTime);
				q.setParameter("endTime", endTime);
				List<MeetClinicAndResult> andResults = q.list();
				setResult(andResults);
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		if (action.getResult() == null) {
			return null;
		}
		return action.getResult();
	}
}
