package eh.base.dao;

import ctd.controller.exception.ControllerException;
import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessAction;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.bus.dao.AppointRecordDAO;
import eh.bus.dao.ConsultDAO;
import eh.bus.dao.MeetClinicDAO;
import eh.bus.dao.TransferDAO;
import eh.entity.base.Doctor;
import eh.entity.base.DoctorAccount;
import eh.entity.base.PatientFeedback;
import eh.entity.bus.AppointRecord;
import eh.entity.bus.Consult;
import eh.entity.bus.MeetClinic;
import eh.entity.bus.Transfer;
import eh.entity.mpi.Patient;
import eh.evaluation.service.PatientFeedbackRelationTabService;
import eh.mpi.dao.PatientDAO;
import eh.utils.DateConversion;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public abstract class PatientFeedbackDAO extends
		HibernateSupportDelegateDAO<PatientFeedback> {

	public PatientFeedbackDAO() {
		super();
		this.setEntityName(PatientFeedback.class.getName());
		this.setKeyField("feedbackId");
	}

	@RpcService
	@DAOMethod
	public abstract PatientFeedback getById(int id);

	@RpcService
	@DAOMethod
	public abstract List<PatientFeedback> findByDoctorId(Integer doctorId);

	/**
	 * 根据点赞人和点赞单查询是否点赞过
	 *
	 * @author ZX
	 * @date 2015-6-18 下午4:59:02
	 * @param serviceType
	 * @param serviceId
	 * @param userId
	 * @param userType
	 * @return
	 */
	@RpcService
	@DAOMethod(sql = "from PatientFeedback where  serviceType=:serviceType and serviceId=:serviceId and userId=:userId and userType=:userType and feedbackType=0")
	public abstract List<PatientFeedback> findPfbsByServiceAndUser(
			@DAOParam("serviceType") String serviceType,
			@DAOParam("serviceId") String serviceId,
			@DAOParam("userId") Integer userId,
			@DAOParam("userType") String userType);

	/**
	 * 判断点赞单是否点赞过
	 *
	 * @author ZX
	 * @date 2015-6-18 下午4:59:02
	 * @param doctorId
	 * @param serviceType
	 * @param serviceId
	 * @param userType
	 * @return
	 */
	@RpcService
	@DAOMethod(limit = 1,sql = "from PatientFeedback where doctorId=:doctorId and  serviceType=:serviceType and serviceId=:serviceId  and userType=:userType and feedbackType=0")
	public abstract List<PatientFeedback> findPfsByDoctorService(
			@DAOParam("doctorId") Integer doctorId,
			@DAOParam("serviceType") String serviceType,
			@DAOParam("serviceId") String serviceId,
			@DAOParam("userType") String userType);

	/**
	 * 根据点赞人和点赞单查询是否点赞过
	 *
	 * @author ZX
	 * @date 2015-6-18 下午4:59:02
	 * @param serviceType
	 * @param serviceId
	 * @param userId
	 * @param userType
	 * @return
	 */
	@RpcService
	@DAOMethod(limit = 1,sql = "from PatientFeedback where doctorId=:doctorId and  serviceType=:serviceType and serviceId=:serviceId and userId=:userId and userType=:userType and feedbackType=0")
	public abstract List<PatientFeedback> findPfsByServiceAndUser(
			@DAOParam("doctorId") Integer doctorId,
			@DAOParam("serviceType") String serviceType,
			@DAOParam("serviceId") String serviceId,
			@DAOParam("userId") Integer userId,
			@DAOParam("userType") String userType);

	/**
	 * 判断是否已经点过赞(会诊页面加载的时候进行使用)
	 *
	 * @author ZX
	 * @date 2015-6-18 下午5:02:51
	 * @param serviceType
	 * @param serviceId
	 * @param userId
	 * @param userType
	 * @return
	 */
	@RpcService
	public boolean isPraise(String serviceType, String serviceId,
			Integer userId, String userType) {
		List<PatientFeedback> feedback = findPfbsByServiceAndUser(serviceType,
				serviceId, userId, userType);
		if (feedback == null || feedback.size() < 1) {
			return false;
		} else {
			return true;
		}
	}

	/**
	 * 判断一个医生是否点过赞
	 *
	 * @author ZX
	 * @date 2015-7-2 下午5:34:50
	 * @param serviceType
	 * @param serviceId
	 * @param userId
	 * @param userType
	 * @return
	 */
	@RpcService
	public boolean isPraiseForDoc(Integer doctorId, String serviceType,
			String serviceId, Integer userId, String userType) {
		List<PatientFeedback> feedbacks = findPfsByServiceAndUser(doctorId, serviceType,
				serviceId, userId, userType);

		if (feedbacks == null || feedbacks.isEmpty()) {
			return false;
		} else {
			return true;
		}
	}

	/**
	 * 查询医生点评点赞记录数
	 *
	 * @author hyj
	 * @param doctorId
	 * @return
	 */
	@RpcService
	public Long getPatientFeedbackNum(final Integer doctorId) {
		if (doctorId == null) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"doctorId is required");
		}
		return getNumByDoctorId(doctorId);
	}

	/**
	 * 查询医生点评总分
	 *
	 * @author hyj
	 * @param doctorId
	 * @return
	 * @throws DAOException
	 */
	@RpcService
	public Object[] addEvaValue(final int doctorId) throws DAOException {

		HibernateStatelessResultAction<Object[]> action = new AbstractHibernateStatelessResultAction<Object[]>() {
			public void execute(StatelessSession ss) throws Exception {
				StringBuilder hql = new StringBuilder(
						"select sum(evaValue),sum(service),sum(tech) from PatientFeedback where doctorId=:doctorId");
				Query q = ss.createQuery(hql.toString());
				q.setParameter("doctorId", doctorId);
				Object[] totalCount = (Object[]) q.uniqueResult();
				setResult(totalCount);
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		return (Object[]) action.getResult();

	}

	/**
	 * 会诊医生点赞服务
	 *
	 * @author hyj
	 * @param patientFeedbackList
	 */
	@RpcService
	public void addFeedBackArrayByGood(
			final List<PatientFeedback> patientFeedbackList) {
		for (PatientFeedback patientFeedback : patientFeedbackList) {
			addFeedBackByGood(patientFeedback);
		}
	}

	/**
	 * 医生点赞服务
	 *
	 * @author hyj
	 * @param patientFeedback
	 */
	@RpcService
	public void addFeedBackByGood(final PatientFeedback patientFeedback) {
		HibernateStatelessResultAction<DoctorAccount> action = new AbstractHibernateStatelessResultAction<DoctorAccount>() {
			@Override
			public void execute(StatelessSession ss) throws Exception {
				if (patientFeedback.getDoctorId() == null) {
					throw new DAOException(DAOException.VALUE_NEEDED,
							"doctorId is required");
				}
				if (StringUtils.isEmpty(patientFeedback.getServiceType())) {
					throw new DAOException(DAOException.VALUE_NEEDED,
							"ServiceType is required");
				}
				if (StringUtils.isEmpty(patientFeedback.getServiceId())) {
					throw new DAOException(DAOException.VALUE_NEEDED,
							"serviceId is required");
				}
				if (patientFeedback.getUserId() == null) {
					throw new DAOException(DAOException.VALUE_NEEDED,
							"userId is required");
				}
				if (StringUtils.isEmpty(patientFeedback.getUserType())) {
					throw new DAOException(DAOException.VALUE_NEEDED,
							"userType is required");
				}
				patientFeedback.setEvaDate(new Date());

				boolean isgood = isPraiseForDoc(patientFeedback.getDoctorId(),
						patientFeedback.getServiceType(),
						patientFeedback.getServiceId(),
						patientFeedback.getUserId(),
						patientFeedback.getUserType());
				if (isgood) {
					return;
				}

				// 根据业务类型和业务id查询mpiid
				patientFeedback.setMpiid(getMpiidByServiceType(
						patientFeedback.getServiceType(),
						patientFeedback.getServiceId()));

				patientFeedback.setFeedbackType(0);
				patientFeedback.setGood(1);

				save(patientFeedback);

				DoctorDAO doctorDao = DAOFactory.getDAO(DoctorDAO.class);
				Doctor d = doctorDao.getByDoctorId(patientFeedback
						.getDoctorId());

				if (d.getGoodRating() == null) {
					doctorDao
							.updateGoodRating(patientFeedback.getDoctorId(), 1);
				} else {
					doctorDao.updateGoodRating(patientFeedback.getDoctorId(),
							d.getGoodRating() + 1, d.getGoodRating());
				}

			}
		};
		HibernateSessionTemplate.instance().executeTrans(action);

	}

	/**
	 * 医生点评查询方法
	 *
	 * @param doctorId
	 *            --医生代码
	 * @param serviceType
	 *            --业务类别
	 * @return
	 */
	@RpcService
	@DAOMethod(sql = "from PatientFeedback where doctorId=:doctorId and serviceType=:serviceType and feedbackType=0 order by evaDate desc")
	public abstract List<PatientFeedback> findByDoctorIdAndServiceType(
			@DAOParam("doctorId") int doctorId,
			@DAOParam("serviceType") String serviceType);

	/**
	 * 根据业务类型和业务id查询mpiid
	 *
	 * @author hyj
	 * @param ServiceType
	 * @param serviceId
	 * @return
	 */
	public String getMpiidByServiceType(String ServiceType, String serviceId) {
		String result = "";
		int id = Integer.parseInt(serviceId);
		// 转诊
		if (ServiceType.equals("1")) {
			TransferDAO dao = DAOFactory.getDAO(TransferDAO.class);
			Transfer t = dao.getById(id);
			if (t == null) {
				throw new DAOException(609, "该转诊单不存在，无法点评");
			}
			result = t.getMpiId();
		}
		// 会诊
		if (ServiceType.equals("2")) {
			MeetClinicDAO resultDAO = DAOFactory.getDAO(MeetClinicDAO.class);
			MeetClinic mc = resultDAO.get(id);
			if (mc == null) {
				throw new DAOException(609, "该会诊单不存在，无法点评");
			}
			result = mc.getMpiid();
		}
		// 咨询
		if (ServiceType.equals("3")) {
			ConsultDAO dao = DAOFactory.getDAO(ConsultDAO.class);
			Consult c = dao.getById(id);
			if (c == null) {
				throw new DAOException(609, "该咨询单不存在，无法点评");
			}
			result = c.getMpiid();
		}
		// 预约
		if (ServiceType.equals("4")) {
			AppointRecordDAO dao = DAOFactory.getDAO(AppointRecordDAO.class);
			AppointRecord a = dao.getByAppointRecordId(id);
			if (a == null) {
				throw new DAOException(609, "该预约单不存在，无法点评");
			}
			result = a.getMpiid();
		}
		return result;
	}

	@RpcService
	@DAOMethod(limit = 10, sql = "from PatientFeedback where doctorId=:doctorId and userType=:userType and feedbackType=0 order by evaDate desc")
	public abstract List<PatientFeedback> findPatientFeedbackListByDoctorId(
			@DAOParam("doctorId") int doctorId,
			@DAOParam("userType") String userType,
			@DAOParam(pageStart = true) int start);

	/**
	 * 医生A被点赞的数
	 *
	 * @author zhangx
	 * @date 2015-12-10 上午11:24:13
	 * @param doctorId
	 *            医生A的doctorId
	 * @param userType
	 *            点赞人角色(patient:患者;doctor:医生)
	 * @return
	 */
	@RpcService
	@DAOMethod(sql = "select count(*) from PatientFeedback where doctorId=:doctorId and userType=:userType and good is not null and feedbackType=0")
	public abstract Long getNumByDoctorIdAndUserType(
			@DAOParam("doctorId") int doctorId,
			@DAOParam("userType") String userType);

	/**
	 * 获取点赞点评总记录数
	 *
	 * @author zhangx
	 * @date 2015-12-22 下午8:07:05
	 * @param doctorId
	 * @return
	 */
	@RpcService
	@DAOMethod(sql = "select count(*) from PatientFeedback where doctorId=:doctorId and feedbackType=0")
	public abstract Long getNumByDoctorId(@DAOParam("doctorId") int doctorId);


	/**
	 * 查询我的点赞业务列表（新，加入图片及时间字段）
	 *
	 * @author AngryKitty
	 * @date 2015-11-10
	 * @param doctorId
	 * @param userType
	 *            doctor:医生;patient:患者
	 *
	 *            修改日期：2016-1-20--添加申请人姓名，点赞医生姓名
	 * @return
	 * @throws ControllerException
	 */
	@RpcService
	public List<PatientFeedback> findFeedInfoAndPhotoByDoctorId(int doctorId,
			String userType, int start) throws ControllerException {
		List<PatientFeedback> returnList = new ArrayList<PatientFeedback>();

		PatientDAO patDao = DAOFactory.getDAO(PatientDAO.class);
		DoctorDAO docDao = DAOFactory.getDAO(DoctorDAO.class);

		List<PatientFeedback> list = findPatientFeedbackListByDoctorId(
				doctorId, userType, start);

		// 循环组装
		for (PatientFeedback patientFeedback : list) {
			String mpiId = patientFeedback.getMpiid();
			if (!patDao.exist(mpiId)) {
				continue;
			}
			Patient p = patDao.getByMpiId(mpiId);
			String time = DateConversion
					.convertRequestDateForBuss(patientFeedback.getEvaDate());
			PatientFeedback back = new PatientFeedback();
			back.setTitle(p.getPatientName());
			back.setTime(time);
			back.setDoctorId(patientFeedback.getDoctorId());
			back.setServiceType(patientFeedback.getServiceType());
			back.setServiceId(patientFeedback.getServiceId());
			back.setUserType(patientFeedback.getUserType());

			Integer sType = Integer.valueOf(patientFeedback.getServiceType());
			Integer sId = Integer.valueOf(patientFeedback.getServiceId());
			Integer requestD = null;
			String requestM = null;
			switch (sType) {
			case 1:
				TransferDAO tDao = DAOFactory.getDAO(TransferDAO.class);
				Transfer t = tDao.get(sId);
				if (t == null) {
					break;
				}
				if (!StringUtils.isEmpty(t.getRequestMpi())) {
					requestM = t.getRequestMpi();
				} else {
					requestD = t.getRequestDoctor();
				}
				break;
			case 2:
				MeetClinicDAO mDao = DAOFactory.getDAO(MeetClinicDAO.class);
				MeetClinic mc = mDao.get(sId);
				if (mc == null) {
					break;
				}
				requestD = mc.getRequestDoctor();
				UserRolesDAO urDao = DAOFactory.getDAO(UserRolesDAO.class);
				Integer urtId = patientFeedback.getUserId();
				if(!urDao.exist(urtId)) {
					break;
				}
				String mobile = urDao.get(urtId).getUserId();
				back.setFeedName(docDao.getNameByMobile(mobile));
				break;
			case 3:
				ConsultDAO cDao = DAOFactory.getDAO(ConsultDAO.class);
				Consult c = cDao.get(sId);
				if (c == null) {
					break;
				}
				requestM = c.getRequestMpi();
				break;
			case 4:
			default:
				break;
			}
			if (!StringUtils.isEmpty(requestM)) {
				back.setRequestName(patDao.getNameByMpiId(requestM));
				back.setPhoto(patDao.get(requestM).getPhoto());
			}
			if (requestD !=null && requestD > 0) {
				back.setRequestName(docDao.getNameById(requestD));
				back.setPhoto(docDao.getPhotoByDoctorId(requestD));
			}

			returnList.add(back);
		}

		return returnList;

	}

	/**
	 * 会诊报告被点赞数
	 *
	 * @author luf
	 * @param doctorId
	 *            医生内码
	 * @param serviceType
	 *            业务类型--2会诊
	 * @param serviceId
	 *            业务序号 -meetclinicId
	 * @return Long
	 */
	@DAOMethod(sql = "select count(*) from PatientFeedback where doctorId=:doctorId and serviceType=:serviceType and serviceId=:serviceId and feedbackType=0")
	public abstract Long getClinicNumByDoctor(
			@DAOParam("doctorId") Integer doctorId,
			@DAOParam("serviceType") String serviceType,
			@DAOParam("serviceId") String serviceId);


	public void upPatientFeedbackByID(final Integer feedbackId) {

		final PatientFeedback feedback = getById(feedbackId);
		if (feedback != null) {
			HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
				@Override
				public void execute(StatelessSession ss) throws Exception {
					feedback.setIsDel(1);
					update(feedback);
					PatientFeedbackRelationTabService feedbackRelationTabService = AppContextHolder.getBean(
							"eh.patientFeedbackRelationTabService", PatientFeedbackRelationTabService.class);
					feedbackRelationTabService.delFeedbackRelationTabByFeedbackId(feedbackId);
				}
			};
			HibernateSessionTemplate.instance().executeTrans(action);
		}
	}
}
