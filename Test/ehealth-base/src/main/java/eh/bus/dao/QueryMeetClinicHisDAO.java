package eh.bus.dao;

import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.annotation.RpcService;
import ctd.util.context.Context;
import ctd.util.converter.support.StringToDate;
import eh.base.dao.DoctorDAO;
import eh.entity.base.Doctor;
import eh.entity.bus.MeetClinic;
import eh.entity.bus.MeetClinicAndResult;
import eh.entity.bus.MeetClinicResult;
import eh.entity.mpi.Patient;
import eh.entity.msg.Group;
import eh.mpi.dao.PatientDAO;
import eh.mpi.dao.RelationDoctorDAO;
import eh.msg.dao.GroupDAO;
import eh.util.DoctorUtil;
import eh.utils.DateConversion;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import org.springframework.util.StringUtils;

import java.util.*;

public abstract class QueryMeetClinicHisDAO extends
		HibernateSupportDelegateDAO<MeetClinicAndResult> {

	public QueryMeetClinicHisDAO() {
		super();
		this.setEntityName(MeetClinicAndResult.class.getName());
		this.setKeyField("meetClinicId");
	}

	/**
	 * 历史会诊单查询服务
	 * 
	 * @author LF
	 * @param startTime
	 * @param endTime
	 * @param doctorId
	 * @param mpiId
	 * @return
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@RpcService
	public List<MeetClinicAndResult> queryMeetClinicHis(final Date startTime,
			final Date endTime, final Integer doctorId, final String mpiId) {
		List<MeetClinicAndResult> list1 = new ArrayList<MeetClinicAndResult>();

		if (startTime == null || startTime.toString().equals("")) {
			throw new DAOException(DAOException.VALUE_NEEDED, "startDate["
					+ startTime + "] is required");
		}

		if (endTime == null || endTime.toString().equals("")) {
			throw new DAOException(DAOException.VALUE_NEEDED, "endDate["
					+ endTime + "] is required");
		}

		HibernateStatelessResultAction<List> action = new AbstractHibernateStatelessResultAction<List>() {
			public void execute(StatelessSession ss) throws Exception {
				List result = new ArrayList<>();
				String hql = null;
				List<MeetClinicAndResult> list = new ArrayList<MeetClinicAndResult>();
				if (doctorId == null) {
					hql = new String(
							"select new eh.entity.bus.MeetClinicAndResult(mc,mr,pt) from MeetClinic mc,MeetClinicResult mr,Patient pt where mc.meetClinicId=mr.meetClinicId and mc.mpiid = pt.mpiId and mc.requestTime>=:startTime and mc.requestTime<=:endTime and mc.mpiid=:mpiid and mc.meetClinicStatus>=2 and mr.exeStatus>=2 ORDER BY mc.requestTime DESC");
					Query q = ss.createQuery(hql);
					q.setParameter("startTime", startTime);
					q.setParameter("endTime", endTime);
					q.setParameter("mpiid", mpiId);
					list = q.list();
				} else if (mpiId == null || mpiId.equals("")) {
					hql = new String(
							"select new eh.entity.bus.MeetClinicAndResult(mc,mr,pt) from MeetClinic mc,MeetClinicResult mr,Patient pt where mc.meetClinicId=mr.meetClinicId and mc.mpiid = pt.mpiId and mc.requestTime>=:startTime and mc.requestTime<=:endTime and ((mr.exeDoctor =:doctorId AND mr.exeStatus>=2) or (mc.requestDoctor =:doctorId AND mc.meetClinicStatus>=2)) ORDER BY mc.requestTime DESC");
					Query q = ss.createQuery(hql);
					q.setParameter("startTime", startTime);
					q.setParameter("endTime", endTime);
					q.setParameter("doctorId", doctorId);
					list = q.list();
				} else {
					hql = new String(
							"select new eh.entity.bus.MeetClinicAndResult(mc,mr,pt) from MeetClinic mc,MeetClinicResult mr,Patient pt where mc.meetClinicId=mr.meetClinicId and mc.mpiid = pt.mpiId and mc.requestTime>=:startTime and mc.requestTime<=:endTime and ((mr.exeDoctor =:doctorId and mr.exeStatus>=2) or (mc.requestDoctor =:doctorId and mc.meetClinicStatus>=2)) and mc.mpiid=:mpiid ORDER BY mc.requestTime DESC");
					Query q = ss.createQuery(hql);
					q.setParameter("startTime", startTime);
					q.setParameter("endTime", endTime);
					q.setParameter("doctorId", doctorId);
					q.setParameter("mpiid", mpiId);
					list = q.list();
				}
				for (int i = 0; i < list.size(); i++) {
					Map map = new HashMap();
					List<MeetClinicResult> mr = new ArrayList<>();
					hql = new String(
							"SELECT familyDoctorFlag FROM RelationDoctor WHERE NOW()>startDate AND NOW()<endDate and mpiId=:mpiid and doctorId=:doctorId");
					Query q2 = ss.createQuery(hql);
					q2.setParameter("mpiid", mpiId);
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
				}
				setResult(result);
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		list1 = (List) action.getResult();
		return list1;
	}

	/**
	 * 历史会诊单查询服务(添加分页)[华哥]
	 * 
	 * @author LF
	 * @param startTime
	 * @param endTime
	 * @param doctorId
	 * @param mpiId
	 * @param start
	 * @return
	 */
	@RpcService
	public List<MeetClinicAndResult> queryMeetClinicHisStart(
			final Date startTime, final Date endTime, final Integer doctorId,
			final String mpiId, final Integer start) {
		return queryMeetClinicHisStartAndLimit(startTime, endTime, doctorId,
				mpiId, start, 10);
	}

	/**
	 * 历史会诊单查询服务(添加分页)[华姐]
	 * 
	 * @param startTime
	 * @param endTime
	 * @param doctorId
	 * @param mpiId
	 * @param start
	 * @param limit
	 * @return
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@RpcService
	public List<MeetClinicAndResult> queryMeetClinicHisStartAndLimit(
			final Date startTime, final Date endTime, final Integer doctorId,
			final String mpiId, final Integer start, final Integer limit) {
		List<MeetClinicAndResult> list1 = new ArrayList<MeetClinicAndResult>();

		if (startTime == null || startTime.toString().equals("")) {
			throw new DAOException(DAOException.VALUE_NEEDED, "startDate["
					+ startTime + "] is required");
		}

		if (endTime == null || endTime.toString().equals("")) {
			throw new DAOException(DAOException.VALUE_NEEDED, "endDate["
					+ endTime + "] is required");
		}

		HibernateStatelessResultAction<List> action = new AbstractHibernateStatelessResultAction<List>() {
			public void execute(StatelessSession ss) throws Exception {
				String hql = null;
				List<MeetClinicAndResult> list = new ArrayList<MeetClinicAndResult>();
				if (doctorId == null) {
					hql = new String(
							"select new eh.entity.bus.MeetClinicAndResult(mc,pt) from MeetClinic mc,Patient pt where mc.mpiid = pt.mpiId and mc.requestTime>=:startTime and mc.requestTime<=:endTime and mc.mpiid=:mpiid and mc.meetClinicStatus>=2 ORDER BY mc.requestTime DESC");
					Query q = ss.createQuery(hql);
					q.setParameter("startTime", startTime);
					q.setParameter("endTime", endTime);
					q.setParameter("mpiid", mpiId);
					q.setFirstResult(start);
					q.setMaxResults(limit);
					list = q.list();
				} else if (StringUtils.isEmpty(mpiId)) {
					hql = new String(
							"select new eh.entity.bus.MeetClinicAndResult(mc,pt) from MeetClinic mc,MeetClinicResult mr,Patient pt where mc.meetClinicId=mr.meetClinicId and mc.mpiid = pt.mpiId and mc.requestTime>=:startTime and mc.requestTime<=:endTime and ((mr.exeDoctor =:doctorId AND mr.exeStatus>=2) or (mc.requestDoctor =:doctorId AND mc.meetClinicStatus>=2)) GROUP BY mc.meetClinicId ORDER BY mc.requestTime DESC");
					Query q = ss.createQuery(hql);
					q.setParameter("startTime", startTime);
					q.setParameter("endTime", endTime);
					q.setParameter("doctorId", doctorId);
					q.setFirstResult(start);
					q.setMaxResults(limit);
					list = q.list();
				} else {
					hql = new String(
							"select new eh.entity.bus.MeetClinicAndResult(mc,pt) from MeetClinic mc,MeetClinicResult mr,Patient pt where mc.meetClinicId=mr.meetClinicId and mc.mpiid = pt.mpiId and mc.requestTime>=:startTime and mc.requestTime<=:endTime and ((mr.exeDoctor =:doctorId and mr.exeStatus>=2) or (mc.requestDoctor =:doctorId and mc.meetClinicStatus>=2)) and mc.mpiid=:mpiid GROUP BY mc.meetClinicId ORDER BY mc.requestTime DESC");
					Query q = ss.createQuery(hql);
					q.setParameter("startTime", startTime);
					q.setParameter("endTime", endTime);
					q.setParameter("doctorId", doctorId);
					q.setParameter("mpiid", mpiId);
					q.setFirstResult(start);
					q.setMaxResults(limit);
					list = q.list();
				}
				EndMeetClinicDAO endMeetClinicDAO = DAOFactory
						.getDAO(EndMeetClinicDAO.class);
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
					// 添加聊天群组号
					GroupDAO groupDAO = DAOFactory.getDAO(GroupDAO.class);
					Group group = groupDAO.getByBussTypeAndBussId(2, list
							.get(i).getMc().getMeetClinicId());// 2-会诊
					if (group != null) {
						list.get(i).setGroupId(group.getGroupId());
					}
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
					}
					list.get(i).setMeetClinicResults(meetClinicResults);
				}
				setResult(list);
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		list1 = (List) action.getResult();
		return list1;
	}

	/**
	 * 历史会诊单查询服务(非Date型入参)
	 * 
	 * @author LF
	 * @param month
	 * @param doctorId
	 * @param mpiId
	 * @return
	 */
	@RpcService
	public List<MeetClinicAndResult> queryMeetClinicHisLastMonth(
			final Integer doctorId, final String mpiId) {
		Date endDate = Context.instance().get("date.getDatetime", Date.class);
		Date startDate = Context.instance().get("date.getDateOfLastMonth",
				Date.class);
		List<MeetClinicAndResult> list = queryMeetClinicHis(startDate, endDate,
				doctorId, mpiId);
		return list;
	}

	/**
	 * 历史会诊单查询服务(非Date型入参) 添加分页[华哥]
	 * 
	 * @author LF
	 * @param doctorId
	 * @param mpiId
	 * @param start
	 * @return
	 */
	@RpcService
	public List<MeetClinicAndResult> queryMeetClinicHisLastMonthStart(
			final Integer doctorId, final String mpiId, Integer start) {
		return queryMeetClinicHisLastMonthStartAndLimit(doctorId, mpiId, start,
				10);
	}

	/**
	 * 历史会诊单查询服务(非Date型入参) 添加分页[华姐]
	 * 
	 * @param doctorId
	 * @param mpiId
	 * @param start
	 * @param limit
	 * @return
	 */
	@RpcService
	public List<MeetClinicAndResult> queryMeetClinicHisLastMonthStartAndLimit(
			final Integer doctorId, final String mpiId, Integer start,
			Integer limit) {
		Date endDate = Context.instance().get("date.getDatetime", Date.class);
		Date startDate = Context.instance().get("date.getDateOfLastMonth",
				Date.class);
		List<MeetClinicAndResult> list = queryMeetClinicHisStartAndLimit(
				startDate, endDate, doctorId, mpiId, start, limit);
		return list;
	}

	/**
	 * 会诊统计查询
	 * 
	 * @author ZX
	 * @date 2015-5-8 上午11:49:44
	 * @param doctorId
	 * @return
	 */
	@RpcService
	public List<MeetClinicAndResult> findMeetClinicAndResultWithStatic(
			final Date startTime, final Date endTime, final MeetClinic mc,
			final MeetClinicResult mr, final int start) {

		if (startTime == null) {
			throw new DAOException(DAOException.VALUE_NEEDED, "统计开始时间不能为空");
		}

		if (endTime == null) {
			throw new DAOException(DAOException.VALUE_NEEDED, "统计结束时间不能为空");
		}

		HibernateStatelessResultAction<List<MeetClinicAndResult>> action = new AbstractHibernateStatelessResultAction<List<MeetClinicAndResult>>() {
			@SuppressWarnings("unchecked")
			@Override
			public void execute(StatelessSession ss) throws Exception {

				StringBuilder hql = new StringBuilder(
						"select new eh.entity.bus.MeetClinicAndResult(mc,mr,pt) from MeetClinic mc,MeetClinicResult mr,Patient pt where mc.meetClinicId=mr.meetClinicId and mc.mpiid=pt.mpiId and DATE(mc.requestTime)>=DATE(:startTime) and DATE(mc.requestTime)<=DATE(:endTime)");

				// 添加申请机构条件
				if (mc.getRequestOrgan() != null) {
					hql.append(" and mc.requestOrgan=" + mc.getRequestOrgan());
				}

				// 添加目标机构条件
				if (mr.getTargetOrgan() != null) {
					hql.append(" and mr.targetOrgan=" + mr.getTargetOrgan());
				}

				// 添加申请医生条件
				if (mc.getRequestDoctor() != null) {
					hql.append(" and mc.requestDoctor=" + mc.getRequestDoctor());
				}

				// 添加目标医生条件
				if (mr.getTargetDoctor() != null) {
					hql.append(" and mr.targetDoctor=" + mr.getTargetDoctor());
				}

				// 添加会诊执行单状态
				if (mr.getExeStatus() != null) {
					hql.append(" and mr.exeStatus=" + mr.getExeStatus());
				}

				hql.append(" order by mc.requestTime desc");

				Query query = ss.createQuery(hql.toString());
				query.setDate("startTime", startTime);
				query.setDate("endTime", endTime);
				query.setFirstResult(start);
				query.setMaxResults(10);

				List<MeetClinicAndResult> list = query.list();

				DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
				for (MeetClinicAndResult meetClinicAndResult : list) {
					// 申请医生电话
					int requestDocId = meetClinicAndResult.getMc()
							.getRequestDoctor();
					Doctor doctor = doctorDAO.getByDoctorId(requestDocId);
					if (!StringUtils.isEmpty(doctor.getMobile())) {
						meetClinicAndResult.setMobile(doctor.getMobile());
					}

					// 目标医生电话
					int targerDocId = meetClinicAndResult.getMr()
							.getTargetDoctor();
					Doctor targetDoctor = doctorDAO.getByDoctorId(targerDocId);
					if (!StringUtils.isEmpty(targetDoctor.getMobile())) {
						meetClinicAndResult.setTargetMobile(targetDoctor
								.getMobile());
					}
				}
				setResult(list);
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		return (List<MeetClinicAndResult>) action.getResult();
	}

	/**
	 * 会诊统计查询记录数
	 * 
	 * @author ZX
	 * @date 2015-5-12 下午4:13:33
	 * @param startTime
	 * @param endTime
	 * @param mc
	 * @param mr
	 * @param start
	 * @return
	 */
	@RpcService
	public long getNumWithStatic(final Date startTime, final Date endTime,
			final MeetClinic mc, final MeetClinicResult mr) {

		if (startTime == null) {
			throw new DAOException(DAOException.VALUE_NEEDED, "统计开始时间不能为空");
		}

		if (endTime == null) {
			throw new DAOException(DAOException.VALUE_NEEDED, "统计结束时间不能为空");
		}

		HibernateStatelessResultAction<Long> action = new AbstractHibernateStatelessResultAction<Long>() {
			@Override
			public void execute(StatelessSession ss) throws Exception {

				StringBuilder hql = new StringBuilder(
						"select count(*) from MeetClinic mc,MeetClinicResult mr,Patient pt where mc.meetClinicId=mr.meetClinicId and mc.mpiid=pt.mpiId and DATE(mc.requestTime)>=DATE(:startTime) and DATE(mc.requestTime)<=DATE(:endTime)");

				// 添加申请机构条件
				if (mc.getRequestOrgan() != null) {
					hql.append(" and mc.requestOrgan=" + mc.getRequestOrgan());
				}

				// 添加目标机构条件
				if (mr.getTargetOrgan() != null) {
					hql.append(" and mr.targetOrgan=" + mr.getTargetOrgan());
				}

				// 添加申请医生条件
				if (mc.getRequestDoctor() != null) {
					hql.append(" and mc.requestDoctor=" + mc.getRequestDoctor());
				}

				// 添加目标医生条件
				if (mr.getTargetDoctor() != null) {
					hql.append(" and mr.targetDoctor=" + mr.getTargetDoctor());
				}

				// 添加会诊执行单状态
				if (mr.getExeStatus() != null) {
					hql.append(" and mr.exeStatus=" + mr.getExeStatus());
				}

				Query query = ss.createQuery(hql.toString());
				query.setDate("startTime", startTime);
				query.setDate("endTime", endTime);

				long num = (long) query.uniqueResult();

				setResult(num);
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		return action.getResult();
	}

	/**
	 * 会诊申请单记录数
	 * 
	 * @author ZX
	 * @date 2015-6-24 下午12:03:47
	 * @param startTime
	 * @param endTime
	 * @param mc
	 * @param mr
	 * @return
	 */
	@RpcService
	public long getRequestNumWithStatic(final Date startTime,
			final Date endTime, final MeetClinic mc, final MeetClinicResult mr) {

		if (startTime == null) {
			throw new DAOException(DAOException.VALUE_NEEDED, "统计开始时间不能为空");
		}

		if (endTime == null) {
			throw new DAOException(DAOException.VALUE_NEEDED, "统计结束时间不能为空");
		}

		HibernateStatelessResultAction<Long> action = new AbstractHibernateStatelessResultAction<Long>() {
			@Override
			public void execute(StatelessSession ss) throws Exception {

				StringBuilder hql = new StringBuilder(
						"select count(*) from MeetClinic mc where DATE(mc.requestTime)>=DATE(:startTime) and DATE(mc.requestTime)<=DATE(:endTime)");

				// 添加申请机构条件
				if (mc.getRequestOrgan() != null) {
					hql.append(" and mc.requestOrgan=" + mc.getRequestOrgan());
				}

				// 添加目标机构条件
				if (mr.getTargetOrgan() != null) {
					hql.append(" and mr.targetOrgan=" + mr.getTargetOrgan());
				}

				// 添加申请医生条件
				if (mc.getRequestDoctor() != null) {
					hql.append(" and mc.requestDoctor=" + mc.getRequestDoctor());
				}

				// 添加目标医生条件
				if (mr.getTargetDoctor() != null) {
					hql.append(" and mr.targetDoctor=" + mr.getTargetDoctor());
				}

				// 添加会诊执行单状态
				if (mc.getMeetClinicStatus() != null) {
					hql.append(" and mc.meetClinicStatus="
							+ mc.getMeetClinicStatus());
				}

				Query query = ss.createQuery(hql.toString());
				query.setDate("startTime", startTime);
				query.setDate("endTime", endTime);

				long num = (long) query.uniqueResult();

				setResult(num);
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		return action.getResult();
	}

	/**
	 * 申请方会诊数统计
	 * 
	 * @author ZX
	 * @date 2015-6-2 下午6:11:10
	 * @param manageUnit
	 * @param startDate
	 * @param endDate
	 * @param transferStatus
	 * @return
	 */
	@RpcService
	public Long getRequestNumFromTo(final String manageUnit,
			final Date startDate, final Date endDate,
			final String... meetClinicStatus) {
		if (startDate == null) {
			throw new DAOException(DAOException.VALUE_NEEDED, "统计开始时间不能为空");
		}

		if (endDate == null) {
			throw new DAOException(DAOException.VALUE_NEEDED, "统计结束时间不能为空");
		}

		HibernateStatelessResultAction<Long> action = new AbstractHibernateStatelessResultAction<Long>() {
			@Override
			public void execute(StatelessSession ss) throws Exception {

				StringBuilder hql = new StringBuilder(
						"select count(*) from MeetClinic a ,Organ o where a.requestOrgan = o.organId and o.manageUnit like :manageUnit and  DATE(a.requestTime)>=DATE(:startTime) and DATE(a.requestTime)<=DATE(:endTime)");

				// 添加会诊单状态
				if (meetClinicStatus.length > 0) {
					hql.append(" and (");

					for (String string : meetClinicStatus) {
						hql.append(" a.meetClinicStatus=" + string + " or ");
					}

					hql.delete(hql.length() - 4, hql.length());
					hql.append(")");
				}

				Query query = ss.createQuery(hql.toString());
				query.setString("manageUnit", manageUnit);
				query.setDate("startTime", startDate);
				query.setDate("endTime", endDate);

				long num = (long) query.uniqueResult();

				setResult(num);
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		return action.getResult();
	}

	/**
	 * 申请方昨日会诊总数统计
	 * 
	 * @author ZX
	 * @date 2015-6-2 下午6:18:25
	 * @param manageUnit
	 * @return
	 */
	@RpcService
	public Long getRequestNumForYesterday(String manageUnit) {
		Date date = Context.instance().get("date.getYesterday", Date.class);
		String[] transferStatus = { "2" };
		return getRequestNumFromTo(manageUnit + "%", date, date, transferStatus);
	}

	/**
	 * 申请方今日会诊总数统计
	 * 
	 * @author ZX
	 * @date 2015-6-2 下午6:18:25
	 * @param manageUnit
	 * @return
	 */
	@RpcService
	public Long getRequestNumForToday(String manageUnit) {
		Date date = Context.instance().get("date.getToday", Date.class);
		String[] transferStatus = { "2" };
		return getRequestNumFromTo(manageUnit + "%", date, date, transferStatus);
	}

	/**
	 * 申请方总会诊数
	 * 
	 * @author ZX
	 * @date 2015-6-2 下午6:18:25
	 * @param manageUnit
	 * @return
	 */
	@RpcService
	public Long getRequestNum(String manageUnit) {
		Date startDate = new StringToDate().convert("2014-05-06");
		Date endDate = Context.instance().get("date.getToday", Date.class);
		String[] transferStatus = { "2" };
		return getRequestNumFromTo(manageUnit + "%", startDate, endDate,
				transferStatus);
	}

	/**
	 * 申请方一段时间内总会诊数
	 * 
	 * @author ZX
	 * @date 2015-8-5 下午4:30:44
	 * @param manageUnit
	 * @param startDate
	 * @param endDate
	 * @return
	 */
	@RpcService
	public Long getRequestNumForTime(String manageUnit, Date startDate,
			Date endDate) {
		String[] transferStatus = { "2" };
		return getRequestNumFromTo(manageUnit + "%", startDate, endDate,
				transferStatus);
	}

	/**
	 * 目标方会诊数统计
	 * 
	 * @author ZX
	 * @date 2015-6-2 下午6:18:25
	 * @param manageUnit
	 * @param startDate
	 * @param endDate
	 * @param transferStatus
	 * @return
	 */
	@RpcService
	public Long getTargetNumFromTo(final String manageUnit,
			final Date startDate, final Date endDate, final String... exeStatus) {
		if (startDate == null) {
			throw new DAOException(DAOException.VALUE_NEEDED, "统计开始时间不能为空");
		}

		if (endDate == null) {
			throw new DAOException(DAOException.VALUE_NEEDED, "统计结束时间不能为空");
		}

		HibernateStatelessResultAction<Long> action = new AbstractHibernateStatelessResultAction<Long>() {
			@Override
			public void execute(StatelessSession ss) throws Exception {

				StringBuilder hql = new StringBuilder(
						"select count(*) from MeetClinicResult a ,Organ o where a.exeOrgan = o.organId and o.manageUnit like :manageUnit and  DATE(a.startTime)>=DATE(:startTime) and DATE(a.startTime)<=DATE(:endTime)");

				// 添加转诊单状态
				if (exeStatus.length > 0) {
					hql.append(" and (");

					for (String string : exeStatus) {
						hql.append(" a.exeStatus=" + string + " or ");
					}

					hql.delete(hql.length() - 4, hql.length());
					hql.append(")");
				}

				Query query = ss.createQuery(hql.toString());
				query.setString("manageUnit", manageUnit);
				query.setDate("startTime", startDate);
				query.setDate("endTime", endDate);

				long num = (long) query.uniqueResult();

				setResult(num);
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		return action.getResult();
	}

	/**
	 * 目标方昨日会诊总数统计
	 * 
	 * @author ZX
	 * @date 2015-6-2 下午6:22:57
	 * @param manageUnit
	 * @return
	 */
	@RpcService
	public Long getTargetNumForYesterday(String manageUnit) {
		Date date = Context.instance().get("date.getYesterday", Date.class);
		String[] exeStatus = { "2" };
		return getTargetNumFromTo(manageUnit + "%", date, date, exeStatus);
	}

	/**
	 * 目标方今日会诊总数统计
	 * 
	 * @author ZX
	 * @date 2015-6-2 下午6:18:25
	 * @param manageUnit
	 * @return
	 */
	@RpcService
	public Long getTargetNumForToday(String manageUnit) {
		Date date = Context.instance().get("date.getToday", Date.class);
		String[] exeStatus = { "2" };
		return getTargetNumFromTo(manageUnit + "%", date, date, exeStatus);
	}

	/**
	 * 目标方总会诊数
	 * 
	 * @author ZX
	 * @date 2015-6-2 下午6:18:25
	 * @param manageUnit
	 * @return
	 */
	@RpcService
	public Long getTargetNum(String manageUnit) {
		Date startDate = new StringToDate().convert("2014-05-06");
		Date endDate = Context.instance().get("date.getToday", Date.class);
		String[] exeStatus = { "2" };
		return getTargetNumFromTo(manageUnit + "%", startDate, endDate,
				exeStatus);
	}

	/**
	 * 目标方一段时间内总会诊数
	 * 
	 * @author ZX
	 * @date 2015-8-5 下午4:34:07
	 * @param manageUnit
	 * @param startDate
	 * @param endDate
	 * @return
	 */
	@RpcService
	public Long getTargetNumForTime(String manageUnit, Date startDate,
			Date endDate) {
		String[] exeStatus = { "2" };
		return getTargetNumFromTo(manageUnit + "%", startDate, endDate,
				exeStatus);
	}

	/**
	 * Title:统计会诊
	 * Description:在原来的基础上，新加一个搜索参数（患者主键），跟机构相关的查询条件（申请机构、目标机构）由原来的一个定值改为一个数组
	 *
	 * @param startTime     ---转诊申请开始时间
	 * @param endTime       ---转诊申请结束时间
	 * @param mc            ---会诊信息
	 * @param mr            ---会诊执行单
	 * @param start         ---分页，开始编号
	 * @param mpiid         ---患者主键
	 * @param requestOrgans ---申请机构（集合）
	 * @param targetOrgans  ---目标机构（集合）
	 * @return List<MeetClinicAndResult>
	 * @author AngryKitty
	 * @date 2015-8-31
	 * @desc 添加会诊单紧急状态查询条件 zhangjr 2016-04-20 优化查询 houxr 2016-06-02
	 */
	@RpcService
	public QueryResult<MeetClinicAndResult> findMeetClinicAndResultByStatic(
			final Date startTime, final Date endTime, final MeetClinic mc,
			final MeetClinicResult mr, final int start, final String mpiid,
			final List<Integer> requestOrgans, final List<Integer> targetOrgans) {

		this.validateOptionForStatistics(startTime,endTime,mc,mr,start,mpiid,requestOrgans,targetOrgans);
		final StringBuilder preparedHql = this.generateHQLforStatistics(startTime,endTime,mc,mr,start,mpiid,requestOrgans,targetOrgans);

		HibernateStatelessResultAction<QueryResult<MeetClinicAndResult>> action = new AbstractHibernateStatelessResultAction<QueryResult<MeetClinicAndResult>>() {
			@SuppressWarnings("unchecked")
			@Override
			public void execute(StatelessSession ss) throws Exception {
				int total = 0;
				StringBuilder hql = preparedHql;
				Query countQuery=ss.createQuery("select count(*) "+ hql.toString());
				countQuery.setDate("startTime", startTime);
				countQuery.setDate("endTime", endTime);
				total=((Long)countQuery.uniqueResult()).intValue();//获取总条数

				hql.append(" order by mc.requestTime desc");
				Query query = ss.createQuery("select new eh.entity.bus.MeetClinicAndResult(mc,mr,pt) "+hql.toString());
				query.setDate("startTime", startTime);
				query.setDate("endTime", endTime);
				query.setFirstResult(start);
				query.setMaxResults(10);

				List<MeetClinicAndResult> list = query.list();

				DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
				for (MeetClinicAndResult meetClinicAndResult : list) {
					// 申请医生电话
					int requestDocId = meetClinicAndResult.getMc()
							.getRequestDoctor();
					Doctor doctor = doctorDAO.getByDoctorId(requestDocId);
					if (!StringUtils.isEmpty(doctor.getMobile())) {
						meetClinicAndResult.setMobile(doctor.getMobile());
					}

					// 目标医生电话
					int targerDocId = meetClinicAndResult.getMr()
							.getTargetDoctor();
					Doctor targetDoctor = doctorDAO.getByDoctorId(targerDocId);
					if (!StringUtils.isEmpty(targetDoctor.getMobile())) {
						meetClinicAndResult.setTargetMobile(targetDoctor
								.getMobile());
					}
				}
				QueryResult<MeetClinicAndResult> qResult = new QueryResult<MeetClinicAndResult>(
						total, query.getFirstResult(), query.getMaxResults(),
						list);
				setResult(qResult);
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		return (QueryResult<MeetClinicAndResult>) action.getResult();
	}

	/**
	 * Title:根据状态统计会诊
	 * @param startTime     ---转诊申请开始时间
	 * @param endTime       ---转诊申请结束时间
	 * @param mc            ---会诊信息
	 * @param mr            ---会诊执行单
	 * @param start         ---分页，开始编号
	 * @param mpiid         ---患者主键
	 * @param requestOrgans ---申请机构（集合）
	 * @param targetOrgans  ---目标机构（集合）
	 * @return HashMap<String, Integer>
	 * @author andywang
	 * @date 2016-11-30
	 */
	public HashMap<String, Integer> getStatisticsByStatus(final Date startTime, final Date endTime, final MeetClinic mc,
														  final MeetClinicResult mr, final int start, final String mpiid,
														  final List<Integer> requestOrgans, final List<Integer> targetOrgans) {

		this.validateOptionForStatistics(startTime,endTime,mc,mr,start,mpiid,requestOrgans,targetOrgans);
		final StringBuilder preparedHql = this.generateHQLforStatistics(startTime,endTime,mc,mr,start,mpiid,requestOrgans,targetOrgans);
		HibernateStatelessResultAction<HashMap<String, Integer>> action = new AbstractHibernateStatelessResultAction<HashMap<String, Integer>>() {
			@SuppressWarnings("unchecked")
			@Override
			public void execute(StatelessSession ss) throws Exception {
				long total = 0;
				StringBuilder hql = preparedHql;
				hql.append(" group by mc.meetClinicStatus ");
				Query query = ss.createQuery("select mc.meetClinicStatus, count(mc.meetClinicId) as count " + hql.toString());
				query.setDate("startTime", startTime);
				query.setDate("endTime", endTime);
				List<Object[]> tfList = query.list();
				HashMap<String, Integer> mapStatistics = new HashMap<String, Integer>();
				if (tfList.size() >0) {
					for (Object[] hps : tfList) {
						if (hps[0] != null && !StringUtils.isEmpty(hps[0].toString()))
						{
							String status = hps[0].toString();
							String statusName = DictionaryController.instance()
									.get("eh.bus.dictionary.MeetClinicStatus").getText(status);
							mapStatistics.put(statusName, Integer.parseInt(hps[1].toString()));
						}
					}
				}
				setResult(mapStatistics);
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		return action.getResult();
	}

    /**
     * Title:根据执行状态统计会诊
     * @param startTime     ---转诊申请开始时间
     * @param endTime       ---转诊申请结束时间
     * @param mc            ---会诊信息
     * @param mr            ---会诊执行单
     * @param start         ---分页，开始编号
     * @param mpiid         ---患者主键
     * @param requestOrgans ---申请机构（集合）
     * @param targetOrgans  ---目标机构（集合）
     * @return HashMap<String, Integer>
     * @author andywang
     * @date 2016-11-30
     */
    public HashMap<String, Integer> getStatisticsByExeStatus(final Date startTime, final Date endTime, final MeetClinic mc,
                                                          final MeetClinicResult mr, final int start, final String mpiid,
                                                          final List<Integer> requestOrgans, final List<Integer> targetOrgans) {

        this.validateOptionForStatistics(startTime,endTime,mc,mr,start,mpiid,requestOrgans,targetOrgans);
        final StringBuilder preparedHql = this.generateHQLforStatistics(startTime,endTime,mc,mr,start,mpiid,requestOrgans,targetOrgans);
        HibernateStatelessResultAction<HashMap<String, Integer>> action = new AbstractHibernateStatelessResultAction<HashMap<String, Integer>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                long total = 0;
                StringBuilder hql = preparedHql;
                hql.append(" group by mr.exeStatus ");
                Query query = ss.createQuery("select mr.exeStatus, count(mr.meetClinicResultId) as count " + hql.toString());
                query.setDate("startTime", startTime);
                query.setDate("endTime", endTime);
                List<Object[]> tfList = query.list();
                HashMap<String, Integer> mapStatistics = new HashMap<String, Integer>();
                if (tfList.size() >0) {
                    for (Object[] hps : tfList) {
                        if (hps[0] != null && !StringUtils.isEmpty(hps[0].toString()))
                        {
                            String status = hps[0].toString();
                            String statusName = DictionaryController.instance()
                                    .get("eh.bus.dictionary.ExeStatus").getText(status);
                            mapStatistics.put(statusName, Integer.parseInt(hps[1].toString()));
                        }
                    }
                }
                setResult(mapStatistics);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }
	/**
	 * Title:根据申请机构统计会诊
	 * @param startTime     ---转诊申请开始时间
	 * @param endTime       ---转诊申请结束时间
	 * @param mc            ---会诊信息
	 * @param mr            ---会诊执行单
	 * @param start         ---分页，开始编号
	 * @param mpiid         ---患者主键
	 * @param requestOrgans ---申请机构（集合）
	 * @param targetOrgans  ---目标机构（集合）
	 * @return HashMap<String, Integer>
	 * @author andywang
	 * @date 2016-11-30
	 */
	public HashMap<String, Integer> getStatisticsByRequestOrgan(final Date startTime, final Date endTime, final MeetClinic mc,
																final MeetClinicResult mr, final int start, final String mpiid,
																final List<Integer> requestOrgans, final List<Integer> targetOrgans) {

		this.validateOptionForStatistics(startTime,endTime,mc,mr,start,mpiid,requestOrgans,targetOrgans);
		final StringBuilder preparedHql = this.generateHQLforStatistics(startTime,endTime,mc,mr,start,mpiid,requestOrgans,targetOrgans);
		HibernateStatelessResultAction<HashMap<Integer, Integer>> action = new AbstractHibernateStatelessResultAction<HashMap<Integer, Integer>>() {
			@SuppressWarnings("unchecked")
			@Override
			public void execute(StatelessSession ss) throws Exception {
				long total = 0;
				StringBuilder hql = preparedHql;
				hql.append(" group by mc.requestOrgan ");
				Query query = ss.createQuery("select mc.requestOrgan, count(mc.meetClinicId) as count " + hql.toString());
				query.setDate("startTime", startTime);
				query.setDate("endTime", endTime);
				List<Object[]> tfList = query.list();
				HashMap<Integer, Integer> mapStatistics = new HashMap<Integer, Integer>();
				if (tfList.size() >0) {
					for (Object[] hps : tfList) {
						if(hps[0] != null && !StringUtils.isEmpty(hps[0].toString()))
						{
							Integer requestOrganId = Integer.parseInt(hps[0].toString());
							mapStatistics.put(requestOrganId, Integer.parseInt(hps[1].toString()));
						}
					}
				}
				setResult(mapStatistics);
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		HashMap<Integer, Integer> map = action.getResult();
		return DoctorUtil.translateOrganHash(map);
	}


	/**
	 * Title:根据目标机构统计会诊
	 * @param startTime     ---转诊申请开始时间
	 * @param endTime       ---转诊申请结束时间
	 * @param mc            ---会诊信息
	 * @param mr            ---会诊执行单
	 * @param start         ---分页，开始编号
	 * @param mpiid         ---患者主键
	 * @param requestOrgans ---申请机构（集合）
	 * @param targetOrgans  ---目标机构（集合）
	 * @return HashMap<String, Integer>
	 * @author andywang
	 * @date 2016-11-30
	 */
	public HashMap<String, Integer> getStatisticsByTargetOrgan(final Date startTime, final Date endTime, final MeetClinic mc,
															   final MeetClinicResult mr, final int start, final String mpiid,
															   final List<Integer> requestOrgans, final List<Integer> targetOrgans) {

		this.validateOptionForStatistics(startTime,endTime,mc,mr,start,mpiid,requestOrgans,targetOrgans);
		final StringBuilder preparedHql = this.generateHQLforStatistics(startTime,endTime,mc,mr,start,mpiid,requestOrgans,targetOrgans);
		HibernateStatelessResultAction<HashMap<Integer, Integer>> action = new AbstractHibernateStatelessResultAction<HashMap<Integer, Integer>>() {
			@SuppressWarnings("unchecked")
			@Override
			public void execute(StatelessSession ss) throws Exception {
				long total = 0;
				StringBuilder hql = preparedHql;
				hql.append(" group by mc.requestTime desc,mr.targetOrgan ");
				Query query = ss.createQuery("select mr.targetOrgan, count(mc.meetClinicId) as count " + hql.toString());
				query.setDate("startTime", startTime);
				query.setDate("endTime", endTime);
				List<Object[]> tfList = query.list();
				HashMap<Integer, Integer> mapStatistics = new HashMap<Integer, Integer>();
				if (tfList.size() >0) {
					for (Object[] hps : tfList) {
						if(hps[0] != null && !StringUtils.isEmpty(hps[0].toString()))
						{
							Integer targetOrganId = Integer.parseInt(hps[0].toString());
							mapStatistics.put(targetOrganId, Integer.parseInt(hps[1].toString()));
						}
					}
				}
				setResult(mapStatistics);
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		HashMap<Integer, Integer> map = action.getResult();
		return DoctorUtil.translateOrganHash(map);
	}

	private void validateOptionForStatistics(
			final Date startTime, final Date endTime, final MeetClinic mc,
			final MeetClinicResult mr, final int start, final String mpiid,
			final List<Integer> requestOrgans, final List<Integer> targetOrgans) {
		if (startTime == null) {
			throw new DAOException(DAOException.VALUE_NEEDED, "统计开始时间不能为空");
		}

		if (endTime == null) {
			throw new DAOException(DAOException.VALUE_NEEDED, "统计结束时间不能为空");
		}
	}



	private StringBuilder generateHQLforStatistics(
			final Date startTime, final Date endTime, final MeetClinic mc,
			final MeetClinicResult mr, final int start, final String mpiid,
			final List<Integer> requestOrgans, final List<Integer> targetOrgans) {
		StringBuilder hql = new StringBuilder(
				" from MeetClinic mc,MeetClinicResult mr,Patient pt where mc.meetClinicId=mr.meetClinicId and mc.mpiid=pt.mpiId and DATE(mc.requestTime)>=DATE(:startTime) and DATE(mc.requestTime)<=DATE(:endTime)");

		// 添加申请机构条件
		if (requestOrgans != null && requestOrgans.size() > 0) {
			boolean flag = true;
			for (Integer i : requestOrgans) {
				if (i != null) {
					if (flag) {
						hql.append(" and mc.requestOrgan in(");
						flag = false;
					}
					hql.append(i + ",");
				}
			}
			if (!flag) {
				hql = new StringBuilder(hql.substring(0,
						hql.length() - 1) + ") ");
			}
		}

		// 添加目标机构条件
		if (targetOrgans != null && targetOrgans.size() > 0) {
			boolean flag = true;
			for (Integer i : targetOrgans) {
				if (i != null) {
					if (flag) {
						hql.append(" and mr.targetOrgan in(");
						flag = false;
					}
					hql.append(i + ",");
				}
			}
			if (!flag) {
				hql = new StringBuilder(hql.substring(0,
						hql.length() - 1) + ") ");
			}
		}
		// 患者主键
		if (!StringUtils.isEmpty(mpiid)) {
			hql.append(" and mc.mpiid='" + mpiid + "'");
		}

		// 添加申请医生条件
		if (mc.getRequestDoctor() != null) {
			hql.append(" and mc.requestDoctor=" + mc.getRequestDoctor());
		}

		// 添加目标医生条件
		if (mr.getTargetDoctor() != null) {
			hql.append(" and mr.targetDoctor=" + mr.getTargetDoctor());
		}

		// 添加会诊执行单状态
		if (mr.getExeStatus() != null) {
			hql.append(" and mr.exeStatus=" + mr.getExeStatus());
		}

		//添加会诊紧急状态
		if(mc.getMeetClinicType() != null){
			hql.append(" and mc.meetClinicType = " + mc.getMeetClinicType());
		}
		return hql;

	}
	/**
	 * 历史会诊单列表查询（纯分页）
	 * 
	 * @author luf
	 * @param doctorId
	 *            医生内码
	 * @param mpiId
	 *            病人主索引
	 * @param start
	 *            分页开始位置
	 * @param limit
	 *            每页限制条数
	 * @return List<MeetClinicAndResult>
	 */
	@RpcService
	public List<MeetClinicAndResult> queryMeetClinicHisWithPage(
			Integer doctorId, String mpiId, Integer start, Integer limit) {
		List<MeetClinicAndResult> mcars = new ArrayList<MeetClinicAndResult>();
		List<MeetClinic> mcs = this.findMeetClinicWithPage(doctorId, mpiId,
				start, limit);
		EndMeetClinicDAO endMeetClinicDAO = DAOFactory
				.getDAO(EndMeetClinicDAO.class);
		RelationDoctorDAO relationDoctorDAO = DAOFactory
				.getDAO(RelationDoctorDAO.class);
		GroupDAO groupDAO = DAOFactory.getDAO(GroupDAO.class);
		PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
		for (MeetClinic mc : mcs) {
			MeetClinicAndResult mcar = new MeetClinicAndResult();
			List<MeetClinicResult> mrs = endMeetClinicDAO.findByMeetClinicId(mc
					.getMeetClinicId());
			// 签约标志
			String rMPIId = mc.getMpiid();
			Integer rDoctorId = mc.getRequestDoctor();
			Boolean signFlag = relationDoctorDAO.getSignFlag(rMPIId, rDoctorId);
			mcar.setFamilyDoctorFlag(signFlag);//mc.setSignFlag(signFlag);
			// 群组
			Group group = groupDAO.getByBussTypeAndBussId(2,
					mc.getMeetClinicId());// 2-会诊
			if (group != null) {
				mcar.setGroupId(group.getGroupId());//mc.setGroupId(group.getGroupId());
			}
			// 目标医生手机号及团队标志
			mrs = this.convertMeetClinicResult(mrs);
			Patient patient = patientDAO.get(rMPIId);
			mc.setRequestString(DateConversion.convertRequestDateForBuss(mc.getRequestTime()));
			mcar.setMc(mc);
			mcar.setPt(patient);
			mcar.setMeetClinicResults(mrs);
			mcars.add(mcar);
		}
		return mcars;
	}

	/**
	 * 会诊单列表添加目标医生手机号和团队标志（供 queryMeetClinicHisWithPage 调用）
	 * 
	 * @author luf
	 * @param meetClinicResults
	 * @return
	 */
	public List<MeetClinicResult> convertMeetClinicResult(
			List<MeetClinicResult> meetClinicResults) {
		List<MeetClinicResult> mrs = new ArrayList<MeetClinicResult>();
		DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
		// 获取目标医生手机号和团队标志
		for (MeetClinicResult mr : meetClinicResults) {
			int targetDoctorId = mr.getTargetDoctor();
			Object[] objs = doctorDAO
					.getMobileAndTeamsByDoctorId(targetDoctorId);
			String targetMobile = (String) objs[0];
			Boolean targetTeams = (Boolean) objs[1];
			if (StringUtils.isEmpty(targetMobile)) {
				targetMobile = " ";
			}
			if (targetTeams == null) {
				targetTeams = false;
			}
			mr.setTargetMobile(targetMobile);
			mr.setTargetTeams(targetTeams);
			mrs.add(mr);
		}
		return mrs;
	}

	/**
	 * 查询历史会诊申请单（供 queryMeetClinicHisWithPage 调用）
	 * 
	 * @author luf
	 * @param doctorId
	 *            医生内码
	 * @param mpiId
	 *            病人主索引
	 * @param start
	 *            分页开始位置
	 * @param limit
	 *            每页限制条数
	 * @return List<MeetClinic>
	 */
	public List<MeetClinic> findMeetClinicWithPage(final Integer doctorId,
			final String mpiId, final Integer start, final Integer limit) {
		HibernateStatelessResultAction<List<MeetClinic>> action = new AbstractHibernateStatelessResultAction<List<MeetClinic>>() {
			@SuppressWarnings("unchecked")
			public void execute(StatelessSession ss) throws Exception {
				StringBuilder hql = new StringBuilder(
						"select DISTINCT mc from MeetClinic mc,MeetClinicResult mr where mc.meetClinicId=mr.meetClinicId ");
				if (!StringUtils.isEmpty(mpiId)) {
					hql.append("and mc.mpiid=:mpiid ");
				}
				if (doctorId == null) {
					hql.append("and mc.meetClinicStatus>=2 ");
				} else {
					hql.append("and (((mr.exeDoctor =:doctorId or mr.targetDoctor=:doctorId) and mr.exeStatus>=2) or "
							+ "(mc.requestDoctor =:doctorId and mc.meetClinicStatus>=2)) ");
				}
				hql.append("ORDER BY mc.requestTime DESC");

				Query q = ss.createQuery(hql.toString());
				if (!StringUtils.isEmpty(mpiId)) {
					q.setParameter("mpiid", mpiId);
				}
				if (doctorId != null) {
					q.setParameter("doctorId", doctorId);
				}
				q.setFirstResult(start);
				q.setMaxResults(limit);
				List<MeetClinic> mcs = q.list();
				setResult(mcs);
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		return action.getResult();
	}
}
