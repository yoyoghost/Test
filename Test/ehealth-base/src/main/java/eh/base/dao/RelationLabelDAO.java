package eh.base.dao;

import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.constant.SystemConstant;
import eh.entity.base.RelationLabel;
import eh.entity.mpi.RelationDoctor;
import eh.mpi.dao.PatientDAO;
import eh.mpi.dao.RelationDoctorDAO;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ctd.persistence.DAOFactory.getDAO;

public abstract class RelationLabelDAO extends
		HibernateSupportDelegateDAO<RelationLabel> {

	public static final Logger log = Logger.getLogger(RelationLabelDAO.class);

    private static final int PATIENT_LABELS_COUNT_LIMIT = 10;

    private static final int DOCTOR_LABELS_COUNT_LIMIT = 100;

	public RelationLabelDAO() {
		super();
		setEntityName(RelationLabel.class.getName());
		setKeyField("relationPatientId");
	}

	/**
	 * 查询标签列表服务
	 * 
	 * @author LF
	 * @param doctorId
	 * @return
	 */
	@RpcService
	public List<Object[]> findRelationLabelByDoctorId(final Integer doctorId) {
		HibernateStatelessResultAction<List<Object[]>> action = new AbstractHibernateStatelessResultAction<List<Object[]>>() {
			@SuppressWarnings("unchecked")
			@Override
			public void execute(StatelessSession ss) throws Exception {
				String hql = new String("SELECT l.labelName,count(*) FROM RelationLabel l,RelationDoctor p WHERE p.doctorId=:doctorId AND l.relationPatientId=p.relationDoctorId GROUP BY labelName");
				Query q = ss.createQuery(hql);
				q.setParameter("doctorId", doctorId);
				List<Object[]> labelNames = q.list();
				setResult(labelNames);
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		return action.getResult();
	}

	/**
	 * 
	*
	* @Class eh.base.dao.RelationLabelDAO.java
	*
	* @Title: findSignRelationLabelByDoctorId
	
	* @Description: TODO 查询签约的标签列表
	
	* @param @param doctorId
	* @param @return    
	
	* @author AngryKitty
	
	* @Date 2015-12-29下午5:27:58 
	
	* @return List<Object[]>   
	
	* @throws
	 */
	@RpcService
	public List<Object[]> findSignRelationLabelByDoctorId(final Integer doctorId) {
		HibernateStatelessResultAction<List<Object[]>> action = new AbstractHibernateStatelessResultAction<List<Object[]>>() {
			@SuppressWarnings("unchecked")
			@Override
			public void execute(StatelessSession ss) throws Exception {
				String hql = new String("SELECT l.labelName,count(*) FROM RelationLabel l,RelationDoctor p "
						+ "WHERE p.doctorId=:doctorId AND l.relationPatientId=p.relationDoctorId"
						+ " AND p.relationType=0 AND  current_timestamp()>=startDate AND current_timestamp()<=endDate GROUP BY labelName");
				Query q = ss.createQuery(hql);
				q.setParameter("doctorId", doctorId);
				List<Object[]> labelNames = q.list();
				setResult(labelNames);
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		return action.getResult();
	}

	/**
	 * 添加某个关注的标签服务
	 * 
	 * @author LF
	 * @param relationPatientId
	 *            关注ID
	 * @param labelNames
	 *            需添加的标签名
	 * @return Boolean
	 */
	@RpcService
	public Boolean addRelationLabel(Integer relationPatientId,
			List<String> labelNames) {
		log.info("添加某个关注的标签服务(addRelationLabel):relationPatientId="
				+ JSONUtils.toString(relationPatientId) + ";labelNames="
				+ JSONUtils.toString(labelNames));
		if (labelNames.size() <= 0) {
			return false;
		}
		RelationLabel relationLabel = new RelationLabel();
		relationLabel.setRelationPatientId(relationPatientId);
		RelationDoctor relationDoctor = getDAO(RelationDoctorDAO.class).get(relationPatientId);
        //医生标签数量
		List<String> ns = this.findLabelNamesByDoctorId(relationDoctor.getDoctorId());
        //某个患者标签数量
		List<String> ls = this.findLabelNamesByRPId(relationPatientId);
		int allCountD = ls.size();
		int allCountA = ns.size();
		int diffCount = 0;
		for (String labelName : labelNames) {
			if (!ls.contains(labelName)) {
				diffCount++;
				allCountD++;
			}
			if (!ns.contains(labelName)) {
				allCountA++;
			}
		}
		if (allCountD > PATIENT_LABELS_COUNT_LIMIT || allCountA > DOCTOR_LABELS_COUNT_LIMIT || diffCount <= 0) {
			return false;
		}
		for (String labelName : labelNames) {
			if (!ls.contains(labelName)) {
				relationLabel.setLabelName(labelName);
				save(relationLabel);
			}
		}
		return true;
	}

	/**
	 * 标签服务（专供IOS）
	 */
	@RpcService
	public void saveRelationLabelByRelationPatientId(Integer relationPatientId,
			List<String> labelNames) {
		log.info("添加某个关注的标签服务(addRelationLabel):relationPatientId="
				+ JSONUtils.toString(relationPatientId) + ";labelNames="
				+ JSONUtils.toString(labelNames));
		if (relationPatientId == null) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"relationPatientId is required");
		}
		if (labelNames == null || labelNames.size() <= 0) {// 未勾选任何标签数据
			deleteRelationLabelByRelationPatientId(relationPatientId);
		} else {
			RelationLabel relationLabel = new RelationLabel();
			relationLabel.setRelationPatientId(relationPatientId);
			RelationDoctor relationDoctor = getDAO(RelationDoctorDAO.class).get(relationPatientId);
			List<String> doctorLabels = this
					.findLabelNamesByDoctorId(relationDoctor.getDoctorId());// 该医生现有的标签列表（去重）
			List<String> relationPatientLabels = this
					.findLabelNamesByRPId(relationPatientId);// 该医生对该病人的标签列表（去重）
			int doc = 0;
			if (doctorLabels != null) {
				doc = doctorLabels.size();
			}
			if (relationPatientLabels == null) {
				relationPatientLabels = new ArrayList<String>();
			}
			for (String labName : labelNames) {// 循环获取传入的标签名
				if (!doctorLabels.contains(labName)) {
					doc++;
				}
			}
			if (doc > DOCTOR_LABELS_COUNT_LIMIT) {
				throw new DAOException(609, "该医生标签达到上限");
			}

			for (String labelName : relationPatientLabels) {
				if (!labelNames.contains(labelName)) {
					this.deleteRelationLabelById(relationPatientId, labelName);
				}
			}
			RelationLabel newLabel = new RelationLabel();
			newLabel.setRelationPatientId(relationPatientId);
			for (String labelName : labelNames) {
				if (!relationPatientLabels.contains(labelName)) {
					newLabel.setLabelName(labelName);
					save(newLabel);
				}
			}

		}
	}

	@RpcService
	@DAOMethod(sql = "delete FROM RelationLabel WHERE relationPatientId=:relationPatientId")
	public abstract void deleteRelationLabelByRelationPatientId(
			@DAOParam("relationPatientId") Integer relationPatientId);

	/**
	 * 根据医生内码获取所有标签名
	 * 
	 * @author luf
	 * @param doctorId
	 *            医生内码
	 * @return List<String>
	 */
	@RpcService
	@DAOMethod(sql = "SELECT l.labelName FROM RelationLabel l,RelationDoctor p WHERE p.doctorId=:doctorId AND l.relationPatientId=p.relationDoctorId GROUP BY labelName")
	public abstract List<String> findLabelNamesByDoctorId(
			@DAOParam("doctorId") Integer doctorId);

	/**
	 * 根据关注ID查询标签名称列表（供添加某个关注的标签服务调用）
	 * 
	 * @author luf
	 * @param relationPatientId
	 *            关注ID
	 * @return List<String>
	 */
	@RpcService
	@DAOMethod(sql = "select labelName from RelationLabel where relationPatientId=:relationPatientId")
	public abstract List<String> findLabelNamesByRPId(
			@DAOParam("relationPatientId") Integer relationPatientId);

	/**
	 * 根据关注ID查询标签列表（供添加某个关注的标签服务调用）
	 * 
	 * @author LF
	 * @param relationPatientId
	 *            关注ID
	 * @return List<RelationLabel>
	 */
	@RpcService
	@DAOMethod
	public abstract List<RelationLabel> findByRelationPatientId(
			Integer relationPatientId);

	/**
	 * 删除标签(自己写的，不用)
	 * 
	 * @author LF
	 * @param relationPatient
	 * @param labelName
	 * @return
	 */
	@RpcService
	public Boolean deleteRelationLabel(RelationDoctor relationPatient,
			String labelName) {
		if (StringUtils.isEmpty(relationPatient.getMpiId())) {
			new DAOException(DAOException.VALUE_NEEDED, "mpiId is required!");
		}
		if (relationPatient.getDoctorId() == null) {
			new DAOException(DAOException.VALUE_NEEDED, "doctorId is required!");
		}
		RelationPatientDAO relationPatientDAO = getDAO(RelationPatientDAO.class);
		RelationDoctor relationPatient2 = relationPatientDAO
				.getByMpiidAndDoctorId(relationPatient.getMpiId(),
						relationPatient.getDoctorId());
		if (relationPatient2 == null) {
			return false;
		}
		RelationLabel relationLabel = getByRelationPatientIdAndLabelName(
				relationPatient2.getRelationDoctorId(), labelName);
		if (relationLabel == null) {
			return false;
		}
		remove(relationLabel.getRpLabelId());
		return true;
	}

    /**
     * 删除标签（供前端调用）
     * @param relationPatientId
     * @param labelName
     * @return
     */
	@RpcService
	public Boolean deleteRelationLabelById(Integer relationPatientId,
			String labelName) {
		log.info("删除标签(deleteRelationLabelById):relationPatientId="
				+ JSONUtils.toString(relationPatientId) + ";labelName="
				+ JSONUtils.toString(labelName));
		RelationLabel relationLabel = getByRelationPatientIdAndLabelName(
				relationPatientId, labelName);
		if (relationLabel == null) {
			return false;
		}
		remove(relationLabel.getRpLabelId());
		return true;
	}

	/**
	 * 删除多个标签
	 * 
	 * @author ZX
	 * @date 2015-9-28上午11:20:29
	 * @param relationDoctorId
	 *            关注病人ID号
	 * @param labelNames
	 *            被删除的标签列表
	 */
	@RpcService
	public void deleteRelationLabelsById(Integer relationDoctorId,
			List<String> labelNames) {
		for (String label : labelNames) {
			this.deleteRelationLabelById(relationDoctorId, label);
		}
	}

	/**
	 * 根据关注病人ID和标签名查询标签（供删除标签调用）
	 * 
	 * @author LF
	 * @param relationPatientId
	 * @param labelName
	 * @return
	 */
	@RpcService
	@DAOMethod
	public abstract RelationLabel getByRelationPatientIdAndLabelName(
			Integer relationPatientId, String labelName);

	/**
	 * 根据关注病人编号删除多个标签
	 * 
	 * @author LF
	 * @param relationPatientId
	 */
	@RpcService
	@DAOMethod
	public abstract void deleteByRelationPatientId(Integer relationPatientId);

	/**
	 * 获取所有标签（供 RelationDoctorDAO findPatientByNamOrIdCOrMob 使用）
	 * 
	 * @author luf
	 * @param doctorId
	 *            医生内码
	 * @return List<Integer>
	 */
	@RpcService
	public List<Integer> findAllRelationPatientId(final int doctorId) {
		HibernateStatelessResultAction<List<Integer>> action = new AbstractHibernateStatelessResultAction<List<Integer>>() {
			@SuppressWarnings("unchecked")
			public void execute(StatelessSession ss) throws Exception {
				String hql = new String("select DISTINCT l.relationPatientId from RelationLabel l,RelationDoctor r "
						+ "where r.doctorId=:doctorId and l.relationPatientId = r.relationDoctorId");
				Query q = ss.createQuery(hql);
				q.setParameter("doctorId", doctorId);
				List<Integer> ids = q.list();
				setResult(ids);
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		List<Integer> ids = action.getResult();
		if (ids == null || ids.size() <= 0) {
			return new ArrayList<Integer>();
		}
		return ids;
	}

	/**
	 * 给app端提供患者推荐标签(现在是固定的)
	 * zhongzx
	 * @return
     */
	@RpcService
	public List<String> getRecommendedLabels(){
		List<String> list = new ArrayList<String>();
		list.add("门诊患者");
		list.add("住院患者");
		list.add("手术患者");
		list.add("重点关注");
		list.add("糖尿病");
		list.add("高血压");
		list.add("心血管");
		list.add("孕产妇");
		list.add("心梗塞");
		return list;
	}

    /**
     * 处理全选时批量保存标签的情况
     * @param doctorId
     * @param labelType   1:未添加标签全部患者
     * @param labels
     * @param unCheckList
     * @return
     */
    @RpcService
    public Map<String,Object> batchSaveLabelForAll(Integer doctorId, int labelType, List<String> labels, List<String> unCheckList){
        if(null == doctorId){
            log.error("saveLabelForAll doctorId为null");
            throw new DAOException(DAOException.VALUE_NEEDED, "saveLabelForAll need doctorId!");
        }

        Map<String,Object> backMap = new HashMap<>();
        List<String> mpiIds = new ArrayList<>(0);
        if(1 == labelType){
            PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
            mpiIds = patientDAO.findNotAddLabelPatientMpiId(doctorId,null,"mpiId");
            if(null != unCheckList && !unCheckList.isEmpty()){
                mpiIds.removeAll(unCheckList);
            }
        }

        if(!mpiIds.isEmpty()){
            backMap = this.batchSaveLabel(doctorId, mpiIds, labels);
        }else{
            String code = SystemConstant.SUCCESS;
            String msg = "";

            backMap.put("code",code);
            backMap.put("msg",msg);
        }

        return backMap;
    }

    /**
     *批量保存
     * @param doctorId
     * @param mpiIds
     * @param labels
     * @return
     */
    @RpcService
    public Map<String,Object> batchSaveLabel(Integer doctorId, List<String> mpiIds, List<String> labels){
        if(null == doctorId){
//            log.error("batchSaveLabel doctorId为null");
            throw new DAOException(DAOException.VALUE_NEEDED, "batchSaveLabel need doctorId!");
        }

        if(null == mpiIds || mpiIds.isEmpty()){
//            log.error("batchSaveLabel mpiIds为空");
            throw new DAOException(DAOException.VALUE_NEEDED, "batchSaveLabel need mpiIds!");
        }

        if(null == labels){
            labels = new ArrayList<>(0);
        }

        RelationPatientDAO relationPatientDAO = DAOFactory.getDAO(RelationPatientDAO.class);

        Map<String,Object> backMap = new HashMap<>();
        String code = SystemConstant.SUCCESS;
        String msg = "";
        List<RelationLabel> saveList = new ArrayList<>(0);
        List<String> errorPatient = new ArrayList<>(0);

        //医生标签数量
        if(!labels.isEmpty()) {
            List<String> doctorLabels = this.findLabelNamesByDoctorId(doctorId);
            int doctorLabelsCount = doctorLabels.size();
            for (String labelName : labels) {
                if (!doctorLabels.contains(labelName)) {
                    doctorLabelsCount++;
                    if (doctorLabelsCount > DOCTOR_LABELS_COUNT_LIMIT) {
                        code = SystemConstant.FAIL;
                        log.error("batchSaveLabel [doctorId:" + doctorId + "] 医生超过标签数量" + DOCTOR_LABELS_COUNT_LIMIT + "限制");
                        break;
                    }
                }
            }
        }

        if(SystemConstant.SUCCESS.equals(code)) {
            for (String mpiId : mpiIds) {
                RelationDoctor relationDoctor = relationPatientDAO.getByMpiidAndDoctorId(mpiId, doctorId);
                if (null != relationDoctor) {
                    Integer relationPatientId = relationDoctor.getRelationDoctorId();
                    //未勾选任何标签数据
                    if (labels.isEmpty()) {
                        deleteRelationLabelByRelationPatientId(relationPatientId);
                        continue;
                    }
                    RelationLabel relationLabel;
                    //某个患者标签数量
                    List<String> patientLabels = this.findLabelNamesByRPId(relationPatientId);
                    int patientLabelsCount = patientLabels.size();
                    for (String labelName : labels) {
                        if (!patientLabels.contains(labelName)) {
                            patientLabelsCount++;

                            if (patientLabelsCount > PATIENT_LABELS_COUNT_LIMIT) {
                                code = SystemConstant.FAIL;
                                errorPatient.add(mpiId);
                                log.error("batchSaveLabel [mpiId:" + mpiId + ",relationPatientId:" + relationPatientId + "] 患者超过标签数量" + PATIENT_LABELS_COUNT_LIMIT + "限制");
                                break;
                            } else {
                                if(SystemConstant.SUCCESS.equals(code)) {
                                    relationLabel = new RelationLabel();
                                    relationLabel.setRelationPatientId(relationPatientId);
                                    relationLabel.setLabelName(labelName);
                                    saveList.add(relationLabel);
                                }
                            }
                        }
                    }
                } else {
                    log.error("batchSaveLabel [mpiId:" + mpiId + ",doctorId:" + doctorId + "] 找不到关注信息");
                }
            }
        }

        if(SystemConstant.SUCCESS.equals(code)){
            for(RelationLabel rl : saveList){
                save(rl);
            }
        }else{
            if(errorPatient.isEmpty()){
                msg = "您至多可以添加"+DOCTOR_LABELS_COUNT_LIMIT+"个标签哦~";
            }else {
                PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
                List<String> patientName = patientDAO.findNameByMpiIdIn(errorPatient);
                msg = "添加失败！" + patientName.toString() + "患者标签数超过" + PATIENT_LABELS_COUNT_LIMIT + "个！";
            }
        }

        backMap.put("code",code);
        backMap.put("msg",msg);
        return backMap;
    }

	/**
	 * 签约成功后将居民类型插入患者标签表
	 * @param relationPatientId 医生关注内码(RelationDoctor主键relationDoctorId)
	 * @param patientLabel 居民类型 key
	 * @return
	 * @throws ControllerException
	 */
	public Boolean savePatientLabel(Integer relationPatientId, Integer patientLabel) {

			if (relationPatientId != null && relationPatientId > 0){
				if (patientLabel != null && patientLabel > 0){

					RelationLabelDAO relationLabelDAO = DAOFactory.getDAO(RelationLabelDAO.class);

					//获取居民类型名字
					String labelName = "";
					try {
						labelName = DictionaryController.instance()
								.get("eh.mpi.dictionary.PatientLabel")
								.getText(patientLabel);
					}catch (Exception e){
						log.error("获取居民类型文本出错,patientLabel="+patientLabel);
						throw new DAOException(ErrorCode.SERVICE_ERROR, "获取居民类型文本出错" + e.getMessage());
					}

					//封装对象
					RelationLabel relationLabel = new RelationLabel();
					relationLabel.setRelationPatientId(relationPatientId);
					relationLabel.setLabelName(labelName);
					//防止重复插入
					RelationLabel rl = relationLabelDAO.getByRelationPatientIdAndLabelName(relationPatientId, labelName);
					if(null != rl){
						return true;
					}
					//判断是否插入成功
					RelationLabel result = relationLabelDAO.save(relationLabel);
					if (result != null && result.getRpLabelId() != null && result.getRpLabelId() > 0){
						return true;
					}
				} else {
					throw new DAOException(ErrorCode.SERVICE_ERROR, "签约成功后将居民类型插入患者标签表patientLabel不为空或者大于0！");
				}
			} else {
				throw new DAOException(ErrorCode.SERVICE_ERROR, "签约成功后将居民类型插入患者标签表,relationPatientId不为空或者大于0！");
			}

		return false;
	}
}
