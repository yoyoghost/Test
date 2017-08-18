package eh.mpi.dao;

import com.alibaba.fastjson.JSONObject;

import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.schema.exception.ValidateException;
import ctd.util.AppContextHolder;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import ctd.util.context.Context;
import eh.base.constant.ErrorCode;
import eh.base.constant.PageConstant;
import eh.base.constant.PagingInfo;
import eh.base.dao.*;
import eh.bus.dao.ConsultSetDAO;
import eh.bus.dao.OperationRecordsDAO;
import eh.cdr.constant.OtherdocConstant;
import eh.cdr.dao.OtherDocDAO;
import eh.coupon.service.CouponPushService;
import eh.entity.base.Doctor;
import eh.entity.base.Employment;
import eh.entity.base.Organ;
import eh.entity.base.RelationLabel;
import eh.entity.bus.ConsultSet;
import eh.entity.bus.OperationRecords;
import eh.entity.mpi.*;
import eh.mpi.constant.PatientConstant;
import eh.mpi.constant.SignRecordConstant;
import eh.mpi.service.follow.FollowQueryService;
import eh.push.SmsPushService;
import eh.task.executor.SaveHisSignPatientExecutor;
import eh.util.ChinaIDNumberUtil;
import eh.utils.LocalStringUtil;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class RelationDoctorDAO extends
        HibernateSupportDelegateDAO<RelationDoctor> {

    private static final Log logger = LogFactory.getLog(RelationDoctorDAO.class);

    private static final int RELATION_BUSSID = 11;

    public RelationDoctorDAO() {
        super();
        setEntityName(RelationDoctor.class.getName());
        setKeyField("relationDoctorId");
    }

    /**
     * 获取有效签约记录
     *
     * @param mpiId    主索引ID
     * @param doctorId 医生编码
     * @return RelationDoctor
     * @author luf
     */
    @RpcService
    @DAOMethod(sql = "From RelationDoctor where mpiId=:mpiId and doctorId=:doctorId and "
            + "relationType=0 and current_timestamp()>=startDate and current_timestamp()<=endDate")
    public abstract RelationDoctor getSignByMpiAndDoc(
            @DAOParam("mpiId") String mpiId,
            @DAOParam("doctorId") Integer doctorId);

    /**
     *
     * @param mpiId
     * @param doctorId
     * @param RelationType (0 签约，1 病人关注医生，2 医生关注病人)
     * @return
     */
    @DAOMethod(sql = "From RelationDoctor where mpiId=:mpiId and doctorId=:doctorId and " +
            "relationType=:relationType")
    public abstract RelationDoctor getSignByMpiAndDocAndType(
            @DAOParam("mpiId") String mpiId,
            @DAOParam("doctorId") Integer doctorId,
            @DAOParam("relationType") Integer RelationType);

    /**
     * 获取该患者有效签约记录
     * @param mpiId
     * @return
     */
    @DAOMethod(sql = "From RelationDoctor where mpiId=:mpiId and "
            + "relationType=0 and current_timestamp()>=startDate and current_timestamp()<=endDate")
    public abstract List<RelationDoctor> findSignByMpi(@DAOParam("mpiId") String mpiId);

    /**
     * 根据病人主键，医生主键查询
     *
     * @param mpiId    病人主键
     * @param doctorId 医生主键
     * @return List<RelationDoctor>
     */
    @RpcService
    @DAOMethod
    public abstract List<RelationDoctor> findByMpiIdAndDoctorId(String mpiId,
                                                                Integer doctorId);

    /**
     * 查询病人关注医生列表
     *
     * @param mpiId    主索引ID
     * @param doctorId 医生编码
     * @return List<RelationDoctor>
     * @author LF
     */
    @RpcService
    @DAOMethod(sql = "From RelationDoctor where mpiId=:mpiId and doctorId=:doctorId and "
            + "((relationType=0 and current_timestamp()>=startDate and current_timestamp()<=endDate) or relationType=1)")
    public abstract RelationDoctor getByMpiIdAndDoctorIdAndRelationType(
            @DAOParam("mpiId") String mpiId,
            @DAOParam("doctorId") Integer doctorId);

    /**
     * 判断病人是否关注过该医生 (根据病人主键，医生主键查询是否存在记录，是则返回true，否返回false)
     *
     * @param mpiId    主索引ID
     * @param doctorId 医生编码
     * @return True关注 False没关注
     */
    @RpcService
    public boolean getRelationDoctorFlag(String mpiId, Integer doctorId) {
        if (mpiId == null || mpiId.equals("")) {
            return false;
        }
        if (doctorId == null || doctorId == 0) {
            return false;
        }
        RelationDoctor relation = this.getByMpiIdAndDoctorIdAndRelationType(
                mpiId, doctorId);
        if (relation == null) {
            return false;
        }
        return true;
    }

    /**
     * 扫码关注医生
     *
     * @param relation
     * @return
     */
    @RpcService
    public Integer addRelationDoctorForScanCode(RelationDoctor relation) {
        relation.setObtainType(2);
        return this.addRelationDoctor(relation);
    }

    @DAOMethod(sql = "from RelationDoctor WHERE doctorId = :doctorId AND mpiId = :mpiId and ((relationType=0 and current_timestamp()>=startDate and current_timestamp()<=endDate) or relationType=2)")
    public abstract List<RelationDoctor> findDoctorAttentionToPatient(
            @DAOParam("doctorId") Integer doctorId,
            @DAOParam("mpiId") String mpiId);

    @RpcService
    public List<RelationDoctor> findDoctorAttentionToPatientRPC(Integer doctorId,String mpiId){
        return this.findDoctorAttentionToPatient(doctorId,mpiId);
    }

    @RpcService
    public RelationDoctor getById(Integer id) throws DAOException {
        return super.get(id);
    }

    /**
     * 关注医生添加服务 (先判断该医生是否关注过，未关注过，则添加关注) 添加病人关注医生服务 纯关注记录 family=false
     *
     * @param relation 关注医生类
     * @return Integer
     */
    @RpcService
    public Integer addRelationDoctor(RelationDoctor relation) {
        String mpiId = relation.getMpiId();
        Integer doctorId = relation.getDoctorId();
        Date relationDate = relation.getRelationDate();
        DoctorDAO doctorDao = DAOFactory.getDAO(DoctorDAO.class);
        Integer organ=doctorDao.getOrganByDoctorId(doctorId);
        if (relationDate == null) {
            relation.setRelationDate(new Date());
        }
        relation.setFamilyDoctorFlag(false);
        relation.setRelationType(1);// 设置关注类型为'病人关注医生'
        if (null == relation.getObtainType()) {
            relation.setObtainType(1);
        }
        // 判断是否已经关注过
        if (!this.getRelationDoctorFlag(mpiId, doctorId)) {
            relation = this.save(relation);
            AppContextHolder.getBean("eh.smsPushService", SmsPushService.class).pushMsgData2Ons(relation.getRelationDoctorId(), organ, "PatientReDoctor", "", 0);

            //wx2.7 关注发放优惠劵
            CouponPushService couponService=new CouponPushService();
            couponService.sendRelationCouponMsg(relation);

            return relation.getRelationDoctorId();
        }
        return null;
    }

    /**
     * 取消关注医生服务 (根据参数查询记录，若查询到记录，该记录显示所关注医生为签约医生，则不能删除；若不为签约医生，根据主键删除记录);
     * <p>
     * 1.只有关注记录，没有签约记录---可取消，可删除 2.只存在无效签约记录----不可取消 3.存在有效签约记录-----不可取消
     *
     * @param mpiId    主索引
     * @param doctorId 医生id
     * @return Boolean
     */
    @RpcService
    public Boolean deleteByMpiIdAndDoctorId(String mpiId, Integer doctorId) {
        boolean isRelationPatient = getRelationDoctorFlag(mpiId, doctorId);
        RelationDoctor rd = this.getSignByMpiAndDoc(mpiId, doctorId);
        logger.info("取消关注医生服务deleteByMpiIdAndDoctorId-eh.relationDoctor：" + JSONUtils.toString(rd));

        if (isRelationPatient && rd == null) {
            RelationDoctor rp = this.getByMpiIdAndDoctorIdAndRelationType(
                    mpiId, doctorId);
            this.remove(rp.getRelationDoctorId());
            return true;
        }
        return false;
    }

    /**
     * 判断是否医生签约病人服务 (判断是否医生签约病人。在关注医生中查询该病人是否为某医生在有效期限的签约病人);
     *
     * @param mpiId    主索引
     * @param doctorId 医生id
     * @return boolean
     */
    @RpcService
    public boolean getSignFlag(final String mpiId, final Integer doctorId) {
        List<RelationDoctor> list = this.findEffectiveFamilyDoctorList(mpiId,
                doctorId);

        if (list.size() > 0) {
        	SignRecordDAO signRecordDAO = DAOFactory.getDAO(SignRecordDAO.class);
        	List<SignRecord> signRecordList = signRecordDAO.findDoctorAndMpiId(doctorId, mpiId);
        	if (CollectionUtils.isNotEmpty(signRecordList)) {
        		Integer recordStatus = signRecordList.get(0).getRecordStatus();
        		if (SignRecordConstant.RECORD_STATUS_CONFIRMATION.equals(recordStatus)) {
        			return false;
        		}
        	}
            return true;
        } else {
            return false;
        }
    }

    /**
     * 查询病人关注医生列表
     *
     * @param mpiId 主索引ID
     * @return List<Integer>
     */
    @RpcService
    @DAOMethod(sql = "select distinct(doctorId) from RelationDoctor where mpiId=:mpiId and "
            + "((relationType=0 and current_timestamp()>=startDate and current_timestamp()<=endDate) or relationType=1)")
    public abstract List<Integer> findRelationDoctorId(
            @DAOParam("mpiId") String mpiId);

    /**
     * 查询关注医生A的患者总数，或者医生A的关注的患者总数
     *
     * @param doctorId     医生A的doctorid
     * @param relationType 关注类型(1:病人关注医生;2:医生关注病人)
     * @return
     * @author zhangx
     * @date 2015-12-10 上午11:14:17
     */
    @RpcService
    @DAOMethod(sql = "select count(*) from RelationDoctor where doctorId=:doctorId and "
            + "((relationType=0 and current_timestamp()>=startDate and current_timestamp()<=endDate) or relationType=:relationType)")
    public abstract long getRelationNum(@DAOParam("doctorId") int doctorId,
                                        @DAOParam("relationType") int relationType);

    /**
     * 查询病人关注医生列表
     *
     * @param mpiId 主索引ID
     * @return List<Doctor>
     */
    @RpcService
    public List<Doctor> findRelationDoctorList(String mpiId) {
        DoctorDAO doctorDao = DAOFactory.getDAO(DoctorDAO.class);
        List<Integer> ids = this.findRelationDoctorId(mpiId);

        if (ids.size() > 0) {
            return doctorDao.findEffectiveDocByDoctorIdIn(ids);
        } else {
            return new ArrayList<Doctor>();
        }
    }

    /**
     * 查询病人签约医生id列表
     *
     * @param mpiId 主索引ID
     * @return List<Integer>
     */
    @RpcService
    @DAOMethod(sql = "select distinct(doctorId) from RelationDoctor where mpiId=:mpiId and familyDoctorFlag=true and current_timestamp() >=startDate and current_timestamp() <=endDate")
    public abstract List<Integer> findFamilyDoctorId(
            @DAOParam("mpiId") String mpiId);

    /**
     * 查询患者有效签约医生列表
     * <p>
     * eh.mpi.dao
     *
     * @param mpiId
     * @return List<Doctor>
     * @author luf 2016-2-26
     */
    @RpcService
    @DAOMethod(sql = "select distinct(d) from RelationDoctor r,Doctor d where mpiId=:mpiId and "
            + "familyDoctorFlag=true and current_timestamp()>=startDate and current_timestamp()<=endDate "
            + "and r.doctorId=d.doctorId and d.status=1")
    public abstract List<Doctor> findDoctorsByMpi(
            @DAOParam("mpiId") String mpiId);

    // /**
    // * 查询病人签约医生列表
    // */
    // @RpcService
    // public List<Doctor> findFamilyDoctorList(String mpiId){
    // DoctorDAO doctorDao = DAOFactory.getDAO(DoctorDAO.class);
    // List<Integer> ids = this.findFamilyDoctorId(mpiId);
    //
    // if(ids.size()>0){
    // return doctorDao.findByDoctorIdIn(ids);
    // }else{
    // return new ArrayList<Doctor>();
    // }
    // }

    /**
     * 查询病人签约医生列表
     *
     * @param mpiId 主索引ID
     * @return List<RelationDoctorAndDoctor>
     */
    @RpcService
    @DAOMethod(sql = "select new eh.entity.mpi.RelationDoctorAndDoctor(d,r)  from RelationDoctor r,Doctor d where r.doctorId=d.doctorId and r.mpiId=:mpiId and r.familyDoctorFlag=true and current_timestamp() >=r.startDate and current_timestamp() <=r.endDate")
    public abstract List<RelationDoctorAndDoctor> findFamilyDoctorList(
            @DAOParam("mpiId") String mpiId);

    /**
     * 获取关注记录(familyDoctorFlag=false的记录)
     *
     * @param mpiId    主索引ID
     * @param doctorId 医生编码
     * @return RelationDoctor
     */
    @RpcService
    @DAOMethod(sql = "from RelationDoctor where mpiId=:mpiId and doctorId=:doctorId and familyDoctorFlag=false and relationType=1")
    public abstract RelationDoctor getByMpiIdAndDoctorId(
            @DAOParam("mpiId") String mpiId,
            @DAOParam("doctorId") Integer doctorId);

    /**
     * 根据病人主键，医生主键查询查找签约记录，(无论是否有效)
     *
     * @param mpiId    主索引ID
     * @param doctorId 医生编码
     * @return List<RelationDoctor>
     */
    @RpcService
    @DAOMethod(sql = "from RelationDoctor where mpiId=:mpiId and doctorId=:doctorId and familyDoctorFlag=true")
    public abstract List<RelationDoctor> findSignListByMpiIdAndDoctorId(
            @DAOParam("mpiId") String mpiId,
            @DAOParam("doctorId") Integer doctorId);

    /**
     * 获取有效的签约医生列表
     *
     * @param mpiId    主索引ID
     * @param doctorId 医生编码
     * @return List<RelationDoctor>
     */
    @RpcService
    @DAOMethod(sql = "from RelationDoctor where mpiId=:mpiId and doctorId=:doctorId and familyDoctorFlag=true and current_timestamp() >=startDate and current_timestamp() <=endDate")
    public abstract List<RelationDoctor> findEffectiveFamilyDoctorList(
            @DAOParam("mpiId") String mpiId,
            @DAOParam("doctorId") Integer doctorId);

    /**
     * 根据病人主键，医生主键查询查找单个签约记录，(无论是否有效)
     *
     * @param mpiId    主索引ID
     * @param doctorId 医生编码
     * @return List<RelationDoctor>
     */
    @RpcService
    @DAOMethod(sql = "from RelationDoctor where mpiId=:mpiId and doctorId=:doctorId and familyDoctorFlag=true")
    public abstract RelationDoctor getSignListByMpiIdAndDoctorId(
            @DAOParam("mpiId") String mpiId,
            @DAOParam("doctorId") Integer doctorId);

    /**
     * 家庭医生签约服务 (纯关注记录 family=true)
     *
     * @param mpiId        主索引ID
     * @param doctorId     医生编码
     * @param relationDate 关注日期
     * @param startDate    签约起始日期
     * @param endDate      签约终止日期
     * @author luf
     */
    @RpcService
    public boolean addFamilyDoctor(final String mpiId, final Integer doctorId,
                                final Date relationDate, final Date startDate, final Date endDate) {
        @SuppressWarnings("rawtypes")
        HibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
            public void execute(StatelessSession ss) {
                RelationDoctor relation = new RelationDoctor();
                relation.setMpiId(mpiId);
                relation.setDoctorId(doctorId);
                relation.setRelationType(0);
                relation.setFamilyDoctorFlag(true);
                relation.setRemindPreSign(false);
                RelationDoctor rDate = new RelationDoctor();
                rDate.setRelationDate(relationDate);
                rDate.setStartDate(startDate);
                rDate.setEndDate(endDate);
                if (endDate.before(startDate)) {
                    throw new DAOException(ErrorCode.SERVICE_ERROR, "签约结束日期不能在签约开始日期之前");
                }
                RelationDoctor signEver = getSignListByMpiIdAndDoctorId(mpiId,
                        doctorId);
                RelationDoctor pSignDoctor = getByMpiIdAndDoctorId(mpiId,
                        doctorId);
                if (pSignDoctor != null) {
                    remove(pSignDoctor.getRelationDoctorId());
                }
                //此返回值 是确保此服务返回true时 数据已经先到数据库完成了
                RelationDoctor saveReturn;
                if (signEver == null) {
                    RelationDoctor dSignPatient = getRelPatByMpiAndDoc(mpiId,
                            doctorId);
                    if (dSignPatient != null) {
                        BeanUtils.map(relation, dSignPatient);
                        BeanUtils.map(rDate, dSignPatient);
                        logger.info("dSignPatient:"+JSONUtils.toString(dSignPatient));
                        saveReturn = update(dSignPatient);
                    } else {
                        BeanUtils.map(rDate, relation);
                        logger.info("relation:"+JSONUtils.toString(relation));
                        saveReturn = save(relation);
                    }
                } else {
                    Date e = signEver.getEndDate();
                    if (relationDate.before(e)) {
                        signEver.setEndDate(endDate);
                        logger.info("signEver:"+JSONUtils.toString(signEver));
                        saveReturn = update(signEver);
                    } else {
                        BeanUtils.map(rDate, signEver);
                        logger.info("signEver:"+JSONUtils.toString(signEver));
                        saveReturn = update(signEver);
                    }
                }
                if(null != saveReturn) {
                    setResult(true);
                }else {
                    setResult(false);
                }
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
        return action.getResult();
    }

    /**
     * 获取全部签约病人列表的服务--hyj
     *
     * @param doctorId 医生编码
     * @return List<Patient>
     */
    @RpcService
    @DAOMethod(sql = "select a from Patient a,RelationDoctor b where a.mpiId=b.mpiId and b.doctorId=:doctorId and b.familyDoctorFlag=1 and NOW()>=b.startDate and NOW()<=b.endDate order by a.patientName")
    public abstract List<Patient> findSignPatient(
            @DAOParam("doctorId") int doctorId);

    /**
     * 获取全部签约病人列表服务(分页)
     *
     * @param doctorId 医生id
     * @param start    数据库中第几条开始查询（第一页的话，值为0，则从第0条开始查询）
     * @return List<Patient>
     * @author ZX
     * @date 2015-4-29 上午11:48:33
     */
    @RpcService
    public List<Patient> findSignPatientWithPage(int doctorId, int start) {
        return findSignPatientByStartAndLimit(doctorId, start, 10);
    }

    /**
     * 获取全部签约病人列表服务(分页)
     *
     * @param doctorId 医生id
     * @param start    数据库中第几条开始查询（第一页的话，值为0，则从第0条开始查询）
     * @param limit    查询几条
     * @return List<Patient>
     * @author ZX
     * @date 2015-4-29 上午11:48:33
     */
    @RpcService
    @DAOMethod(sql = "select a from Patient a,RelationDoctor b where a.mpiId=b.mpiId and b.doctorId=:doctorId and b.familyDoctorFlag=1 and NOW()>=b.startDate and NOW()<=b.endDate order by a.patientName")
    public abstract List<Patient> findSignPatientByStartAndLimit(
            @DAOParam("doctorId") int doctorId,
            @DAOParam(pageStart = true) int start,
            @DAOParam(pageLimit = true) int limit);

    /**
     * 按姓名模糊查询签约病人(分页)
     *
     * @param doctorId    医生id
     * @param patientName 患者姓名
     * @param start       数据库中第几条开始查询（第一页的话，值为0，则从第0条开始查询）
     * @param limit       查询几条
     * @return List<Patient>
     * @author ZX
     * @date 2015-5-8 上午9:50:46
     */
    @DAOMethod(sql = "select a from Patient a,RelationDoctor b where a.mpiId=b.mpiId and b.doctorId=:doctorId and b.familyDoctorFlag=1 and NOW()>=b.startDate and NOW()<=b.endDate and a.patientName like :patientName and b.mpiId not in(select requestMpiId from SignRecord WHERE doctor=:doctorId AND recordStatus = 4 and NOW()>=date_format(startDate,'%Y-%m-%d') and  NOW()<= date_format(endDate,'%Y-%m-%d')) order by a.patientName")
    public abstract List<Patient> findSignPatientByNameLike(
            @DAOParam("doctorId") int doctorId,
            @DAOParam("patientName") String patientName,
            @DAOParam(pageStart = true) int start,
            @DAOParam(pageLimit = true) int limit);

    /**
     * 按姓名模糊查询签约病人(分页)
     *
     * @param doctorId    医生id
     * @param patientName 患者姓名
     * @param start       数据库中第几条开始查询（第一页的话，值为0，则从第0条开始查询）
     * @return List<Patient>
     * @author ZX
     * @date 2015-5-8 上午10:08:22
     */
    @RpcService
    public List<Patient> findSignPatientByNameByStartAndLimit(int doctorId,
                                                              String patientName, int start, int limit) {
        return findSignPatientByNameLike(doctorId, "%" + patientName + "%",
                start, limit);
    }

    /**
     * 按姓名模糊查询签约病人(分页)
     *
     * @param doctorId    医生id
     * @param patientName 患者姓名
     * @param start       数据库中第几条开始查询（第一页的话，值为0，则从第0条开始查询）
     * @return List<Patient>
     * @author ZX
     * @date 2015-5-8 上午10:08:22
     */
    @RpcService
    public List<Patient> findSignPatientByNameWithPage(int doctorId,
                                                       String patientName, int start) {
        return findSignPatientByNameLike(doctorId, "%" + patientName + "%",
                start, 10);
    }

    /**
     * 查询所有关注病人(没“%”)
     *
     * @param doctorId
     * @param patientName
     * @param start
     * @param limit
     * @return List<Patient>
     * @author LF
     */
    @RpcService
    @DAOMethod(sql = "select DISTINCT a from Patient a,RelationDoctor b where a.mpiId=b.mpiId and b.doctorId=:doctorId and ((b.familyDoctorFlag=1 and NOW()>=b.startDate and NOW()<=b.endDate) or relationType=2) and a.patientName like :patientName order by a.patientName")
    public abstract List<Patient> findfindAllPatientByNameLike(
            @DAOParam("doctorId") int doctorId,
            @DAOParam("patientName") String patientName, long start, long limit);

    /**
     * 查询所有关注病人（华哥）
     *
     * @param doctorId
     * @param patientName
     * @param start
     * @return List<Patient>
     * @author LF
     */
    @RpcService
    public List<Patient> findAllPatientByNameLikeLimitStatic(Integer doctorId,
                                                             String patientName, long start) {
        return findfindAllPatientByNameLike(doctorId, "%" + patientName + "%",
                start, 10);
    }

    /**
     * 按手机模糊查询签约病人(分页)
     *
     * @param doctorId 医生id
     * @param mobile   患者手机
     * @param start    数据库中第几条开始查询（第一页的话，值为0，则从第0条开始查询）
     * @param limit    查询几条
     * @return List<Patient>
     * @author ZX
     * @date 2015-5-8 上午9:50:46
     */
    @DAOMethod(sql = "select a from Patient a,RelationDoctor b where a.mpiId=b.mpiId and b.doctorId=:doctorId and b.familyDoctorFlag=1 and NOW()>=b.startDate and NOW()<=b.endDate and a.mobile like :mobile order by a.patientName")
    public abstract List<Patient> findSignPatientByMobileLike(
            @DAOParam("doctorId") int doctorId,
            @DAOParam("mobile") String mobile,
            @DAOParam(pageStart = true) int start,
            @DAOParam(pageLimit = true) int limit);

    /**
     * 按手机模糊查询签约病人(分页)
     *
     * @param doctorId 医生id
     * @param start    数据库中第几条开始查询（第一页的话，值为0，则从第0条开始查询）
     * @return List<Patient>
     * @author ZX
     * @date 2015-5-8 上午10:08:22
     */
    @RpcService
    public List<Patient> findSignPatientByMobileByStartAndLimit(int doctorId,
                                                                String mobile, int start, int limit) {
        return findSignPatientByMobileLike(doctorId, mobile + "%", start, limit);
    }

    /**
     * 按手机模糊查询签约病人(分页)
     *
     * @param doctorId 医生id
     * @param start    数据库中第几条开始查询（第一页的话，值为0，则从第0条开始查询）
     * @return List<Patient>
     * @author ZX
     * @date 2015-5-8 上午10:08:22
     */
    @RpcService
    public List<Patient> findSignPatientByMobileWithPage(int doctorId,
                                                         String mobile, int start) {
        return findSignPatientByMobileLike(doctorId, mobile + "%", start, 10);
    }

    /**
     * 按身份证模糊查询签约病人(分页)
     *
     * @param doctorId 医生id
     * @param start    数据库中第几条开始查询（第一页的话，值为0，则从第0条开始查询）
     * @param limit    查询几条
     * @return List<Patient>
     * @author ZX
     * @date 2015-5-8 上午9:50:46
     */
    @DAOMethod(sql = "select a from Patient a,RelationDoctor b where a.mpiId=b.mpiId and b.doctorId=:doctorId and b.familyDoctorFlag=1 and NOW()>=b.startDate and NOW()<=b.endDate and a.idcard like :idcard order by a.idcard")
    public abstract List<Patient> findSignPatientByIdcardLike(
            @DAOParam("doctorId") int doctorId,
            @DAOParam("idcard") String idcard,
            @DAOParam(pageStart = true) int start,
            @DAOParam(pageLimit = true) int limit);

    /**
     * 按身份证模糊查询签约病人(分页)
     *
     * @param doctorId 医生id
     * @param start    数据库中第几条开始查询（第一页的话，值为0，则从第0条开始查询）
     * @return List<Patient>
     * @author ZX
     * @date 2015-5-8 上午10:08:22
     */
    @RpcService
    public List<Patient> findSignPatientByIdcardByStartAndLimit(int doctorId,
                                                                String idcard, int start, int limit) {
        return findSignPatientByIdcardLike(doctorId, "%" + idcard + "%", start,
                limit);
    }

    /**
     * 按身份证模糊查询签约病人(分页)
     *
     * @param doctorId 医生id
     * @param start    数据库中第几条开始查询（第一页的话，值为0，则从第0条开始查询）
     * @return List<Patient>
     * @author ZX
     * @date 2015-5-8 上午10:08:22
     */
    @RpcService
    public List<Patient> findSignPatientByIdcardWithPage(int doctorId,
                                                         String idcard, int start) {
        return findSignPatientByIdcardLike(doctorId, "%" + idcard + "%", start,
                10);
    }

    /**
     * 保存签约病人信息列表
     *
     * @param signPatients
     * @throws DAOException
     * @author hyj
     */
    @RpcService
    public void saveSignPatients(final List<SignPatient> signPatients)
            throws DAOException {
        SaveHisSignPatientExecutor executor = new SaveHisSignPatientExecutor(
                signPatients);
        executor.execute();
    }

    /**
     * 保存签约病人信息
     *
     * @param signPatient
     * @author hyj
     */
    @SuppressWarnings("rawtypes")
    public void saveSignPatient(final SignPatient signPatient) {
        logger.info("保存签约病人信息saveSignPatient:" + JSONUtils.toString(signPatient));

        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            @Override
            public void execute(StatelessSession ss) {
                Patient patient = creatPatientData(signPatient);

                Patient target;

                // 保存患者信息
                if (StringUtils.isEmpty(patient.getMpiId())) {
                    PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
                    target = patientDAO.getOrUpdate(patient);
                } else {
                    target = patient;
                }

                // his机构编号转换成平台机构编号
                OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
                Organ o = organDAO.getOrganByOrganizeCode(signPatient
                        .getOrganId());

                // 根据医生工号，机构编码查询医生内部主键
                EmploymentDAO employmentDAO = DAOFactory
                        .getDAO(EmploymentDAO.class);
                List<Employment> es = employmentDAO.findByJobNumberAndOrganId(
                        signPatient.getDoctorId(), o.getOrganId());

                if (es != null && es.size() > 0) {
                    /**
                     * 2015-09-02
                     *
                     * 同以机构下，同一医生有多个执业，则默认取第一条
                     */
                    Employment e = es.get(0);
                    addFamilyDoctor(target.getMpiId(), e.getDoctorId(),
                            signPatient.getRelationDate(),
                            signPatient.getStartDate(),
                            signPatient.getEndDate());
                } else {
                    logger.info("签约病人导入失败【找不到医生】"
                            + JSONUtils.toString(signPatient));
                }
            }
        };

        try {
            HibernateSessionTemplate.instance().executeTrans(action);
        } catch (Exception e) {
            logger.error("导入签约病人异常：" + e.getMessage());
        }
    }

    /**
     * 根据医生编号和患者mpi获取有效的签约病人列表服务--hyj
     *
     * @param doctorId
     * @return
     */
    @RpcService
    @DAOMethod(sql = " from RelationDoctor where mpiId=:mpiId and doctorId=:doctorId and NOW()>=startDate and NOW()<=endDate")
    public abstract List<RelationDoctor> findSignPatientByDoctorIdAndMpi(
            @DAOParam("mpiId") String mpiId, @DAOParam("doctorId") int doctorId);

    /**
     * 组装患者信息对象
     *
     * @param signPatient
     * @return
     * @author hyj
     */
    public Patient creatPatientData(SignPatient signPatient) {
        // 验证身份证合理性
        String idCard = signPatient.getCertId();
        String newIdCard = idCard;
        try {
            newIdCard = ChinaIDNumberUtil.convert15To18(idCard);
        } catch (ValidateException e) {
            logger.error("签约病人身份证异常：" + e.getMessage());
            logger.error(LocalStringUtil.format("error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace())));
        }

        logger.info("签约病人【" + signPatient.getPatientName() + "】身份证15位转化成18位:"
                + idCard + "---->" + newIdCard);

        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);

        // 判断数据库中是否存在
        Patient target = patientDAO.getByIdCard(newIdCard);
        if (target != null) {
            return target;
        }

        target = new Patient();

        // his机构编号转换成平台机构编号
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        Organ o = organDAO.getOrganByOrganizeCode(signPatient.getCardOrgan());

        // 卡号信息
        if (!StringUtils.isEmpty(signPatient.getCardType()) && o != null) {
            HealthCard healthCard = new HealthCard();
            healthCard.setCardId(signPatient.getCardId());
            healthCard.setCardType(signPatient.getCardType());
            healthCard.setCardOrgan(o.getOrganId());
            List<HealthCard> healthCards = new ArrayList<HealthCard>();
            healthCards.add(healthCard);
            target.setHealthCards(healthCards);
        }

        target.setPatientType("1");// 20150820：医保卡没有对照列表，全部默认为自费
        target.setPatientName(signPatient.getPatientName());
        target.setIdcard(newIdCard);
        target.setRawIdcard(newIdCard);
        target.setCreateDate(new Date());
        target.setLastModify(new Date());
        target = convertIdcard(target.getIdcard(), target);
        target.setStatus(PatientConstant.PATIENT_STATUS_NORMAL);
        target.setHealthProfileFlag(target.getHealthProfileFlag()==null?false:target.getHealthProfileFlag());

        if (!StringUtils.isEmpty(signPatient.getMobile())) {
            Pattern p = Pattern.compile("^[0-9]{11}$");
            Matcher m = p.matcher(signPatient.getMobile());
            if (!m.matches()) {
                target.setMobile(null);
            } else {
                target.setMobile(signPatient.getMobile());
            }
        }

        return target;
    }

    /**
     * 组装签约病人信息(不用)
     *
     * @param signPatient
     * @return
     * @author hyj
     */
    public RelationDoctor creatRelationDoctorData(SignPatient signPatient) {
        RelationDoctor relationDoctor = new RelationDoctor();
        relationDoctor.setRelationDate(signPatient.getRelationDate());
        relationDoctor.setStartDate(signPatient.getStartDate());
        relationDoctor.setEndDate(signPatient.getEndDate());
        relationDoctor.setFamilyDoctorFlag(true);
        relationDoctor.setRelationType(0);
        return relationDoctor;
    }

    /**
     * 从身份证获取出生年月和性别
     *
     * @param idcard
     * @return
     * @author hyj
     */
    public Patient convertIdcard(String idcard, Patient p) {
        if (idcard.length() == 15) {
            int idcardsex = Integer
                    .parseInt(idcard.substring(idcard.length() - 1));
            p.setPatientSex(idcardsex % 2 == 0 ? "2" : "1");
            String idcardbirthday = "19" + idcard.substring(6, 8) + "-"
                    + idcard.substring(8, 10) + "-" + idcard.substring(10, 12);
            DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Date birthday = null;
            try {
                birthday = sdf.parse(idcardbirthday);
                p.setBirthday(birthday);
            } catch (ParseException e) {
                logger.error(LocalStringUtil.format("error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace())));
            }
        } else {
            int idcardsex = Integer.parseInt(idcard.substring(
                    idcard.length() - 2, idcard.length() - 1));
            p.setPatientSex(idcardsex % 2 == 0 ? "2" : "1");
            String idcardbirthday = idcard.substring(6, 10) + "-"
                    + idcard.substring(10, 12) + "-" + idcard.substring(12, 14);
            DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Date birthday = null;
            try {
                birthday = sdf.parse(idcardbirthday);
                p.setBirthday(birthday);
            } catch (ParseException e) {
                logger.error(LocalStringUtil.format("error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace())));
            }
        }
        return p;
    }

    /**
     * 签约剩余时间
     *
     * @param endDate 签约结束时间
     * @return String
     * @author luf
     */
    @RpcService
    public String remainingRelationTime(Date endDate) {
        Date date = Context.instance().get("date.getToday", Date.class);
        long ms = endDate.getTime() - date.getTime();
        long day = ms / (1000 * 60 * 60 * 24);
        long month = day / 30;
        long year = month / 12;
        if (ms <= 0) {
            return null;
        }
        if (year >= 1) {
            return "余1年以上";
        }
        if (month >= 2) {
            return "余" + month + "个月";
        }
        return "余" + day + "天";
    }

    /**
     * 三页搜索条服务（姓名，身份证，手机号）
     *
     * @param name       患者姓名 --可空
     * @param idCard     患者身份证 --可空
     * @param mobile     患者电话 --可空
     * @param doctorId   当前医生内码 --不可空
     * @param searchType 搜索条所在页面 --0全部患者 1签约患者 2非签约且非标签患者
     * @param start      页面初始位置 --首页从0开始
     * @param limit      每页限制值 --不可空
     * @return List<Patient>
     * @author luf
     */
    @RpcService
    public List<Patient> findPatientByNamOrIdCOrMob(final String name,
                                                    final String idCard, final String mobile, final int doctorId,
                                                    final int searchType, final int start, final int limit) {
        HibernateStatelessResultAction<List<Patient>> action = new AbstractHibernateStatelessResultAction<List<Patient>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select p,r from RelationDoctor r,Patient p where p.mpiId=r.mpiId and r.doctorId=:doctorId and r.mpiId not in(select requestMpiId from SignRecord WHERE doctor=:doctorId AND recordStatus = 4 and NOW()>=date_format(startDate,'%Y-%m-%d') and  NOW()<= date_format(endDate,'%Y-%m-%d'))");
                switch (searchType) {
                    case 0:
                        hql.append(" and (((r.familyDoctorFlag=true or r.relationType=0) and "
                                + "r.startDate<=now() and r.endDate>=now()) or r.relationType=2)");
                        break;
                    case 1:
                        hql.append(" and ((r.familyDoctorFlag=true or r.relationType=0) and "
                                + "r.startDate<=now() and r.endDate>=now())");
                        break;
                    case 2:
                        hql.append(" and r.relationType=2 and r.relationDoctorId not in(:ids)");
                        break;
                    default:
                        break;
                }
                StringBuilder p = new StringBuilder();
                if (!StringUtils.isEmpty(name)) {
                    p.append("or p.patientName like :name ");
                }
                if (!StringUtils.isEmpty(idCard)) {
                    p.append("or p.idcard like :idCard or p.rawIdcard like :idCard ");
                }
                if (!StringUtils.isEmpty(mobile)) {
                    p.append("or p.mobile like :mobile ");
                }
                if (p.length() > 0) {
                    hql.append(" and ( ");
                    hql.append(p.toString().substring(2));
                    hql.append(")");
                }

                Query q = ss.createQuery(hql.toString());
                List<Integer> ids = DAOFactory.getDAO(RelationLabelDAO.class)
                        .findAllRelationPatientId(doctorId);
                if (ids.size() <= 0) {
                    ids.add(0);
                }
                q.setParameter("doctorId", doctorId);
                if (searchType == 2) {
                    q.setParameterList("ids", ids);
                }
                if (!StringUtils.isEmpty(name)) {
                    q.setParameter("name", "%" + name + "%");
                }
                if (!StringUtils.isEmpty(idCard)) {
                    q.setParameter("idCard", "%" + idCard + "%");
                }
                if (!StringUtils.isEmpty(mobile)) {
                    q.setParameter("mobile", "%" + mobile + "%");
                }
                q.setFirstResult(start);
                q.setMaxResults(limit);

                List<Object[]> list = q.list();

                List<Patient> ps = new ArrayList<Patient>();
                for (Object[] os : list) {
                    Patient patient = (Patient) os[0];
                    RelationDoctor relationDoctor = (RelationDoctor) os[1];
                    // 计算签约剩余时间
                    if (relationDoctor.getFamilyDoctorFlag() && relationDoctor.getEndDate() != null) {
                        patient.setRemainDates(remainingRelationTime(relationDoctor.getEndDate()));
                    }

                    patient.setSignFlag(relationDoctor.getFamilyDoctorFlag() == null ? Boolean.valueOf(false)
                            : relationDoctor.getFamilyDoctorFlag());
                    patient.setRelationFlag(true);
                    RelationLabelDAO labelDAO = DAOFactory
                            .getDAO(RelationLabelDAO.class);
                    patient.setLabels(labelDAO
                            .findByRelationPatientId(relationDoctor
                                    .getRelationDoctorId()));
                    patient.setRelationPatientId(relationDoctor
                            .getRelationDoctorId());

                    //2016-12-12 11:23:48 zhangx wx2.6 由于注册时患者不填写身份证，app前端奔溃，
                    //解决方案，idcard字段赋值为空字符串
                    if(StringUtils.isEmpty(patient.getIdcard())){
                        patient.setIdcard("");
                    }
                    ps.add(patient);
                }
                setResult(ps);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<Patient> ps = action.getResult();
        if (ps == null || ps.size() <= 0) {
            return new ArrayList<Patient>();
        }
        return ps;
    }

    /**
     * 三页搜索条服务（姓名）
     *
     * @param name       患者姓名 --可空
     * @param doctorId   当前医生内码 --不可空
     * @param searchType 搜索条所在页面 --0全部患者 1签约患者 2非签约且非标签患者
     * @param start      页面初始位置 --首页从0开始
     * @return List<Patient>
     * @author luf
     */
    @RpcService
    public List<Patient> findThreePagesByName(String name, int doctorId,
                                              int searchType, int start) {
        String idCard = null;
        String mobile = null;
        int limit = 10;
        return this.findPatientByNamOrIdCOrMob(name, idCard, mobile, doctorId,
                searchType, start, limit);
    }

    /**
     * 三页搜索条服务（带随访信息）
     *
     * @param name
     * @param doctorId
     * @param searchType
     * @param start
     * @return
     */
    @RpcService
    public List<Patient> findThreePagesByNameForFollow(String name, int doctorId,
                                                       int searchType, int start) {
        List<Patient> patientList = this.findThreePagesByName(name, doctorId, searchType, start);
        if (null != patientList && !patientList.isEmpty()) {
            FollowQueryService followQueryService = new FollowQueryService();
            followQueryService.setPatientFollowInfo(doctorId, patientList);
        }
        return patientList;
    }

    /**
     * Title: 获取医生全部患者的总数
     *
     * @param doctorId 医生主键
     * @param start    数据库中第几条开始查询（第一页的话，值为0，则从第0条开始查询）
     * @return 总数
     * @author zhangx
     * @date 2015-10-09
     */
    @RpcService
    public Long getAllPatientNumByDoctorId(final Integer doctorId,
                                           final int start) {
        if (doctorId == null) {
            new DAOException(DAOException.VALUE_NEEDED, "doctorId is required!");
        }
        HibernateStatelessResultAction<Long> action = new AbstractHibernateStatelessResultAction<Long>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql_patient = "select count(*) from Patient a,RelationDoctor b "
                        + " where b.doctorId = :doctorId and a.mpiId=b.mpiId "
                        + " and ((b.familyDoctorFlag=1 and NOW()>=date_format(b.startDate,'%Y-%m-%d') "
                        + " and date_format(NOW(),'%Y-%m-%d')<=b.endDate) or b.relationType=2) and b.mpiId not in(select requestMpiId from SignRecord WHERE doctor=:doctorId AND recordStatus = 4 and NOW()>=date_format(startDate,'%Y-%m-%d') and  NOW()<= date_format(endDate,'%Y-%m-%d'))";
                Query query = ss.createQuery(hql_patient);
                query.setParameter("doctorId", doctorId);
                long num = (long) query.uniqueResult();
                setResult(num);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 医生关注的患者
     */
    @RpcService
    @SuppressWarnings({"unchecked"})
    public List<Patient> findAllPatientByDoctorIdWithPage(
            final Integer doctorId, final int start) {
        if (doctorId == null) {
            new DAOException(DAOException.VALUE_NEEDED, "doctorId is required!");
        }
        HibernateStatelessResultAction<List<Patient>> action = new AbstractHibernateStatelessResultAction<List<Patient>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                List<Patient> rpList = new ArrayList<Patient>();
                // TODO Auto-generated method stub
                String hql_patient = "select a,b from Patient a,RelationDoctor b "
                        + " where b.doctorId = :doctorId and a.mpiId=b.mpiId "
                        + " and ((b.familyDoctorFlag=1 and NOW()>=date_format(b.startDate,'%Y-%m-%d') "
                        + " and date_format(NOW(),'%Y-%m-%d')<=b.endDate) or b.relationType=2) and b.mpiId not in(select requestMpiId from SignRecord WHERE doctor=:doctorId AND recordStatus = 4 and NOW()>=date_format(startDate,'%Y-%m-%d') and  NOW()<= date_format(endDate,'%Y-%m-%d'))";
                Query query = ss.createQuery(hql_patient);
                query.setParameter("doctorId", doctorId);
                query.setFirstResult(start); // 设置第一条记录开始的位置
                query.setMaxResults(10); // 设置返回的纪录总条数
                List<Object[]> objArrList = query.list();

                // 查询医生患者标签
                Date endDate = null;
                Integer relationPatientId = 0;
                String remainDates = "";
                for (Object[] objArr : objArrList) {
                    Patient patient = (Patient) objArr[0];
                    RelationDoctor relationDoctor = (RelationDoctor) objArr[1];
                    relationPatientId = relationDoctor.getRelationDoctorId();
                    endDate = relationDoctor.getEndDate();
                    // 计算签约剩余时间
                    if (relationDoctor.getFamilyDoctorFlag() && endDate != null) {
                        remainDates = remainingRelationTime(endDate);
                    } else {
                        remainDates = "";
                    }
                    patient.setSignFlag(relationDoctor.getFamilyDoctorFlag());
                    patient.setRelationFlag(true);
                    patient.setRemainDates(remainDates);

                    // 查询患者标签
                    RelationLabelDAO labelDao = DAOFactory
                            .getDAO(RelationLabelDAO.class);
                    List<RelationLabel> rLabelList = labelDao
                            .findByRelationPatientId(relationPatientId);

                    // 患者标签 赋值
                    patient.setLabels(rLabelList);

                    // 医生关注患者关注id,用于删除标签，添加标签
                    patient.setRelationPatientId(relationPatientId);

                    //2016-12-12 11:23:48 zhangx wx2.6 由于注册时患者不填写身份证，app前端奔溃，
                    //解决方案，idcard字段赋值为空字符串
                    if(StringUtils.isEmpty(patient.getIdcard())){
                        patient.setIdcard("");
                    }

                    rpList.add(patient);
                }
                setResult(rpList);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<Patient> patientList = action.getResult();
        return patientList;
    }

    /**
     * 关注医生A的患者列表
     *
     * @param doctorId 医生A的doctorID
     * @param start
     * @param asc      排序方式(asc:按关注时间正序;desc:按关注时间倒序)
     * @return
     * @author zhangx
     * @date 2015-12-10 上午11:06:44
     */
    @RpcService
    @SuppressWarnings({"unchecked"})
    public List<Patient> findRelationPatientByDoctorIdWithPage(
            final Integer doctorId, final int start, final String asc) {
        if (doctorId == null) {
            new DAOException(DAOException.VALUE_NEEDED, "doctorId is required!");
        }
        HibernateStatelessResultAction<List<Patient>> action = new AbstractHibernateStatelessResultAction<List<Patient>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                List<Patient> rpList = new ArrayList<Patient>();
                // TODO Auto-generated method stub
                String hql_patient = "select a,b from Patient a,RelationDoctor b "
                        + " where b.doctorId = :doctorId and a.mpiId=b.mpiId "
                        + " and ((b.familyDoctorFlag=1 and NOW()>=date_format(b.startDate,'%Y-%m-%d') "
                        + " and date_format(NOW(),'%Y-%m-%d')<=b.endDate) or b.relationType=1)";

                if (!StringUtils.isEmpty(asc)) {
                    hql_patient = hql_patient + " order by b.relationDate "
                            + asc;
                }

                Query query = ss.createQuery(hql_patient);
                query.setParameter("doctorId", doctorId);
                query.setFirstResult(start); // 设置第一条记录开始的位置
                query.setMaxResults(10); // 设置返回的纪录总条数
                List<Object[]> objArrList = query.list();

                // 查询医生患者标签
                Date endDate = null;
                Integer relationPatientId = 0;
                String remainDates = "";
                for (Object[] objArr : objArrList) {
                    Patient patient = (Patient) objArr[0];
                    RelationDoctor relationDoctor = (RelationDoctor) objArr[1];
                    relationPatientId = relationDoctor.getRelationDoctorId();
                    endDate = relationDoctor.getEndDate();
                    // 计算签约剩余时间
                    boolean familyDoctorFlag = relationDoctor.getFamilyDoctorFlag();
                    if (familyDoctorFlag && endDate != null) {
                        remainDates = remainingRelationTime(endDate);
                    } else {
                        remainDates = "";
                    }
                    patient.setSignFlag(familyDoctorFlag);
                    if(familyDoctorFlag) {
                        patient.setRelationFlag(true);
                    }else{
                        //查询 医生是否关注过该患者
                        RelationDoctor rd = getSignByMpiAndDocAndType(patient.getMpiId(), doctorId, 2);
                        if(null == rd){
                            patient.setRelationFlag(false);
                        }else{
                            patient.setRelationFlag(true);
                        }
                    }
                    patient.setRemainDates(remainDates);

                    // 查询患者标签
                    RelationLabelDAO labelDao = DAOFactory
                            .getDAO(RelationLabelDAO.class);
                    List<RelationLabel> rLabelList = labelDao
                            .findByRelationPatientId(relationPatientId);

                    // 患者标签 赋值
                    patient.setLabels(rLabelList);

                    // 医生关注患者关注id,用于删除标签，添加标签
                    patient.setRelationPatientId(relationPatientId);

                    //2016-12-12 11:23:48 zhangx wx2.6 由于注册时患者不填写身份证，app前端奔溃，
                    //解决方案，idcard字段赋值为空字符串
                    if(StringUtils.isEmpty(patient.getIdcard())){
                        patient.setIdcard("");
                    }

                    rpList.add(patient);
                }
                setResult(rpList);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<Patient> patientList = action.getResult();
        return patientList;
    }

    /**
     * 粉丝列表查询 查询关注我的患者
     * zhongzx
     * @param doctorId
     * @param start
     * @param asc
     * @return
     */
    public List<PatientConcernBean> findRelationPatientWithPage(
            final Integer doctorId, final int start, final String asc) {
        if (doctorId == null) {
            new DAOException(DAOException.VALUE_NEEDED, "doctorId is required!");
        }
        HibernateStatelessResultAction<List<PatientConcernBean>> action = new AbstractHibernateStatelessResultAction<List<PatientConcernBean>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                List<PatientConcernBean> rpList = new ArrayList<>();
                // TODO Auto-generated method stub
                String hql_patient = "select a,b from Patient a,RelationDoctor b "
                        + " where b.doctorId = :doctorId and a.mpiId=b.mpiId "
                        + " and ((b.familyDoctorFlag=1 and NOW()>=date_format(b.startDate,'%Y-%m-%d') "
                        + " and date_format(NOW(),'%Y-%m-%d')<=b.endDate) or b.relationType=1)";

                if (!StringUtils.isEmpty(asc)) {
                    hql_patient = hql_patient + " order by b.relationDate "
                            + asc;
                }

                Query query = ss.createQuery(hql_patient);
                query.setParameter("doctorId", doctorId);
                query.setFirstResult(start); // 设置第一条记录开始的位置
                query.setMaxResults(10); // 设置返回的纪录总条数
                List<Object[]> objArrList = query.list();

                for (Object[] objArr : objArrList) {
                    PatientConcernBean patientConcernBean = new PatientConcernBean();
                    Patient patient = (Patient) objArr[0];
                    patientConcernBean.setMpiId(patient.getMpiId());
                    patientConcernBean.setBirthday(patient.getBirthday());
                    patientConcernBean.setPatientName(patient.getPatientName());
                    patientConcernBean.setPatientSex(patient.getPatientSex());
                    patientConcernBean.setPhoto(patient.getPhoto());
                    patientConcernBean.setPatientType(patient.getPatientType());

                    RelationDoctor relationDoctor = (RelationDoctor) objArr[1];
                    patientConcernBean.setObtainType(relationDoctor.getObtainType());
                    patientConcernBean.setRelationType(relationDoctor.getRelationType());
                    boolean familyDoctorFlag = relationDoctor.getFamilyDoctorFlag();

                    patientConcernBean.setSignFlag(familyDoctorFlag);
                    if(familyDoctorFlag) {
                        patientConcernBean.setRelationFlag(true);
                    }else{
                        //查询 医生是否关注过该患者
                        RelationDoctor rd = getSignByMpiAndDocAndType(patient.getMpiId(), doctorId, 2);
                        if(null == rd){
                            patientConcernBean.setRelationFlag(false);
                        }else{
                            patientConcernBean.setRelationFlag(true);
                        }
                    }

                    rpList.add(patientConcernBean);
                }
                setResult(rpList);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<PatientConcernBean> patientList = action.getResult();
        return patientList;
    }

    /**
     * 关注医生A的患者列表
     * 医生粉丝列表 （返回增加病历图片和业务来往）
     * zhongzx 修改
     * @param doctorId 医生A的doctorID
     * @param start
     * @param asc      排序方式(asc:按关注时间正序;desc:按关注时间倒序)
     * @return
     * @author zhangx
     * @date 2015-12-10 上午11:06:44
     */
    @RpcService
    public List<Map<String, Object>> findRelationPatientsWithPage(Integer doctorId, int start, String asc){

        List<PatientConcernBean> patients = findRelationPatientWithPage(doctorId, start, asc);
        List<Map<String, Object>> resList = new ArrayList<>();
        if(null != patients){
            OtherDocDAO otherDocDAO = DAOFactory.getDAO(OtherDocDAO.class);
            OperationRecordsDAO recordsDAO = DAOFactory.getDAO(OperationRecordsDAO.class);
            for(PatientConcernBean p:patients){
                //每个患者的相关信息放在这个map里
                Map<String, Object> map = new HashMap<>();

                //新注册机制 出生日期可能为空
                Date birthDay = p.getBirthday();
                if(null == birthDay){
                    Date date = new Date();
                    p.setBirthday(date);
                }

                String mpiId = p.getMpiId();
                //查询是否有病例图片 如果有图片业务最多两条 没有图片最多三条
                List<Integer> otherDocIds = otherDocDAO.findDocIdsByMpiIdAndType(mpiId, OtherdocConstant.CLINIC_TYPE_UPLOAD_PATIENT);
                Integer num = 2;
                if(null == otherDocIds || 0 == otherDocIds.size()){
                    num = 3;
                }
                //对于每个患者 查询最近业务来往
                List<OperationRecords> ops = new ArrayList<>();
                ops = recordsDAO.findRecentOpRecordsFinal(ops, mpiId, doctorId, 0, num);
                List<Map<String, Object>> busList = null;
                if(null != ops){
                    busList = new ArrayList<>();
                    for(OperationRecords record:ops){
                        Map<String, Object> recordMap = recordsDAO.getBusDetail(record, doctorId);
                        busList.add(recordMap);
                    }
                }

                map.put("patientInfo", p);
                map.put("otherDocIds", otherDocIds);
                map.put("busList", busList);

                resList.add(map);
            }
        }
        return resList;
    }

    /**
     * @param @param  doctorId
     * @param @param  start
     * @param @param  asc
     * @param @return
     * @return List<Patient>
     * @throws
     * @Class eh.mpi.dao.RelationDoctorDAO.java
     * @Title: findSignPatientByDoctorIdWithPage
     * @Description: TODO查询签约患者列表（包括标签、签约剩余时间）
     * @author AngryKitty
     * @Date 2015-12-30上午10:17:25
     */
    @RpcService
    @SuppressWarnings({"unchecked"})
    public List<Patient> findSignPatientByDoctorIdWithPage(
            final Integer doctorId, final int start, final String asc) {
        if (doctorId == null) {
            new DAOException(DAOException.VALUE_NEEDED, "doctorId is required!");
        }
        HibernateStatelessResultAction<List<Patient>> action = new AbstractHibernateStatelessResultAction<List<Patient>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                List<Patient> rpList = new ArrayList<Patient>();
                // TODO Auto-generated method stub
                String hql_patient = "select a,b from Patient a,RelationDoctor b "
                        + " where b.doctorId = :doctorId and a.mpiId=b.mpiId "
                        + " and (b.familyDoctorFlag=1 and NOW()>=date_format(b.startDate,'%Y-%m-%d') "
                        + " and date_format(NOW(),'%Y-%m-%d')<=b.endDate) and b.mpiId not in(select requestMpiId from SignRecord WHERE doctor=:doctorId AND recordStatus = 4 and NOW()>=date_format(startDate,'%Y-%m-%d') and  NOW()<= date_format(endDate,'%Y-%m-%d'))";

                if (!StringUtils.isEmpty(asc)) {
                    hql_patient = hql_patient + " order by b.relationDate "
                            + asc;
                }

                Query query = ss.createQuery(hql_patient);
                query.setParameter("doctorId", doctorId);
                query.setFirstResult(start); // 设置第一条记录开始的位置
                query.setMaxResults(10); // 设置返回的纪录总条数
                List<Object[]> objArrList = query.list();

                // 查询医生患者标签
                Date endDate = null;
                Integer relationPatientId = 0;
                String remainDates = "";
                for (Object[] objArr : objArrList) {
                    Patient patient = (Patient) objArr[0];
                    RelationDoctor relationDoctor = (RelationDoctor) objArr[1];
                    relationPatientId = relationDoctor.getRelationDoctorId();
                    endDate = relationDoctor.getEndDate();
                    // 计算签约剩余时间
                    if (relationDoctor.getFamilyDoctorFlag() && endDate != null) {
                        remainDates = remainingRelationTime(endDate);
                    } else {
                        remainDates = "";
                    }
                    patient.setSignFlag(relationDoctor.getFamilyDoctorFlag());
                    patient.setRelationFlag(true);
                    patient.setRemainDates(remainDates);

                    // 查询患者标签
                    RelationLabelDAO labelDao = DAOFactory
                            .getDAO(RelationLabelDAO.class);
                    List<RelationLabel> rLabelList = labelDao
                            .findByRelationPatientId(relationPatientId);

                    // 患者标签 赋值
                    patient.setLabels(rLabelList);

                    // 医生关注患者关注id,用于删除标签，添加标签
                    patient.setRelationPatientId(relationPatientId);

                    rpList.add(patient);
                }
                setResult(rpList);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<Patient> patientList = action.getResult();
        return patientList;
    }

    /**
     * Title:查询一个医生的签约患者列表 包含患者的标签、签约等信息
     *
     * @param doctorId 医生主键
     * @param start    数据库中第几条开始查询（第一页的话，值为0，则从第0条开始查询）
     * @return List<Patient>
     * @author zhangjr
     * @date 2015-9-24
     */
    @RpcService
    public List<Patient> findSignPatientByDoctorIdWithPage(Integer doctorId, int start) {
        if (doctorId == null) {
            new DAOException(DAOException.VALUE_NEEDED, "doctorId is required!");
        }
        PagingInfo pagingInfo = new PagingInfo();
        pagingInfo.setCurrentIndex(start);
        pagingInfo.setLimit(PageConstant.getPageLimit(10));
        return findSignPatientByDoctorIdImpl(doctorId, pagingInfo, null);
    }

    /**
     * 查询签约患者列表（带随访信息）
     *
     * @param doctorId
     * @param start
     * @return
     */
    @RpcService
    public List<Patient> findSignPatientByDoctorIdWithPageForFollow(Integer doctorId, int start) {
        List<Patient> patientList = this.findSignPatientByDoctorIdWithPage(doctorId, start);
        if (null != patientList && !patientList.isEmpty()) {
            FollowQueryService followQueryService = new FollowQueryService();
            followQueryService.setPatientFollowInfo(doctorId, patientList);
        }
        return patientList;
    }

    /**
     * 查询签约患者列表（搜索）
     *
     * @param doctorId
     * @param searchKey
     * @param start
     * @return
     */
    @RpcService
    public List<Patient> findSignPatientByDoctorIdWithPageForSearch(Integer doctorId, String searchKey, int start) {
        if (doctorId == null) {
            new DAOException(DAOException.VALUE_NEEDED, "doctorId is required!");
        }
        PagingInfo pagingInfo = new PagingInfo();
        pagingInfo.setCurrentIndex(start);
        pagingInfo.setLimit(PageConstant.getPageLimit(10));
        return findSignPatientByDoctorIdImpl(doctorId, pagingInfo, searchKey);
    }

    /**
     * 患者管理-签约患者查询
     *
     * @param doctorId
     * @param pagingInfo
     * @return
     */
    private List<Patient> findSignPatientByDoctorIdImpl(final Integer doctorId, final PagingInfo pagingInfo, final String searchKey) {
        HibernateStatelessResultAction<List<Patient>> action = new AbstractHibernateStatelessResultAction<List<Patient>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                List<Patient> rpList = new ArrayList<Patient>();
                StringBuilder hql_patient = new StringBuilder();
                SignRecordDAO signRecordDAO = DAOFactory.getDAO(SignRecordDAO.class);
                hql_patient.append("select a,b from Patient a,RelationDoctor b where a.mpiId=b.mpiId"
                        + " and b.doctorId=:doctorId and b.familyDoctorFlag=1 and NOW()>=date_format(b.startDate,'%Y-%m-%d') "
                        + " and NOW()<=date_format(b.endDate,'%Y-%m-%d') and b.mpiId not in(select requestMpiId from SignRecord WHERE doctor=:doctorId AND recordStatus = 4 and NOW()>=date_format(startDate,'%Y-%m-%d') and  NOW()<= date_format(endDate,'%Y-%m-%d'))");

                if (StringUtils.isNotEmpty(searchKey)) {
                    hql_patient.append(" and a.patientName like :patientName ");
                }
                Query query = ss.createQuery(hql_patient.toString());
                query.setParameter("doctorId", doctorId);
                if (StringUtils.isNotEmpty(searchKey)) {
                    query.setParameter("patientName", "%" + searchKey + "%");
                }
                if (null != pagingInfo) {
                    query.setFirstResult(pagingInfo.getCurrentIndex());  // 设置第一条记录开始的位置
                    query.setMaxResults(pagingInfo.getLimit());   // 设置返回的纪录总条数
                }
                List<Object[]> objArrList = query.list();

                // 查询医生患者标签
                Date endDate = null;
                Integer relationPatientId = 0;
                String remainDates = "";
                for (Object[] objArr : objArrList) {
                    Patient patient = (Patient) objArr[0];
                    RelationDoctor relationDoctor = (RelationDoctor) objArr[1];
                    relationPatientId = relationDoctor.getRelationDoctorId();
                    endDate = relationDoctor.getEndDate();
                    // 计算签约剩余时间
                    if (relationDoctor.getFamilyDoctorFlag() && endDate != null) {
                        remainDates = remainingRelationTime(endDate);
                    } else {
                        remainDates = "";
                    }
                    patient.setSignFlag(relationDoctor.getFamilyDoctorFlag());
                    patient.setRelationFlag(true);
                    patient.setRemainDates(remainDates);

                    // 查询患者标签
                    RelationLabelDAO labelDao = DAOFactory
                            .getDAO(RelationLabelDAO.class);
                    List<RelationLabel> rLabelList = labelDao
                            .findByRelationPatientId(relationPatientId);

                    // 患者标签 赋值
                    patient.setLabels(rLabelList);

                    // 医生关注患者关注id,用于删除标签，添加标签
                    patient.setRelationPatientId(relationPatientId);

                    //2016-12-12 11:23:48 zhangx wx2.6 由于注册时患者不填写身份证，app前端奔溃，
                    //解决方案，idcard字段赋值为空字符串
                    if(StringUtils.isEmpty(patient.getIdcard())){
                        patient.setIdcard("");
                    }

                    rpList.add(patient);
                }
                setResult(rpList);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<Patient> patientList = action.getResult();
        return patientList;
    }

    /**
     * Title:查询一个医生的签约患者总数
     *
     * @param doctorId 医生主键
     * @param start    数据库中第几条开始查询（第一页的话，值为0，则从第0条开始查询）
     * @return 总数
     * @author zhangx
     * @date 2015-10-09
     */
    @RpcService
    public Long getSignPatientNumByDoctorId(final Integer doctorId,
                                            final int start) {
        if (doctorId == null) {
            new DAOException(DAOException.VALUE_NEEDED, "doctorId is required!");
        }
        HibernateStatelessResultAction<Long> action = new AbstractHibernateStatelessResultAction<Long>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql_patient = "select count(*) from Patient a,RelationDoctor b where a.mpiId=b.mpiId"
                        + " and b.doctorId=:doctorId and b.familyDoctorFlag=1 and NOW()>=date_format(b.startDate,'%Y-%m-%d') "
                        + " and NOW()<= date_format(b.endDate,'%Y-%m-%d') and b.mpiId not in(select requestMpiId from SignRecord WHERE doctor=:doctorId AND recordStatus = 4 and NOW()>=date_format(startDate,'%Y-%m-%d') and  NOW()<= date_format(endDate,'%Y-%m-%d'))";
                Query query = ss.createQuery(hql_patient);
                query.setParameter("doctorId", doctorId);
                long num = (long) query.uniqueResult();
                setResult(num);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }
    
    public Long getSignPatientNumByDoctorIdNew(final Integer doctorId){
    	if (doctorId == null) {
            new DAOException(DAOException.VALUE_NEEDED, "doctorId is required!");
        }
    	
        HibernateStatelessResultAction<Long> action = new AbstractHibernateStatelessResultAction<Long>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
            	StringBuilder hql_patient = new StringBuilder();
                hql_patient.append("select a,b from Patient a,RelationDoctor b where a.mpiId=b.mpiId"
                        + " and b.doctorId=:doctorId and b.familyDoctorFlag=1 and NOW()>=date_format(b.startDate,'%Y-%m-%d') "
                        + " and NOW()<=date_format(b.endDate,'%Y-%m-%d') ");
                SignRecordDAO signRecordDAO = DAOFactory.getDAO(SignRecordDAO.class);
                
                Query query = ss.createQuery(hql_patient.toString());
                query.setParameter("doctorId", doctorId);
                List<Object[]> objArrList = query.list();
                Long num = 0L;
                for (Object[] objArr : objArrList) {
                    RelationDoctor relationDoctor = (RelationDoctor) objArr[1];
                    List<SignRecord> signRecordList = signRecordDAO.findDoctorAndMpiId(relationDoctor.getDoctorId(), relationDoctor.getMpiId());
                	boolean flag = false;
                	if (CollectionUtils.isNotEmpty(signRecordList)) {
                		for (SignRecord signRecord : signRecordList) {
                			Integer recordStatus = signRecord.getRecordStatus();
                			//签约确认中
                			if (null != recordStatus && recordStatus.equals(SignRecordConstant.RECORD_STATUS_CONFIRMATION)) {
                				flag = true;
                				break;
                			}
    					}
                	}
                	if (flag) {
                		continue;
                	}
                	num += 1L;
                }
                setResult(num);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }
    

    /**
     * Title:查询一个医生的未添加标签的患者
     *
     * @param doctorId 医生主键
     * @param start    数据库中第几条开始查询（第一页的话，值为0，则从第0条开始查询）
     * @return 总数
     * @author zhangyq
     * @date 2017-02-14
     */
    @RpcService
    public Long getNoLabelPatientNumByDoctorId(final Integer doctorId,final int start){
        if(doctorId==null){
            new DAOException(DAOException.VALUE_NEEDED,"doctorId is required!");
        }
        HibernateStatelessResultAction<Long>action=new AbstractHibernateStatelessResultAction<Long>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql="select count(*) from Patient a,RelationDoctor b where b.doctorId = :doctorId and (a.mpiId=b.mpiId "
                        + " and ((b.familyDoctorFlag=1 and NOW()>=date_format(b.startDate,'%Y-%m-%d') "
                        + " and date_format(NOW(),'%Y-%m-%d')<=b.endDate) or b.relationType=2))"
                        + "and b.relationDoctorId not in (:ids)";
                List<Integer> ids = DAOFactory.getDAO(RelationLabelDAO.class).findAllRelationPatientId(doctorId);
                if(ids.size()<=0){
                    ids.add(0);
                }
                Query query=ss.createQuery(hql);
                query.setParameter("doctorId",doctorId);
                query.setParameterList("ids",ids);
                long num= (long) query.uniqueResult();
                setResult(num);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * Title:查询一个医生的未分组患者列表 包含未签约且没有标签的患者
     *
     * @param doctorId 医生主键
     * @param start    数据库中第几条开始查询（第一页的话，值为0，则从第0条开始查询）
     * @return List<Patient>
     * @author zhangjr
     * @date 2015-9-24
     */
    @RpcService
    @SuppressWarnings({"unchecked"})
    public List<Patient> findNoGroupPatientWidthPage(final Integer doctorId,
                                                     final int start) {
        if (doctorId == null) {
            new DAOException(DAOException.VALUE_NEEDED, "doctorId is required!");
        }
        HibernateStatelessResultAction<List<Patient>> action = new AbstractHibernateStatelessResultAction<List<Patient>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                List<Patient> rpList = new ArrayList<Patient>();
                // TODO Auto-generated method stub
                String hql_patient = "select a,b from Patient a,RelationDoctor b "
                        + " where a.mpiId=b.mpiId and b.doctorId=:doctorId and b.familyDoctorFlag=0 and b.relationType=2 "
                        + " and not exists(select r from RelationLabel r where r.relationPatientId = b.relationDoctorId)";
                Query query = ss.createQuery(hql_patient);
                query.setParameter("doctorId", doctorId);
                query.setFirstResult(start); // 设置第一条记录开始的位置
                query.setMaxResults(10); // 设置返回的纪录总条数
                List<Object[]> objArrList = query.list();

                Integer relationPatientId = 0;
                for (Object[] objArr : objArrList) {
                    Patient patient = (Patient) objArr[0];
                    RelationDoctor relationDoctor = (RelationDoctor) objArr[1];
                    relationPatientId = relationDoctor.getRelationDoctorId();

                    // 医生关注患者关注id,用于删除标签，添加标签
                    patient.setRelationPatientId(relationPatientId);
                    patient.setSignFlag(relationDoctor.getFamilyDoctorFlag());
                    patient.setRelationFlag(true);

                    rpList.add(patient);
                }
                setResult(rpList);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<Patient> patientList = action.getResult();
        return patientList;
    }

    /**
     * Title:查询一个医生的未分组患者总数
     *
     * @param doctorId 医生主键
     * @param start    数据库中第几条开始查询（第一页的话，值为0，则从第0条开始查询）
     * @return 总数
     * @author zhangx
     * @date 2015-10-09
     */
    @RpcService
    public Long getNoGroupPatientNum(final Integer doctorId, final int start) {
        if (doctorId == null) {
            new DAOException(DAOException.VALUE_NEEDED, "doctorId is required!");
        }
        HibernateStatelessResultAction<Long> action = new AbstractHibernateStatelessResultAction<Long>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql_patient = "select count(*) from Patient a,RelationDoctor b "
                        + " where a.mpiId=b.mpiId and b.doctorId=:doctorId and b.familyDoctorFlag=0 and b.relationType=2 "
                        + " and not exists(select r from RelationLabel r where r.relationPatientId = b.relationDoctorId)";
                Query query = ss.createQuery(hql_patient);
                query.setParameter("doctorId", doctorId);
                long num = (long) query.uniqueResult();
                setResult(num);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * Title:查询一个医生的指定标签患者
     *
     * @param doctorId  医生主键
     * @param labelName 标签名称
     * @param start     数据库中第几条开始查询（第一页的话，值为0，则从第0条开始查询）
     * @return List<Patient>
     * @author zhangjr
     * @date 2015-9-24
     */
    @RpcService
    @SuppressWarnings("unchecked")
    public List<Patient> findPatientByLabelAndDoctorIdWidthPage(
            final Integer doctorId, final String labelName, final int start) {
        if (doctorId == null) {
            new DAOException(DAOException.VALUE_NEEDED, "doctorId is required!");
        }
        HibernateStatelessResultAction<List<Patient>> action = new AbstractHibernateStatelessResultAction<List<Patient>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                List<Patient> rpList = new ArrayList<Patient>();
                // TODO Auto-generated method stub
                String hql_patient = "select a,b from Patient a,RelationDoctor b ,RelationLabel c where b.doctorId=:doctorId "
                        + " and a.mpiId=b.mpiId and b.relationDoctorId=c.relationPatientId and c.labelName =:labelName ";
                Query query = ss.createQuery(hql_patient);
                query.setParameter("doctorId", doctorId);
                query.setParameter("labelName", labelName);
                query.setFirstResult(start); // 设置第一条记录开始的位置
                query.setMaxResults(10); // 设置返回的纪录总条数
                List<Object[]> objArrList = query.list();

                // 查询医生患者标签
                Date endDate = null;
                Integer relationPatientId = 0;
                String remainDates = "";
                for (Object[] objArr : objArrList) {
                    Patient patient = (Patient) objArr[0];
                    RelationDoctor relationDoctor = (RelationDoctor) objArr[1];
                    relationPatientId = relationDoctor.getRelationDoctorId();
                    endDate = relationDoctor.getEndDate();
                    // 计算签约剩余时间
                    if (relationDoctor.getFamilyDoctorFlag() && endDate != null) {
                        remainDates = remainingRelationTime(endDate);
                    } else {
                        remainDates = "";
                    }
                    patient.setSignFlag(relationDoctor.getFamilyDoctorFlag());
                    patient.setRemainDates(remainDates);

                    // 查询患者标签
                    RelationLabelDAO labelDao = DAOFactory
                            .getDAO(RelationLabelDAO.class);
                    List<RelationLabel> rLabelList = labelDao
                            .findByRelationPatientId(relationPatientId);

                    // 患者标签 赋值
                    patient.setLabels(rLabelList);

                    // 医生关注患者关注id,用于删除标签，添加标签
                    patient.setRelationPatientId(relationPatientId);

                    //2016-12-12 11:23:48 zhangx wx2.6 由于注册时患者不填写身份证，app前端奔溃，
                    //解决方案，idcard字段赋值为空字符串
                    if(StringUtils.isEmpty(patient.getIdcard())){
                        patient.setIdcard("");
                    }
                    rpList.add(patient);
                }
                setResult(rpList);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<Patient> patientList = action.getResult();
        return patientList;
    }

    /**
     * 查询一个医生的指定标签患者(带随访信息)
     *
     * @param doctorId
     * @param labelName
     * @param start
     * @return
     */
    @RpcService
    public List<Patient> findPatientByLabelAndDoctorIdWidthPageForFollow(
            final Integer doctorId, final String labelName, final int start) {
        List<Patient> patientList = this.findPatientByLabelAndDoctorIdWidthPage(doctorId, labelName, start);
        if (null != patientList && !patientList.isEmpty()) {
            FollowQueryService followQueryService = new FollowQueryService();
            followQueryService.setPatientFollowInfo(doctorId, patientList);
        }

        return patientList;
    }

    /**
     * Title:查询一个医生的指定标签患者(个数)
     *
     * @param doctorId  医生主键
     * @param labelName 标签名称
     * @param start     数据库中第几条开始查询（第一页的话，值为0，则从第0条开始查询）
     * @return List<Patient>
     * @author zhangjr
     * @date 2015-9-24
     */
    @RpcService
    public Long getPatientNumByLabelAndDoctorId(final Integer doctorId,
                                                final String labelName, final int start) {
        if (doctorId == null) {
            new DAOException(DAOException.VALUE_NEEDED, "doctorId is required!");
        }
        HibernateStatelessResultAction<Long> action = new AbstractHibernateStatelessResultAction<Long>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql_patient = "select count(*) from Patient a,RelationDoctor b ,RelationLabel c where b.doctorId=:doctorId "
                        + " and a.mpiId=b.mpiId and b.relationDoctorId=c.relationPatientId and c.labelName =:labelName ";
                Query query = ss.createQuery(hql_patient);
                query.setParameter("doctorId", doctorId);
                query.setParameter("labelName", labelName);

                long num = (long) query.uniqueResult();
                setResult(num);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 获取全部签约病人主键
     *
     * @param doctorId 医生ID
     * @return
     * @author AngryKitty
     */
    @RpcService
    @DAOMethod(limit = 9999, sql = "select distinct b.mpiId from RelationDoctor b where  b.doctorId=:doctorId and b.familyDoctorFlag=1 and NOW()>b.startDate and NOW()<b.endDate")
    public abstract List<String> findSignPatientByDoctorId(
            @DAOParam("doctorId") int doctorId);

    /**
     * 根据医生主键查询所有病人主键
     *
     * @param doctorId 医生ID
     * @return
     * @author AngryKitty
     */
    @RpcService
    @DAOMethod(limit = 9999, sql = " select distinct d.mpiId  from RelationDoctor d where d.doctorId=:doctorId and( (d.familyDoctorFlag=1 and NOW()>=d.startDate and NOW()<=d.endDate) or d.relationType=2) ")
    public abstract List<String> findByDoctorId(
            @DAOParam("doctorId") Integer doctorId);

    /**
     * 根据医生主键查询未分组病人主键
     *
     * @param doctorId 医生ID
     * @return
     * @author AngryKitty
     */
    @RpcService
    @DAOMethod(limit = 9999, sql = " select distinct d.mpiId  from RelationDoctor d where d.doctorId=:doctorId   and d.relationType=2  and not exists(select r from RelationLabel r where r.relationPatientId = d.relationDoctorId) ")
    public abstract List<String> findNoGroupByDoctorId(
            @DAOParam("doctorId") Integer doctorId);

    /**
     * 根据医生主键和标签名查询病人主键
     *
     * @param doctorId  医生ID
     * @param labelName 标签名
     * @return
     * @author AngryKitty
     */
    @RpcService
    @DAOMethod(limit = 9999, sql = " select distinct d.mpiId  from RelationDoctor d,RelationLabel l where d.doctorId=:doctorId  and d.relationDoctorId=l.relationPatientId and l.labelName =:labelName")
    public abstract List<String> findByDoctorIdAndLabel(
            @DAOParam("doctorId") Integer doctorId,
            @DAOParam("labelName") String labelName);

    /**
     * 获取关注病人记录(familyDoctorFlag=false的记录)
     *
     * @param mpiId    主索引ID
     * @param doctorId 医生编码
     * @return RelationDoctor
     */
    @RpcService
    @DAOMethod(sql = "from RelationDoctor where mpiId=:mpiId and doctorId=:doctorId and familyDoctorFlag=false and relationType=2")
    public abstract RelationDoctor getRelPatByMpiAndDoc(
            @DAOParam("mpiId") String mpiId,
            @DAOParam("doctorId") Integer doctorId);

    /**
     * 根据关注ID查病人详细信息
     *
     * @param relationDoctorId 医生关注内码
     * @return Patient
     * @author luf
     */
    @RpcService
    public Patient getPatientByRelationId(Integer relationDoctorId) {
        RelationDoctor rd = this.get(relationDoctorId);
        if (rd == null) {
            return null;
        }
        Patient p = DAOFactory.getDAO(PatientDAO.class).get(rd.getMpiId());
        if (p == null) {
            return null;
        }
        p.setRelationPatientId(relationDoctorId);
        p.setSignFlag(rd.getFamilyDoctorFlag());
        p.setRelationFlag(true);
        List<RelationLabel> rls = DAOFactory.getDAO(RelationLabelDAO.class)
                .findByRelationPatientId(relationDoctorId);
        p.setLabels(rls);
        p.setNote(rd.getNote());
        return p;
    }

    /**
     * 整理关注医生数据
     *
     * @author Luf
     * @date 2015-10-20上午11:06:36
     */
    public void cleanDataOfThisTAble() {
        @SuppressWarnings("rawtypes")
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                String hql = "SELECT COUNT(relationDoctorId),mpiId,doctorId from RelationDoctor GROUP BY doctorId,mpiId";
                Query q = ss.createQuery(hql);
                List<Object[]> list = q.list();
                for (Object[] os : list) {
                    Long count = (Long) os[0];
                    if (count > 1) {
                        List<RelationDoctor> doctors = findByMpiIdAndDoctorId(
                                os[1].toString(), (Integer) os[2]);
                        List<Integer> rdss = new ArrayList<Integer>();
                        List<Integer> rdfs = new ArrayList<Integer>();
                        List<Integer> rd = new ArrayList<Integer>();
                        List<Integer> rp = new ArrayList<Integer>();
                        for (RelationDoctor r : doctors) {
                            Integer rdId = r.getRelationDoctorId();
                            Integer rt = r.getRelationType();
                            Date nowDate = new Date();
                            if (r.getFamilyDoctorFlag() == true || rt == 0) {
                                if (r.getEndDate().after(nowDate)) {
                                    rdss.add(rdId);
                                } else {
                                    rdfs.add(rdId);
                                }
                                continue;
                            }
                            if (rt == 1) {
                                rd.add(rdId);
                                continue;
                            }
                            if (rt == 2) {
                                rp.add(rdId);
                            }
                        }
                        if (rdss != null && rdss.size() > 0) {
                            for (Integer id : rdfs) {
                                remove(id);
                            }
                            for (Integer id : rp) {
                                DAOFactory.getDAO(RelationLabelDAO.class)
                                        .deleteByRelationPatientId(id);
                                remove(id);
                            }
                            for (Integer id : rd) {
                                remove(id);
                            }
                            if (rdss.size() > 1) {
                                List<RelationDoctor> rds = new ArrayList<RelationDoctor>();
                                for (Integer id : rdss) {
                                    rds.add(get(id));
                                }
                                RelationDoctor sign = rds.get(0);
                                Date sd = sign.getStartDate();
                                Date ed = sign.getEndDate();
                                for (int i = 1; i < rds.size(); i++) {
                                    Date s = rds.get(i).getStartDate();
                                    Date e = rds.get(i).getEndDate();
                                    Integer id = rds.get(i)
                                            .getRelationDoctorId();
                                    if (s.before(sd)) {
                                        sign.setStartDate(s);
                                    }
                                    if (e.after(ed)) {
                                        sign.setEndDate(e);
                                    }
                                    remove(id);
                                }
                                update(sign);
                            }
                            continue;
                        }
                        if (rdfs != null && rdfs.size() > 0) {
                            if (rp.size() > 0) {
                                for (Integer id : rdfs) {
                                    remove(id);
                                }
                                for (int i = 0; i < rp.size() - 1; i++) {
                                    remove(rp.get(i));
                                }
                                for (int i = 0; i < rd.size() - 1; i++) {
                                    remove(rd.get(i));
                                }
                                continue;
                            }
                            for (int i = 0; i < rdfs.size() - 1; i++) {
                                remove(rdfs.get(i));
                            }
                            for (int i = 0; i < rd.size() - 1; i++) {
                                remove(rd.get(i));
                            }
                            continue;
                        }
                        for (int i = 0; i < rp.size() - 1; i++) {
                            remove(rp.get(i));
                        }
                        for (int i = 0; i < rd.size() - 1; i++) {
                            remove(rd.get(i));
                        }
                    }
                }
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
    }

    /**
     * 医生和病人对应的所有关注信息查询服务
     *
     * @param doctorId 医生内码
     * @param mpiId    主索引
     * @return Patient
     * @author luf
     */
    @RpcService
    public Patient getPatientAndRelationByDocAndMpi(int doctorId, String mpiId) {
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        Patient patient = patientDAO.get(mpiId);
        List<RelationDoctor> relationDoctors = findByDoctorIdAndMpiId(doctorId,
                mpiId);
        Boolean signFlag = false;
        Boolean relationFlag = false;
        String note = null;
        List<String> labelNames = new ArrayList<String>();
        for (RelationDoctor relationDoctor : relationDoctors) {
            Integer relationType = relationDoctor.getRelationType();
            Integer relationDoctorId = relationDoctor.getRelationDoctorId();
            Date now = new Date();
            switch (relationType) {
                case 0:
                    if ((relationDoctor.getEndDate()).after(now)
                            && (relationDoctor.getStartDate()).before(now)) {
                        signFlag = true;
                    }
                    break;
                case 2:
                    labelNames = DAOFactory.getDAO(RelationLabelDAO.class)
                            .findLabelNamesByRPId(relationDoctorId);
                    note = relationDoctor.getNote();
                    break;
                default:
                    break;
            }
            relationFlag = true;
        }
        patient.setSignFlag(signFlag);
        patient.setRelationFlag(relationFlag);
        patient.setNote(note);
        patient.setLabelNames(labelNames);
        return patient;
    }

    /**
     * 供getPatientAndRelationByDocAndMpi调用
     *
     * @param doctorId 医生内码
     * @param mpiId    主索引
     * @return List<RelationDoctor>
     * @author luf
     */
    @DAOMethod
    public abstract List<RelationDoctor> findByDoctorIdAndMpiId(
            Integer doctorId, String mpiId);

    /**
     * 按姓名，身份证，手机号三个其中的一个或多个搜索医生关注的患者中符合条件的患者
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
    @DAOMethod(sql = "select distinct(p) from RelationDoctor r,Patient p where p.mpiId=r.mpiId and r.doctorId=:doctorId and (  ( (r.familyDoctorFlag=true or r.relationType=0) and r.startDate<=now() and r.endDate>=now() ) or r.relationType=2  ) and ( p.patientName like :name or p.idcard like :idCard or p.mobile like :mobile)")
    public abstract List<Patient> findAttentionPatients(
            @DAOParam("doctorId") Integer doctorId,
            @DAOParam("name") String name, @DAOParam("idCard") String idCard,
            @DAOParam("mobile") String mobile, long start, long limit);

    /**
     * 查询病人关注医生列表(健康端)
     * <p>
     * eh.mpi.dao
     *
     * @param mpiId 主索引ID
     * @param flag  标志-0咨询1预约
     * @return List<HashMap<String,Object>>
     * @author luf 2016-2-26 增加排序规则-按入口分别排序
     */
    @RpcService
    public List<HashMap<String, Object>> findRelationDoctorListForHealth(
            final String mpiId, final int flag) {
        HibernateStatelessResultAction<List<Object[]>> action = new AbstractHibernateStatelessResultAction<List<Object[]>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
                List<Integer> organs = organDAO.findOrgansByUnitForHealth();

                StringBuffer hql = new StringBuffer(
                        "select distinct d,r.familyDoctorFlag from RelationDoctor r,Doctor d,ConsultSet c where mpiId=:mpiId and "
                                + "((relationType=0 and current_timestamp()>=startDate and current_timestamp()<=endDate) "
                                // 2016-3-5
                                // luf:患者端除预约入口按机构、科室找医生有挂号科室医生外，其他所有与医生有关的都必须是正常有效医生
                                //2016-6-2 luf:去除团队医生限制，增加 or d.teams=1
                                + "or relationType=1) and d.doctorId=r.doctorId and ((d.idNumber is not null and d.idNumber<>:empty)or d.teams=1)"
                                + " and d.status=1 and d.doctorId=c.doctorId");
                // 2016-4-25 luf:添加个性化  and (d.organ in :organs)
                if (organs != null && organs.size() > 0) {
                    hql.append(" and (");
                    for (Integer i : organs) {
                        hql.append("d.organ=").append(i).append(" or ");
                    }
                    hql = new StringBuffer(hql.substring(0, hql.length() - 3)).append(")");
                }
                //2016-7-15 luf:业务设置为null是排序出错bug修复，添加IFNULL(,0)
                if (flag == 0) {
                    hql.append(" order by (IFNULL(c.onLineStatus,0)+IFNULL(c.appointStatus,0)) DESC,d.goodRating DESC");
                } else {
                    hql.append(" order by (IFNULL(d.haveAppoint,0)+IFNULL(c.patientTransferStatus,0)) DESC,d.goodRating DESC");
                }
                Query q = ss.createQuery(hql.toString());
                q.setParameter("mpiId", mpiId);
                q.setParameter("empty", "");
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<Object[]> doctors = action.getResult();
        List<HashMap<String, Object>> targets = new ArrayList<HashMap<String, Object>>();
        ConsultSetDAO dao = DAOFactory.getDAO(ConsultSetDAO.class);
        for (Object[] os : doctors) {
            Doctor doctor = (Doctor) os[0];
            Boolean signflag = (Boolean) os[1];
            HashMap<String, Object> result = new HashMap<String, Object>();
            int doctorId = doctor.getDoctorId();
            ConsultSet consultSet = dao.get(doctorId);
            doctor.setIsSign(signflag);
            result.put("doctor", doctor);
            result.put("consultSet", consultSet);
            targets.add(result);
        }
        return targets;
    }

    /**
     * 获取医生关注病人信息
     *
     * @param mpiId    主索引
     * @param doctorId 医生内码
     * @return Patient
     * @author luf
     */
    @RpcService
    public Patient getPatientRelation(String mpiId, int doctorId) {
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        Patient p = patientDAO.get(mpiId);
        RelationPatientDAO dao = DAOFactory.getDAO(RelationPatientDAO.class);
        RelationDoctor relationDoctor = dao.getByMpiidAndDoctorId(mpiId,
                doctorId);
        Integer relationDoctorId = 0;
        Boolean relationFlag = false;
        Boolean signFlag = false;
        List<String> labelNames = new ArrayList<String>();
        if (relationDoctor != null) {
            relationDoctorId = relationDoctor.getRelationDoctorId();
            relationFlag = true;
            if (relationDoctor.getFamilyDoctorFlag()) {
                signFlag = true;
            }
            RelationLabelDAO labelDAO = DAOFactory
                    .getDAO(RelationLabelDAO.class);
            labelNames = labelDAO.findLabelNamesByRPId(relationDoctorId);
            p.setNote(relationDoctor.getNote());
        }
        p.setRelationPatientId(relationDoctorId);
        p.setRelationFlag(relationFlag);
        p.setSignFlag(signFlag);
        p.setLabelNames(labelNames);
        return p;
    }

    /**
     * @param @param  doctorId
     * @param @param  labelName
     * @param @param  start
     * @param @return
     * @return List<Patient>
     * @throws
     * @Class eh.mpi.dao.RelationDoctorDAO.java
     * @Title: findSignPatientByLabelAndDoctorIdWidthPage
     * @Description: TODO 根据标签查询签约患者
     * @author AngryKitty
     * @Date 2015-12-30上午10:37:01
     */
    @RpcService
    @SuppressWarnings("unchecked")
    public List<Patient> findSignPatientByLabelAndDoctorIdWidthPage(
            final Integer doctorId, final String labelName, final int start) {
        if (doctorId == null) {
            new DAOException(DAOException.VALUE_NEEDED, "doctorId is required!");
        }
        HibernateStatelessResultAction<List<Patient>> action = new AbstractHibernateStatelessResultAction<List<Patient>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                List<Patient> rpList = new ArrayList<Patient>();
                // TODO Auto-generated method stub
                String hql_patient = "select a,b from Patient a,RelationDoctor b ,RelationLabel c where b.doctorId=:doctorId "
                        + " and a.mpiId=b.mpiId and b.relationDoctorId=c.relationPatientId and c.labelName =:labelName "
                        + "and b.relationType=0 and current_timestamp()>=b.startDate and current_timestamp()<=b.endDate";
                Query query = ss.createQuery(hql_patient);
                query.setParameter("doctorId", doctorId);
                query.setParameter("labelName", labelName);
                query.setFirstResult(start); // 设置第一条记录开始的位置
                query.setMaxResults(10); // 设置返回的纪录总条数
                List<Object[]> objArrList = query.list();

                // 查询医生患者标签
                Date endDate = null;
                Integer relationPatientId = 0;
                String remainDates = "";
                for (Object[] objArr : objArrList) {
                    Patient patient = (Patient) objArr[0];
                    RelationDoctor relationDoctor = (RelationDoctor) objArr[1];
                    relationPatientId = relationDoctor.getRelationDoctorId();
                    endDate = relationDoctor.getEndDate();
                    // 计算签约剩余时间
                    if (relationDoctor.getFamilyDoctorFlag() && endDate != null) {
                        remainDates = remainingRelationTime(endDate);
                    } else {
                        remainDates = "";
                    }
                    patient.setSignFlag(relationDoctor.getFamilyDoctorFlag());
                    patient.setRemainDates(remainDates);

                    // 查询患者标签
                    RelationLabelDAO labelDao = DAOFactory
                            .getDAO(RelationLabelDAO.class);
                    List<RelationLabel> rLabelList = labelDao
                            .findByRelationPatientId(relationPatientId);

                    // 患者标签 赋值
                    patient.setLabels(rLabelList);

                    // 医生关注患者关注id,用于删除标签，添加标签
                    patient.setRelationPatientId(relationPatientId);

                    rpList.add(patient);
                }
                setResult(rpList);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<Patient> patientList = action.getResult();
        return patientList;
    }

    /**
     * 个性化医生签约或关注列表
     *
     * @param mpiId
     * @param flag    0首页，1我的
     * @param myStart 分页开始位置--用于我的及更多
     * @param myLimit 没页限制条数--用于我的及更多
     * @return
     */
    @RpcService
    public List<HashMap<String, Object>> signOrRelationDoctors(String mpiId, int flag, Integer myStart, Integer myLimit) {
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        SignRecordDAO signRecordDAO = DAOFactory.getDAO(SignRecordDAO.class);
        List<Integer> organs = organDAO.findOrgansByUnitForHealth();
        int limit = 10;
        if (flag == 0) {
            myStart = null;
        } else if (myLimit > 0) {
            limit = myLimit;
        }
        List<Object[]> result = new ArrayList<Object[]>();
        Integer signNum = getAllSignNumByUnit(mpiId, organs).intValue();
        if (flag == 0 || myStart <= signNum) {
            result.addAll(this.findDoctorsInUnitOrgan(mpiId, organs, myStart, limit, 0));
        }
        if (result != null && result.size() > 0) {
            int size = result.size();
            if (flag == 1 && size < limit) {
                myStart += size;
                myStart -= signNum;
            }
            limit -= size;
        } else if (flag == 1) {
            myStart -= signNum;
        }
        if (limit > 0) {
            result.addAll(this.findDoctorsInUnitOrgan(mpiId, organs, myStart, limit, 1));
        }
        List<HashMap<String, Object>> results = new ArrayList<HashMap<String, Object>>();
        if (flag == 0) {
            for (Object[] os : result) {
                HashMap<String, Object> map = new HashMap<String, Object>();
                Doctor o1 = (Doctor) os[0];
                //过滤医生
                List<SignRecord> signRecordList = signRecordDAO.findDoctorAndMpiId(o1.getDoctorId(), mpiId);
                boolean doctorFlag = false;
            	if (CollectionUtils.isNotEmpty(signRecordList)) {
            		for (SignRecord signRecord : signRecordList) {
            			Integer recordStatus = signRecord.getRecordStatus();
            			//签约确认中
            			if (null != recordStatus && recordStatus.equals(SignRecordConstant.RECORD_STATUS_CONFIRMATION)) {
            				doctorFlag = true;
            				break;
            			}
					}
            	}
            	if (doctorFlag) {
            		continue;
            	}
                Boolean signFlag = (Boolean) os[1];
                Doctor d = new Doctor();
                d.setDoctorId(o1.getDoctorId());
                d.setPhoto(o1.getPhoto());
                d.setGender(o1.getGender());
                d.setName(o1.getName());
                d.setProTitle(o1.getProTitle());
                d.setProfession(o1.getProfession());
                d.setIsSign(signFlag);
                d.setTeams(o1.getTeams());
                d.setRating(o1.getRating());
                d.setVirtualDoctor(o1.getVirtualDoctor());
                map.put("doctor", d);
                results.add(map);
            }
        } else {
            ConsultSetDAO consDao = DAOFactory.getDAO(ConsultSetDAO.class);
            DoctorDAO doctorDao = DAOFactory.getDAO(DoctorDAO.class);
            for (Object[] os : result) {
                HashMap<String, Object> map = new HashMap<String, Object>();
                Doctor o1 = (Doctor) os[0];
              //过滤医生
                List<SignRecord> signRecordList = signRecordDAO.findDoctorAndMpiId(o1.getDoctorId(), mpiId);
                boolean doctorFlag = false;
            	if (CollectionUtils.isNotEmpty(signRecordList)) {
            		for (SignRecord signRecord : signRecordList) {
            			Integer recordStatus = signRecord.getRecordStatus();
            			//签约确认中
            			if (null != recordStatus && recordStatus.equals(SignRecordConstant.RECORD_STATUS_CONFIRMATION)) {
            				doctorFlag = true;
            				break;
            			}
					}
            	}
            	if (doctorFlag) {
            		continue;
            	}
                Boolean signFlag = (Boolean) os[1];
                Integer doctorId = o1.getDoctorId();
                ConsultSet consultSet = consDao.get(doctorId);
                Doctor d = new Doctor();
                d.setDoctorId(doctorId);
                d.setPhoto(o1.getPhoto());
                d.setGender(o1.getGender());
                d.setName(o1.getName());
                d.setProTitle(o1.getProTitle());
                d.setIsSign(signFlag);
                d.setOrgan(o1.getOrgan());
                d.setProfession(o1.getProfession());
                d.setHaveAppoint(doctorDao.getRealTimeDoctorHaveAppointStatus(doctorId, 1).getHaveAppoint());
                d.setDomain(o1.getDomain());
                d.setTeams(o1.getTeams());
                d.setRating(o1.getRating());
                d.setVirtualDoctor(o1.getVirtualDoctor());
                map.put("doctor", d);
                map.put("consultSet", consultSet);
                results.add(map);
            }
        }
        return results;
    }

    /**
     * 个性化获取有效签约或关注医生列表（供 signOrRelationDoctors）
     *
     * @param mpiId
     * @param organs
     * @param start
     * @param limit
     * @param mark   0签约，1关注
     * @return
     */
    public List<Object[]> findDoctorsInUnitOrgan(final String mpiId, final List<Integer> organs, final Integer start, final int limit, final int mark) {
        HibernateStatelessResultAction<List<Object[]>> action = new AbstractHibernateStatelessResultAction<List<Object[]>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder("select d,r.familyDoctorFlag From RelationDoctor r,Doctor d where r.doctorId=d.doctorId and mpiId=:mpiId and d.status=1");
                if (organs != null && organs.size() > 0) {
                    hql.append(" and (");
                    for (Integer i : organs) {
                        hql.append("d.organ=").append(i).append(" or ");
                    }
                    hql = new StringBuilder(hql.substring(0, hql.length() - 3)).append(")");
                }
                if (mark == 0) {
                    hql.append(" and(r.relationType=0 and r.startDate<=now() and r.endDate>=now())");
                } else {
                    hql.append(" and r.relationType=1");
                }

                //2016-11-21 15:02:24  zhangx 由于该情况产生是扫码关注的医生批量插入到数据库，关注时间relationDate相同,因此再加上relationDoctorId进行排序
                //6717 【扫码关注】用户未登录情况下扫码关注多个医生后，再进行登录，登录之后的首页我关注的医生排序和未登录时的排序不一致【见附件】
                hql.append(" order by r.relationDate desc,r.relationDoctorId desc");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("mpiId", mpiId);
                if (start != null && start >= 0) {
                    q.setFirstResult(start);
                }
                q.setMaxResults(limit);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 个性化获取患者所有签约记录（供 signOrRelationDoctors）
     *
     * @param mpiId
     * @param organs
     * @return
     */
    public Long getAllSignNumByUnit(final String mpiId, final List<Integer> organs) {
        HibernateStatelessResultAction<Long> action = new AbstractHibernateStatelessResultAction<Long>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder("select count(*) From RelationDoctor r,Doctor d where r.doctorId=d.doctorId and mpiId=:mpiId and d.status=1 and(r.relationType=0 and r.startDate<=now() and r.endDate>=now())");
                if (organs != null && organs.size() > 0) {
                    hql.append(" and (");
                    for (Integer i : organs) {
                        hql.append("d.organ=").append(i).append(" or ");
                    }
                    hql = new StringBuilder(hql.substring(0, hql.length() - 3)).append(")");
                }
                Query q = ss.createQuery(hql.toString());
                q.setParameter("mpiId", mpiId);
                setResult((Long) q.uniqueResult());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 获取医生关注患者的关系
     *
     * @param mpiId
     * @param doctorId
     * @return
     */
    @RpcService
    @DAOMethod(sql = "from RelationDoctor where mpiId=:mpiId and doctorId=:doctorId and (relationType=0 or relationType=2)")
    public abstract RelationDoctor getRelationDoc(
            @DAOParam("mpiId") String mpiId,
            @DAOParam("doctorId") Integer doctorId);

    /**
     * 检测某患者的家庭成员是不是有签约医生
     * @param selfMpi
     * @return
     */
    public boolean haveSignDoctorForFamily(final String selfMpi){
        HibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                //上海六院就诊人改造-zhangx 添加自己为就诊人，删除改成软删除，不直接删除数据
                StringBuilder hql = new StringBuilder("select count(1) From RelationDoctor r,FamilyMember m where m.memberMpi=r.mpiId and m.mpiid=:mpiId and  m.memeberStatus=1 and m.relation>0 and (r.relationType=0 and r.startDate<=now() and r.endDate>=now())");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("mpiId", selfMpi);
                Long count = (Long)q.uniqueResult();
                setResult(count>0);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 查询患者管理医生关注病人的数量
     * @param doctorId
     * @return
     */
    @RpcService
    public Map<String,Long> getPatientNum(Integer doctorId){
            SignRecordDAO signRecordDAO=DAOFactory.getDAO(SignRecordDAO.class);
            RelationLabelDAO relationLabelDAO=DAOFactory.getDAO(RelationLabelDAO.class);
            Map<String,Long>results=new HashMap<>();
            //1.获取待处理签约申请数量
            Long toDealSignApply=signRecordDAO.getCountSignList(doctorId);
            results.put("新的签约申请",toDealSignApply);
            //2.获取所有患者的总数
            Long allPatient=this.getAllPatientNumByDoctorId(doctorId,0);
            results.put("全部患者",allPatient);
            //3.获取签约患者数量
            Long signPatient=this.getSignPatientNumByDoctorIdNew(doctorId);
            results.put("签约患者",signPatient);
            //4.未标记患者数量
            Long noLabelPatient=this.getNoLabelPatientNumByDoctorId(doctorId,0);
            results.put("未添加标签",noLabelPatient);
            //5.查询特定标签患者数量(先获取该医生所拥有的所有标签在迭代查询)
            List<String>labels=relationLabelDAO.findLabelNamesByDoctorId(doctorId);
            if(labels!=null){
            for (String label:labels){
                Long sum=this.getPatientNumByLabelAndDoctorId(doctorId,label,0);
                results.put(label,sum);
             }
            }
             return results;
    }

    //筛选签约日期即将到期（运营平台设置距离到期的月份数），且医院打开了签约开关的签约记录列表
    @DAOMethod(sql = "SELECT a FROM RelationDoctor a,Doctor b,OrganConfig c WHERE a.doctorId = b.doctorId AND b.organ = c.organId AND a.familyDoctorFlag = 1 AND a.remindPreSign=0 AND a.endDate > NOW() AND TIMESTAMPDIFF(MONTH, NOW(), a.endDate) <=c.signingAhead AND c.canSign = 1",limit = 0)
    public abstract List<RelationDoctor> queryMpiList();


    @DAOMethod(sql = "SELECT CASE WHEN (TIMESTAMPDIFF(MONTH, NOW(), EndDate) < :months) THEN 1 ELSE 0 END FROM RelationDoctor WHERE mpiId = :mpiId AND doctorId = :doctorId AND familyDoctorFlag = 1")
    public abstract int getMonthFlag(@DAOParam("months")Integer months,@DAOParam("mpiId") String mpiId,@DAOParam("doctorId") int doctorId);

    @RpcService
    @DAOMethod(sql = "update RelationDoctor set remindPreSign=1 where relationDoctorId =:relationDoctorId")
    public abstract void updateSignRemind(@DAOParam("relationDoctorId") Integer relationDoctorId);
}
