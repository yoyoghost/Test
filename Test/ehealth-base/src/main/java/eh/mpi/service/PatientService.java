package eh.mpi.service;

import com.alibaba.fastjson.JSONObject;
import com.ngari.his.patient.service.IPatientHisService;
import ctd.access.AccessToken;
import ctd.access.AccessTokenController;
import ctd.account.AccountCenter;
import ctd.account.Client;
import ctd.account.UserRoleToken;
import ctd.account.thirdparty.ThirdParty;
import ctd.account.user.UserRoleTokenEntity;
import ctd.controller.exception.ControllerException;
import ctd.mvc.controller.support.LogonManager;
import ctd.mvc.controller.util.ThirdPartyProvider;
import ctd.mvc.weixin.WXUser;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.impl.access.AccessTokenDAO;
import ctd.persistence.support.impl.user.UserDAO;
import ctd.persistence.support.impl.user.UserRoleTokenDAO;
import ctd.schema.exception.ValidateException;
import ctd.spring.AppDomainContext;
import ctd.util.AppContextHolder;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import ctd.util.context.Context;
import eh.base.constant.ErrorCode;
import eh.base.constant.PageConstant;
import eh.base.constant.PagingInfo;
import eh.base.constant.SystemConstant;
import eh.base.dao.*;
import eh.base.user.UserSevice;
import eh.bus.dao.OperationRecordsDAO;
import eh.bus.service.common.CurrentUserInfo;
import eh.cdr.constant.OtherdocConstant;
import eh.cdr.dao.OtherDocDAO;
import eh.cdr.service.RecipeService;
import eh.entity.base.HisServiceConfig;
import eh.entity.bus.OperationRecords;
import eh.entity.bus.msg.SimpleWxAccount;
import eh.entity.mpi.HealthCard;
import eh.entity.mpi.Patient;
import eh.entity.mpi.PatientConcernBean;
import eh.entity.mpi.RelationDoctor;
import eh.mpi.constant.PatientConstant;
import eh.mpi.dao.AutoInvalidLogDAO;
import eh.mpi.dao.HealthCardDAO;
import eh.mpi.dao.PatientDAO;
import eh.mpi.dao.RelationDoctorDAO;
import eh.mpi.service.follow.FollowQueryService;
import eh.mpi.service.healthcard.QueryHealthCardService;
import eh.remote.IPatientService;
import eh.util.ChinaIDNumberUtil;
import eh.util.DBParamLoaderUtil;
import eh.util.RpcServiceInfoUtil;
import eh.utils.DateConversion;
import eh.utils.LocalStringUtil;
import eh.utils.ValidateUtil;
import eh.utils.params.ParamUtils;
import eh.utils.params.ParameterConstant;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.StatelessSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

import java.util.*;

/**
 * Created by luf on 2016/5/30.
 */

public class PatientService {

    private static final Logger logger = LoggerFactory.getLogger(PatientService.class);

    private static final String CONCERED = "concered";

    private static final String HISTROY = "histroy";

    //全部患者-近期扫码
    private static final String SCANCODE = "scancode";

    //全部患者-其他
    private static final String OTHER = "other";

    /**
     * 外包医保卡（自费则取最后一条）
     *
     * @param idCard   身份证
     * @param doctorId 医生内码
     * @return Patient
     */
    @RpcService
    public Patient getByIdAddHealthCardsWithSign(String idCard, int doctorId) {
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        Patient p = patientDAO.getByIdCardAddHealthCards(idCard);
        if (p == null || StringUtils.isEmpty(p.getMpiId())) {
            return p;
        }
        RelationPatientDAO reDao = DAOFactory.getDAO(RelationPatientDAO.class);
        RelationLabelDAO labelDAO = DAOFactory.getDAO(RelationLabelDAO.class);
        RelationDoctor rd = reDao.getByMpiidAndDoctorId(p.getMpiId(), doctorId);
        if (rd != null) {
            p.setRelationPatientId(rd.getRelationDoctorId());
            p.setRelationFlag(true);
            if (rd.getRelationType() == 0) {
                p.setSignFlag(true);
            } else {
                p.setSignFlag(false);
            }
            p.setLabelNames(labelDAO.findLabelNamesByRPId(rd
                    .getRelationDoctorId()));
        }
        return p;
    }

    /**
     * 修改病人姓名-手机号-医保卡号
     *
     * @param patient  患者信息
     * @param doctorId 医生内码
     * @return Patient
     */
    @RpcService
    public Patient updateBussNameWithSign(Patient patient, int doctorId) {
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        Patient p = patientDAO.updateBussAndAppointPatientName(patient);
        if (p == null || StringUtils.isEmpty(p.getMpiId())) {
            return p;
        }
        RelationPatientDAO reDao = DAOFactory.getDAO(RelationPatientDAO.class);
        RelationLabelDAO labelDAO = DAOFactory.getDAO(RelationLabelDAO.class);
        RelationDoctor rd = reDao.getByMpiidAndDoctorId(p.getMpiId(), doctorId);
        if (rd != null) {
            p.setRelationPatientId(rd.getRelationDoctorId());
            p.setRelationFlag(true);
            if (rd.getRelationType() == 0) {
                p.setSignFlag(true);
            } else {
                p.setSignFlag(false);
            }
            p.setLabelNames(labelDAO.findLabelNamesByRPId(rd
                    .getRelationDoctorId()));
        }
        return p;
    }

    /**
     * 医生未关注的患者
     *
     * @param doctorId
     * @return
     */
    @RpcService
    public Map<String, List<Patient>> findNotConcernedPatient(Integer doctorId, int limit) {
        if (null == doctorId) {
//            logger.error("findNotConcernedPatient doctorId为null");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "need doctorId!");
        }
        Map<String, List<Patient>> map = new HashMap<>();

        //未关注医生的患者列表
        List<Patient> cdPatientList = this.findConceredDoctorPatient(doctorId, 0, limit);
        map.put(this.CONCERED, cdPatientList);
        //历史患者列表
        List<Patient> hisPatientList = this.findHistoryPatient(doctorId, 0, limit);
        map.put(this.HISTROY, hisPatientList);

        return map;
    }

    /**
     * 添加患者-已关注医生的患者(医生未关注患者)
     *
     * @param doctorId
     * @param index
     * @param limit
     * @return
     */
    @RpcService
    public List<Patient> findConceredDoctorPatient(Integer doctorId, int index, int limit) {
        if (null == doctorId) {
//            logger.error("findConceredDoctorPatient doctorId为null");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "need doctorId!");
        }
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        PagingInfo pagingInfo = new PagingInfo();
        pagingInfo.setCurrentIndex(index);
        pagingInfo.setLimit(PageConstant.getPageLimit(limit));
        List<Patient> patientList = patientDAO.findConceredDoctorPatient(doctorId, pagingInfo);
        if (null == patientList) {
            patientList = new ArrayList<>(0);
        }
        for (Patient p : patientList) {
            p.setRelationFlag(false);
            p.setSignFlag(false);
        }
        return patientList;
    }

