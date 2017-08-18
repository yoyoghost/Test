package eh.bus.dao;

import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.entity.bus.MeetClinic;
import eh.entity.bus.MeetClinicResult;
import eh.msg.dao.GroupDAO;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class StartMeetClinicDAO extends
		HibernateSupportDelegateDAO<MeetClinicResult> {
	public static final Logger logger = LoggerFactory.getLogger(StartMeetClinicDAO.class);

	public StartMeetClinicDAO() {
		super();
		this.setEntityName(MeetClinicResult.class.getName());
		this.setKeyField("meetClinicResultId");
	}

	@RpcService
	@DAOMethod
	public abstract MeetClinicResult getByMeetClinicResultId(
			int meetClinicResultid);

	/**
	 * 会诊开始服务
	 *
	 * @author LF
	 * @param meetClinicResult
	 * @return
	 * @throws DAOException
	 */
	@RpcService
	public boolean startMeetClinicNew(MeetClinicResult meetClinicResult)
			throws DAOException {
		logger.info("会诊开始服务(startMeetClinicNew):meetClinicResult="
				+ JSONUtils.toString(meetClinicResult));
		if (meetClinicResult.getMeetClinicResultId() == null) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"===== meetClinicResultId is required =====");
		}
		if (meetClinicResult.getMeetClinicId() == null) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"===== meetClinicId is required =====");
		}
		if (meetClinicResult.getExeOrgan() == null) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"===== exeOrgan is required =====");
		}
		if (meetClinicResult.getExeDepart() == null) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"===== exeDepart is required =====");
		}
		if (meetClinicResult.getExeDoctor() == null) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"===== exeDoctor is required =====");
		}
		final Integer meetClinicResultId = meetClinicResult
				.getMeetClinicResultId();
		final Integer meetClinicId = meetClinicResult.getMeetClinicId();
		final Integer doctorId = meetClinicResult.getExeDoctor();
		final Integer exeOrgan = meetClinicResult.getExeOrgan();
		final Integer exeDepart = meetClinicResult.getExeDepart();
		final MeetClinicResult meetClinicResult1 = this
				.getByMeetClinicResultId(meetClinicResultId);
		HibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
			@Override
			public void execute(StatelessSession ss) throws Exception {
				Integer exeStatus = 0;
				Integer exeDoctor = 0;
				exeStatus = meetClinicResult1.getExeStatus();
				exeDoctor = meetClinicResult1.getExeDoctor();
				if (exeStatus.equals(0) && exeDoctor == null) {
					String hql = new String(
							"UPDATE MeetClinicResult SET exeStatus=:exeStatus, exeOrgan =:exeOrgan, exeDepart =:exeDepart, exeDoctor =:exeDoctor, startTime = NOW() WHERE meetClinicResultId =:meetClinicResultId");
					Query q = ss.createQuery(hql);
					q.setParameter("exeStatus", 1);
					q.setParameter("meetClinicResultId", meetClinicResultId);
					q.setParameter("exeOrgan", exeOrgan);
					q.setParameter("exeDepart", exeDepart);
					q.setParameter("exeDoctor", doctorId);
					q.executeUpdate();
					hql = new String(
							"UPDATE MeetClinic SET meetClinicStatus =:exeStatus WHERE meetClinicId =:meetClinicId");
					q = ss.createQuery(hql);
					q.setParameter("exeStatus", 1);
					q.setParameter("meetClinicId", meetClinicId);
					q.executeUpdate();
					setResult(true);
				} else if (exeStatus.equals(1) && exeDoctor != null && exeDoctor.equals(doctorId)) {
					setResult(true);
				} else if (exeStatus > 0 && exeDoctor != null && !exeDoctor.equals(doctorId)) {
					setResult(false);
				} else {
					setResult(false);
				}
			}
		};
		HibernateSessionTemplate.instance().executeTrans(action);
		Boolean isSuccess = action.getResult();
		if(!isSuccess) {
			return isSuccess;
		}
		GroupDAO gDao = DAOFactory.getDAO(GroupDAO.class);
		Integer bussType = 2;
		gDao.addUserToGroup(bussType, meetClinicId, doctorId);
		return isSuccess;
	}

	/**
	 * 会诊中心接收会诊
	 * @param meetClinicResultId
	 * @return
	 */
	@RpcService
	public Boolean receiveMeetClinicForMeetCenter(final int meetClinicResultId){
		HibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
			@Override
			public void execute(StatelessSession ss) throws Exception {
				MeetClinicResult mr = getByMeetClinicResultId(meetClinicResultId);
				int meetClinicId = mr.getMeetClinicId();
				MeetClinic mc = DAOFactory.getDAO(MeetClinicDAO.class).getByMeetClinicId(meetClinicId);
				Integer exeStatus = 0;
				Integer exeDoctor = 0;
				exeStatus = mr.getExeStatus();
				exeDoctor = mr.getExeDoctor();
				int count=0;
				if (exeStatus.equals(1) && exeDoctor != null) {
					String hql = new String(
							"UPDATE MeetClinicResult SET meetCenterStatus=:meetCenterStatus WHERE meetClinicResultId =:meetClinicResultId and meetCenter=true");
					Query q = ss.createQuery(hql);
					q.setParameter("meetCenterStatus", 1);
					q.setParameter("meetClinicResultId", meetClinicResultId);
					count = q.executeUpdate();
					if (count > 0 && mc.getExpectTime() != null) {
						DAOFactory.getDAO(MeetClinicDAO.class).updatePlanTimeByMeetClinicId(meetClinicId, mc.getExpectTime());
					}
				}
				setResult(count>0);
			}
		};
		HibernateSessionTemplate.instance().executeTrans(action);
		logger.info("会诊中心接收会诊meetClinicResultId[{}]返回结果[{}]",meetClinicResultId,action.getResult());
		return action.getResult();

	}
}
