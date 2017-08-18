package eh.base.dao;

import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.AppContextHolder;
import ctd.util.BeanUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.SystemConstant;
import eh.entity.base.RelationPatientAndLabel;
import eh.entity.mpi.RelationDoctor;
import eh.mpi.dao.FollowPlanDAO;
import eh.mpi.dao.FollowScheduleDAO;
import eh.mpi.dao.PatientDAO;
import eh.mpi.dao.RelationDoctorDAO;
import eh.mpi.service.follow.FollowQueryService;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import java.util.*;

/**
 * 关注的病人
 */
public abstract class RelationPatientDAO extends
		HibernateSupportDelegateDAO<RelationDoctor> {

	public static final Logger log = Logger.getLogger(RelationPatientDAO.class);

	public RelationPatientDAO() {
		super();
		setEntityName(RelationDoctor.class.getName());
		setKeyField("relationDoctorId");
	}

	/**
	 * 查询医生关注的病人列表(全部关注病人)
	 *
	 * @author LF
	 * @param id
	 * @return
	 */
	@RpcService
	@DAOMethod(limit = 10)
	public abstract List<RelationDoctor> findByDoctorIdAndRelationType(
			Integer doctorId, Integer relationType, long start);

	/**
	 * 根据mpiId和doctorId查询
	 *
	 * @author ZX
	 * @date 2015-6-7 下午5:59:56
	 * @param id
	 *
	 * @return
	 */
	@RpcService
	@DAOMethod(sql = "From RelationDoctor where mpiId=:mpiId and doctorId=:doctorId and (relationType=2 or (relationType=0 and current_timestamp()>=startDate and current_timestamp()<=endDate))")
	public abstract RelationDoctor getByMpiidAndDoctorId(
			@DAOParam("mpiId") String mpiId,
			@DAOParam("doctorId") Integer doctorId);

	/**
	 * 查询某医生关注的所有病人（供关注病人查询服务调用）
	 *
	 * @author LF
	 * @param doctorId
	 * @param start
	 * @return
	 */
	/*
	 * @RpcService
	 *
	 * @DAOMethod(limit=10,sql=
	 * "select new eh.entity.base.RelationPatientAndLabel(p) from RelationPatient p where doctorId=:doctorId"
	 * ) public abstract List<RelationPatientAndLabel>
	 * findRelationPatientAndLabelByDoctorId(@DAOParam("doctorId")Integer
	 * doctorId,
	 *
	 * @DAOParam(pageStart = true) int start);
	 */
	/**
	 * 更新医生对患者的备注
	 *
	 * @author XBZ
	 * @param doctorId
	 * @param mpiId
	 * @param newNote
	 * @return
	 */
	@RpcService
	public RelationDoctor updateNote(String mpiId, Integer doctorId,
									 String newNote) {
		RelationPatientDAO dao = DAOFactory.getDAO(RelationPatientDAO.class);
		RelationDoctor relDoctor = dao.getByMpiidAndDoctorId(mpiId, doctorId);
		if (relDoctor != null) {
			relDoctor.setNote(newNote);
			update(relDoctor);
		}
		return relDoctor;
	}

	/**
	 * 有标签的情况下（供关注病人查询服务（Old）和（前段调用）调用）
	 *
	 * @author LF
	 * @param doctorId
	 * @param labelName
	 * @param start
	 * @return List<RelationPatientAndLabel>
	 */
	@RpcService
	@DAOMethod(limit = 10, sql = "SELECT new eh.entity.base.RelationPatientAndLabel(p,l) FROM RelationDoctor p,RelationLabel l WHERE p.doctorId=:doctorId AND l.labelName=:labelName AND p.relationDoctorId=l.relationPatientId AND (p.relationType=2 or (p.relationType=0 and current_timestamp()>=startDate and current_timestamp()<=endDate))")
	public abstract List<RelationPatientAndLabel> findRelationPatientAndLabelByDoctorIdAndLabelName(
			@DAOParam("doctorId") Integer doctorId,
			@DAOParam("labelName") String labelName,
			@DAOParam(pageStart = true) int start);

	/**
	 * 关注病人查询服务(Old)
	 *
	 * @author LF
	 * @param doctorId
	 * @param labelName
	 * @param start
	 * @return
	 */
	/*
	 * @RpcService public List<RelationPatientAndLabel>
	 * findRelationPatients(Integer doctorId, String labelName, int start) {
	 * if(StringUtils.isEmpty(labelName)) { return
	 * findRelationPatientAndLabelByDoctorId(doctorId, start); } return
	 * findRelationPatientAndLabelByDoctorIdAndLabelName(doctorId, labelName,
	 * start); }
	 */

	/**
	 * 供 关注病人查询服务(供前端调用) 调用
	 *
	 * @author LF
	 * @param doctorId
	 * @param start
	 * @return List<RelationPatientAndLabel>
	 */
	@DAOMethod(limit = 10, sql = "select new eh.entity.base.RelationPatientAndLabel(r,p) from RelationDoctor r,Patient p where r.doctorId=:doctorId and r.mpiId=p.mpiId and (r.relationType=2 or (r.relationType=0 and current_timestamp()>=startDate and current_timestamp()<=endDate))")
	public abstract List<RelationPatientAndLabel> findRelationPatientAndLabelsByDoctorId(
			@DAOParam("doctorId") Integer doctorId,
			@DAOParam(pageStart = true) int start);

	/**
	 * 关注病人查询服务(供前端调用)
	 *
	 * @author LF
	 * @param doctorId
	 * @param labelName
	 * @param start
	 * @return List<RelationPatientAndLabel>
	 */
	@RpcService
	public List<RelationPatientAndLabel> findRelationPatientAndLabels(
			Integer doctorId, String labelName, int start) {
		if (doctorId == null) {
			new DAOException(DAOException.VALUE_NEEDED, "doctorId is required!");
		}
		List<RelationPatientAndLabel> relationPatientAndLabels = new ArrayList<RelationPatientAndLabel>();
		if (StringUtils.isEmpty(labelName)) {
			relationPatientAndLabels = findRelationPatientAndLabelsByDoctorId(
					doctorId, start);
			RelationLabelDAO relationLabelDAO = DAOFactory
					.getDAO(RelationLabelDAO.class);
			for (int i = 0; i < relationPatientAndLabels.size(); i++) {
				relationPatientAndLabels
						.get(i)
						.setRelationLabels(
								relationLabelDAO
										.findByRelationPatientId(relationPatientAndLabels
												.get(i).getRelationPatient()
												.getRelationDoctorId()));
			}
		} else {
			relationPatientAndLabels = findRelationPatientAndLabelByDoctorIdAndLabelName(
					doctorId, labelName, start);
			PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
			for (int i = 0; i < relationPatientAndLabels.size(); i++) {
				relationPatientAndLabels.get(i).setPatient(
						patientDAO.getByMpiId(relationPatientAndLabels.get(i)
								.getRelationPatient().getMpiId()));
			}
		}
		return relationPatientAndLabels;
	}

	/**
	 * 关注病人
	 *
	 * @author luf
	 * @param relationPatient
	 *            --RelationDoctor（mpiId，doctorId不空）
	 * @return Integer
	 */
	@RpcService
	public Integer addRelationPatient(RelationDoctor relationPatient) {
		String mpiId = relationPatient.getMpiId();
		Integer doctorId = relationPatient.getDoctorId();
		boolean isRelationPatient = isRelationPatient(mpiId, doctorId);
		Integer relationPatientId = null;
		if (isRelationPatient) {
			return relationPatientId;
		}
		RelationDoctor rd = this.getOffSignByMpiAndDoc(mpiId, doctorId);
		relationPatient.setRelationDate(new Date());
		relationPatient.setFamilyDoctorFlag(false);
		relationPatient.setRelationType(2);// 医生关注病人
		if (rd == null) {
			relationPatient = save(relationPatient);
		} else {
			BeanUtils.map(relationPatient, rd);
			rd.setStartDate(null);
			rd.setEndDate(null);
			relationPatient = update(rd);
		}
		relationPatientId = relationPatient.getRelationDoctorId();
		return relationPatientId;
	}

	/**
	 * 返回relationPatientId
	 * @param relationPatient
	 * @return
     */
	public Integer addRelationPatientReturnId(RelationDoctor relationPatient){
		String mpiId = relationPatient.getMpiId();
		Integer doctorId = relationPatient.getDoctorId();
		if(StringUtils.isEmpty(mpiId) || null == doctorId){
			throw new DAOException(DAOException.VALUE_NEEDED, "mpiId or doctorId is null");
		}
		RelationDoctor rp = this.getByMpiidAndDoctorId(mpiId, doctorId);
		Integer relationPatientId;
		if (null != rp) {
			relationPatientId = rp.getRelationDoctorId();
		}else {
			RelationDoctor rd = this.getOffSignByMpiAndDoc(mpiId, doctorId);
			relationPatient.setRelationDate(new Date());
			relationPatient.setFamilyDoctorFlag(false);
			relationPatient.setRelationType(2);// 医生关注病人
			if (rd == null) {
				relationPatient = save(relationPatient);
			} else {
				BeanUtils.map(relationPatient, rd);
				rd.setStartDate(null);
				rd.setEndDate(null);
				relationPatient = update(rd);
			}
			relationPatientId = relationPatient.getRelationDoctorId();
		}
		return relationPatientId;
	}

	/**
	 * 医生关注患者（出参包含该医生是否有对目标患者的随访记录）
	 * @param relationPatient
	 * @return
	 */
	@RpcService
	public Map addRelationPatientNew(RelationDoctor relationPatient) {
		Map map = new HashMap();
		Map fMap = AppContextHolder.getBean("eh.followQueryService", FollowQueryService.class).findNearTwoSchedule(relationPatient.getMpiId(),relationPatient.getDoctorId());
		map.put("flag",fMap.get("flag"));
		String mpiId = relationPatient.getMpiId();
		Integer doctorId = relationPatient.getDoctorId();
		boolean isRelationPatient = isRelationPatient(mpiId, doctorId);
		Integer relationPatientId = null;
		if (isRelationPatient) {
			map.put("relationPatientId",relationPatientId);
			return map;
		}
		RelationDoctor rd = this.getOffSignByMpiAndDoc(mpiId, doctorId);
		relationPatient.setRelationDate(new Date());
		relationPatient.setFamilyDoctorFlag(false);
		relationPatient.setRelationType(2);// 医生关注病人
		if (rd == null) {
			relationPatient = save(relationPatient);
		} else {
			BeanUtils.map(relationPatient, rd);
			rd.setStartDate(null);
			rd.setEndDate(null);
			relationPatient = update(rd);
		}
		relationPatientId = relationPatient.getRelationDoctorId();
		map.put("relationPatientId",relationPatientId);
		return map;
	}

	/**
	 * 获取医生和病人的过期签约记录（供其它调用）
	 *
	 * @author luf
	 * @param mpiId
	 *            主索引ID
	 * @param doctorId
	 *            医生编码
	 * @return RelationDoctor
	 */
	@RpcService
	@DAOMethod(sql = "From RelationDoctor where mpiId=:mpiId and doctorId=:doctorId and relationType=0 "
			+ "and(current_timestamp()<=startDate or current_timestamp()>=endDate)")
	public abstract RelationDoctor getOffSignByMpiAndDoc(
			@DAOParam("mpiId") String mpiId,
			@DAOParam("doctorId") Integer doctorId);

	/**
	 * 关注病人(添加标签)
	 *
	 * @author LF
	 * @param relationPatient
	 * @param labelNames
	 * @return
	 */
	@RpcService
	public Map<String,Object> focuseOnThePatient(RelationDoctor relationPatient, List<String> labelNames) {
		if (relationPatient.getDoctorId() == null) {
//            log.error("focuseOnThePatient doctorId为null");
			throw new DAOException(DAOException.VALUE_NEEDED, "focuseOnThePatient need doctorId!");
		}
		if (StringUtils.isEmpty(relationPatient.getMpiId())) {
//            log.error("focuseOnThePatient mpiId为null");
			throw new DAOException(DAOException.VALUE_NEEDED, "focuseOnThePatient need mpiId!");
		}
		Map<String,Object> backMap = new HashMap<>();
		String code = SystemConstant.SUCCESS;
		String msg = "";

		Integer relId = addRelationPatient(relationPatient);
		//如果关注病人ID为空，即该病人已被关注
		if (null == labelNames || labelNames.isEmpty()) {
			backMap.put("code", code);
			backMap.put("msg", msg);
		}else{
			// 有标签，且病人关注成功，增加标签
			RelationLabelDAO relationLabelDAO = DAOFactory.getDAO(RelationLabelDAO.class);
			backMap = relationLabelDAO.batchSaveLabel(relationPatient.getDoctorId(), Arrays.asList(relationPatient.getMpiId()),labelNames);
		}

		return backMap;
	}

	/**
	 * 取消关注
	 *
	 * @author luf
	 * @param mpiId
	 *            主索引ID
	 * @param doctorId
	 *            医生编码
	 * @return Boolean
	 */
	@RpcService
	public Boolean delRelationPatient(String mpiId, Integer doctorId) {
		boolean isRelationPatient = isRelationPatient(mpiId, doctorId);
		RelationDoctor rd = DAOFactory.getDAO(RelationDoctorDAO.class)
				.getSignByMpiAndDoc(mpiId, doctorId);
		FollowPlanDAO followPlanDAO = DAOFactory.getDAO(FollowPlanDAO.class);
		FollowScheduleDAO followScheduleDAO = DAOFactory.getDAO(FollowScheduleDAO.class);
		if (isRelationPatient && rd == null) {
			RelationDoctor rp = this.getByMpiidAndDoctorId(mpiId, doctorId);
			this.remove(rp.getRelationDoctorId());
			DAOFactory.getDAO(RelationLabelDAO.class)
					.deleteByRelationPatientId(rp.getRelationDoctorId());
			List<String> planIds = followPlanDAO.findPlanByManual(doctorId, mpiId);
			for(String planId : planIds){
				followPlanDAO.deleteByPlanId(planId);
				followScheduleDAO.deleteByPlanId(planId);
			}
			return true;
		}
		return false;
	}

	/**
	 * 查看医生是否关注过这个这个病人
	 *
	 * @author ZX
	 * @date 2015-6-7 下午6:12:20
	 * @param mpiId
	 * @param doctorId
	 * @return
	 */
	@RpcService
	public boolean isRelationPatient(String mpiId, Integer doctorId) {
		if (StringUtils.isEmpty(mpiId)) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"mpiId can't  is null or ''");
		}

		// 判断是否存在这个病人
		PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
		if (!patientDAO.exist(mpiId)) {
			throw new DAOException(DAOException.VALUE_NEEDED, "Patient["
					+ mpiId + "] not exist");
		}

		if (doctorId == null || doctorId == 0) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"doctorId can't  is null or 0");
		}

		// 判断是否存在这个医生
		DoctorDAO doctorDao = DAOFactory.getDAO(DoctorDAO.class);
		if (!doctorDao.exist(doctorId)) {
			throw new DAOException(DAOException.VALUE_NEEDED, "doctor["
					+ doctorId + "] not exist");
		}

		RelationDoctor rp = this.getByMpiidAndDoctorId(mpiId, doctorId);

		if (rp == null) {
			return false;
		} else {
			return true;
		}
	}
}