    /**
     * zhongzx
     * app3.7需求 添加患者信息包括 病例和最近业务来往 findConceredDoctorPatient接口的改进版
     *
     * @param doctorId
     * @param index
     * @param limit
     * @return
     */
    @RpcService
    public List<Map<String, Object>> findConcernPatientsForDoctor(Integer doctorId, int index, int limit) {
        if (null == doctorId) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "doctorId is needed!");
        }
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        PagingInfo pagingInfo = new PagingInfo();
        pagingInfo.setCurrentIndex(index);
        pagingInfo.setLimit(PageConstant.getPageLimit(limit));
        //查询该医生的团队
        DoctorGroupDAO groupDAO = DAOFactory.getDAO(DoctorGroupDAO.class);
        List<Integer> doctorIds = groupDAO.findDoctorIdsByLeaderId(doctorId);
        List<Integer> ids = new ArrayList<>();
        ids.add(doctorId);
        if (null != doctorIds && 0 < doctorIds.size()) {
            ids.addAll(doctorIds);
        }
        //查询医生包括该医生作为队长或者管理员的医生团队的未关注患者
        List<PatientConcernBean> patientList = patientDAO.findConcernPatientForDoctor(ids, pagingInfo);
        if (null == patientList) {
            return null;
        }
        OperationRecordsDAO recordsDAO = DAOFactory.getDAO(OperationRecordsDAO.class);
        OtherDocDAO otherDocDAO = DAOFactory.getDAO(OtherDocDAO.class);
        List<Map<String, Object>> resList = new ArrayList<>();
        for (PatientConcernBean p : patientList) {
            //每个患者的相关信息放在这个map里
            Map<String, Object> map = new HashMap<>();

            //新注册机制 出生日期可能为空
            Date birthDay = p.getBirthday();
            Integer docId = p.getDoctorId();
            if (null == birthDay) {
                Date date = new Date();
                p.setBirthday(date);
            }
            //医生是否关注患者
            p.setRelationFlag(false);
            p.setSignFlag(false);

            String mpiId = p.getMpiId();
            //查询是否有病例图片 如果有图片业务最多两条 没有图片最多三条
            List<Integer> otherDocIds = otherDocDAO.findDocIdsByMpiIdAndType(mpiId, OtherdocConstant.CLINIC_TYPE_UPLOAD_PATIENT);
            Integer num = 2;
            if (null == otherDocIds || 0 == otherDocIds.size()) {
                num = 3;
            }
            //对于每个患者 查询最近业务来往
            List<OperationRecords> ops = new ArrayList<>();
            ops = recordsDAO.findRecentOpRecordsFinal(ops, mpiId, docId, 0, num);
            List<Map<String, Object>> busList = null;
            if (null != ops) {
                busList = new ArrayList<>();
                for (OperationRecords record : ops) {
                    Map<String, Object> recordMap = recordsDAO.getBusDetail(record, doctorId);
                    busList.add(recordMap);
                }
            }

            map.put("patientInfo", p);
            map.put("otherDocIds", otherDocIds);
            map.put("busList", busList);

            resList.add(map);
        }
        return resList;
    }


    /**
     * 添加患者-历史患者(医生未关注患者)
     *
     * @param doctorId
     * @param index
     * @param limit
     * @return
     */
    @RpcService
    public List<Patient> findHistoryPatient(Integer doctorId, int index, int limit) {
        if (null == doctorId) {
//            logger.error("findHistoryPatient doctorId为null");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "need doctorId!");
        }
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        PagingInfo pagingInfo = new PagingInfo();
        pagingInfo.setCurrentIndex(index);
        pagingInfo.setLimit(PageConstant.getPageLimit(limit));
        List<Patient> patientList = patientDAO.findHistoryPatient(doctorId, pagingInfo);
        if (null == patientList) {
            patientList = new ArrayList<>(0);
        }
        for (Patient p : patientList) {
            p.setRelationFlag(false);
            p.setSignFlag(false);
        }
        return patientList;
    }

    /**
     * zhongzx
     * findHistoryPatient 改进版 返回增加1条业务记录
     * 添加患者-历史患者(医生未关注患者)
     *
     * @param doctorId
     * @param index
     * @param limit
     * @return
     */
    @RpcService
    public List<Map<String, Object>> findHistoryPatientsForDoctor(Integer doctorId, int index, int limit) {
        if (null == doctorId) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "need doctorId!");
        }
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        PagingInfo pagingInfo = new PagingInfo();
        pagingInfo.setCurrentIndex(index);
        pagingInfo.setLimit(PageConstant.getPageLimit(limit));
        List<Patient> patientList = patientDAO.findHistoryPatient(doctorId, pagingInfo);
        if (null == patientList) {
            patientList = new ArrayList<>(0);
        }
        OperationRecordsDAO recordsDAO = DAOFactory.getDAO(OperationRecordsDAO.class);
        List<Map<String, Object>> resList = new ArrayList<>();
        for (Patient p : patientList) {
            //每个患者的相关信息放在这个map里
            Map<String, Object> map = new HashMap<>();

            //新注册机制 出生日期可能为空
            Date birthDay = p.getBirthday();
            if (null == birthDay) {
                Date date = new Date();
                p.setBirthday(date);
            }
            String mpiId = p.getMpiId();
            //对于每个患者 查询最近业务来往 1条
            List<OperationRecords> ops = new ArrayList<>();
            ops = recordsDAO.findRecentOpRecordsFinal(ops, mpiId, doctorId, 0, 1);
            List<Map<String, Object>> busList = null;
            if (null != ops) {
                busList = new ArrayList<>();
                for (OperationRecords record : ops) {
                    Map<String, Object> recordMap = recordsDAO.getBusDetail(record, doctorId);
                    busList.add(recordMap);
                }
            }

            p.setRelationFlag(false);
            p.setSignFlag(false);

            map.put("patientInfo", p);
            map.put("busList", busList);

            resList.add(map);
        }
        return resList;
    }

    /**
     * 患者管理-未添加标签的患者总数
     *
     * @param doctorId
     * @return
     */
    @RpcService
    public Long findNotAddLabelPatientCount(Integer doctorId) {
        if (null == doctorId) {
//            logger.error("findNotAddLabelPatientCount doctorId为null");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "need doctorId!");
        }

        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        return patientDAO.findNotAddLabelPatientCount(doctorId);
    }

    /**
     * 患者管理-未添加标签的患者
     *
     * @param doctorId
     * @param index
     * @param limit
     * @return
     */
    @RpcService
    public List<Patient> findNotAddLabelPatient(Integer doctorId, int index, int limit) {
        if (null == doctorId) {
//            logger.error("findNotAddLabelPatient doctorId为null");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "need doctorId!");
        }

        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        PagingInfo pagingInfo = new PagingInfo();
        pagingInfo.setCurrentIndex(index);
        pagingInfo.setLimit(PageConstant.getPageLimit(limit));
        List<Patient> patientList = patientDAO.findNotAddLabelPatient(doctorId, pagingInfo, null);
        if (null == patientList) {
            patientList = new ArrayList<>(0);
        }

        setPatientTags(patientList, true);

        return patientList;
    }

    /**
     * 患者管理-未添加标签的患者(带随访信息)
     *
     * @param doctorId
     * @param index
     * @param limit
     * @return
     */
    @RpcService
    public List<Patient> findNotAddLabelPatientForFollow(Integer doctorId, int index, int limit) {
        List<Patient> patientList = this.findNotAddLabelPatient(doctorId, index, limit);
        if (null != patientList && !patientList.isEmpty()) {
            FollowQueryService followQueryService = new FollowQueryService();
            followQueryService.setPatientFollowInfo(doctorId, patientList);
        }
        return patientList;
    }

    /**
     * 患者管理-未添加标签的患者(搜索)
     *
     * @param doctorId
     * @param searchKey
     * @param index
     * @param limit
     * @return
     */
    @RpcService
    public List<Patient> findNotAddLabelPatientForSearch(Integer doctorId, String searchKey, int index, int limit) {
        if (null == doctorId) {
//            logger.error("findNotAddLabelPatientForSearch doctorId为null");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "need doctorId!");
        }

        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        PagingInfo pagingInfo = new PagingInfo();
        pagingInfo.setCurrentIndex(index);
        pagingInfo.setLimit(PageConstant.getPageLimit(limit));
        List<Patient> patientList = patientDAO.findNotAddLabelPatient(doctorId, pagingInfo, searchKey);
        if (null == patientList) {
            patientList = new ArrayList<>(0);
        }

        setPatientTags(patientList, true);

        return patientList;
    }

    /**
     * 获取医生关注的全部患者
     *
     * @param doctorId
     * @param mark     1:近期扫码 2:其他患者
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    public Map<String, List<Patient>> findAllConcernedPatient(Integer doctorId, int mark, int start, int limit) {
        if (null == doctorId) {
//            logger.error("findAllConcernedPatient doctorId为null");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "need doctorId!");
        }
        Map<String, List<Patient>> map = new HashMap<>();

        if (1 == mark) {
            //近期扫码的患者列表
            List<Patient> scPatients = this.findScanCodePatient(doctorId, start, limit);
            if (null == scPatients) {
                scPatients = new ArrayList<>(0);
            }
            map.put(this.SCANCODE, scPatients);
            if (null != scPatients && scPatients.size() >= limit) {
                map.put(this.OTHER, new ArrayList<Patient>(0));
            } else {
                List<Patient> otherPatients = this.findAllExceptScanCodePatient(doctorId, 0, limit - scPatients.size());
                map.put(this.OTHER, otherPatients);
            }
        } else if (2 == mark) {
            List<Patient> otherPatients = this.findAllExceptScanCodePatient(doctorId, start, limit);
            if (null == otherPatients) {
                otherPatients = new ArrayList<>(0);
            }
            map.put(this.SCANCODE, new ArrayList<Patient>(0));
            map.put(this.OTHER, otherPatients);
        }

        return map;
    }

    /**
     * 获取医生关注的全部患者(带随访信息)
     *
     * @param doctorId
     * @param mark
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    public Map<String, List<Patient>> findAllConcernedPatientForFollow(Integer doctorId, int mark, int start, int limit) {
        Map<String, List<Patient>> map = this.findAllConcernedPatient(doctorId, mark, start, limit);
        if (null != map) {
            FollowQueryService followQueryService = new FollowQueryService();
            followQueryService.setPatientFollowInfo(doctorId, map.get(this.SCANCODE));
            followQueryService.setPatientFollowInfo(doctorId, map.get(this.OTHER));
        }

        return map;
    }

    /**
     * 当天7天内（包括当天）扫码关注医生的患者列表(同时医生也要对该患者进行关注)
     *
     * @param doctorId
     * @param index
     * @param limit
     * @return
     */
    @RpcService
    public List<Patient> findScanCodePatient(Integer doctorId, int index, int limit) {
        if (null == doctorId) {
//            logger.error("findScanCodePatient doctorId为null");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "need doctorId!");
        }

        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        PagingInfo pagingInfo = new PagingInfo();
        pagingInfo.setCurrentIndex(index);
        pagingInfo.setLimit(PageConstant.getPageLimit(limit));
        List<Patient> patientList = patientDAO.findScanCodePatient(doctorId, pagingInfo);
        if (null == patientList) {
            patientList = new ArrayList<>(0);
        }

        setPatientTags(patientList, true, true);

        return patientList;

    }

    /**
     * 全部患者列表下除最近一周扫描二维码关注我的患者之外的患者(同时医生也要对该患者进行关注)
     *
     * @param doctorId
     * @param index
     * @param limit
     * @return
     */
    @RpcService
    public List<Patient> findAllExceptScanCodePatient(Integer doctorId, int index, int limit) {
        if (null == doctorId) {
//            logger.error("findAllExceptScanCodePatient doctorId为null");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "need doctorId!");
        }

        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        PagingInfo pagingInfo = new PagingInfo();
        pagingInfo.setCurrentIndex(index);
        pagingInfo.setLimit(PageConstant.getPageLimit(limit));
        List<Patient> patientList = patientDAO.findAllExceptScanCodePatient(doctorId, pagingInfo);
        if (null == patientList) {
            patientList = new ArrayList<>(0);
        }

        setPatientTags(patientList, true, true);

        return patientList;

    }

    /**
     * 在自定义标签内搜索患者
     *
     * @param labelName   标签名
     * @param doctorId    医生ID
     * @param patientName 患者名字关键字
     * @param index       分页起始下标
     * @param limit       分页每页数据量
     * @return
     */
    @RpcService
    public List<Patient> findLabelPatient(String labelName, Integer doctorId, String patientName, int index, int limit) {
        Assert.hasLength(labelName, "findLabelPatient labelName is null");
        Assert.notNull(doctorId, "findLabelPatient doctorId is null");

        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        PagingInfo pagingInfo = new PagingInfo();
        pagingInfo.setCurrentIndex(index);
        pagingInfo.setLimit(PageConstant.getPageLimit(limit));
        List<Patient> patientList = patientDAO.findLabelPatient(labelName, doctorId, patientName, pagingInfo);
        if (null == patientList) {
            patientList = new ArrayList<>(0);
        }

        setPatientTags(patientList, true, true);

        return patientList;

    }

    private void setPatientTags(List<Patient> patientList, boolean relationFlag) {
        this.setPatientTags(patientList, relationFlag, false);
    }

    private void setPatientTags(List<Patient> patientList, boolean relationFlag, boolean searchLabels) {
        RelationLabelDAO labelDAO = DAOFactory.getDAO(RelationLabelDAO.class);
        RelationDoctorDAO relationDoctorDAO = DAOFactory.getDAO(RelationDoctorDAO.class);
        for (Patient p : patientList) {
            p.setRelationFlag(relationFlag);
            p.setSignFlag(false);
            if (null != p.getRelationType() && 0 == p.getRelationType()) {
                p.setSignFlag(true);
                if (null != p.getSignEndDate()) {
                    p.setRemainDates(relationDoctorDAO.remainingRelationTime(p.getSignEndDate()));
                }
            }

            if (searchLabels) {
                if (null != p.getRelationPatientId() && 0 != p.getRelationPatientId()) {
                    p.setLabelNames(labelDAO.findLabelNamesByRPId(p.getRelationPatientId()));
                }
            }
        }
    }


    /**
     * 手机号验证码通过后，进行注册
     * 1>注册时，发现该手机号已有患者用户>>直接进入
     * 2>注册时，发现该手机号未有患者用户>>创建患者用户-mpi的身份证是null
     * 事务在WXUserService.createWXUserAndLogin2中处理
     *
     *
     * @return
     */
    @RpcService
    public Patient createWXPatientUser2(WXUser wxUser) {

        PatientDAO patDao = DAOFactory.getDAO(PatientDAO.class);

        // 未实现:更新或插入Patient表
        String sex = wxUser.getGender()==null||StringUtils.isEmpty(wxUser.getGender().get("key")) ? "1" : wxUser.getGender().get("key");

        Patient p = new Patient();
        p.setPatientName(wxUser.getName());
        p.setPatientSex(sex);
        p.setBirthday(wxUser.getBirthday());
        p.setMobile(wxUser.getMobile());
        p.setHomeArea(wxUser.getHomeArea());

        if (StringUtils.isEmpty(p.getFullHomeArea())) {
            p.setFullHomeArea(patDao.getFullHomeArea(p.getHomeArea()));
        }

        if (p.getBirthday() == null) {
            p.setBirthday(new Date());
        }

        Patient returnPatient = null;
        if(ValidateUtil.notBlankString(wxUser.getIdCard())){
            returnPatient = createOrUpdatePatientWithIdcard(wxUser, p);
        }else {
            returnPatient = createOrUpdatePatientWithoutIdcard(wxUser, p);
        }
        return returnPatient;
    }

    private Patient createOrUpdatePatientWithIdcard(WXUser wxUser, Patient p) {
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        Patient returnPatient = null;
        String idCard18;
        String idcard = wxUser.getIdCard();
        String mobile = p.getMobile();
        try {
            idCard18 = ChinaIDNumberUtil.convert15To18(idcard.toUpperCase());
            p.setBirthday(ChinaIDNumberUtil.getBirthFromIDNumber(idCard18));
            p.setPatientSex(ChinaIDNumberUtil.getSexFromIDNumber(idCard18));
            p.setIdcard(idCard18);
            p.setRawIdcard(p.getIdcard());
            p.setLoginId(mobile);
            Boolean guardianFlag=p.getGuardianFlag()==null?false:p.getGuardianFlag();
            p.setGuardianFlag(guardianFlag);

            p.setCreateDate(new Date());
            p.setLastModify(new Date());
            p.setStatus(PatientConstant.PATIENT_STATUS_NORMAL);
            p.setHealthProfileFlag(p.getHealthProfileFlag()==null?false:p.getHealthProfileFlag());
            p.setPatientType("1");// 1：自费
        } catch (ValidateException e) {
            logger.error("createOrUpdatePatientWithIdcard error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw new DAOException(ErrorCode.PATIENT_IDCARD_ERROR, "身份证不正确");
        }

        String customerTel = ParamUtils.getParam(ParameterConstant.KEY_CUSTOMER_TEL, SystemConstant.CUSTOMER_TEL);
        Patient idCardPatient = patientDAO.getByIdCard(idcard)==null?patientDAO.getByIdCard(idCard18):patientDAO.getByIdCard(idcard);
        Patient mobilePatient = patientDAO.getByLoginId(mobile);
        if(idCardPatient==null && mobilePatient==null){
            returnPatient = patientDAO.save(p);
        } else if(idCardPatient==null && mobilePatient!=null){
            if(ValidateUtil.blankString(mobilePatient.getIdcard())){
                BeanUtils.map(p, mobilePatient);
                mobilePatient.setIdcard(idCard18);
                mobilePatient.setLastModify(new Date());
                returnPatient = patientDAO.update(mobilePatient);
            } else {
                logger.info("该手机号已注册，mobile[{}]", mobile);
                throw new DAOException(ErrorCode.PATIENT_MOBILE_ERROR, LocalStringUtil.format("该手机号已注册，请仔细核对信息或联系客服{}", customerTel));
            }
        } else if(idCardPatient!=null && mobilePatient==null){
            if(ValidateUtil.blankString(idCardPatient.getLoginId())){
                try {
                    p.setIdcard(null);
                    patientDAO.save(p);
                    p.setIdcard(idCard18);
                    p.setRawIdcard(p.getIdcard());
                    returnPatient=invalidOldKeepNew(idCardPatient, p);
                } catch (Exception e) {
                    logger.error("createOrUpdatePatientWithIdcard invalidOldKeepNew error, idCardPatient[{}], p[{}], errorMessage[{}], stackTrace[{}]", JSONObject.toJSONString(idCardPatient), JSONObject.toJSONString(p), e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
                    throw new DAOException(609, LocalStringUtil.format("注册失败，请联系客服{}", customerTel));
                }
            }else {
                logger.info("该身份证号已注册，idcard[{}]", idcard);
                throw new DAOException(ErrorCode.PATIENT_IDCARD_ERROR, LocalStringUtil.format("该身份证已注册，请仔细核对信息或联系客服{}", customerTel));
            }
        } else if(idCardPatient!=null && mobilePatient!=null){
            if(idCardPatient.getMpiId().equals(mobilePatient.getMpiId())){
                idCardPatient.setHomeArea(p.getHomeArea());
                idCardPatient.setFullHomeArea(p.getFullHomeArea());
                idCardPatient.setLastModify(new Date());
                returnPatient = patientDAO.update(idCardPatient);
            } else {
                logger.info("该用户注册信息已存在，mobile[{}], idcard[{}]", mobile, idcard);
                throw new DAOException(ErrorCode.PATIENT_MOBILE_ERROR, LocalStringUtil.format("该用户信息已存在，请仔细核对信息或联系客服{}", customerTel));
            }
        }
        return returnPatient;
    }

    private Patient createOrUpdatePatientWithoutIdcard(WXUser wxUser, Patient p) {
        PatientDAO patDao = DAOFactory.getDAO(PatientDAO.class);
        Patient returnPatient = null;
        String uid = p.getMobile();
        Patient pat = patDao.getByLoginId(uid);
        //未注册过
        if (pat == null) {
            p.setLoginId(uid);
            p.setCreateDate(new Date());
            p.setLastModify(new Date());
            p.setPatientType("1");// 1：自费
            Boolean guardianFlag = p.getGuardianFlag() == null ? false : p.getGuardianFlag();
            p.setGuardianFlag(guardianFlag);
            p.setStatus(PatientConstant.PATIENT_STATUS_NORMAL);
            p.setHealthProfileFlag(p.getHealthProfileFlag()==null?false:p.getHealthProfileFlag());
            returnPatient = patDao.save(p);
        } else {
            pat.setHomeArea(p.getHomeArea());
            pat.setFullHomeArea(p.getFullHomeArea());
            pat.setLastModify(new Date());
            returnPatient = patDao.update(pat);
        }
        return returnPatient;
    }

    /**
     * 2016-11-16 完善信息v1.0-完善患者信息
     * 1>身份证不存在>>更新mpi数据，绑定患者用户
     * 2>身份证存在且未绑定患者用户>>该身份证已被使用，若有问题，请联系客服400-116-5175。
     * 3>身份证存在且绑定另一个用户名>>提示：该身份证已被尾号4567的用户注册，若有问题，请联系客服400-116-5175。
     * <p>
     * 2016-12-2 20:46:19 zhangx app3.7 医生端能帮患者完善信息，调该接口，需要将业务表中的姓名数据修改掉
     * <p>
     * 2016-12-7 完善信息v2.0-完善患者信息
     * 1>身份证不存在>>更新mpi数据，绑定患者用户
     * 2>身份证存在且未绑定患者用户>>
     * 2.1>身份证相同，姓名不相同>>提示：该用户姓名有误，若有问题，请联系客服400-116-5175。
     * 2.2>身份证相同，姓名相同>>旧用户zdzf+身份证，新用户填身份证，插入一条更新日志，用于后期更新业务数据使用，
     * 所有业务数据不做处理，如果医生发起业务使用导入身份证的患者，发起业务不成功，属于正常现象
     * 3>身份证存在且绑定另一用户名>>提示：该身份证已被尾号4567的用户注册，若有问题，请联系客服400-116-5175。
     */
    @RpcService
    public Patient perfectPatientUserInfo(Patient p) throws Exception {
        logger.info("患者完善信息perfectPatientUserInfo:" + JSONUtils.toString(p));
        Patient returnPatient = null;

        isValidPerfectPatientUserInfoData(p);
        String newName = p.getPatientName();

        PatientDAO patDao = DAOFactory.getDAO(PatientDAO.class);

        String mpiId = p.getMpiId();
        Patient dbp = patDao.get(mpiId);
        if (dbp == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该患者不存在");
        }

        if (!StringUtils.isEmpty(dbp.getIdcard())) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该患者已完善信息，不需要重新完善");
        }

        String idCard = p.getIdcard();
        String idCard18;
        try {
            idCard18 = ChinaIDNumberUtil.convert15To18(idCard.toUpperCase());
        } catch (ValidateException e) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "身份证不正确");
        }
        Patient idCardPatient = patDao.getByIdCard(idCard);
        if (idCardPatient == null) {
            idCardPatient = patDao.getByIdCard(idCard18);
        }

        //更新mpi数据，绑定患者用户
        if (idCardPatient == null) {
            p.setRawIdcard(p.getIdcard());
            p.setIdcard(idCard18);
            p.setBirthday(ChinaIDNumberUtil.getBirthFromIDNumber(idCard18));
            p.setPatientSex(ChinaIDNumberUtil.getSexFromIDNumber(idCard18));
            p.setLastModify(new Date());
            BeanUtils.map(p, dbp);
            // 2016-11-25 15:45:24 zhangx：健康2.6 完善患者信息需要填写医保类型，医保卡号等信息
            returnPatient = patDao.getOrUpdate(dbp);
        } else {
            String uid = idCardPatient.getLoginId();
            String oldName = idCardPatient.getPatientName();

            //身份证存在且未绑定患者用户>>提示数据已存在
            if (StringUtils.isEmpty(uid)) {
                //2016-12-8 10:03:56 zhangx 由于后台导入的患者数据达数十万，数量较大，由完善信息v1.0 改为 完善信息v2.0
                //throw new DAOException(ErrorCode.SERVICE_ERROR, "该身份证已被使用，若有问题，请联系客服"+ SystemConstant.CUSTOMER_TEL);

                //身份证相同，姓名不相同>>提示：该用户姓名有误，若有问题，请联系客服400-116-5175。
                if (!newName.equals(oldName)) {
                    String error = "该用户姓名有误，若有问题，请联系客服" + ParamUtils.getParam(ParameterConstant.KEY_CUSTOMER_TEL, SystemConstant.CUSTOMER_TEL);
                    logger.info("mpi[" + mpiId + "]完善信息：" + error);
                    throw new DAOException(ErrorCode.SERVICE_ERROR, error);
                } else {
                    //身份证相同，姓名相同>>旧用户zdzf+身份证，新用户填身份证，插入一条更新日志，用于后期更新业务数据使用，
                    p.setRawIdcard(p.getIdcard());
                    p.setIdcard(idCard18);
                    p.setBirthday(ChinaIDNumberUtil.getBirthFromIDNumber(idCard18));
                    p.setPatientSex(ChinaIDNumberUtil.getSexFromIDNumber(idCard18));
                    p.setLastModify(new Date());
                    BeanUtils.map(p, dbp);
                    returnPatient=invalidOldKeepNew(idCardPatient, dbp);
                }
            } else {
                //身份证存在且绑定另一个用户名>>提示：该身份证已被尾号4567的用户注册，若有问题，请联系客服400-116-5175。
                String error = "该身份证已被尾号" + uid.substring(uid.length() - 4, uid.length())
                        + "的用户注册，若有问题，请联系客服" + ParamUtils.getParam(ParameterConstant.KEY_CUSTOMER_TEL, SystemConstant.CUSTOMER_TEL);
                logger.info("mpi[" + mpiId + "]完善信息：" + error);
                throw new DAOException(ErrorCode.SERVICE_ERROR, error);
            }
        }

        //2017-6-5 17:06:11 【bug-10522】zhangx 由于读写分离，造成数据同步延迟，不能先更新数据，再重新获取数据
        Patient patient = returnPatient;
        if (patient != null) {
            UserSevice userService = AppContextHolder.getBean("eh.userSevice", UserSevice.class);
            userService.updateUserCache(patient.getLoginId(), SystemConstant.ROLES_PATIENT, "patient", patient);

            //wx2.6 2016-12-2 20:45:14 zhangx 完善信息后，需要把业务历史记录表中的姓名字段修改掉
            OperationRecordsDAO operationRecordsDAO = DAOFactory
                    .getDAO(OperationRecordsDAO.class);
            operationRecordsDAO.updatePatientNameByMpiId(patient.getPatientName(), mpiId);
        }



        return patient;
    }

    /**
     * 2016-12-7 完善信息v2.0-完善患者信息-身份证相同，姓名相同>>
     * 旧用户zdzf+身份证(idcard+rawIdCardId)，状态标记为作废状态，新用户填身份证，插入一条更新日志，用于后期更新业务数据使用，
     * 所有业务数据不做处理，如果医生发起业务使用导入身份证的患者，发起业务不成功，属于正常现象
     */
    private Patient invalidOldKeepNew(final Patient invalidPatient, final Patient keepPatient) throws Exception {
        logger.info("完善信息作废导入数据，修改注册数据,invalidPatient=" + JSONUtils.toString(invalidPatient)
                + "keepPatient=" + JSONUtils.toString(keepPatient));

        final PatientDAO patDao = DAOFactory.getDAO(PatientDAO.class);
        final HealthCardDAO cardDao = DAOFactory.getDAO(HealthCardDAO.class);
        AbstractHibernateStatelessResultAction<Patient> action = new AbstractHibernateStatelessResultAction<Patient>() {
            @SuppressWarnings({"unchecked"})
            @Override
            public void execute(StatelessSession ss) {
                Patient returnPat=null;
                try {
                    Date now = Context.instance().get("date.now", Date.class);
                    //旧用户zdzf+身份证(idcard+rawIdCardId)，状态标记为作废状态
                    invalidPatient.setStatus(PatientConstant.PATIENT_STATUS_INVALID);
                    invalidPatient.setIdcard(PatientConstant.INVALID_IDCARD_PREFIX + invalidPatient.getIdcard());
                    invalidPatient.setRawIdcard(PatientConstant.INVALID_IDCARD_PREFIX + invalidPatient.getRawIdcard());
                    invalidPatient.setLastModify(now);
                    patDao.update(invalidPatient);

                    //新用户填身份证
                    returnPat=patDao.update(keepPatient);

                    //新用户保存或更新卡号卡信息
                    cardDao.saveOrUpdateHealthCards(keepPatient.getHealthCards(), keepPatient.getMpiId());

                    //更新处方患者数据
                    RecipeService recipeService = AppContextHolder.getBean("eh.recipeService", RecipeService.class);
                    recipeService.updatePatientInfoForRecipe(keepPatient,invalidPatient.getMpiId());
                } catch (Exception e) {
                    logger.error("完善信息v2.0-完善患者信息-身份证相同，姓名相同情况下异常>>message[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
                    String error = "完善信息失败,请联系客服" + ParamUtils.getParam(ParameterConstant.KEY_CUSTOMER_TEL, SystemConstant.CUSTOMER_TEL);
                    throw new DAOException(ErrorCode.SERVICE_ERROR, error);
                } finally {
                    setResult(returnPat);
                }
            }

        };
        HibernateSessionTemplate.instance().executeTrans(action);

        //插入更新日志
        //2017-6-5 17:06:11 【bug-10522】zhangx 由于读写分离，造成数据同步延迟，不能先更新数据，再重新获取数据
        Patient returnPat = action.getResult();
        if (returnPat!=null) {
            String invalidMpi = invalidPatient.getMpiId();
            String keepMpi = keepPatient.getMpiId();
            logger.info("完善信息v2.0-完善信息作废导入数据[" + invalidMpi + "]，修改注册数据[" + keepMpi + "]");
            AutoInvalidLogDAO logDao = DAOFactory.getDAO(AutoInvalidLogDAO.class);
            logDao.saveInvalidLog(invalidMpi, keepMpi);
        }

        return returnPat;
    }

    /**
     * 校验数据
     *
     * @param p
     */
    private void isValidPerfectPatientUserInfoData(Patient p) {
        String idCard = p.getIdcard();
        if (StringUtils.isEmpty(p.getMpiId())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "mpiid is required");
        }
        if (StringUtils.isEmpty(p.getPatientName())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "patientName is required");
        }
        if (StringUtils.isEmpty(p.getIdcard())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "idCard is required");
        }
        if (StringUtils.isEmpty(p.getPatientType())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "patientType is required");
        }
    }

    /**
     * wx2.7 儿童患者需求，获取儿童身份证后缀
     *
     * @param idCard
     */
    public String getChildIdCardSuffix(String idCard, String idCard18) {
        String charWord = "";
        PatientDAO patdao = DAOFactory.getDAO(PatientDAO.class);
        for (int i = (int) 'A'; i < 'A' + 26; i++) {
            charWord = "-" + (char) i;
            List<Patient> patientList = patdao.findPatientByIdCard(idCard + charWord, idCard18 + charWord, idCard18.toLowerCase() + charWord);
            charWord = "";
            if (patientList.size() == 0) {
                charWord = "-" + (char) i;
                break;
            }
        }

        if (StringUtils.isEmpty(charWord)) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "新增的儿童数量已达上限!");
        }

        return charWord;
    }

    /**
     * wx2.7 儿童患者需求， 【姓名，性别，出生年月，监护人身份证】判断是否为同一个婴儿患者
     *
     * @param patient
     * @return
     */
    public Patient getChildPatient(Patient patient, String idCard18) {
        String idCard = patient.getIdcard();
        PatientDAO patdao = DAOFactory.getDAO(PatientDAO.class);
        for (int i = (int) 'A'; i < 'A' + 26; i++) {
            Boolean nameFlag = false;
            Boolean sexFlg = false;
            Boolean birthFlag = false;

            String charWord = "-" + (char) i;
            List<Patient> patientList = patdao.findPatientByIdCard(idCard + charWord, idCard18 + charWord, idCard18.toLowerCase() + charWord);
            if (patientList.size() > 0) {
                Patient p = patientList.get(0);
                if (p.getPatientName().equals(patient.getPatientName())) {
                    nameFlag = true;
                }
                if (p.getPatientSex().equals(patient.getPatientSex())) {
                    sexFlg = true;
                }
                if (DateConversion.isSameDay(p.getBirthday(), patient.getBirthday())) {
                    birthFlag = true;
                }

                if (nameFlag && sexFlg && birthFlag) {
                    return p;
                }
            }
        }

        return null;
    }

    /**
     * /**
     * wx2.7 儿童患者需求， 【姓名，性别，出生年月，监护人身份证】判断是否为同一个婴儿患者
     *
     * @param patient
     * @return 2017-1-2 16:29:33 zhangx 接口getChildPatient查询时间过长，容易出错，改为这种方法，提高查询效率
     */
    public Patient getChildPatientLike(Patient patient, String idCard18) {
        String idCard = patient.getIdcard();
        PatientDAO patdao = DAOFactory.getDAO(PatientDAO.class);

        String charWord = "-%";
        List<Patient> patientList = patdao.findChildPatientByIdCard(patient.getPatientName(), patient.getPatientSex(), patient.getBirthday(),
                idCard + charWord, idCard18 + charWord, idCard18.toLowerCase() + charWord);
        if (patientList.size() > 0) {
            return patientList.get(0);
        }

        return null;
    }

    /**
     * 查询该病人在机构下的病历号条形码  咸阳用10002
     */
    @RpcService
    public String getOrganPatientBarCode(Integer organID, String mpiID) {
        return getOrganPatient(organID, mpiID);
    }

    private String getOrganPatient(Integer organID, String mpiID) {
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        Patient patient = patientDAO.getByMpiId(mpiID);
        if (patient == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "患者不存在");
        }
        HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
        HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(organID);
        //调用服务id
        String hisServiceId = cfg.getAppDomainId() + ".patientUserService";
        String pName = patient.getPatientName();
        String certId = patient.getIdcard();
        if (patient.getGuardianFlag() != null && patient.getGuardianFlag().booleanValue()) {
            if (certId.contains("-")) {
                int index = certId.indexOf("-");
                certId = certId.substring(0, index);
            }
        }

        String patientType = patient.getGuardianFlag() == null ? "1" : patient.getGuardianFlag() ? "2" : "1";
        String mobile = patient.getMobile();
        //Object objRes = RpcServiceInfoUtil.getClientService(
        //        IPatientService.class, hisServiceId, "getPatientCode", pName, certId, mobile, patientType);
        Object objRes = null;
    	if(DBParamLoaderUtil.getOrganSwich(cfg.getOrganid())){
    		IPatientHisService iPatientHisService = AppDomainContext.getBean("his.iPatientHisService", IPatientHisService.class);
    		objRes = iPatientHisService.getPatientCode(cfg.getOrganid(),pName, certId, mobile, patientType);
		}else{
			objRes = RpcServiceInfoUtil.getClientService(
					IPatientService.class, hisServiceId, "getPatientCode", pName, certId, mobile, patientType);
		}
        if (objRes == null) {
            return null;
        }
        return objRes.toString();
    }


    /**
     * 第三方web型:(咸阳)接入的机构需要完善现象
     *
     * @param p
     * @return
     */
    private Patient perfect4ThirdPartyWeb(Patient p) {
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);

        Patient returnPat = null;

        //校验身份证是否正确
        String idCard = p.getIdcard();
        String idCard18;
        try {
            idCard18 = ChinaIDNumberUtil.convert15To18(idCard);
        } catch (ValidateException e) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "身份证不正确");
        }

        //根据获取过来的手机号判断是否在库里面已含有账户，若已有账户且姓名，身份证相同，则直接更新第三方关联信息
        String loginId = p.getMobile();
        Patient patUser = patientDAO.getByLoginId(loginId);
        if (patUser != null) {
            //查询当前患者
            UserRoleToken urt = UserRoleToken.getCurrent();
            int myUrt = urt.getId();

            //判断是否是当前账户,当前账户则直接更新
            UserSevice service = AppContextHolder.getBean("userSevice", UserSevice.class);
            int patUserUrt = service.getUrtIdByUserId(loginId, SystemConstant.ROLES_PATIENT);
            logger.info("perfect4ThirdPartyWeb:patUserUrt=" + patUserUrt + ",myUrt=" + myUrt);

            if (patUserUrt > 0 && patUserUrt == myUrt) {
                return validatePatientInfo(p, idCard18, idCard);
            }

            if (!StringUtils.isEmpty(patUser.getIdcard()) && !StringUtils.isEmpty(patUser.getPatientName()) &&
                    p.getPatientName().equals(patUser.getPatientName()) && p.getIdcard().equals(patUser.getIdcard())) {

                //更新第三方关联信息
                if (bindThirdPartyWeb(loginId)) {
                    try {
                        Client client = CurrentUserInfo.getCurrentClient();
                        AccessToken accessToken = DAOFactory.getDAO(AccessTokenDAO.class).getByUserAndClient(urt.getUserId(), urt.getId(), client.getOs());
                        accessToken.setUrt(patUserUrt);
                        accessToken.setUserId(patUser.getLoginId());
                        logger.info("perfect4ThirdPartyWeb os[{}], accessToken[{}]", client.getOs(), JSONObject.toJSONString(accessToken));
                        AccessTokenController.instance().getUpdater().update(accessToken);
                    } catch (Exception e) {
                        logger.error("perfect4ThirdPartyWeb error, errorMessage[{}], userId[{}], urt[{}], loginId[{}], stackTrace[{}]", e.getMessage(), urt.getUserId(), urt.getId(), loginId, JSONObject.toJSONString(e.getStackTrace()));
                    }
                    return patUser;
                }
            } else {
                logger.info("身份证,姓名为空或者与更新信息不匹配:idcard=" + idCard18 + ",loginId=" + p.getMobile() + ",pName=" + p.getPatientName() +
                        "patUser=" + JSONUtils.toString(patUser) + ",p=" + JSONUtils.toString(p));

                throw new DAOException(ErrorCode.SERVICE_ERROR, "您的账号绑定失败，请联系纳里客服" + ParamUtils.getParam(ParameterConstant.KEY_CUSTOMER_TEL, SystemConstant.CUSTOMER_TEL));
            }

        }

        return validatePatientInfo(p, idCard18, idCard);
    }

    private Patient validatePatientInfo(Patient p, String idCard18, String idCard) {
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);

        Patient idCardPatient = patientDAO.getByIdCard(idCard);
        if (idCardPatient == null) {
            idCardPatient = patientDAO.getByIdCard(idCard18);
        }

        //手机查不到账户，且身份证查不到患者信息,直接更新数据
        if (idCardPatient == null) {
            return updatePatientInfo(p, idCard18);
        }

        if (idCardPatient != null) {
            logger.info("身份证已存在:idcard=" + idCard18 + ",loginId=" + p.getMobile());
            throw new DAOException(ErrorCode.SERVICE_ERROR, "您的账号绑定失败，请联系纳里客服" + ParamUtils.getParam(ParameterConstant.KEY_CUSTOMER_TEL, SystemConstant.CUSTOMER_TEL));
        }

        return null;
    }

    /**
     * 根据mpiId更新患者信息
     *
     * @param p
     * @param idCard18
     * @return
     */
    private Patient updatePatientInfo(Patient p, String idCard18) {
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);

        Patient returnPat = null;

        Patient dbp = patientDAO.get(p.getMpiId());
        try {
            p.setRawIdcard(p.getIdcard());
            p.setIdcard(idCard18);
            p.setBirthday(ChinaIDNumberUtil.getBirthFromIDNumber(idCard18));
            p.setPatientSex(ChinaIDNumberUtil.getSexFromIDNumber(idCard18));
            p.setLastModify(new Date());
            p.setGuardianFlag(false);//默认为成人用户
            BeanUtils.map(p, dbp);
            patientDAO.update(dbp);

            //刷新缓存
            returnPat = patientDAO.get(p.getMpiId());
            if (returnPat != null) {
                UserSevice userService = AppContextHolder.getBean("eh.userSevice", UserSevice.class);
                userService.updateUserCache(returnPat.getLoginId(), SystemConstant.ROLES_PATIENT, "patient", returnPat);
            }

        } catch (Exception e) {
            throw new DAOException("updatePatientInfo:" + e.getMessage());
        } finally {
            return returnPat;
        }
    }

    /**
     * 第三方(web)更新第三方关联信息
     *
     * @param loginId
     */
    private Boolean bindThirdPartyWeb(String loginId) {
        Boolean bindFlag = true;
        try {
            SimpleWxAccount account = CurrentUserInfo.getSimpleWxAccount();
            String appkey = account.getAppId();
            String tid = account.getOpenId();

            ThirdPartyProvider tp = LogonManager.instance().getThirdPartyProvider();
            ThirdParty thirdParty = tp.get(appkey);
            LogonManager.instance().getThirdPartyProvider().bind(appkey, thirdParty.getAppsecret(), tid,
                    AccountCenter.getUser(loginId), SystemConstant.ROLES_PATIENT);
        } catch (ControllerException e) {
            bindFlag = false;
            throw new DAOException("user " + loginId + " not found");
        } finally {
            return bindFlag;
        }
    }


    /**
     * 完善信息-咸阳，需要验证验证码
     *
     * @param p
     * @param validateCode
     */
    @RpcService
    public Boolean perfect4ThirdPartyWebValidateCode(Patient p, String validateCode) {
        logger.info("perfect4ThirdPartyValidateCode.p=" + JSONUtils.toString(p));

        Boolean perfectFlag = true;

        UserRoleToken urt = UserRoleToken.getCurrent();
        if (urt == null ) {
            logger.info("perfect4ThirdPartyValidateCode完善信息获取不到手机号");
            return false;
        }
        Patient patientCash = (Patient) urt.getProperty("patient");
        if(patientCash == null){
            logger.info("perfect4ThirdPartyValidateCode完善信息获取不到手机号");
            return false;
        }

        p.setMpiId(patientCash.getMpiId());
        p.setMobile(patientCash.getMobile());

        //验证基础信息
        isValidPerfect4ThirdPartyValidateCodeData(p);

        // 检验验证码
        ValidateCodeDAO codeDao = DAOFactory.getDAO(ValidateCodeDAO.class);
        Boolean machFlag = codeDao.machValidateCode(p.getMobile(), validateCode);

        //验证码验证通过后
        if (machFlag) {
            Patient returnPat = perfect4ThirdPartyWeb(p);
            if (returnPat == null) {
                perfectFlag = false;
            }
        } else {
            perfectFlag = false;
        }

        return perfectFlag;
    }

    /**
     * 校验数据第三方接口的完善信息-需验证码
     *
     * @param p
     */
    private void isValidPerfect4ThirdPartyValidateCodeData(Patient p) {
        String idCard = p.getIdcard();
        if (StringUtils.isEmpty(p.getMpiId())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "mpiid is required");
        }
        if (StringUtils.isEmpty(p.getPatientName())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "patientName is required");
        }
        if (StringUtils.isEmpty(p.getIdcard())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "idCard is required");
        }
        if (StringUtils.isEmpty(p.getMobile())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "mobile is required");
        }
    }
    /**
     * 获取患者信息(含头像，性别，敏感处理过的患者姓名)
     * @param mpiId
     * @return
     */
    public Patient getPatientCoverData(String mpiId){
        PatientDAO patientDAO=DAOFactory.getDAO(PatientDAO.class);
        Patient pat=patientDAO.get(mpiId);

        Patient p=new Patient();
        p.setPatientSex(pat.getPatientSex());
        p.setPhoto(pat.getPhoto());

        String patientName=StringUtils.isEmpty(pat.getPatientName())?"":pat.getPatientName().trim();
        p.setPatientName(LocalStringUtil.coverName(patientName));
        return p;
    }
    /**
     * wx2.7 儿童患者需求：当勾选监护人标记时，
     * 新增时前端录入idCard为监护人身份证，后台保存为idCard-(ABCD.....)
     * 更新时，前端录入idCard为idCard-(ABCD.....)，
     *
     * @param patient 新增或者更新的患者信息
     * @return patient
     * @author cuill
     * @date  2017/3/22
     */
    public HashMap<String, Object> saveOrUpdateFamilyMember(final Patient patient) {
        logger.info("saveOrUpdateFamilyMember:" + JSONUtils.toString(patient));

        //wx2.7 zhangx 2016-12-19 17:06:27 如果是儿童数据，身份证需要截取标记前18位身份证
        Boolean guardianFlag = patient.getGuardianFlag() == null ? false : patient.getGuardianFlag();
        String idCard = patient.getIdcard().toUpperCase();
        String interceptIdCard = patient.getIdcard().substring(0, 4);
        if (guardianFlag) {
            idCard = patient.getIdcard().toUpperCase().split("-")[0];
        }
        String idCard18 = null;
        if (!interceptIdCard.equals("ZDZF")) {
            try {
                idCard18 = ChinaIDNumberUtil.convert15To18(idCard);
                //wx2.7 儿童患者需求： 儿童的出生日期以前端输入为准,成人的需求是用身份证后台判断
                if (!guardianFlag) {
                    patient.setGuardianName(null);
                    patient.setBirthday(ChinaIDNumberUtil.getBirthFromIDNumber(idCard18));
                    patient.setPatientSex(ChinaIDNumberUtil.getSexFromIDNumber(idCard18));
                }
            } catch (ValidateException e) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "身份证不正确");
            }
        }
        Patient targetPatient = null;
        //根据患者主键mpi查询
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        if (!StringUtils.isEmpty(patient.getMpiId())) {
            targetPatient = patientDAO.get(patient.getMpiId());
        }
        //根据患者身份证查询数据库是否有此条信息
        if (targetPatient == null) {
            //wx2.7 儿童患者需求:患者端添加家庭成员的时候,【姓名,性别,出生年月,监护人身份证】判断,有则更新,无则新加
            //婴儿患者【姓名，性别，出生年月，监护人身份证】不更新
            if (guardianFlag) {
                //2017-1-2 15:44:15 zhangx 根据身份证循环查询校验,查询的是儿童的身份信息,时间过长，使用like方式查询
                targetPatient = getChildPatientLike(patient, idCard18);
                if (targetPatient != null) {
                    patient.setIdcard(targetPatient.getIdcard());
                    patient.setRawIdcard(targetPatient.getRawIdcard());
                }
            } else {
                if (!StringUtils.isEmpty(patient.getIdcard())) {
                    targetPatient = patientDAO.getByIdCard(patient.getIdcard());
                }
            }
        }
        //根据卡号卡信息查询
        List<HealthCard> cards = patient.getHealthCards();
        List<HealthCard> newCards = new ArrayList<HealthCard>();
        HealthCardDAO cardDAO = DAOFactory.getDAO(HealthCardDAO.class);
        if (cards != null && cards.size() != 0) {
            for (HealthCard card : cards) {
                if (!StringUtils.isEmpty(card.getCardId())) {
                    HealthCard orgCard = cardDAO.getByCardOrganAndCardId(
                            card.getCardOrgan(), card.getCardId().toUpperCase(), card.getCardId());
                    if (targetPatient == null && orgCard != null) {
                        targetPatient = patientDAO.get(orgCard.getMpiId());
                    }
                    if (orgCard == null) {
                        newCards.add(card);
                        continue;
                    }
                }
            }
        }
        //身份证相同，姓名不相同>>提示：该用户姓名有误，若有问题，请联系客服400-116-5175. @author: cuill
        if (targetPatient != null){
            if (!targetPatient.getPatientName().equals(patient.getPatientName())){
                throw new DAOException(ErrorCode.SERVICE_ERROR, "该用户姓名有误，若有问题，请联系客服:"
                        + SystemConstant.CUSTOMER_TEL);
            }
        }
        Patient patientAfterHealthCard = updateHealthCards(newCards, targetPatient);
        HashMap<String, Object> resultPatientMap = null;
        //当数据库查到了该就诊人信息,执行更新操作
        if (patientAfterHealthCard != null) {
            resultPatientMap = updatePatientForFamilyMember(patient, patientAfterHealthCard);
        } else {
            //当数据库没有查到了该就诊人信息,对于该就诊人进行新增操作
            resultPatientMap = addPatientForFamilyMember(patient, guardianFlag, idCard18);
            Patient resultPatient = (Patient) resultPatientMap.get("patient");
            if (newCards.size() > 0) {
                for (HealthCard card : newCards) {
                    card.setMpiId(resultPatient.getMpiId());
                    card.setInitialCardID(card.getCardId());
                    card.setCardId(card.getCardId().toUpperCase());
                    cardDAO.save(card);
                }
            }
        }
        return resultPatientMap;
    }

    /**
     *  如果儿童监护人在患者表没有,则增加一条监护人信息
     * @param patient 前端传的患者信息
     * @author cuill
     * @date 2017/3/24
     */
    public HashMap<String, Object> saveForGuardianPatient(Patient patient){
        Patient guardianPatient = new Patient();
        guardianPatient.setIdcard(patient.getIdcard());
        guardianPatient.setPatientName(patient.getGuardianName());
        guardianPatient.setMobile(patient.getMobile());
        if (StringUtils.isEmpty(patient.getHomeArea())) {
            guardianPatient.setHomeArea(patient.getHomeArea());
        }
        guardianPatient.setPatientType("1");
        Boolean guardianFlag = guardianPatient.getGuardianFlag() == null ? false : guardianPatient.getGuardianFlag();
        String idCard = guardianPatient.getIdcard().toUpperCase().split("-")[0];
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        String idCard18;
        try {
            idCard18 = ChinaIDNumberUtil.convert15To18(idCard);
            //出生日期和性别,成人的需求是用身份证后台判断
            guardianPatient.setGuardianName(null);
            guardianPatient.setBirthday(ChinaIDNumberUtil.getBirthFromIDNumber(idCard18));
            guardianPatient.setPatientSex(ChinaIDNumberUtil.getSexFromIDNumber(idCard18));
        } catch (ValidateException e) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "监护人身份证不正确");
        }
        Patient targetPatient = patientDAO.getByIdCard(idCard18);
        HashMap<String, Object> resultPatientMap = null;
        if (targetPatient == null) {
            resultPatientMap = addPatientForFamilyMember(guardianPatient, guardianFlag, idCard18);
            return resultPatientMap;
        } else {
            //如果就诊填写的儿童监护人信息,和数据库里面获取到的监护人姓名不一致的话,提醒就诊人联系客服
            if (!targetPatient.getPatientName().equals(guardianPatient.getPatientName())) {
                logger.info("进入到监护人信息异常,请联系客服:" + SystemConstant.CUSTOMER_TEL);
                throw new DAOException(ErrorCode.SERVICE_ERROR, "监护人信息异常,请联系客服:" + SystemConstant.CUSTOMER_TEL);
            }
        }
        return resultPatientMap;
    }
    /**
     *
     * @param patient  患者的基本信息
     * @param patientAfterHealthCard  根据身份证信息读取到的患者基本信息并根据医保卡定义为何种患者类型
     * @return
     * @author cuill
     * @date 2017/3/28
     */
    public HashMap<String, Object> updatePatientForFamilyMember(Patient patient, Patient patientAfterHealthCard){
        Boolean registerFlag = false;
        HashMap<String, Object> resultMap = new HashMap<String, Object>();
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        HealthCardDAO cardDAO = DAOFactory.getDAO(HealthCardDAO.class);
        /**
         * 2017-4-14 10:08:50 zhangx 当患者主键不为空时，不更改身份证，出生日期，性别，rawIdcard
         * wx2.9 测试阳阳提出BUG：先添加家庭成员A，添加成功再进入家庭成员详情页，再使用另一只手机注册，完善信息时使用家庭成员A的
         * 身份证在家庭成员详情页点击保存，提示系统错误，产生原因：此时家庭家庭成员身份证为zdzf***，
         * 新完善信息身份证为****执行更新操作,s身份证唯一主键冲突
         */
        if(!StringUtils.isEmpty(patient.getMpiId())){
            patient.setIdcard(null);
            patient.setRawIdcard(null);
            patient.setBirthday(null);
            patient.setStatus(null);
        }

        BeanUtils.map(patient, patientAfterHealthCard);
        patientAfterHealthCard.setLastModify(new Date());
        if ("".equals(patient.getAddress())) {
            patientAfterHealthCard.setAddress("");
        }
        if (patient.getPhoto() != null && patient.getPhoto() == 0) {
            patientAfterHealthCard.setPhoto(null);
        }
        patientDAO.update(patientAfterHealthCard);
        patientAfterHealthCard.setHealthCards(cardDAO.findByMpiId(patientAfterHealthCard.getMpiId()));
        resultMap.put("registerFlag", registerFlag);
        resultMap.put("patient", patientAfterHealthCard);
        return resultMap;
    }

    /**
     *
     * @param patient 患者基本信息
     * @param guardianFlag 是否是监护人
     * @param idCard18  被转换过的18位身份证号码
     * @return
     * @author cuill
     * @date 2017/3/28
     */
    public HashMap<String, Object> addPatientForFamilyMember(Patient patient, Boolean guardianFlag, String idCard18) {
        HashMap<String, Object> resultMap = new HashMap<String, Object>();
        Boolean registerFlag = false;
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        HealthCardDAO cardDAO = DAOFactory.getDAO(HealthCardDAO.class);
        isValidPatientData(patient);
        Date now = new Date();
        //wx2.7 新增儿童患者： 儿童的身份证为监护人身份证加后缀
        if (guardianFlag) {
            String suffix = getChildIdCardSuffix(patient.getIdcard(), idCard18);
            patient.setRawIdcard(patient.getIdcard() + suffix);
            patient.setIdcard(idCard18 + suffix);
        } else {
            patient.setRawIdcard(patient.getIdcard());
            patient.setIdcard(idCard18);
        }
        patient.setCreateDate(now);
        patient.setGuardianFlag(guardianFlag);
        patient.setStatus(PatientConstant.PATIENT_STATUS_NORMAL);
        patient.setHealthProfileFlag(patient.getHealthProfileFlag()==null?false:patient.getHealthProfileFlag());
        patient.setLastModify(now);
        //在增加就诊人或者增加儿童监护人的时候,判断该手机号码是否被注册过,如果没有被注册过,则注册.
        //注册的时候不发送优惠劵
        Patient patientHaveLoginId = patientDAO.getByLoginId(patient.getMobile());
        // 当就诊人的没有注册的时候,就是loginId为空的时候,给就诊人注册
        if (patientHaveLoginId == null && !guardianFlag) {
            patient.setLoginId(patient.getMobile());
            if (StringUtils.isEmpty(patient.getHomeArea())) {
                patient.setFullHomeArea(null);
            }
            patient.setStatus(PatientConstant.PATIENT_STATUS_NORMAL);
            registerFlag = true;
        }
        Patient returnPatient = patientDAO.save(patient);
        returnPatient.setHealthCards(cardDAO.findByMpiId(returnPatient.getMpiId()));
        resultMap.put("registerFlag", registerFlag);
        resultMap.put("patient", returnPatient);
        return resultMap;
    }

    /**
     *
     * @param healthCardList 医保卡集合
     * @param patient        患者信息
     * @author cuill
     * @date 2017/3/22
     */
    public Patient updateHealthCards(List<HealthCard> healthCardList, Patient patient) {

        HealthCardDAO cardDAO = DAOFactory.getDAO(HealthCardDAO.class);
        //当患者已存在的时候
        if (patient != null && healthCardList != null && healthCardList.size() > 0) {
            String mpiId = patient.getMpiId();
            for (HealthCard healthCard : healthCardList) {
                List<HealthCard> cardList = cardDAO.findByCardOrganAndMpiId(healthCard.getCardOrgan(),
                        patient.getMpiId());
                // 如果该卡是健康卡(CardType:1-医院卡证；2-医保卡证),则直接插入该健康卡
                if (healthCard.getCardType().equals("1")) {
                    healthCard.setMpiId(mpiId);
                    cardDAO.save(healthCard);
                    patient.setPatientType("1");
                } else {
                    // 该患者没有添加过医保卡,则添加医保卡
                    if (cardList.size() == 0) {
                        healthCard.setMpiId(mpiId);
                        healthCard.setInitialCardID(healthCard.getCardId());
                        healthCard.setCardId(healthCard.getCardId().toUpperCase());
                        cardDAO.save(healthCard);
                    }
                    //该患者已经添加过医保卡,更新取出来的第一条
                    if (cardList.size() > 0) {
                        HealthCard card = cardList.get(0);
                        card.setInitialCardID(healthCard.getCardId());
                        card.setCardId(healthCard.getCardId().toUpperCase());
                        cardDAO.update(card);
                    }
                    patient.setPatientType(healthCard.getCardOrgan()
                            .toString());
                }
            }
        }
        return patient;
    }

    /**
     * @param patient 要验证的患者信息属性
     * @author cuill
     * @date 2017/3/22
     */
    public void isValidPatientData(Patient patient) {
        if (StringUtils.isEmpty(patient.getPatientName())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "name is required");
        }
        if (StringUtils.isEmpty(patient.getPatientSex())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "patient sex is required");
        }
        if (StringUtils.isEmpty(patient.getMobile())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "patient mobile is required");
        }
        if (ObjectUtils.isEmpty(patient.getBirthday())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "birthday is required");
        }
        if (StringUtils.isEmpty(patient.getIdcard())) {
            List<HealthCard> cards = patient.getHealthCards();
            if (cards == null || cards.isEmpty()) {
                throw new DAOException(DAOException.VALUE_NEEDED,
                        "idcard or cards must has one exist.");
            }
            for (HealthCard card : cards) {
                if (StringUtils.isEmpty(card.getCardId())) {
                    throw new DAOException(DAOException.VALUE_NEEDED,
                            "cardId is required.");
                }
                if (card.getCardOrgan() == null || card.getCardOrgan() == 0) {
                    throw new DAOException(DAOException.VALUE_NEEDED,
                            "cardOrgan is required.");
                }
            }
        }
    }

    /**
     * 2017-7-1 09:39:09 zhangx 上海六院就诊人模块修改v1.0，添加就诊人基础信息时，
     * 若身份证不存在，则按原来的逻辑进行新增，，身份证保留在idcard，rawIdcard
     * 若身份证存在，则将A的身份证保留在idcard2，rawIdcard
     * @param p
     * @return
     */
    public Patient saveVisitingPersonInfo (Patient p,String idCard18){

        PatientDAO patientDao=DAOFactory.getDAO(PatientDAO.class);
        p.setRawIdcard(p.getIdcard());
        p.setBirthday(new Date());
        p.setPatientSex(PatientConstant.PATIENT_SEX_MALE);
        try{
            p.setBirthday(ChinaIDNumberUtil.getBirthFromIDNumber(idCard18));
            p.setPatientSex(ChinaIDNumberUtil.getSexFromIDNumber(idCard18));
        }catch (Exception e){
            logger.error("saveVisitingPersonInfo ["+p.getIdcard()+"]-["+idCard18+"]  error: "+e.getStackTrace());
        }


        //身份证查询患者信息不存在
        if(patientDao.getByIdCard(idCard18)==null){
            p.setIdcard(idCard18);
        }else {
            p.setIdcard2(idCard18);
            p.setIdcard(null);
        }

        return patientDao.save(p);
    }

    /**
     * 更新患者用户信息
     * @param patient
     * @return
     */
    public Patient updatePatientUserInfo(Patient patient){
        logger.info("更新患者用户信息updatePatientUserInfo:" + JSONUtils.toString(patient));

        UserDAO userDao = DAOFactory.getDAO(UserDAO.class);
        UserRoleTokenDAO tokenDao = DAOFactory
                .getDAO(UserRoleTokenDAO.class);

        // loginId不能为空
        if (StringUtils.isEmpty(patient.getLoginId())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "loginId不能为空");
        }

        // user表中不存在记录
        if (!userDao.exist(patient.getLoginId())) {
            throw new DAOException(602, "该用户不存在");
        }

        UserRoleTokenEntity urt = (UserRoleTokenEntity) tokenDao
                .getExist(patient.getLoginId(), "eh", "patient");

        if (urt == null) {
            throw new DAOException(602, "该用户不存在");
        }

        return updatePatientInfo(patient);
    }


    /**
     * 调用此方法前，需调用validupdatePatientInfoData判断相关参数
     * @param patient
     * @return
     */
    public Patient updatePatientInfo(Patient patient){
        PatientDAO patientDAO=DAOFactory.getDAO(PatientDAO.class);

       Patient updatePat=validupdatePatientInfoData(patient);

        //校验卡号是否已被使用
        QueryHealthCardService queryHealthCardService = AppContextHolder.getBean("eh.queryHealthCardService",
                QueryHealthCardService.class);
        List<HealthCard> cards = patient.getHealthCards();
        if(ValidateUtil.notBlankList(cards)) {
            for (HealthCard card : cards) {
                queryHealthCardService.isUsedCard(patient.getMpiId(), card.getCardOrgan(), card.getCardId());
            }
        }

        HealthCardDAO cardDAO = DAOFactory.getDAO(HealthCardDAO.class);
        //更新,新增卡信息
        cardDAO.saveOrUpdateHealthCards(updatePat.getHealthCards(),patient.getMpiId());

        // 更新患者表信息,刷新缓存,如果该患者为已注册，则刷新缓存
        updatePat = patientDAO.update(updatePat);
        new UserSevice().updateUserCache(updatePat.getLoginId(), SystemConstant.ROLES_PATIENT, "patient", updatePat);
        return updatePat;
    }

    /**
     * 校验更新患者基础数据
     * @param patient
     * @return
     */
    public Patient validupdatePatientInfoData(Patient patient){
        PatientDAO patientDAO=DAOFactory.getDAO(PatientDAO.class);
        String mpiId=patient.getMpiId();
        if (StringUtils.isEmpty(mpiId)) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "mpiId is required");
        }

        Patient dbPat=patientDAO.get(mpiId);
        if(dbPat==null){
            throw new DAOException(ErrorCode.SERVICE_ERROR,
                    "当前用户不存在，无法添加就诊人");
        }

        setUnUpdatePatient(patient);
        BeanUtils.map(patient,dbPat);
        return dbPat;
    }


    /**
     * 更新患者基本信息：身份证(idCard,rawidCard)，出生日期，性别，登录账户名等不能修改
     * 在更新前，BeanUtils.map方法前使用，
     * @param patient 前端传入的待更新的对象
     */
    public void setUnUpdatePatient(Patient patient){
        patient.setIdcard(null);
        patient.setIdcard2(null);
        patient.setRawIdcard(null);
        patient.setPatientSex(null);
        patient.setLoginId(null);
        patient.setBirthday(null);
        patient.setStatus(null);
        patient.setCreateDate(null);
    }



    /**
     * zhongzx
     * 根据手机号和姓名获取患者集合
     * @param mobile
     * @param name
     * @return
     */
    @RpcService
    public List<Patient> findByMobileAndName(String mobile, String name){
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        List<Patient> patientList = patientDAO.findByMobileAndName(mobile, name);
        if(null != patientList && patientList.size() > 0){
            for(Patient patient:patientList){
                patient.setAge(DateConversion.getAge(patient.getBirthday()));
            }
        }
        return patientList;
    }
}
