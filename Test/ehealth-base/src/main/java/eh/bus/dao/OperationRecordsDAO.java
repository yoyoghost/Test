package eh.bus.dao;

import ctd.account.UserRoleToken;
import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.annotation.RpcService;
import ctd.util.context.Context;
import ctd.util.context.ContextUtils;
import eh.base.constant.BussTypeConstant;
import eh.base.dao.*;
import eh.cdr.dao.RecipeDAO;
import eh.entity.base.Doctor;
import eh.entity.base.Employment;
import eh.entity.base.RelationLabel;
import eh.entity.bus.*;
import eh.entity.cdr.Recipe;
import eh.entity.mpi.Patient;
import eh.entity.mpi.RelationDoctor;
import eh.mpi.dao.PatientDAO;
import eh.mpi.dao.RelationDoctorDAO;
import eh.utils.DateConversion;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.*;

public abstract class OperationRecordsDAO extends
        HibernateSupportDelegateDAO<OperationRecords> {
    public OperationRecordsDAO() {
        super();
        this.setEntityName(OperationRecords.class.getName());
        this.setKeyField("id");
    }

	public static final Logger log = Logger.getLogger(OperationRecordsDAO.class);

    /**
     * 增加业务记录
     *
     * @param record
     * @author ZX
     * @date 2015-7-3 下午3:16:00
     */
    @RpcService
    public void addRecord(OperationRecords record) {
        if (StringUtils.isEmpty(record.getMpiId())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "mpiId is required");
        }
        if (record.getRequestTime() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "mpiId is required");
        }
        save(record);
    }

    /**
     * 获取常用病人MPIID(前10条)
     *
     * @param requestDoctor
     * @param start
     * @return
     * @author ZX
     * @date 2015-7-3 下午3:49:00
     */
    @RpcService
    @DAOMethod(limit = 10, sql = "select r.mpiId from OperationRecords r, Patient p where r.mpiId=p.mpiId and " +
            "r.requestDoctor =:requestDoctor and p.status=1 group by r.mpiId order by r.requestTime desc")
    public abstract List<String> findMpiIdByRequestDoctor(
            @DAOParam("requestDoctor") int requestDoctor,
            @DAOParam(pageStart = true) int start);

    /**
     * 获取常用病人信息
     *
     * @param requestDoctor
     * @return
     * @author ZX
     * @date 2015-7-3 下午4:07:24
     */
    @RpcService
    public List<Patient> findUsedPatients(int requestDoctor) {
        List<String> mpiList = findMpiIdByRequestDoctor(requestDoctor, 0);
        if (mpiList.size() == 0) {
            return new ArrayList<>();
        }

        PatientDAO patientDao = DAOFactory.getDAO(PatientDAO.class);
        RelationDoctorDAO relationDao = DAOFactory
                .getDAO(RelationDoctorDAO.class);
        RelationPatientDAO relationPatientDao = DAOFactory
                .getDAO(RelationPatientDAO.class);
        RelationLabelDAO labelDao = DAOFactory.getDAO(RelationLabelDAO.class);

        List<Patient> patientList = new ArrayList<Patient>();
        for (String mpiId : mpiList) {
            Patient patient = patientDao.getByMpiId(mpiId);

            // 获取是否签约
            Boolean signFlag = relationDao.getSignFlag(mpiId, requestDoctor);
            patient.setSignFlag(signFlag);

            // 获取是否关注
            RelationDoctor rp = relationPatientDao.getByMpiidAndDoctorId(mpiId,
                    requestDoctor);
            if (rp == null) {
                patient.setRelationFlag(false);
            } else {
                patient.setRelationFlag(true);

                Integer rpId = rp.getRelationDoctorId();
                // 关注标签
                List<RelationLabel> labels = labelDao
                        .findByRelationPatientId(rpId);
                patient.setLabels(labels);
                patient.setRelationPatientId(rpId);
            }

            patientList.add(patient);
        }

        return patientList;
    }

    /**
     * 获取常用病人信息(分页)-不分是否关注
     *
     * @param requestDoctor
     * @return
     * @author ZX
     * @date 2015-7-3 下午4:07:24
     */
    @RpcService
    public List<Patient> findUsedPatientsPage(int requestDoctor, int start) {
        List<String> mpiList = findMpiIdByRequestDoctor(requestDoctor, start);
        if (mpiList.size() == 0) {
            return new ArrayList<>();
        }
        PatientDAO patientDao = DAOFactory.getDAO(PatientDAO.class);
        RelationDoctorDAO relationDao = DAOFactory
                .getDAO(RelationDoctorDAO.class);
        RelationPatientDAO relationPatientDao = DAOFactory
                .getDAO(RelationPatientDAO.class);
        RelationLabelDAO labelDao = DAOFactory.getDAO(RelationLabelDAO.class);

        List<Patient> patientList = new ArrayList<Patient>();
        for (String mpiId : mpiList) {
            Patient patient = patientDao.getByMpiId(mpiId);

            // 获取是否签约
            Boolean signFlag = relationDao.getSignFlag(mpiId, requestDoctor);
            patient.setSignFlag(signFlag);

            // 获取是否关注
            RelationDoctor rp = relationPatientDao.getByMpiidAndDoctorId(mpiId,
                    requestDoctor);
            if (rp == null) {
                patient.setRelationFlag(false);
            } else {
                patient.setRelationFlag(true);

                Integer rpId = rp.getRelationDoctorId();
                // 关注标签
                List<RelationLabel> labels = labelDao
                        .findByRelationPatientId(rpId);
                patient.setLabels(labels);
                patient.setRelationPatientId(rpId);
            }
            patientList.add(patient);
        }

        return patientList;
    }

    /**
     * 根据姓名检索常用病人
     *
     * @param requestDoctor
     * @param patientName
     * @param start
     * @return
     * @author ZX
     * @date 2015-7-6 上午10:39:23
     */
    @RpcService
    @DAOMethod(limit = 10, sql = "select distinct(mpiId) from  OperationRecords where requestDoctor =:requestDoctor and patientName like :patientName group by mpiId order by	requestTime desc")
    public abstract List<String> findMpiIdByRequestDoctorLike(
            @DAOParam("requestDoctor") int requestDoctor,
            @DAOParam("patientName") String patientName,
            @DAOParam(pageStart = true) int start);

    /**
     * 根据姓名检索常用病人
     *
     * @param requestDoctor
     * @param patientName
     * @return
     * @author ZX
     * @date 2015-7-6 上午10:42:40
     */
    @RpcService
    public List<Patient> findPatientByRequestDoctorLike(int requestDoctor,
                                                        String patientName, int start) {
        List<String> mpiList = findMpiIdByRequestDoctorLike(requestDoctor,
                '%' + patientName + '%', start);
        if (mpiList.size() == 0) {
            return new ArrayList<>();
        }
        PatientDAO patientDao = DAOFactory.getDAO(PatientDAO.class);
        RelationDoctorDAO relationDao = DAOFactory
                .getDAO(RelationDoctorDAO.class);
        RelationPatientDAO relationPatientDao = DAOFactory
                .getDAO(RelationPatientDAO.class);
        RelationLabelDAO labelDao = DAOFactory.getDAO(RelationLabelDAO.class);

        List<Patient> patientList = new ArrayList<Patient>();
        for (String mpiId : mpiList) {
            Patient patient = patientDao.getByMpiId(mpiId);

            // 是否签约
            Boolean signFlag = relationDao.getSignFlag(mpiId, requestDoctor);
            patient.setSignFlag(signFlag);

            // 获取是否关注
            RelationDoctor rp = relationPatientDao.getByMpiidAndDoctorId(mpiId,
                    requestDoctor);
            if (rp == null) {
                patient.setRelationFlag(false);
            } else {
                patient.setRelationFlag(true);

                // 关注标签
                List<RelationLabel> labels = labelDao
                        .findByRelationPatientId(rp.getRelationDoctorId());
                patient.setLabels(labels);
            }
            patientList.add(patient);
        }

        return patientList;
    }

    /**
     * 获取未关注的常用患者
     *
     * @param requestDoctor 申请医生
     * @param start         起始位置
     * @return
     * @author zhangx
     * @date 2015-10-22下午3:27:22
     */
    @RpcService
    @DAOMethod(limit = 10, sql = "select distinct(o.mpiId) from OperationRecords o where o.requestDoctor =:requestDoctor and not exists(  select r from RelationDoctor r where r.mpiId=o.mpiId and r.doctorId=:requestDoctor and (relationType=2 or (relationType=0 and current_timestamp()>=startDate and current_timestamp()<=endDate) )  ) group by	o.mpiId order by o.requestTime desc")
    public abstract List<String> findUnRelationMpiIdByRequestDoctor(
            @DAOParam("requestDoctor") int requestDoctor,
            @DAOParam(pageStart = true) int start);

    /**
     * 获取未关注的常用患者(分页)
     *
     * @param requestDoctor 申请医生
     * @param start         起始位置
     * @return
     * @author zhangx
     * @date 2015-10-22下午3:28:40
     */
    @RpcService
    public List<Patient> findUnRelationUsedPatientsPage(int requestDoctor,
                                                        int start) {
        List<String> mpiList = findUnRelationMpiIdByRequestDoctor(
                requestDoctor, start);
        if (mpiList.size() == 0) {
            return new ArrayList<>();
        }
        PatientDAO patientDao = DAOFactory.getDAO(PatientDAO.class);
        RelationDoctorDAO relationDao = DAOFactory
                .getDAO(RelationDoctorDAO.class);

        List<Patient> patientList = new ArrayList<Patient>();
        for (String mpiId : mpiList) {
            Patient patient = patientDao.getByMpiId(mpiId);

            // 获取是否签约
            Boolean signFlag = relationDao.getSignFlag(mpiId, requestDoctor);
            patient.setSignFlag(signFlag);

            // 是否关注
            patient.setRelationFlag(false);

            patientList.add(patient);
        }

        return patientList;
    }

	/**
	 * 按姓名检索未关注的常用患者
	 * 
	 * @author zhangx
	 * @date 2015-10-22下午3:54:47
	 * @param requestDoctor
	 *            医生id
	 * @param patientName
	 *            患者姓名
	 * @param start
	 *            开始查询位置
	 * @return
	 */
	@RpcService
	@DAOMethod(limit = 10, sql = "select distinct(o.mpiId) from  OperationRecords o where o.requestDoctor =:requestDoctor and o.patientName like :patientName and not exists(  select r from RelationDoctor r where r.mpiId=o.mpiId and r.doctorId=:requestDoctor and (relationType=2 or (relationType=0 and current_timestamp()>=startDate and current_timestamp()<=endDate) )  ) group by o.mpiId order by	o.requestTime desc")
	public abstract List<String> findUnRelationMpiIdByRequestDoctorLike(
			@DAOParam("requestDoctor") int requestDoctor,
			@DAOParam("patientName") String patientName,
			@DAOParam(pageStart = true) int start);

	/**
	 * 按姓名检索未关注的常用患者
	 * 
	 * @author zhangx
	 * @date 2015-10-22下午3:56:47
	 * @param requestDoctor
	 *            医生id
	 * @param patientName
	 *            患者姓名
	 * @param start
	 *            开始查询位置
	 * @return
	 */
	@RpcService
	public List<Patient> findUnRelationPatientByRequestDoctorLike(
			int requestDoctor, String patientName, int start) {
		List<String> mpiList = findUnRelationMpiIdByRequestDoctorLike(
				requestDoctor, '%' + patientName + '%', start);
		if (mpiList.size() == 0) {
			return new ArrayList<>();
		}
		PatientDAO patientDao = DAOFactory.getDAO(PatientDAO.class);
		RelationDoctorDAO relationDao = DAOFactory
				.getDAO(RelationDoctorDAO.class);

		List<Patient> patientList = new ArrayList<Patient>();
		for (String mpiId : mpiList) {
			Patient patient = patientDao.getByMpiId(mpiId);

			// 是否签约
			Boolean signFlag = relationDao.getSignFlag(mpiId, requestDoctor);
			patient.setSignFlag(signFlag);

			// 获取是否关注
			patient.setRelationFlag(false);

			patientList.add(patient);
		}

		return patientList;
	}

	/**
	 * 获取常用医生列表
	 * 
	 * @author ZX
	 * @date 2015-7-6 上午11:58:16
	 * @param mpiId
	 * @param start
	 * @return
	 */
	@RpcService
	@DAOMethod(limit = 10, sql = "select requestDoctor  from OperationRecords where mpiId=:mpiId group by requestDoctor order by count(requestDoctor) desc")
	public abstract List<Integer> findDocIdByMpiId(
			@DAOParam("mpiId") String mpiId,
			@DAOParam(pageStart = true) int start);

	@RpcService
	@DAOMethod(limit = 10, sql = "select exeDoctor  from OperationRecords where requestDoctor=:requestDoctor group by exeDoctor order by count(exeDoctor) desc")
	public abstract List<Integer> findDocIdByRequestDoctor(
			@DAOParam("requestDoctor") Integer requestDoctor,
			@DAOParam(pageStart = true) int start);

	/**
	 * 获取常用医生列表
	 * 
	 * @author ZX
	 * @date 2015-7-6 上午11:57:33
	 * @param mpiId
	 * @param patientName
	 * @return
	 */
	@RpcService
	public List<Doctor> findDocByMpiId(String mpiId, int start) {
		List<Integer> IdList = findDocIdByMpiId(mpiId, start);
		if (IdList.size() == 0) {
			return new ArrayList<>();
		}
		DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
		List<Doctor> docs = new ArrayList<Doctor>();
		for (Integer id : IdList) {
			Doctor doc = doctorDAO.getByDoctorId(id);
			if (doc != null && doc.getStatus() != 1) {
				continue;
			}
			docs.add(doc);
		}

		return docs;
	}

	/**
	 * 获取常用医生列表(筛选出医生设置打开的医生列表)
	 *
	 * ！！！已作废！！！！
	 * 
	 * @author ZX
	 * @date 2015-8-19 下午4:45:00
	 * @param mpiId
	 * @param start
	 * @param bussType
	 *            1:转诊；2：会诊；3：咨询; 4:预约
	 * @return
	 */
	@RpcService
	public List<Doctor> findDocByMpiIdWithServiceType(String mpiId, int start,
			int bussType) {
		List<Integer> IdList = findDocIdByMpiId(mpiId, start);
		if (IdList.size() == 0) {
			return new ArrayList<>();
		}

		DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
		ConsultSetDAO setDao = DAOFactory.getDAO(ConsultSetDAO.class);
		List<Integer> ids = new ArrayList<Integer>();

		for (Integer id : IdList) {
			if (id == null) {
				continue;
			}
			ConsultSet consultSet = setDao.getById(id);
			if (consultSet == null) {
				continue;
			}
			// 转诊
			if (bussType == 1 && consultSet.getTransferStatus() != null
					&& consultSet.getTransferStatus() == 1) {
				ids.add(id);
				continue;
			}
			// 会诊
			if (bussType == 2 && consultSet.getMeetClinicStatus() != null
					&& consultSet.getMeetClinicStatus() == 1) {
				ids.add(id);
				continue;
			}
			// 咨询
			if (bussType == 3) {
				if (consultSet.getOnLineStatus() == 1
						|| consultSet.getAppointStatus() == 1) {
					ids.add(id);
					continue;
				}
			}

			if (bussType == 4) {
				ids.add(id);
				continue;
			}
		}

		List<Doctor> docs = new ArrayList<Doctor>();
		for (Integer id : ids) {
			Doctor doc = doctorDAO.getByDoctorId(id);
			if (doc != null && doc.getStatus() != 1) {
				continue;
			}
			EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
			if (employmentDAO != null) {
				Employment em = employmentDAO.getPrimaryEmpByDoctorId(doc.getDoctorId());
				if (em != null) {
					Doctor doctor = doctorDAO.getByDoctorId(id);
					doctor.setDepartment(em.getDepartment());
					docs.add(doctor);
				}
			}
		}

		return docs;
	}

	/**
	 * 保存预约记录
	 * 
	 * @author ZX
	 * @date 2015-7-6 下午12:23:53
	 * @param ar
	 *            预约记录
	 */
	public void saveOperationRecordsForAppoint(AppointRecord ar) {
		OperationRecords record = new OperationRecords();
		String mpiId = ar.getMpiid();
		if (StringUtils.isEmpty(mpiId)) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"mpiId is required");
		}

		String appointUser = ar.getAppointUser();
		if (StringUtils.isEmpty(appointUser)) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"appointUser is required");
		}

		PatientDAO patientDao = DAOFactory.getDAO(PatientDAO.class);
		Patient patient = patientDao.get(mpiId);
		if (patient == null) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"patient[] is not exist");
		}

		record.setMpiId(mpiId);
		record.setPatientName(patient.getPatientName());
		record.setBussType(4);
		record.setBussId(ar.getAppointRecordId());
		if (appointUser.length() == 32) {
			// 病人自己预约的
			record.setRequestMpiId(appointUser);
		} else {
			// 医生代约
			record.setRequestDoctor(Integer.parseInt(appointUser));
		}
		record.setExeDoctor(ar.getDoctorId());
		record.setRequestTime(ar.getAppointDate());

		save(record);
	}

	/**
	 * 保存咨询记录日志
	 * 
	 * @author ZX
	 * @date 2015-7-6 下午12:23:53
	 * @param consult
	 */
	public void saveOperationRecordsForConsult(Consult consult) {
		OperationRecords record = new OperationRecords();
		String mpiId = consult.getMpiid();
		if (StringUtils.isEmpty(mpiId)) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"mpiId is required");
		}

		PatientDAO patientDao = DAOFactory.getDAO(PatientDAO.class);
		Patient patient = patientDao.get(mpiId);
		if (patient == null) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"patient[] is not exist");
		}

		record.setMpiId(mpiId);
		record.setPatientName(patient.getPatientName());
		record.setBussType(3);
		record.setBussId(consult.getConsultId());
		record.setRequestMpiId(consult.getRequestMpi());
		record.setExeDoctor(consult.getConsultDoctor());
		record.setRequestTime(consult.getRequestTime());

		save(record);
	}

	/**
	 * 保存会诊记录日志
	 * 
	 * @author ZX
	 * @date 2015-7-3 下午5:04:12
	 * @param trans
	 */
	public void saveOperationRecordsForMeetClinic(MeetClinic mc,
			List<MeetClinicResult> list) {
		OperationRecords record = new OperationRecords();
		String mpiId = mc.getMpiid();
		if (StringUtils.isEmpty(mpiId)) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"mpiId is required");
		}

		PatientDAO patientDao = DAOFactory.getDAO(PatientDAO.class);
		Patient patient = patientDao.get(mpiId);
		if (patient == null) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"patient[] is not exist");
		}

		record.setMpiId(mpiId);
		record.setPatientName(patient.getPatientName());
		record.setBussType(2);
		record.setBussId(mc.getMeetClinicId());
		record.setRequestDoctor(mc.getRequestDoctor());
		record.setRequestTime(mc.getRequestTime());

		for (MeetClinicResult mr : list) {
			int targetDocId = mr.getTargetDoctor();
			record.setExeDoctor(targetDocId);
			save(record);
		}
	}

	/**
	 * 保存转诊记录日志
	 * 
	 * @author ZX
	 * @date 2015-7-3 下午5:04:12
	 * @param trans
	 */
	public void saveOperationRecordsForTransfer(Transfer trans) {
		OperationRecords record = new OperationRecords();
		String mpiId = trans.getMpiId();
		if (StringUtils.isEmpty(mpiId)) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"mpiId is required");
		}

		PatientDAO patientDao = DAOFactory.getDAO(PatientDAO.class);
		Patient patient = patientDao.get(mpiId);
		if (patient == null) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"patient[] is not exist");
		}

        record.setMpiId(mpiId);
        record.setPatientName(patient.getPatientName());
        record.setBussType(1);
        record.setBussId(trans.getTransferId());
        record.setRequestDoctor(trans.getRequestDoctor());
        record.setRequestMpiId(trans.getRequestMpi());
        record.setExeDoctor(trans.getTargetDoctor());
        record.setRequestTime(trans.getRequestTime());

        save(record);
    }

    /**
     * 保存预约检查记录日志
     *
     * @param cr
     * @author ZXQ
     * @date 2016-01-25
     */
    public void saveOperationRecordsForCheck(CheckRequest cr) {
        OperationRecords record = new OperationRecords();
        String mpiId = cr.getMpiid();
        if (StringUtils.isEmpty(mpiId)) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "mpiId is required");
        }

        PatientDAO patientDao = DAOFactory.getDAO(PatientDAO.class);
        Patient patient = patientDao.get(mpiId);
        if (patient == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "patient[] is not exist");
        }

        record.setMpiId(mpiId);
        record.setPatientName(patient.getPatientName());
        record.setBussType(5);
        record.setBussId(cr.getCheckRequestId());
        record.setRequestDoctor(cr.getRequestDoctorId());
        // record.setRequestMpiId();
        // record.setExeDoctor();
        record.setRequestTime(cr.getRequestDate());

        save(record);
    }

    /**
     * 保存处方记录日志
     *
     * @param recipe
     * @author luf
     * @date 2016-4-28
     */
    public void saveOperationRecordsForRecipe(Recipe recipe) {
        OperationRecords record = new OperationRecords();
        String mpiId = recipe.getMpiid();
        if (StringUtils.isEmpty(mpiId)) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "mpiId is required");
        }

        PatientDAO patientDao = DAOFactory.getDAO(PatientDAO.class);
        Patient patient = patientDao.get(mpiId);
        if (patient == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "patient[] is not exist");
        }

        record.setMpiId(mpiId);
        record.setRequestMpiId(mpiId);
        record.setPatientName(patient.getPatientName());
        record.setBussType(BussTypeConstant.RECIPE);
        record.setBussId(recipe.getRecipeId());
        record.setRequestDoctor(recipe.getDoctor());
        record.setExeDoctor(recipe.getDoctor());
        record.setRequestTime(recipe.getCreateDate());

        save(record);
    }

    /**
     * 根据MpiId更新病人姓名
     *
     * @param patientName
     * @param mpiid
     * @author ZX
     * @date 2015-7-23 上午10:10:10
     */
    @RpcService
    @DAOMethod
    public abstract void updatePatientNameByMpiId(String patientName,
                                                  String mpiId);

    /**
     * Title: 查询医生的常用医生
     *
     * @param requestDoctor --申请医生
     * @param bussType      --业务类型 --- 1:转诊；2：会诊；3：咨询; 4:预约
     * @param start         --开始位置
     * @return List<Doctor>
     * @author AngryKitty
     * @date 2015-9-14
     */
    @RpcService
    @DAOMethod(limit = 10, sql = "select  d  from OperationRecords o,Doctor d where o.exeDoctor = d.doctorId  and o.requestDoctor=:requestDoctor and o.bussType=:bussType group by o.exeDoctor order by count(o.exeDoctor) desc")
    public abstract List<Doctor> findDoctorsByRequestDoctorAndBussType(
            @DAOParam("requestDoctor") Integer requestDoctor,
            @DAOParam("bussType") Integer bussType,
            @DAOParam(pageStart = true) int start);

    /**
     * 查询医生的常用医生(返回值添加deptartment)
     *
     * @param requestDoctor 申请医生
     * @param start         起始位置
     * @param bussType      业务类型--1转诊 2会诊 3咨询 4预约
     * @return List<Doctor>查询医生的常用医生</br>
     * <p>
     * 2015-10-23 zhangx 授权机构由于前段排序显示问题，对原来的设计进行修改</br>
     * 修改前：A授权给B，B能看到A，A也能看到B;修改后：A授权给B，B能看到A，A不能看到B</br>
     * 增加按orderNum排序</br>
     * @author ZX
     * @date 2015-9-14 上午11:58:30
     */
    @RpcService
    public List<Doctor> findDocByRequestDocIdWithServiceType(
            final Integer requestDoctor, final int start, final int bussType) {
        UserRoleToken ur = (UserRoleToken) ContextUtils
                .get(Context.USER_ROLE_TOKEN);
        Employment eSelf = (Employment) ur.getProperty("employment");
        List<Doctor> docList = new ArrayList<Doctor>();
        UnitOpauthorizeDAO dao = DAOFactory.getDAO(UnitOpauthorizeDAO.class);
        List<Integer> oList = dao.findByBussId(bussType);
        if (oList == null) {
            oList = new ArrayList<Integer>();

        }
        if (eSelf == null || eSelf.getOrganId() == null) {
            return docList;
        }
        oList.add(eSelf.getOrganId());
        StringBuilder sb = new StringBuilder(" and(");
        for (Integer o : oList) {
            sb.append(" e.organId=").append(o).append(" OR");
        }
        final String strUO = sb.substring(0, sb.length() - 2) + ")";

        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            List<Doctor> result = new ArrayList<Doctor>();

            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                // TODO Auto-generated method stub
                StringBuilder hql = new StringBuilder(
                        "select distinct d from OperationRecords op, Doctor d, Organ o, Employment e, ConsultSet c where op.exeDoctor = d.doctorId and op.requestDoctor =:requestDoctor AND op.bussType =:bussType AND o.organId = e.organId AND d.doctorId = e.doctorId AND d.doctorId = c.doctorId AND d.status = 1");

                hql.append(strUO);
                switch (bussType) {
                    case 1:
                        hql.append(" and c.transferStatus=1");
                        break;
                    case 2:
                        hql.append(" and c.meetClinicStatus=1");
                        break;
                    case 3:
                        hql.append(" and (c.onLineStatus=1 or c.appointStatus=1)");
                        break;
                    case 4:
                        hql.append(" and d.teams<>1");
                }

                hql.append(" group by d order by count(d) desc");

                Query q = ss.createQuery(hql.toString());
                q.setParameter("requestDoctor", requestDoctor);
                q.setParameter("bussType", bussType);

                q.setMaxResults(10);
                q.setFirstResult(start);

                result = q.list();
                setResult(result);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        docList = action.getResult();

        if (CollectionUtils.isEmpty(docList)) {
            return new ArrayList<Doctor>();
        }

        List<Doctor> targets = new ArrayList<Doctor>();
        EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
		DoctorTabDAO doctorTabDAO=DAOFactory.getDAO(DoctorTabDAO.class);
        for (Doctor doctor : docList) {
            Employment employment = employmentDAO
                    .getPrimaryEmpByDoctorId(doctor.getDoctorId());
            if (employment != null) {
                doctor.setDepartment(employment.getDepartment());
            }
			if(bussType==BussTypeConstant.MEETCLINIC){//zhangsl 2017-05-26 15:25:51会诊中心标记新增
				doctor.setMeetCenter(doctorTabDAO.getMeetTypeByDoctorId(doctor.getDoctorId()));
			}
            targets.add(doctor);
        }
        return targets;
    }

    /**
     * 按姓名，身份证，手机号三个其中的一个或多个搜索医生的历史患者中符合条件的患者
     *
     * @param doctorId 医生ID
     * @param name     搜索姓名
     * @param idCard   搜索的身份证
     * @param mobile   搜索的手机号
     * @param start    开始查询条数
     * @param limit    查询数目
     * @return
     * @desc 供 PatientDAO.searchPatientByDoctorId 使用
     * @author zhangx
     * @date 2015-11-25 下午4:08:14
     */
    @DAOMethod(sql = "select distinct(p) from OperationRecords r,Patient p where p.mpiId=r.mpiId and r.requestDoctor=:doctorId and ( p.patientName like :name or p.idcard like :idCard or p.mobile like :mobile) group by r.mpiId order by r.requestTime desc")
    public abstract List<Patient> findHistoryPatients(
            @DAOParam("doctorId") Integer doctorId,
            @DAOParam("name") String name, @DAOParam("idCard") String idCard,
            @DAOParam("mobile") String mobile, long start, long limit);

    /**
     * 历史医生列表(健康端)
     *
     * @param mpiId 主索引
     * @return List<Doctor>
     * @author luf
     */
    @RpcService
    public List<HashMap<String, Object>> findDocByMpiIdForHealth(
            final String mpiId) {
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
				OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
				List<Integer> organs = organDAO.findOrgansByUnitForHealth();
				StringBuilder hql = new StringBuilder("select d from OperationRecords o,Doctor d where o.requestMpiId=:mpiId "
                        + "and d.doctorId=o.exeDoctor and d.status=1");
				// 2016-4-25:luf 添加个性化 and (d.organ in :organs)
				if (organs != null && organs.size() > 0) {
					hql.append(" and (");
					for(Integer i:organs) {
						hql.append("d.organ=").append(i).append(" or ");
					}
					hql= new StringBuilder(hql.substring(0,hql.length()-3)).append(")");
				}
				hql.append(" group by d.doctorId order by count(d.doctorId) desc");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("mpiId", mpiId);
                q.setFirstResult(0);
                q.setMaxResults(6);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<Doctor> doctors = action.getResult();
        List<HashMap<String, Object>> targets = new ArrayList<HashMap<String, Object>>();
        for (Doctor doctor : doctors) {
            HashMap<String, Object> result = new HashMap<String, Object>();
			int doctorId = doctor.getDoctorId();

			//医生实时号源标记判断
			DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
			doctor.setHaveAppoint(doctorDAO.getRealTimeDoctorHaveAppointStatus(doctorId, 1).getHaveAppoint());

			ConsultSetDAO dao = DAOFactory.getDAO(ConsultSetDAO.class);
			Employment employment = DAOFactory.getDAO(EmploymentDAO.class).getPrimaryEmpByDoctorId(doctorId);
			doctor.setDepartment(employment.getDepartment());
            ConsultSet consultSet = dao.get(doctorId);
            result.put("doctor", doctor);
            result.put("consultSet", consultSet);
            targets.add(result);
        }
        return targets;
    }

    /**
     * 查询医生的常用医生(原findDocByRequestDocIdWithServiceType)-原生
     * <p>
     * eh.bus.dao
     *
     * @param requestDoctor 申请医生
     * @param start         起始位置
     * @return List<Doctor>
     * @author luf 2016-3-10
     */
    @RpcService
    public List<Doctor> findDocByRequestWithoutBuss(
            final Integer requestDoctor, final int start) {
        UserRoleToken ur = (UserRoleToken) ContextUtils
                .get(Context.USER_ROLE_TOKEN);
        Employment eSelf = (Employment) ur.getProperty("employment");
        List<Doctor> docList = new ArrayList<Doctor>();
        if (eSelf == null || eSelf.getOrganId() == null) {
            return docList;
        }
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            List<Doctor> result = new ArrayList<Doctor>();

            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                // TODO Auto-generated method stub
                StringBuilder hql = new StringBuilder(
                        "select distinct d from OperationRecords op,Doctor d where "
                                + "op.exeDoctor=d.doctorId and op.requestDoctor=:requestDoctor "
                                + "AND d.status=1 group by d order by count(d) desc");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("requestDoctor", requestDoctor);
                q.setMaxResults(10);
                q.setFirstResult(start);
                result = q.list();
                setResult(result);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        docList = action.getResult();
        if (CollectionUtils.isEmpty(docList)) {
            return new ArrayList<Doctor>();
        }
        List<Doctor> targets = new ArrayList<Doctor>();
        EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
        for (Doctor doctor : docList) {
            Employment employment = employmentDAO
                    .getPrimaryEmpByDoctorId(doctor.getDoctorId());
            if (employment != null) {
                doctor.setDepartment(employment.getDepartment());
            }
            targets.add(doctor);
        }
        return targets;
    }

    /**
     * 查询医生的常用医生(原findDocByRequestWithoutBuss，findDocByRequestDocIdWithServiceType)-原生
     * <p>
     * eh.bus.dao
     *
     * @param requestDoctor 申请医生
     * @param start         起始位置
     * @param busType       业务类型 -1转诊2会诊3咨询、预约
     * @return List<Doctor>
     * @author luf 2016-5-6
     */
    @RpcService
    public List<Doctor> findDocByRequestWithIsOpen(
            final Integer requestDoctor, final int start, final int busType) {
        UserRoleToken ur = (UserRoleToken) ContextUtils
                .get(Context.USER_ROLE_TOKEN);
        Employment eSelf = (Employment) ur.getProperty("employment");
        List<Doctor> docList = new ArrayList<Doctor>();
        if (eSelf == null || eSelf.getOrganId() == null) {
            return docList;
        }
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            List<Doctor> result = new ArrayList<Doctor>();

            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                // TODO Auto-generated method stub
                StringBuilder hql = new StringBuilder(
                        "select distinct d from OperationRecords op,Doctor d where "
                                + "op.exeDoctor=d.doctorId and op.requestDoctor=:requestDoctor "
                                + "AND d.status=1");
                if(busType==4) {
                    hql.append(" and d.teams<>1");
                }
                hql.append(" group by d order by count(d) desc");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("requestDoctor", requestDoctor);
                q.setMaxResults(10);
                q.setFirstResult(start);
                result = q.list();
                setResult(result);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        docList = action.getResult();
        if (CollectionUtils.isEmpty(docList)) {
            return new ArrayList<Doctor>();
        }
        List<Doctor> targets = new ArrayList<Doctor>();
        EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
        ConsultSetDAO csDao = DAOFactory.getDAO(ConsultSetDAO.class);
        DoctorTabDAO doctorTabDAO=DAOFactory.getDAO(DoctorTabDAO.class);
        for (Doctor doctor : docList) {
            Integer docId = doctor.getDoctorId();
            Employment employment = employmentDAO
                    .getPrimaryEmpByDoctorId(docId);
            if (employment != null) {
                doctor.setDepartment(employment.getDepartment());
            }
            if(busType==BussTypeConstant.MEETCLINIC){//zhangsl 2017-05-26 15:25:51会诊中心标记新增
            	doctor.setMeetCenter(doctorTabDAO.getMeetTypeByDoctorId(docId));
			}
            ConsultSet cs = csDao.get(docId);
            Integer isOpen = 0;
            if (cs != null) {
                switch (busType) {
                    case BussTypeConstant.TRANSFER:
                        isOpen = cs.getTransferStatus();
                        break;
                    case BussTypeConstant.MEETCLINIC:
                        isOpen = cs.getMeetClinicStatus();
                        break;
                    case BussTypeConstant.CONSULT:
                    case BussTypeConstant.APPOINTMENT:
                        if ((cs.getOnLineStatus() != null && cs.getOnLineStatus() == 1) || (cs.getAppointStatus() != null && cs.getAppointStatus() == 1)) {
                            isOpen = 1;
                        }
                }
            }
            doctor.setIsOpen(isOpen);
            targets.add(doctor);
        }
        return targets;
    }

	/**
	 * 查询患者和医生最近来往业务记录（自定义条数）
	 * 业务来往指的是 医生帮助患者申请的业务（如果患者直接申请，就是医生接收的业务）
	 * zhongzx
	 * @param mpiId
	 * @param doctor
	 * @param start
	 * @param limit
     * @return
     */
	@DAOMethod(sql = "from OperationRecords where mpiId=:mpiId and ((exeDoctor=:doctor and requestDoctor is null) or (requestDoctor=:doctor and requestDoctor is not null)) group by bussId order by requestTime desc")
	public abstract List<OperationRecords> findRecentOpRecords(@DAOParam("mpiId") String mpiId, @DAOParam("doctor") Integer doctor, @DAOParam(pageStart = true) int start, @DAOParam(pageLimit = true) int limit);

	/**
	 * zhongzx
	 * 先查询往来业务 再从最近往来业务中筛选远程门诊预约 和在线云门诊业务
	 * @param
	 * @return
     */
	public List<OperationRecords> findRecentOpRecordsFinal(List<OperationRecords> opRecords, String mpiId, Integer doctorId, Integer start, Integer num){
		//首先查询10条
		List<OperationRecords> opList = findRecentOpRecords(mpiId, doctorId, start, 10);
		if(null == opList || 0 == opList.size()){
			return opRecords;
		}
		for(OperationRecords record:opList){
			Integer busType = record.getBussType();
			//预约 远程云门诊会有两条记录（去除一条 取作为申请方帮患者预约出诊方的记录） 根据需求在线云门诊也去除
			Integer busId = record.getBussId();
			if(4 == busType){
				AppointRecordDAO recordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
				AppointRecord appointRecord = recordDAO.get(busId);
				Integer telClinicFlag = appointRecord.getTelClinicFlag();
				//根据需求在线云门诊也去除
				if(null != telClinicFlag && 2 == telClinicFlag){
					continue;
				}
				if(null != telClinicFlag && 1 == telClinicFlag){
					Integer requestDoctor = record.getRequestDoctor();
					Integer exeDoctor = record.getExeDoctor();
					//取作为申请方帮患者预约出诊方的记录
					if(requestDoctor.equals(exeDoctor)){
						continue;
					}
				}
			}
			opRecords.add(record);
			if(num == opRecords.size()){
				break;
			}
		}
		if(num > opRecords.size() && 10 == opList.size()){
			return findRecentOpRecordsFinal(opRecords, mpiId, doctorId, start+10, num);
		}
		return opRecords;
	}

	/**
	 * zhongzx
	 * 根据业务类型显示业务内容
	 * 转诊、会诊、医技检查、特需预约、咨询 显示病情描述 处方显示病情诊断 预约显示就诊日期 就诊医生
	 * @param record
	 * @return
	 */
	public Map<String, Object> getBusDetail(OperationRecords record, Integer doctor){
		if(null == record){
			throw new DAOException(DAOException.VALUE_NEEDED, "record is null");
		}
		Map<String, Object> resMap = new HashMap<>();
		Integer busType = record.getBussType();
		Integer busId = record.getBussId();
		String requestMpiId = record.getRequestMpiId();
		String patientCondition = "";
		Date date = record.getRequestTime();
		String dateString = DateConversion.getDateFormatter(date, "M月d日");
		resMap.put("requestDate", dateString);
		//转诊（包括特需预约）
		if(1 == busType){
			TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
			Transfer transfer = transferDAO.get(busId);
			if(null == transfer){
				log.error("找不到对应转诊单===busId:" + busId);
				return resMap;
			}
			Integer transferType = transfer.getTransferType();
			Boolean isAdd = transfer.getIsAdd();
			patientCondition = transfer.getPatientCondition();
			if(StringUtils.isNotEmpty(requestMpiId)){
				resMap.put("busName", "特需预约");
				resMap.put("busType", 3);
			}else if(2 == transferType){
				resMap.put("busName", "住院转诊");
				resMap.put("busType", 4);
			}else if(3 == transferType){
				resMap.put("busName", "远程门诊转诊");
				resMap.put("busType", 5);
			}else if(null != isAdd && isAdd){
				resMap.put("busName", "加号转诊");
				resMap.put("busType", 2);
			}else{
				resMap.put("busName", "有号转诊");
				resMap.put("busType", 1);
			}
		}
		//会诊
		if(2 == busType){
			MeetClinicDAO meetClinicDAO = DAOFactory.getDAO(MeetClinicDAO.class);
			MeetClinic meetClinic = meetClinicDAO.get(busId);
			if(null == meetClinic){
				log.error("找不到对应会诊单===busId:" + busId);
				return resMap;
			}
			patientCondition = meetClinic.getPatientCondition();
			resMap.put("busName", "会诊");
			resMap.put("busType", 13);
		}
		//咨询
		if(3 == busType){
			ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
			Consult consult = consultDAO.get(busId);
			if(null == consult){
				log.error("找不到对应咨询单===busId:" + busId);
				return resMap;
			}
			Integer mode = consult.getRequestMode();
			patientCondition = consult.getLeaveMess();
			if(1 == mode){
				resMap.put("busName", "电话咨询");
				resMap.put("busType", 6);
			}
			if(2 == mode){
				resMap.put("busName", "图文咨询");
				resMap.put("busType", 7);
			}
		}
		//检查
		if(5 == busType){
			CheckRequestDAO checkRequestDAO = DAOFactory.getDAO(CheckRequestDAO.class);
			CheckRequest checkRequest = checkRequestDAO.get(busId);
			if(null == checkRequest){
				log.error("找不到对应医技检查申请单===busId:" + busId);
				return resMap;
			}
			patientCondition = checkRequest.getDiseasesHistory();
			resMap.put("busName", "医技检查");
			resMap.put("busType", 8);
		}
		//处方
		if(6 == busType){
			RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
			Recipe recipe = recipeDAO.get(busId);
			if(null == recipe){
				log.error("找不到对应处方单===busId:" + busId);
				return resMap;
			}
			patientCondition = recipe.getOrganDiseaseName();
			resMap.put("busType", 9);
			resMap.put("busName", "电子处方");
		}
		//预约分为远程门诊预约 普通预约（预约我的 和 我预约的）
		if(4 == busType){
			AppointRecordDAO recordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
			AppointRecord appointRecord = recordDAO.get(busId);
			if(null == appointRecord){
				log.error("找不到对应预约单===busId:" + busId);
				return resMap;
			}
			Integer telClinicFlag = appointRecord.getTelClinicFlag();
			Date workDate = appointRecord.getWorkDate();
			String departName = appointRecord.getAppointDepartName();
			Integer doctorId = appointRecord.getDoctorId();
			Integer organId = appointRecord.getOrganId();

			DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
			String doctorName = doctorDAO.getNameById(doctorId);
			OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
			String organName =  organDAO.getNameById(organId);
			resMap.put("docName", doctorName);
			resMap.put("organName", organName);
			resMap.put("departName", departName);
			if(null != workDate){
				String wdString = DateConversion.getDateFormatter(workDate, "M月d日");
				resMap.put("workDate", wdString);
			}
			//远程云门诊预约 类型定义为8
			if(null != telClinicFlag && 1 == telClinicFlag){
				resMap.put("busType", 10);
				resMap.put("busName", "远程门诊预约");
			}else{
				//预约我的
				if(doctor.equals(doctorId)){
					resMap.put("docName", "我");
					resMap.put("busType", 11);
					resMap.put("busName", "普通预约");
				}else{
					resMap.put("busType", 12);
					resMap.put("busName", "普通预约");
				}
			}

		}
		resMap.put("patientCondition", StringUtils.isEmpty(patientCondition)?"":patientCondition);
		return resMap;
	}

	/**
	 * 根据业务记录生成相应的采集任务
	 * */
	@RpcService
	public List<OperationRecords>  createCollectionTaskByOperationReords(int endTime){
		final Date time = DateConversion.getDateAftHour(new Date(), -endTime);
		HibernateStatelessResultAction<List<OperationRecords>> action = new AbstractHibernateStatelessResultAction<List<OperationRecords>>() {
			List<OperationRecords> result = new ArrayList<OperationRecords>();
			@Override
			public void execute(StatelessSession ss) throws Exception {
				StringBuilder hql = new StringBuilder(
						" from OperationRecords op where requestTime >=:time ");
				Query q = ss.createQuery(hql.toString());
				q.setParameter("time",time);
				result = q.list();
				setResult(result);
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		return action.getResult();
	}

	@RpcService
	@DAOMethod
	public abstract void updateCollectFlagById(Integer collectFlag, Integer id);
}


