package eh.bus.dao;

import com.google.common.collect.Ordering;
import com.ngari.his.appoint.mode.HisDoctorParamTO;
import com.ngari.his.appoint.service.IAppointHisService;
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
import ctd.util.AppContextHolder;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.ServiceType;
import eh.base.dao.DoctorDAO;
import eh.base.dao.EmploymentDAO;
import eh.base.dao.HisServiceConfigDAO;
import eh.base.dao.OrganDAO;
import eh.base.service.BusActionLogService;
import eh.entity.base.Doctor;
import eh.entity.base.Employment;
import eh.entity.base.HisServiceConfig;
import eh.entity.base.Organ;
import eh.entity.bus.*;
import eh.entity.his.HisDoctorParam;
import eh.entity.his.TempTable;
import eh.entity.his.hisCommonModule.HisAppointSchedule;
import eh.entity.his.push.callNum.PushResponseModel;
import eh.op.auth.service.SecurityService;
import eh.remote.IHisServiceInterface;
import eh.task.executor.SaveHisAppointDepartExecutor;
import eh.task.executor.SaveHisAppointSourceExecutor;
import eh.util.DBParamLoaderUtil;
import eh.util.DBUtil;
import eh.util.RpcAsynchronousUtil;
import eh.util.RpcServiceInfoUtil;
import eh.utils.DateConversion;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import org.joda.time.LocalDate;
import org.springframework.util.ObjectUtils;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public abstract class AppointSourceDAO extends
        HibernateSupportDelegateDAO<AppointSource> {
    private static final Log logger = LogFactory.getLog(AppointSourceDAO.class);
    private AppointmentRequest request;

    public AppointSourceDAO() {
        super();
        this.setEntityName(AppointSource.class.getName());
        this.setKeyField("appointSourceId");
    }

    /**
     * 根据时间字符串 和指定格式 返回日期
     *
     * @param dateStr
     * @param format
     * @return
     */
    public static Date getCurrentDate(String dateStr, String format) {
        Date currentTime = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat(format);
        try {
            currentTime = formatter.parse(dateStr);
        } catch (ParseException e) {
            return null;
        }
        return currentTime;
    }

    public static Date getDateAftXDays(Date d, int num) {
        Calendar now = Calendar.getInstance();
        now.setTime(d);
        now.set(Calendar.DATE, now.get(Calendar.DATE) + num);
        return now.getTime();
    }

    @RpcService
    @DAOMethod
    public abstract AppointSource getById(int id);

    @DAOMethod(sql = "select DISTINCT(workDate) from AppointSource where doctorId=:doctorId and stopFlag=0 and workDate>=:startDate and workDate <=:endDate order by workDate")
    public abstract List<Date> findWorkDateListByDoctorId(@DAOParam("doctorId") Integer doctorId, @DAOParam("startDate") Date startDate, @DAOParam("endDate") Date endDate);

    /**
     * 服务名:查询医生分时段号源服务
     *
     * @param doctorId
     * @param sourceType
     * @param workDate
     * @param workType
     * @param organID
     * @param appointDepartCode
     * @param price
     * @return
     * @throws DAOException
     * @author yxq
     */
    @RpcService
    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<AppointSource> queryDoctorSource(final int doctorId,
                                                 final int sourceType, final Date workDate, final int workType,
                                                 final int organID, final String appointDepartCode,
                                                 final Double price) throws DAOException {
        List<AppointSource> asList;
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql;
                Query query ;
                StringBuilder h1 = new StringBuilder("from AppointSource where doctorId=:doctorId and ");
                StringBuilder workTypeStr = new StringBuilder("sourceType=:sourceType and ");
                StringBuilder h2 = new StringBuilder("workDate=:workDate and workType=:workType and organID=:organID and appointDepartCode=:appointDepartCode and "
                        + "sourceNum>usedNum and stopFlag=0 "
                        + "and (cloudClinic<>1 or cloudClinic is null)"
                        + " order by startTime asc,orderNum");
                if (sourceType != 2){
                    hql = h1.append(workTypeStr).append(h2);
                }else{
                    hql = h1.append(h2);
                }
                query = ss.createQuery(hql.toString());
                if(sourceType != 2) {
                    query.setInteger("sourceType", sourceType);
                }
                query.setInteger("doctorId", doctorId);
                query.setTimestamp("workDate", workDate);
                query.setInteger("workType", workType);
                query.setInteger("organID", organID);
                query.setString("appointDepartCode", appointDepartCode);
                List<AppointSource> temp = query.list();
                AppointDepartDAO AppointDepartDAO = DAOFactory.getDAO(AppointDepartDAO.class);
                for (int i = 0; i < temp.size(); i++) {
                    temp.get(i).setAppointDepartId(
                            AppointDepartDAO
                                    .getIdByOrganIdAndAppointDepartCode(
                                            organID, temp.get(i)
                                                    .getAppointDepartCode()));
                }
                setResult(temp);

            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        asList = (List) action.getResult();
        List<AppointSource> results = new ArrayList<AppointSource>();
        for (AppointSource as : asList) {
            as.setWeek(DateConversion.getWeekOfDate(as.getWorkDate()));
            results.add(as);
        }
        return results;
    }

    /**
     * 服务名:云门诊号源分时段查询
     *
     * @param doctorId
     * @param sourceType
     * @param workDate
     * @param workType
     * @param organID
     * @param appointDepartCode
     * @param price
     * @return
     * @throws DAOException
     * @author yxq
     */
    @RpcService
    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<AppointSource> queryDoctorSourceCloudAll(final int doctorId,
                                                         final int sourceType, final Date workDate, final int workType,
                                                         final int organID, final String appointDepartCode)
            throws DAOException {
        List<AppointSource> asList;
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql;
                Query query;
                Date now = new Date();
                if (sourceType == 2) {
                    hql = new StringBuilder(
                            "from AppointSource where doctorId=:doctorId and "
                                    + "workDate=:workDate and workType=:workType and organID=:organID and appointDepartCode=:appointDepartCode and startTime >:startTime and "
                                    + "sourceNum>usedNum and stopFlag=0 "
                                    + "and cloudClinic = 1 and startTime>=:startTime  "
                                    + " order by startTime asc");//
                    query = ss.createQuery(hql.toString());
                } else {
                    hql = new StringBuilder(
                            "from AppointSource where doctorId=:doctorId and sourceType=:sourceType and "
                                    + "workDate=:workDate and workType=:workType and organID=:organID and appointDepartCode=:appointDepartCode and startTime >:startTime and "
                                    + "sourceNum>usedNum and stopFlag=0 "
                                    + "and cloudClinic = 1  "
                                    + " order by startTime asc");
                    query = ss.createQuery(hql.toString());
                    query.setInteger("sourceType", sourceType);
                }
                query.setDate("startTime", now);
                query.setInteger("doctorId", doctorId);
                query.setTimestamp("workDate", workDate);
                query.setInteger("workType", workType);
                query.setInteger("organID", organID);
                query.setString("appointDepartCode", appointDepartCode);
                List<AppointSource> temp = query.list();
                AppointDepartDAO AppointDepartDAO = DAOFactory
                        .getDAO(AppointDepartDAO.class);
                Date time = DateConversion.getDateAftHour(new Date(), 1);
                List<AppointSource> res = new ArrayList<AppointSource>();
                for (int i = 0; i < temp.size(); i++) {
                    if (temp.get(i).getStartTime().before(time)) {
                        continue;
                    }
                    temp.get(i).setAppointDepartId(AppointDepartDAO.getIdByOrganIdAndAppointDepartCode(
                            organID, temp.get(i).getAppointDepartCode()));
                    res.add(temp.get(i));
                }
                setResult(res);

            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        asList = (List) action.getResult();
        return asList;
    }

    /**
     * 可用接诊时段号源查询服务
     *
     * @param inAddrArea
     * @param inOrganId
     * @param outDoctorId
     * @param outWorkDate
     * @param workType
     * @return
     * @author LF
     */
    @RpcService
    public Map<String, Object> queryDoctorSourceCloud(final String inAddrArea,
                                                      final Integer inOrganId, final Integer outDoctorId,
                                                      final Date outWorkDate, final Integer workType) {
        //出诊方号源
        final List<AppointSource> datas = findAppointSource(outDoctorId,
                outWorkDate, workType);
        //过滤当天当前时间一小时前的号源
        Date time = DateConversion.getDateAftHour(new Date(), 1);
        final List<AppointSource> datasRes = new ArrayList<AppointSource>();
        for (AppointSource s : datas) {
            int cloudclinic = s.getCloudClinic();
            if (cloudclinic == 1 && !s.getStartTime().before(time)) {
                datasRes.add(s);
            }

        }
        if (datasRes == null || datasRes.size() < 1) {
            return null;
        }
        //出诊方starttime
        final List<Date> dates = new ArrayList<Date>();
        for (AppointSource data : datasRes) {
            dates.add(data.getStartTime());
        }

        //匹配的接诊方号源
        List<AppointSource> appointSources = new ArrayList<AppointSource>();
        if (inOrganId != null) {
            appointSources = findAppointSourceCloud(inOrganId,
                    outDoctorId, dates);
        } else {
            appointSources = findAppointSourceCloudArea(inAddrArea,
                    outDoctorId, dates);
        }

        List<AppointSourceListAndDoctor> asld = new ArrayList<AppointSourceListAndDoctor>();
        //接诊
        for (int i = 0; i < appointSources.size(); i++) {
            AppointSource appSource = appointSources.get(i);
            Integer doctorId = appSource.getDoctorId();
            boolean exists = false;
            for (AppointSourceListAndDoctor as : asld) {
                Doctor doc = as.getDoctor();
                if (doc != null && doc.getDoctorId().equals(doctorId)) {
                    exists = true;
                    List<AppointSource> asList = as.getAppointSource();
                    asList.add(appSource);
                    break;
                }
            }
            if (exists == false) {
                AppointSourceListAndDoctor asl = new AppointSourceListAndDoctor();
                Doctor doctor = DAOFactory.getDAO(DoctorDAO.class).get(doctorId);
                asl.setDoctor(doctor);
                List<AppointSource> list = new ArrayList<AppointSource>();
                list.add(appSource);
                asl.setDoctor(doctor);
                asl.setAppointSource(list);
                asld.add(asl);
            }
        }
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("outSource", datasRes);
        result.put("inSource", asld);

        // 2016-6-16 luf:app3.2版需求，若无匹配则显示全部就诊号源
        //如果没有匹配上，但是申请医生有远程号源，则outsource为空
//				if(appointSources==null || appointSources.size()==0){
//					List<Integer> l = findCloudSource(outDoctorId);
//					if(l!=null && l.size()>0){//申请医生有远程排班号源
//						result.remove("outSource");
//					}
//				}

        return result;
    }

    @DAOMethod(sql = "select 1 from AppointSource where workDate>now() and doctorId=:doctorId and cloudClinic=1 and  sourceNum>usedNum and stopFlag =0  ")
    public abstract List<Integer> findCloudSource(@DAOParam("doctorId") Integer doctorId);

    /**
     * 供 可用接诊时段号源查询服务 调用
     *
     * @param inAddrArea
     * @param dates
     * @return
     * @author LF
     */
    @DAOMethod(sql = "select a from AppointSource a,Organ o where o.addrArea=:inAddrArea AND a.organId=o.organId AND cloudClinic>0 AND (cloudClinicType=0 OR cloudClinicType=2) AND sourceNum>usedNum AND doctorId <> :doctorId AND startTime in :startTime order by a.organId,a.doctorId,a.startTime")
    public abstract List<AppointSource> findAppointSourceCloudArea(
            @DAOParam("inAddrArea") String inAddrArea,
            @DAOParam("doctorId") Integer doctorId,
            @DAOParam("startTime") List<Date> dates);

    /**
     * 供 可用接诊时段号源查询服务 调用
     *
     * @param doctorId
     * @param organId
     * @param dates
     * @return
     * @author LF
     */
    @DAOMethod(sql = "FROM AppointSource WHERE organId=:organId AND cloudClinic>0 AND (cloudClinicType=0 OR cloudClinicType=2) AND sourceNum>usedNum AND doctorId <> :doctorId AND startTime in :startTime order by organId,doctorId,startTime")
    public abstract List<AppointSource> findAppointSourceCloud(
            @DAOParam("organId") Integer organId,
            @DAOParam("doctorId") Integer doctorId,
            @DAOParam("startTime") List<Date> dates);

    /**
     * 查询医生的号源信息( 供 可用接诊时段号源查询服务 调用)
     *
     * @param doctorId
     * @param workDate
     * @param workType
     * @return
     * @author LF
     */
    @DAOMethod(sql = "FROM AppointSource WHERE doctorId=:doctorId AND workDate=:workDate AND workType=:workType AND sourceNum>usedNum AND stopFlag=0 AND cloudClinic>0 AND cloudClinicType<>2")
    public abstract List<AppointSource> findAppointSource(
            @DAOParam("doctorId") Integer doctorId,
            @DAOParam("workDate") Date workDate,
            @DAOParam("workType") Integer workType);

    /**
     * 查询医生的可用号源信息(上午)
     *
     * @param doctorId
     * @param workDate
     * @param workType
     * @return
     * @author AngryKitty
     */
    @DAOMethod(sql = "FROM AppointSource WHERE doctorId=:doctorId AND workDate=:workDate AND startTime>=:startTime  AND sourceNum>usedNum AND stopFlag=0 AND HOUR(startTime)<12 AND stopFlag=0 order by orderNum")
    public abstract List<AppointSource> findAppointSourceByDocAndWorkDateAM(
            @DAOParam("doctorId") Integer doctorId,
            @DAOParam("workDate") Date workDate,
            @DAOParam("startTime") Date startTime);

    /**
     * 查询医生的可用号源信息(下午)
     *
     * @param doctorId
     * @param workDate
     * @param workType
     * @return
     * @author AngryKitty
     */
    @DAOMethod(sql = "FROM AppointSource WHERE doctorId=:doctorId AND workDate=:workDate AND startTime>=:startTime  AND sourceNum>usedNum AND stopFlag=0 AND  HOUR(startTime)>=12 AND stopFlag=0 order by orderNum")
    public abstract List<AppointSource> findAppointSourceByDocAndWorkDatePM(
            @DAOParam("doctorId") Integer doctorId,
            @DAOParam("workDate") Date workDate,
            @DAOParam("startTime") Date startTime);

    /**
     * 更新号源已用数量服务-jhc
     *
     * @param usedNum
     * @param appointSourceId
     */
    @RpcService
    @DAOMethod(sql = "update AppointSource set usedNum=1 where appointSourceID=:appointSourceID")
    public abstract void updateUseNumBySourceID(
            @DAOParam("appointSourceID") int appointSourceId);

    /**
     * 查询医生日期号源服务--hyj
     *
     * @param doctorId   --医生编号
     * @param sourceType --号源类别
     * @return
     */
    @RpcService
    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<DoctorDateSource> totalByDoctorDate(final int doctorId,
                                                    final int sourceType) throws DAOException {
        List<DoctorDateSource> List;
        HisServiceConfigDAO configDAO = DAOFactory.getDAO(HisServiceConfigDAO.class);
        final List<Integer> organs = configDAO.findTodayOrgans();
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String dateParam = "and workDate>=:currentTime ";
                if (organs != null && !organs.isEmpty()) {
                    dateParam = "and (workDate>=:currentTime or (organId in(:organs) and startTime>=:currentTime)) ";
                }

                Query query = null;
                StringBuffer hql = new StringBuffer();
                if (sourceType == 2) {// 医生端预约默认取全部，现在医生app传2
                    hql.append("select new eh.entity.bus.DoctorDateSource(organId,appointDepartCode,appointDepartName,doctorId,workDate,sum(sourceNum-usedNum),price,workType,sourceLevel,cloudClinic) from AppointSource where doctorId=:doctorId ")
                            .append(dateParam).append("and stopFlag=0 group by organId,appointDepartCode,workDate,price,workType,sourceLevel order by workDate,workType");
                    query = ss.createQuery(hql.toString());
                    query.setInteger("doctorId", doctorId);
                    query.setParameter("currentTime", new Date());
                    if (organs != null && !organs.isEmpty()) {
                        query.setParameterList("organs", organs);
                    }
                } else {
                    hql.append("select new eh.entity.bus.DoctorDateSource(organId,appointDepartCode,appointDepartName,doctorId,workDate,sum(sourceNum-usedNum),price,workType,sourceLevel,cloudClinic) from AppointSource where doctorId=:doctorId and sourceType=:sourceType ").append(dateParam).append("and stopFlag=0 group by organId,appointDepartCode,workDate,price,workType,sourceLevel order by workDate");
                    query = ss.createQuery(hql.toString());
                    query.setInteger("doctorId", doctorId);
                    query.setInteger("sourceType", sourceType);
                    query.setParameter("currentTime", new Date());
                    if (organs != null && !organs.isEmpty()) {
                        query.setParameterList("organs", organs);
                    }
                }
                List<DoctorDateSource> temp = query.list();
                checkHaveAppoint(temp, doctorId);
                setResult(temp);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List = (List) action.getResult();
        return List;
    }

    /**
     * 更新号源已用数量服务--hyj
     *
     * @param usedNum
     * @param appointSourceId
     */
    @RpcService
    @DAOMethod(sql = "update AppointSource set usedNum=:usedNum,orderNum=:orderNum where appointSourceID=:appointSourceID")
    public abstract void updateUsedNum(@DAOParam("usedNum") int usedNum,
                                       @DAOParam("orderNum") int orderNum,
                                       @DAOParam("appointSourceID") int appointSourceId);

    /**
     * 预约取消后更新号源已用数量服务--hyj
     *
     * @param usedNum
     * @param appointSourceId
     */
    @RpcService
    @DAOMethod(sql = "update AppointSource set usedNum=:usedNum where appointSourceID=:appointSourceID and workDate > now()")
    public abstract void updateUsedNumAfterCancel(
            @DAOParam("usedNum") int usedNum,
            @DAOParam("appointSourceID") int appointSourceId);

    @RpcService
    @DAOMethod(sql = "update AppointSource set usedNum=:usedNum where appointSourceID=:appointSourceID")
    public abstract void updateUsedNumByAppointSourceId(
            @DAOParam("usedNum") int usedNum,
            @DAOParam("appointSourceID") int appointSourceId);

    /**
     * 按医生统计号源服务--hyj
     *
     * @param oragnID
     * @param appointDepartCode
     * @param sourceType
     * @return
     */
    @RpcService
    @DAOMethod(sql = "select new eh.entity.bus.DoctorSource(doctorId,sum(sourceNum-usedNum))"
            + " from AppointSource where organId=:oragnID and appointDepartCode=:appointDepartCode and "
            + "sourceType=:sourceType group by doctorId")
    public abstract List<DoctorSource> findTotalByDoctor(
            @DAOParam("oragnID") int oragnID,
            @DAOParam("appointDepartCode") String appointDepartCode,
            @DAOParam("sourceType") Integer sourceType);

    @RpcService(timeout = 50)
    public void saveAppointSources(final List<AppointSource> sourceList)
            throws DAOException {
        SaveHisAppointSourceExecutor executor = new SaveHisAppointSourceExecutor();
        executor.execute(sourceList);
        /*
         * for(AppointSource source:sourceList){ saveAppointSource(source); }
		 */
    }

    /**
     * 预约号源增加服务--hyj
     *
     * @param appointSource
     */
    @SuppressWarnings("rawtypes")
    @RpcService
    public void saveAppointSource(final AppointSource appointSource)
            throws DAOException {


                if (appointSource.getFromFlag() == null) {
                    appointSource.setFromFlag(0);
                }
                if (appointSource.getOrganId() == null) {
                    throw new DAOException(DAOException.VALUE_NEEDED,
                            "organId is required");
                }
                if (appointSource.getDoctorId() == null) {
                    throw new DAOException(DAOException.VALUE_NEEDED,
                            "doctorId is required");
                }
                if (appointSource.getAppointDepartCode() == null
                        || appointSource.getAppointDepartCode().equals("")) {
                    throw new DAOException(DAOException.VALUE_NEEDED,
                            "appointDepartCode is required");
                }
                if (appointSource.getWorkDate() == null) {
                    throw new DAOException(DAOException.VALUE_NEEDED,
                            "workDate is required");
                }

				/*
                 * * if(appointSource.getWorkType()==null){ throw new
				 * DAOException(
				 * DAOException.VALUE_NEEDED,"workType is required"); }
				 */

                if (appointSource.getSourceType() == null) {
                    throw new DAOException(DAOException.VALUE_NEEDED,
                            "sourceType is required");
                }
                if (appointSource.getStartTime() == null) {
                    throw new DAOException(DAOException.VALUE_NEEDED,
                            "startTime is required");
                }
                if (appointSource.getEndTime() == null) {
                    throw new DAOException(DAOException.VALUE_NEEDED,
                            "endTime is required");
                }
                if (appointSource.getOrderNum() == null) {
                    throw new DAOException(DAOException.VALUE_NEEDED,
                            "orderNum is required");
                }
                if (appointSource.getFromFlag() == null) {
                    appointSource.setFromFlag(0);
                }
                SaveHisAppointDepartExecutor executor = new SaveHisAppointDepartExecutor(appointSource);
                try {
                    executor.execute();
                } catch (Exception e) {
                    logger.error(e);
                }
                saveOrUpdate(appointSource);


    }

    /**
     * 更新号源为停诊
     * @param appointSource
     * @return
     * @auth zhuangyq
     */
    @RpcService
    public Boolean updateAppointSourceToClose(final AppointSource appointSource){
        AbstractHibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {

            @Override
            public void execute(StatelessSession arg0) throws Exception {
                logger.info("设置号源停诊收到信息:"+JSONUtils.toString(appointSource));
                int organId = appointSource.getOrganId();
                String organSchedulingId = appointSource.getOrganSchedulingId();
                String organSourceId = appointSource.getOrganSourceId();
                HisServiceConfigDAO configDAO = DAOFactory.getDAO(HisServiceConfigDAO.class);
                boolean f = configDAO.isServiceEnable(organId, ServiceType.EXISTORGANID);
                if(f){
                    AppointSource old = getAppointSourceOld(organId, organSchedulingId,
                            organSourceId, appointSource.getWorkDate(),
                            appointSource.getOriginalSourceId());
                    if (old != null) {
                        //号源已标志为停诊则不更新
                        if(null!=old.getStopFlag() && 1==old.getStopFlag()){
                            logger.info("号源已标志为停诊则不更新");
                            setResult(true);
                            return;
                        }
                        old.setStopFlag(1);
                        old.setModifyDate(new Date());
                        update(old);
                        logger.info("现有号源更新成停诊");
                        setResult(true);
                    }else{
                        logger.info("找不到需要停诊的号源");
                        setResult(false);
                    }
                }else{
                    logger.info("停诊服务organid不存在");
                }
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    @RpcService
    public Boolean saveOrUpdate(final AppointSource appointSource) {

        AbstractHibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {

            @Override
            public void execute(StatelessSession arg0) throws Exception {
                int organId = appointSource.getOrganId();
                String organSchedulingId = appointSource.getOrganSchedulingId();
                String organSourceId = appointSource.getOrganSourceId();
                AppointSource old = null;
                HisServiceConfigDAO configDAO = DAOFactory.getDAO(HisServiceConfigDAO.class);
                boolean f = configDAO.isServiceEnable(organId, ServiceType.EXISTORGANID);
                if (f) {// 桐乡医院/武汉中心医院
                    old = getAppointSourceOld(organId, organSchedulingId,
                            organSourceId, appointSource.getWorkDate(),
                            appointSource.getOriginalSourceId());
                } else {
                    old = getAppointSource(organId, organSchedulingId,
                            organSourceId, appointSource.getWorkDate());
                }
                if (old != null) {
                    //邵逸夫 和省中医院 如果已存在的号源以停诊 就不再更新
//                    if (OrganConstant.Organ_SZ == organId || OrganConstant.Organ_SYF == organId) {
//                        if(1 == old.getStopFlag().intValue()){
//                            setResult(false);
//                            return;
//                        }
//                    }
                    int usedNum = old.getUsedNum();
                    int newSourceNum = appointSource.getSourceNum();
                    if (newSourceNum < usedNum) {// 如果新号源总数比已用数还要少 则不更新了
                        logger.error("新号源总数比已用数还要少!"
                                + JSONUtils.toString(appointSource));
                        setResult(false);
                    } else {
                        // 邵逸夫可不更新号源数量 默认1
                        // old.setSourceNum(newSourceNum);

                        // 现有号源更新时，也要根据 his更新号源表里重新计算 已用数usedNum 保证两边号源统一
                        // int number=getNewUsedNum(appointSource);
                        /*
                         * if(number+old.getUsedNum()<=old.getSourceNum()){
						 * old.setUsedNum(number+old.getUsedNum());
						 * }else{//说明该号源已用完 old.setUsedNum(old.getSourceNum());
						 * }
						 */
                        if (appointSource.getStopFlag() == null) {
                            appointSource.setStopFlag(0);
                        }
                        // logger.info("现有号源已用数发生变化，需要更新");
                        old.setStopFlag(appointSource.getStopFlag());
                        if (appointSource.getStopFlag().intValue() == 1) {
                            old.setStopFlag(1);
                            old.setModifyDate(new Date());
                            logger.info("现有号源更新成停诊");
                        }

                        old.setModifyDate(new Date());
                        if(appointSource.getPrice()!=null){
                            old.setPrice(appointSource.getPrice());
                        }
                        boolean sourceReal = DAOFactory.getDAO(HisServiceConfigDAO.class).isServiceEnable(organId, ServiceType.SOURCEREAL);
                        //如果是实时号源的则更新
                        if(sourceReal){
                            old.setUsedNum(appointSource.getUsedNum());
                            old.setSourceNum(appointSource.getSourceNum());
                        }
                        Date hisStartTime = appointSource.getStartTime();
                        if(hisStartTime!=null){
                            old.setStartTime(hisStartTime);
                        }
                        String clinicAddr = appointSource.getClinicAddr();
                        if(StringUtils.isNotEmpty(clinicAddr)){
                            old.setClinicAddr(clinicAddr);
                        }
                        Date hisEndTime = appointSource.getEndTime();
                        if(hisEndTime!=null){
                            old.setEndTime(hisEndTime);
                        }
                        Double price=appointSource.getPrice();
                        if(null!=price){
                            old.setPrice(price);
                        }
                        Integer sourceLevel=appointSource.getSourceLevel();
                        if(null!=sourceLevel){
                            old.setSourceLevel(sourceLevel);
                        }
                        update(old);
                        setResult(true);
                    }
                } else {
                    // 新增的号源此处需要从his更新号源表里重新计算 已用数usedNum 保证两边号源统一
                    appointSource.setUsedNum(0);
                    /*
                     * int usedNum=getNewUsedNum(appointSource);
					 * if(usedNum>appointSource.getSourceNum()){
					 * logger.error("已用号源数大于实际号源总数！！"
					 * +JSONUtils.toString(appointSource));
					 * usedNum=appointSource.getSourceNum(); } if(usedNum>0){
					 * logger.info("新增号源时，检查到号源已用数有变化，需更新"); }
					 * appointSource.setUsedNum(usedNum);
					 */
                    if (appointSource.getStopFlag() == null) {
                        appointSource.setStopFlag(0);
                    }
                    appointSource.setCreateDate(new Date());
                    // appointSource.setModifyDate(new Date());
                    save(appointSource);
                    setResult(true);
                }

            }

        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    @RpcService
    @DAOMethod(sql = "from AppointSource where organId=:organId and organSchedulingId=:organSchedulingId and OrganSourceId=:organSourceId and workDate=:workDate ")
    public abstract AppointSource getAppointSource(
            @DAOParam("organId") int organId,
            @DAOParam("organSchedulingId") String organSchedulingId,
            @DAOParam("organSourceId") String organSourceId,
            @DAOParam("workDate") Date workDate);

    @RpcService
    @DAOMethod(sql = "from AppointSource where organId=:organId and organSchedulingId=:organSchedulingId and OrganSourceId=:organSourceId and stopFlag=0")
    public abstract AppointSource getAppointSourceNew(
            @DAOParam("organId") int organId,
            @DAOParam("organSchedulingId") String organSchedulingId,
            @DAOParam("organSourceId") String organSourceId);

    /**
     * 检查医生是否有号源服务
     *
     * @param doctorId
     */
    @RpcService
    public void checkHaveAppoint(List<DoctorDateSource> doctorAllSource,
                                 int doctorId) {
        DoctorDAO DoctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        long doctorSumSource = 0;
        for (int i = 0; i < doctorAllSource.size(); i++) {
            doctorSumSource = doctorSumSource
                    + doctorAllSource.get(i).getSourceNum();
        }
        if (doctorSumSource > 0) {
            DoctorDAO.updateHaveAppointByDoctorId(doctorId, 1);
        } else {
            logger.info("更新成无号源标志:doctorId:" + doctorId);
            DoctorDAO.updateHaveAppointByDoctorId(doctorId, 0);
        }
    }

    public boolean checkIsExistScheduling(final int organId,final int doctorId,
                                          final Integer workType, final Date workDate) {
        AbstractHibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {

            @Override
            public void execute(StatelessSession ss) throws Exception {

                String hql = "";
                Query qu = null;
                if (workType != null) {
                    hql = "select count(*) from AppointSource where organId=:organId and fromFlag=0 and doctorId=:doctorId and (workType=:workType or workType=0) and workDate=date(:workDate) and stopFlag=0";
                    qu = ss.createQuery(hql);
                    qu.setParameter("workType", workType);
                } else {
                    hql = "select count(*) from AppointSource where organId=:organId and fromFlag=0 and doctorId=:doctorId  and workDate=date(:workDate) and stopFlag=0";
                    qu = ss.createQuery(hql);
                }

                qu.setParameter("doctorId", doctorId);
                qu.setParameter("workDate", workDate);
                qu.setParameter("organId", organId);
                Long count = (Long) qu.uniqueResult();
                if (count > 0)
                    setResult(true);
                else
                    setResult(false);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 取可转诊的号源信息
     *
     * @param doctorId
     * @param workType
     * @param workDate
     * @return
     */
    public AppointSource getExistSchedulingSource(final int organId,final int doctorId,
                                                  final Integer workType, final Date workDate) {
        AbstractHibernateStatelessResultAction<AppointSource> action = new AbstractHibernateStatelessResultAction<AppointSource>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = "";
                Query qu = null;
                if (workType != null) {
                    hql = "from AppointSource where organId=:organId and doctorId=:doctorId and (workType=:workType) and workDate=date(:workDate) and stopFlag=0 order by orderNum desc";
                    qu = ss.createQuery(hql);
                    qu.setParameter("workType", workType);
                } else {
                    hql = "from AppointSource where organId=:organId and doctorId=:doctorId  and workDate=date(:workDate) and stopFlag=0 order by orderNum desc";
                    qu = ss.createQuery(hql);
                }
                qu.setParameter("doctorId", doctorId);
                qu.setParameter("workDate", workDate);
                qu.setParameter("organId", organId);
                List<AppointSource> list = qu.list();
                int a = 0, b = 0;
                if (list != null && list.size() > 0) {
                    a = list.get(0).getOrderNum();
                    AppointSource as = list.get(0);
                    as.setOrderNum(as.getOrderNum() + 1);
                    setResult(as);

                }
                // 查询转诊预约记录最大号
                String hql_ar = "from AppointRecord where doctorId=:doctorId "
                        + "and workDate=date(:workDate) and workType=:workType  and transferId!=0 and orderNum!=0 and appointStatus!=2 order by orderNum desc";
                Query qu_ar = ss.createQuery(hql_ar);
                qu_ar.setParameter("doctorId", doctorId);
                qu_ar.setParameter("workDate", workDate);
                qu_ar.setParameter("workType", workType);
                // qu_ar.setParameter("appointDepartId", appointDepartCode);

                List<AppointRecord> list_ar = qu_ar.list();
                if (list_ar != null && list_ar.size() > 0) {
                    AppointRecord ar = list_ar.get(0);
                    b = ar.getOrderNum();
                    if (b > a) {
                        AppointSource as = new AppointSource();
                        as.setOrganSchedulingId(ar.getOrganSchedulingId());
                        as.setAppointDepartCode(ar.getAppointDepartId());
                        as.setAppointDepartName(ar.getAppointDepartName());
                        as.setDoctorId(ar.getDoctorId());
                        as.setOrderNum(ar.getOrderNum() + 1);
                        as.setWorkDate(ar.getWorkDate());
                        as.setStartTime(ar.getStartTime());
                        as.setEndTime(ar.getEndTime());
                        as.setWorkType(ar.getWorkType());
                        setResult(as);
                    }
                }

            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    public int getNewUsedNum(final AppointSource source) {
        int newUsedNum = 0;
        HisAppointRecordDAO dao = DAOFactory.getDAO(HisAppointRecordDAO.class);
        HisAppointRecord ar = dao.getHisAppoitRecord(source.getOrganId(),
                source.getOrganSchedulingId(), source.getOrganSourceId(),
                source.getWorkDate());
        if (ar != null)
            newUsedNum = ar.getNumber();
        return newUsedNum;
    }

	/*
     * @RpcService
	 *
	 * @DAOMethod(sql=
	 * "update AppointSource set stopFlag=1 where organId=:organId and organSchedulingId=:organSchedulingId"
	 * ) public abstract void updateStopFlagForHisFailed(@DAOParam("organId")int
	 * organId,@DAOParam("organSchedulingId")String organSchedulingId);
	 */

    /**
     * 按日期期限查询医生是否有号源
     *
     * @param doctorId
     * @param sourceType
     * @param workDateKey
     * @return
     * @author hyj
     */
    @RpcService
    public Doctor checkAppointSourceByWorkDateKey(final int doctorId,
                                                  final int sourceType, final int workDateKey) {

        HibernateStatelessResultAction<Doctor> action = new AbstractHibernateStatelessResultAction<Doctor>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                Query query = null;
                String hql = null;
                if (sourceType == 2) {// 医生端预约默认取全部，现在医生app传2
                    hql = "select distinct d from AppointSource a,Doctor d where a.doctorId=:doctorId and a.workDate>=:currentTime and a.doctorId=d.doctorId and DAY(a.workDate)-DAY(NOW())<=:workDateKey and a.sourceNum>a.usedNum";
                    query = ss.createQuery(hql);
                    query.setInteger("doctorId", doctorId);
                    query.setInteger("workDateKey", workDateKey);
                    query.setParameter("currentTime", new Date());
                } else {
                    hql = "select distinct d from AppointSource a,Doctor d where a.doctorId=:doctorId and a.sourceType=:sourceType and a.workDate>=:currentTime and a.doctorId=d.doctorId and DAY(a.workDate)-DAY(NOW())<=:workDateKey and a.sourceNum>a.usedNum";
                    query = ss.createQuery(hql);
                    query.setInteger("doctorId", doctorId);
                    query.setInteger("sourceType", sourceType);
                    query.setInteger("workDateKey", workDateKey);
                    query.setParameter("currentTime", new Date());
                }
                Doctor temp = (Doctor) query.uniqueResult();
                setResult(temp);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 保存his预约记录,用于更新号源模板
     *
     * @param sourceList
     * @throws DAOException
     */
    @RpcService
    public void saveAppointRecords(final List<TempTable> sourceList)
            throws DAOException {
        TempTableDAO dao = DAOFactory.getDAO(TempTableDAO.class);
        dao.saveTempTable(sourceList);
    }

    /**
     * 上下午排班更新成停诊
     *
     * @param organId
     * @param organSchedulingId
     * @param workType
     */
    @RpcService
    @DAOMethod(sql = "update AppointSource set stopFlag=1,modifyDate=NOW() where organId=:organId and organSchedulingId=:organSchedulingId and workType=:workType and stopFlag=0")
    public abstract void updateStopFlagForHisFailed(
            @DAOParam("organId") int organId,
            @DAOParam("organSchedulingId") String organSchedulingId,
            @DAOParam("workType") int workType);

    /**
     * 排班删除时更新号源停诊标记
     *
     * @param organId
     * @param organSchedulingId
     * @param stopFlag
     * @author hyj
     */
    @RpcService
    @DAOMethod(sql = "update AppointSource set stopFlag=:stopFlag,modifyDate=NOW() where organId=:organId and organSchedulingId=:organSchedulingId and workDate>=CURDATE()")
    public abstract void updateStopFlagForSchedulingDelete(
            @DAOParam("organId") int organId,
            @DAOParam("organSchedulingId") String organSchedulingId,
            @DAOParam("stopFlag") int stopFlag);

    /**
     * 排班停诊或开诊时更新号源停诊标记
     *
     * @param organId
     * @param organSchedulingId
     * @param stopFlag
     * @param workType
     * @author hyj
     */
    @RpcService
    @DAOMethod(sql = "update AppointSource set stopFlag=:stopFlag,modifyDate=NOW() where organId=:organId and organSchedulingId=:organSchedulingId and workDate>=CURDATE() and workType=:workType")
    public abstract void updateStopFlagForSchedulingOpenOrStop(
            @DAOParam("organId") int organId,
            @DAOParam("organSchedulingId") String organSchedulingId,
            @DAOParam("stopFlag") int stopFlag,
            @DAOParam("workType") int workType);

    /**
     * 全天停诊或开诊
     *
     * @param organId
     * @param organSchedulingId
     * @param stopFlag
     */
    @DAOMethod(sql = "update AppointSource set stopFlag=:stopFlag,modifyDate=NOW() where organId=:organId and organSchedulingId=:organSchedulingId and workDate>=CURDATE()")
    public abstract void updateStopFlagForSchedulingOpenOrStopAllDay(
            @DAOParam("organId") int organId,
            @DAOParam("organSchedulingId") String organSchedulingId,
            @DAOParam("stopFlag") int stopFlag);

    /**
     * 号源删除时更新号源停诊标记
     *
     * @param organId
     * @param originalSourceId
     * @param stopFlag
     * @author hyj
     */
    @RpcService
    @DAOMethod(sql = "update AppointSource set stopFlag=:stopFlag,modifyDate=NOW() where organId=:organId and originalSourceId=:originalSourceId and workDate>=CURDATE()")
    public abstract void updateStopFlagForSourceDelete(
            @DAOParam("organId") int organId,
            @DAOParam("originalSourceId") String originalSourceId,
            @DAOParam("stopFlag") int stopFlag);

    /**
     * 检查并更新医生是否有号源标记
     *
     * @author hyj
     */
    public void checkAndUpateHaveAppoint() {
        HibernateStatelessResultAction<Doctor> action = new AbstractHibernateStatelessResultAction<Doctor>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                // 查询今天之后存在有效号源的医生列表
                Query query = null;
                String hql = "select DISTINCT doctorId from AppointSource where workDate>CURDATE() and stopFlag=0 and sourceNum>usedNum";
                query = ss.createQuery(hql);
                List<Integer> doctorIds = query.list();
                DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                // 将这些医生的haveAppoint字段更新成有号源
                if (doctorIds.size() > 0) {
                    doctorDAO.updateHaveAppoint(1, doctorIds);
                }
                // 查询存在今天号源且今天以后无号源的医生列表
                hql = "select DISTINCT doctorId from AppointSource where workDate=CURDATE() and doctorId not in"
                        + "(select DISTINCT doctorId from AppointSource where workDate>CURDATE() and stopFlag=0 and sourceNum>usedNum)";
                query = ss.createQuery(hql);
                doctorIds = query.list();
                // 将这些医生的haveAppoint字段更新成无号源
                if (doctorIds.size() > 0) {
                    doctorDAO.updateHaveAppoint(0, doctorIds);
                }
                // 查询大于今天且号源全部停诊的医生列表
                hql = "select DISTINCT doctorId from AppointSource where workDate>CURDATE() and stopFlag=1 "
                        + "and doctorId not in(select DISTINCT doctorId from AppointSource "
                        + "where workDate>CURDATE() and stopFlag=0 and sourceNum>usedNum)";
                query = ss.createQuery(hql);
                doctorIds = query.list();
                // 将这些医生的haveAppoint字段更新成无号源
                if (doctorIds.size() > 0) {
                    doctorDAO.updateHaveAppoint(0, doctorIds);
                }

            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
    }

    /**
     * 调用实时号源实现异步查询
     *
     * @param doctorId
     * @return
     * @throws Exception
     */
    @RpcService
    public List<DoctorDateSource> hisByDoctorData(final int doctorId, final int sourceType) throws DAOException {
        List<DoctorDateSource> List;

        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doctor = doctorDAO.get(doctorId);
        final Integer organId = doctor.getOrgan();
        EmploymentDAO dao = DAOFactory.getDAO(EmploymentDAO.class);
        Employment employ = dao.getPrimaryEmpByDoctorId(doctorId);
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        Organ o = organDAO.getByOrganId(organId);
        String jobNum = employ.getJobNumber();
        HisDoctorParam doctorParam = new HisDoctorParam();
        doctorParam.setJobNum(jobNum);
        doctorParam.setDoctorId(doctorId);
        doctorParam.setOrganizeCode(o.getOrganizeCode());
        doctorParam.setOrganID(o.getOrganId());

        HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
        boolean f = hisServiceConfigDao.isServiceEnable(organId, ServiceType.SOURCEREAL);
        logger.info(organId + "sourceReal:"+f);
        if (f) {
            new RpcAsynchronousUtil(doctorParam, organId).obtainNowSource();
        }
        return null;
    }

    /**
     * 查询医生日期号源服务--新增云门诊判断
     *
     * @param doctorId   --医生编号
     * @param sourceType --号源类别
     * @return
     * @author hyj
     */
    @RpcService
    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<DoctorDateSource> totalByDoctorDateAndCloudClinic(final int doctorId, final int sourceType) throws DAOException {
        List<DoctorDateSource> List;

        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doctor = doctorDAO.get(doctorId);
        final Integer organId = doctor.getOrgan();
        EmploymentDAO dao = DAOFactory.getDAO(EmploymentDAO.class);
        Employment employ = dao.getPrimaryEmpByDoctorId(doctorId);
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        Organ o = organDAO.getByOrganId(organId);
        String jobNum = employ.getJobNumber();
        HisDoctorParam doctorParam = new HisDoctorParam();
        doctorParam.setJobNum(jobNum);
        doctorParam.setDoctorId(doctorId);
        doctorParam.setOrganizeCode(o.getOrganizeCode());
        doctorParam.setOrganID(o.getOrganId());

        HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
        boolean f = hisServiceConfigDao.isServiceEnable(organId, ServiceType.SOURCEREAL);
        if (f) {
            new RpcAsynchronousUtil(doctorParam, organId).obtainNowSource();

        }
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                Query query = null;
                String hql = null;

                if (sourceType == 2) {// 医生端预约默认取全部，现在医生app传2
                    hql = "select new eh.entity.bus.DoctorDateSource(organId,appointDepartCode,appointDepartName,doctorId,workDate,sum(sourceNum-usedNum),price,workType,sourceLevel,cloudClinic,startTime) from AppointSource " +
                            "where doctorId=:doctorId  and workDate>=:currentTime and stopFlag=0 and (cloudClinic=0  or cloudClinic is null) group by organId,appointDepartCode,workDate,price,workType,sourceLevel,cloudClinic order by workDate";
                    query = ss.createQuery(hql);
                    query.setInteger("doctorId", doctorId);
                    query.setParameter("currentTime", new Date());
                    // query.setInteger("sourceType", sourceType);
                } else {
                    hql = "select new eh.entity.bus.DoctorDateSource(organId,appointDepartCode,appointDepartName,doctorId,workDate,sum(sourceNum-usedNum),price,workType,sourceLevel,cloudClinic,startTime) from AppointSource " +
                            "where doctorId=:doctorId and sourceType=:sourceType and workDate>=:currentTime  and stopFlag=0 and ((cloudClinic = 1 and cloudClinicType <>2 ) or cloudClinic=0 or cloudClinic is null ) group by organId,appointDepartCode,workDate,price,workType,sourceLevel,cloudClinic order by workDate";
                    query = ss.createQuery(hql);
                    query.setInteger("doctorId", doctorId);
                    query.setInteger("sourceType", sourceType);
                    query.setParameter("currentTime", new Date());
                }

                List<DoctorDateSource> temp = query.list();

                Date today = DateConversion.getFormatDate(new Date(), "yyyy-MM-dd");
                if (sourceType == 2) {
                    hql = "select new eh.entity.bus.DoctorDateSource(organId,appointDepartCode,appointDepartName,doctorId,workDate,sum(sourceNum-usedNum),price,workType,sourceLevel,cloudClinic,startTime) from AppointSource where doctorId=:doctorId  and workDate>DATE(NOW()) and stopFlag=0 and cloudClinic =1 and cloudClinicType <>2 group by organId,appointDepartCode,workDate,price,workType,sourceLevel,cloudClinic order by workDate";
                    query = ss.createQuery(hql);
                    query.setInteger("doctorId", doctorId);
                    List<DoctorDateSource> cloudTemp = query.list();
                    //zhangsl 2017-06-15 11:00:00 app3.8.5云门诊当天号源可约机构配置
                    final List<Integer> todayOrgans=DAOFactory.getDAO(HisServiceConfigDAO.class).findByCanAppointToday();
                    if (sourceType == 2&&todayOrgans!=null&&!todayOrgans.isEmpty()) {
                        hql = "select new eh.entity.bus.DoctorDateSource(organId,appointDepartCode,appointDepartName,doctorId,workDate,sum(sourceNum-usedNum),price,workType,sourceLevel,cloudClinic,startTime) " +
                                "from AppointSource where doctorId=:doctorId  and stopFlag=0 and cloudClinic =1 and cloudClinicType <>2 and WorkDate=DATE(NOW()) and sourceNum>=usedNum " +
                                //2016-11-15 luf:为了显示当天号源，去掉已约一条的限制
                                //"and usedNum >=1 ";
                                "and organId in(:todayOrgans)" +
                                "group by organId,appointDepartCode,workDate,workType,sourceLevel,price,cloudClinic " +//,appointSourceId
                                "order by workDate,workType,usedNum";
                        query = ss.createQuery(hql);
                        query.setInteger("doctorId", doctorId);
                        query.setParameterList("todayOrgans", todayOrgans);
                        List<DoctorDateSource> todayClinicSource = query.list();
                        todayClinicSource.addAll(cloudTemp);//云门诊号源
                        todayClinicSource.addAll(temp);//线下号源
                        setResult(todayClinicSource);
                    }
                } else {
                    setResult(temp);
                }
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List = (List) action.getResult();


        for (DoctorDateSource d : List) {
            String week = DateConversion.getWeekOfDate(d.getStartTime());
            d.setWeek(week);
        }
        return List;
    }

    /**
     * 检查号源类型是云门诊还是普通门诊
     *
     * @param doctorId
     * @param sourceType
     * @return 0:只有线下门诊号源;1,只有远程门诊号源; 2:两种号源都有
     */
    public int checkCloudClinic(int doctorId, int sourceType, Date workDate) {
        int cloudClinicFlag = 0;
        // 云诊室标志 =0的号源数量N1
        long ordinaryNum = totalByOrdinary(doctorId, sourceType, workDate);
        // 云诊室标志 =1并且云诊室类别=0或者1的号源数量N2
        long cloudNum = totalByCloud(doctorId, sourceType, workDate);
        // 云诊室标志 =2并且云诊室类别=0或者1的号源数量N3
        long ordinaryAndCloudNum = totalByOrdinaryAndCloud(doctorId,
                sourceType, workDate);
        if (ordinaryAndCloudNum > 0
                || (ordinaryAndCloudNum == 0 && cloudNum > 0 && ordinaryNum > 0)) {
            cloudClinicFlag = 2;
        }
        if (ordinaryAndCloudNum == 0 && cloudNum > 0 && ordinaryNum == 0) {
            cloudClinicFlag = 1;
        }
        if (ordinaryAndCloudNum == 0 && cloudNum == 0 && ordinaryNum > 0) {
            cloudClinicFlag = 0;
        }
        return cloudClinicFlag;
    }

    private void sort(List<DoctorDateSource> source) {

        Collections.sort(source, new Comparator<DoctorDateSource>() {
            @Override
            public int compare(DoctorDateSource o1, DoctorDateSource o2) {
                String key1 = "organId" + o1.getOragnID() + "professionCode" + o1.getProfessionCode() + "workDate" + o1.getWorkDate();
                String key2 = "organId" + o2.getOragnID() + "professionCode" + o2.getProfessionCode() + "workDate" + o2.getWorkDate();
                return key1.compareTo(key2);
            }


        });
    }

    /**
     * 查询云诊室标志 =0的号源数量N1
     *
     * @param doctorId
     * @param sourceType
     * @return
     * @author hyj
     */
    public Long totalByOrdinary(final int doctorId, final int sourceType,
                                final Date workDate) {
        HibernateStatelessResultAction<Object> action = new AbstractHibernateStatelessResultAction<Object>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                Query query = null;
                String hql = null;
                if (sourceType == 2) {// 医生端预约默认取全部，现在医生app传2
                    hql = "select count(*) from AppointSource where (cloudClinic=0 or cloudClinic is null ) and doctorId=:doctorId  and workDate=:workDate and stopFlag=0";
                    query = ss.createQuery(hql);
                    query.setInteger("doctorId", doctorId);
                    query.setDate("workDate", workDate);
                    // query.setInteger("sourceType", sourceType);
                } else {
                    hql = "select count(*) from AppointSource where ( cloudClinic=0 or cloudClinic is null ) and doctorId=:doctorId and sourceType=:sourceType and workDate=:workDate and stopFlag=0";
                    query = ss.createQuery(hql);
                    query.setInteger("doctorId", doctorId);
                    query.setInteger("sourceType", sourceType);
                    query.setDate("workDate", workDate);
                }
                long totalCount = (long) query.uniqueResult();
                setResult(totalCount);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (Long) action.getResult();
    }

    /**
     * 查询云诊室标志 =1并且云诊室类别=0或者1的号源数量N2
     *
     * @param doctorId
     * @param sourceType
     * @return
     * @author hyj
     */
    public Long totalByCloud(final int doctorId, final int sourceType,
                             final Date workDate) {
        HibernateStatelessResultAction<Object> action = new AbstractHibernateStatelessResultAction<Object>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                Query query = null;
                String hql = null;
                if (sourceType == 2) {// 医生端预约默认取全部，现在医生app传2
                    hql = "select count(*) from AppointSource where cloudClinic=1 and (cloudClinicType=0 or cloudClinicType=1) and doctorId=:doctorId  and workDate=:workDate and stopFlag=0";
                    query = ss.createQuery(hql);
                    query.setInteger("doctorId", doctorId);
                    query.setDate("workDate", workDate);
                    // query.setInteger("sourceType", sourceType);
                } else {
                    hql = "select count(*) from AppointSource where cloudClinic=1 and (cloudClinicType=0 or cloudClinicType=1) and doctorId=:doctorId and sourceType=:sourceType and workDate=:workDate and stopFlag=0";
                    query = ss.createQuery(hql);
                    query.setInteger("doctorId", doctorId);
                    query.setInteger("sourceType", sourceType);
                    query.setDate("workDate", workDate);
                }
                long totalCount = (long) query.uniqueResult();
                setResult(totalCount);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (Long) action.getResult();
    }

    /**
     * 查询云诊室标志 =2并且云诊室类别=0或者1的号源数量N3
     *
     * @param doctorId
     * @param sourceType
     * @return
     * @author hyj
     */
    public Long totalByOrdinaryAndCloud(final int doctorId,
                                        final int sourceType, final Date workDate) {
        HibernateStatelessResultAction<Object> action = new AbstractHibernateStatelessResultAction<Object>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                Query query = null;
                String hql = null;
                if (sourceType == 2) {// 医生端预约默认取全部，现在医生app传2
                    hql = "select count(*) from AppointSource where cloudClinic=2 and (cloudClinicType=0 or cloudClinicType=1) and doctorId=:doctorId  and workDate=:workDate and stopFlag=0";
                    query = ss.createQuery(hql);
                    query.setInteger("doctorId", doctorId);
                    query.setDate("workDate", workDate);
                    // query.setInteger("sourceType", sourceType);
                } else {
                    hql = "select count(*) from AppointSource where cloudClinic=2 and (cloudClinicType=0 or cloudClinicType=1) and doctorId=:doctorId and sourceType=:sourceType and workDate=:workDate and stopFlag=0";
                    query = ss.createQuery(hql);
                    query.setInteger("doctorId", doctorId);
                    query.setInteger("sourceType", sourceType);
                    query.setDate("workDate", workDate);
                }
                long totalCount = (long) query.uniqueResult();
                setResult(totalCount);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (Long) action.getResult();
    }

    @DAOMethod(sql = "from AppointSource where organId=:organId and organSchedulingId=:organSchedulingId and workDate>CURDATE()")
    public abstract List<AppointSource> findByOrganIdAndOrganSchedulingId(
            @DAOParam("organId") int organId,
            @DAOParam("organSchedulingId") String organSchedulingId);

    /**
     * 供 判断就诊方是否有排班 使用
     *
     * @param organId
     * @param workDate
     * @return
     * @author LF
     */
    @RpcService
    @DAOMethod
    public abstract List<AppointSource> findByOrganIdAndWorkDate(int organId,
                                                                 Date workDate);

    /**
     * 判断就诊方是否有排班
     *
     * @param organId
     * @param workDate
     * @return
     * @author LF
     */
    @RpcService
    public Boolean haveSchedulingOrNot(int organId, Date workDate) {
        if (workDate == null) {
            return false;
        }
        List<AppointSource> list = findByOrganIdAndWorkDate(organId, workDate);
        if (list == null || list.size() <= 0) {
            return false;
        }
        return true;
    }

    /**
     * Title:桐乡医院 根据机构、科室、医生、号源日期查询所有正常号源
     *
     * @param organId
     * @param departCode
     * @param doctorId
     * @param workDate
     * @return List<AppointSource>
     * @author zhangjr
     * @date 2015-10-10
     */
    @DAOMethod(sql = "from AppointSource where stopFlag=0 and organId=:organId and appointDepartCode=:departCode and doctorId=:doctorId and workDate=:workDate")
    public abstract List<AppointSource> findByOrganAndDoctorAndWorkdate(
            @DAOParam("organId") Integer organId,
            @DAOParam("departCode") String departCode,
            @DAOParam("doctorId") Integer doctorId,
            @DAOParam("workDate") Date workDate);

    /**
     * Title:根据排班id，机构、科室、医生、号源日期查询所有正常号源
     *
     * @param organId
     * @param departCode
     * @param doctorId
     * @param workDate
     * @param OrganSchedulingID
     * @return List<AppointSource>
     * @author zhangjr
     * @date 2015-10-10
     */
    @DAOMethod(sql = "from AppointSource where stopFlag=0 and OrganSchedulingID=:OrganSchedulingID and organId=:organId and appointDepartCode=:departCode and doctorId=:doctorId and workDate=:workDate")
    public abstract List<AppointSource> findBySchedulingAndOrganAndDoctorAndWorkdate(
            @DAOParam("organId") Integer organId,
            @DAOParam("departCode") String departCode,
            @DAOParam("doctorId") Integer doctorId,
            @DAOParam("workDate") Date workDate,
            @DAOParam("OrganSchedulingID") String OrganSchedulingID);

    /**
     * Title:根据appointSourceId将号源设置为停诊
     *
     * @param appointSourceId
     * @return void
     * @author zhangjr
     * @date 2015-10-10
     */
    @RpcService
    @DAOMethod(sql = "update AppointSource set stopFlag = 1 where appointSourceId = :appointSourceId")
    public abstract void updateStopFlagById(
            @DAOParam("appointSourceId") Integer appointSourceId);

    @DAOMethod(sql = "update AppointSource set stopFlag = 1 where appointSourceId in :appointSourceIds")
    public abstract void updateStopFlagByIds(
            @DAOParam("appointSourceIds") List<Integer> appointSourceIds);

    /**
     * Title:查询号源是否已存在，相比旧的方法getAppointSource，添加 了一个原始号源id查询条件
     *
     * @param organId
     * @param organSchedulingId
     * @param organSourceId
     * @param workDate
     * @param originalSourceId
     * @return AppointSource
     * @author zhangjr
     * @date 2015-10-19
     */
    @RpcService
    @DAOMethod(sql = "from AppointSource where organId=:organId and organSchedulingId=:organSchedulingId and "
            + "OrganSourceId=:organSourceId and workDate=:workDate and originalSourceId=:originalSourceId")
    public abstract AppointSource getAppointSourceOld(
            @DAOParam("organId") int organId,
            @DAOParam("organSchedulingId") String organSchedulingId,
            @DAOParam("organSourceId") String organSourceId,
            @DAOParam("workDate") Date workDate,
            @DAOParam("originalSourceId") String originalSourceId);

    /**
     * 号源信息列表服务
     *
     * @param organId    机构编号
     * @param profession 专科代码
     * @param department 科室代码
     * @param name       医生姓名 --可空，空则查全部
     * @param start      分页起始位置 --从0开始
     * @param startDate  号源分页开始时间
     * @return List<Doctor> 医生信息列表，包括号源列表
     * @author luf
     */
    @RpcService
    public List<Doctor> findDocAndSourcesBySix(int organId, String profession,
                                               int department, String name, Integer start, Date startDate) {
        AppointScheduleDAO dao = DAOFactory.getDAO(AppointScheduleDAO.class);
        List<Doctor> ds = dao.findDocsByFour(organId, department, profession,
                name, start);
        List<Doctor> target = new ArrayList<Doctor>();
        startDate = DateConversion.getFormatDate(startDate, "yyyy-MM-dd");
        Date endDate = DateConversion.getDateOfWeekNow(startDate);
        for (Doctor d : ds) {
            List<AppointSource> as = findSourcesByFive(organId,
                    d.getDoctorId(), startDate, endDate);
            d.setAppointSources(as);
            target.add(d);
        }
        return target;
    }

    /**
     * @param organId    机构代码
     * @param profession 专科代码
     * @param department 科室代码
     * @param startDate  开始日期
     * @return List<Doctor>
     * @throws
     * @Class eh.bus.dao.AppointSourceDAO.java
     * @Title: findDocAndSourcesByFourZ 把findDocAndSourcesBySix方法中的医生查询方法替换掉
     * @Description: TODO 号源信息列表服务
     * @author Zhongzx
     * @Date 2015-12-28下午4:56:19
     */
    @RpcService
    public List<Doctor> findDocAndSourcesByFourZ(Integer organId,
                                                 String profession, Integer department, Date startDate) {
        DoctorDAO dao = DAOFactory.getDAO(DoctorDAO.class);
        List<Doctor> ds = dao
                .findDoctorByThree(organId, profession, department);
        List<Doctor> target = new ArrayList<Doctor>();
        startDate = DateConversion.getFormatDate(startDate, "yyyy-MM-dd");
        Date endDate = DateConversion.getDateOfWeekNow(startDate);
        for (Doctor d : ds) {
            List<AppointSource> as = findSourcesByFive(organId,
                    d.getDoctorId(), startDate, endDate);
            d.setAppointSources(as);
            target.add(d);
        }
        return target;
    }

    /**
     * @param organId    机构编码
     * @param profession 专科代码 可空
     * @param department 科室代码 可空
     * @param name       医生姓名 可空
     * @param startDate  号源开始日期
     * @return List<Doctor>
     * @throws
     * @Class eh.bus.dao.AppointSourceDAO.java
     * @Title: findDocAndSourcesByFive
     * @Description: TODO
     * @author Zhongzx
     * @Date 2016-1-22下午2:09:38
     */
    @RpcService
    public List<Doctor> findDocAndSourcesByFive(Integer organId,
                                                String profession, Integer department, String name, Date startDate,
                                                Integer start, Integer limit) {
        AppointScheduleDAO dao = DAOFactory.getDAO(AppointScheduleDAO.class);
        List<Doctor> ds = dao.findDocsByFourByLimitZ(organId, department,
                profession, name, start, limit);
        List<Doctor> target = new ArrayList<Doctor>();
        startDate = DateConversion.getFormatDate(startDate, "yyyy-MM-dd");
        Date endDate = DateConversion.getDateOfWeekNow(startDate);
        for (Doctor d : ds) {
            List<AppointSource> as = this.findSourcesByFive(organId,
                    d.getDoctorId(), startDate, endDate);
            d.setAppointSources(as);
            target.add(d);
        }
        return target;
    }

    /**
     * 分页查询号源服务
     *
     * @param organId   机构编号
     * @param doctorId  医生内码
     * @param startDate 分页开始时间
     * @param endDate   分页结束时间
     * @return List<AppointSource> 号源列表
     * @author luf
     */
    @RpcService
    public List<AppointSource> findSourcesByFive(final int organId,
                                                 final int doctorId, final Date startDate, final Date endDate) {
        HibernateStatelessResultAction<List<AppointSource>> action = new AbstractHibernateStatelessResultAction<List<AppointSource>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                String hql = "From AppointSource where doctorId=:doctorId and organId=:organId"
                        + " and workDate>=:startDate and workDate<=:endDate and fromFlag=1 order by startTime";
                Query q = ss.createQuery(hql);
                q.setParameter("doctorId", doctorId);
                q.setParameter("organId", organId);
                q.setParameter("startDate", startDate);
                q.setParameter("endDate", endDate);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 分页查询号源服务(包含所有来源的号源)
     *
     * @param organId   机构编号
     * @param doctorId  医生内码
     * @param startDate 分页开始时间
     * @param endDate   分页结束时间
     * @return List<AppointSource> 号源列表
     */
    @RpcService
    public List<AppointSource> findAllSourcesByFive(final int organId,
                                                    final int doctorId, final Date startDate, final Date endDate) {
        HibernateStatelessResultAction<List<AppointSource>> action = new AbstractHibernateStatelessResultAction<List<AppointSource>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                /*String hql = "From AppointSource where doctorId=:doctorId and organId=:organId"
                        + " and workDate>=:startDate and workDate<=:endDate and fromFlag=1 order by startTime";*/
                String hql = "From AppointSource where doctorId=:doctorId and organId=:organId"
                        + " and workDate>=:startDate and workDate<=:endDate  order by startTime";
                Query q = ss.createQuery(hql);
                q.setParameter("doctorId", doctorId);
                q.setParameter("organId", organId);
                q.setParameter("startDate", startDate);
                q.setParameter("endDate", endDate);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 获取该医生该天的号源信息列表服务
     *
     * @param organId  机构编号
     * @param doctorId 医生内码
     * @param dateNow  该天日期
     * @return Hashtable<String, Object> 号源列表和数据列表
     * @author luf
     */
    @RpcService
    public Hashtable<String, Object> findOneDocSourcesByOneDate(int organId,
                                                                int doctorId, Date dateNow) {
        List<AppointSource> as = this.findSourcesByFive(organId, doctorId,
                dateNow, dateNow);
        int stop = 0;
        int hadAppoint = 0;
        int all = 0;
        Hashtable<String, Object> table = new Hashtable<String, Object>();
        Hashtable<String, Integer> counts = new Hashtable<String, Integer>();
        for (AppointSource a : as) {
            all++;
            if (a.getStopFlag() == 1) {
                stop++;
            }
            if (a.getSourceNum().equals(a.getUsedNum())) {
                hadAppoint++;
            }
        }
        counts.put("all", all);
        counts.put("stop", stop);
        counts.put("hadAppoint", hadAppoint);
        table.put("appointSources", as);
        table.put("counts", counts);
        return table;
    }

    /**
     * 出/停诊服务
     *
     * @param ids      需修改的号源序号列表
     * @param stopFlag 停诊标志
     * @return int 修改成功的条数
     * @author luf
     */
    @RpcService
    public int updateSourcesStopOrNot(List<Integer> ids, int stopFlag) {
        logger.info("出/停诊服务 AppointSourceDAO====> updateSourcesStopOrNot <==="
                + "ids:" + JSONUtils.toString(ids) + ";stopFlag:" + stopFlag);
        int count = 0;
        for (Integer id : ids) {
            if (id == null) {
                continue;
            }
            AppointSource ac = get(id);
            if (ac == null) {
                continue;
            }
            ac.setStopFlag(stopFlag);
            update(ac);
            count++;
        }
        return count;
    }

    /**
     * 修改单条号源信息
     *
     * @param a 需修改的号源信息组成的对象
     * @return Boolean 是否修改成功
     * @author luf
     */
    @RpcService
    public Boolean updateOneSource(AppointSource a) {
        logger.info("修改单条号源信息 AppointSourceDAO====> updateOneSource <==="
                + "a:" + JSONUtils.toString(a));

        if (a == null || a.getAppointSourceId() == null) {
            return false;
        }
        Integer id = a.getAppointSourceId();
        AppointSource ac = get(id);
        if (ac == null) {
            return false;
        }
        int organId = ac.getOrganId();
        BeanUtils.map(a, ac);
        ac.setFromFlag(1);// 平台生成的号源
        if (a.getOrganId() == null) {
            ac.setOrganId(organId);
        }
        update(ac);
        String doctorMessage = this.getDoctorInfoBySource(ac);
        BusActionLogService.recordBusinessLog("医生号源", String.valueOf(a.getAppointSourceId()), "AppointSource",
                doctorMessage + "的号源[" + a.getAppointSourceId() + "]已修改");
        return true;
    }

    /**
     * 删除单条/多条号源
     *
     * @param ids 需删除的号源序号列表
     * @return int 成功删除的条数
     * @author luf
     */
    @RpcService
    public int deleteOneOrMoreSource(List<Integer> ids) {
        logger.info("删除单条/多条号源 AppointSourceDAO====> deleteOneOrMoreSchedule <==="
                + "ids:" + JSONUtils.toString(ids));

        int count = 0;
        String doctorMessage = null;
        for (Integer id : ids) {
            if (id == null) {
                continue;
            }
            AppointSource a = get(id);
            if (a == null) {
                continue;
            }
            if (doctorMessage == null) {
                doctorMessage = this.getDoctorInfoBySource(a);
            }
            remove(id);
            count++;
        }
        BusActionLogService.recordBusinessLog("医生号源", JSONUtils.toString(ids), "AppointSource",
                doctorMessage + "的号源" + JSONUtils.toString(ids) + "已删除");
        return count;
    }

    public String getDoctorInfoBySource(AppointSource a) {
        String doctorMessage = null;
        if (a != null) {
            Doctor doctor = null;
            Organ organ = null;
            DoctorDAO doctorDao = DAOFactory.getDAO(DoctorDAO.class);
            doctor = doctorDao.getByDoctorId(a.getDoctorId());
            OrganDAO organDao = DAOFactory.getDAO(OrganDAO.class);
            organ = organDao.getByOrganId(a.getOrganId());
            String deptName = null;
            if (doctor != null) {
                EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
                Employment e = employmentDAO.getDeptNameByDoctorIdAndOrganId(doctor.getDoctorId(), doctor.getOrgan());
                if (e != null) {
                    deptName = e.getDeptName();
                }
            }
            if (organ != null && doctor != null) {
                if (deptName != null) {
                    doctorMessage = organ.getShortName() + " " + deptName + " " + doctor.getName();
                } else {
                    doctorMessage = organ.getShortName() + " " + doctor.getName();
                }
            }
        }
        return doctorMessage;
    }

    /**
     * 新增号源信息
     *
     * @param a 新增的号源信息组成的对象
     * @return List<Integer> 新增成功返回号源序号列表
     * @throws DAOException
     * @author luf
     *
     *
     * OrganSchedulingId添加默认值
     *
     *
     */
    @RpcService
    public List<Integer> addOneSource(AppointSource a) throws DAOException {
        logger.info("新增号源信息 AppointSourceDAO==> addOneSource <===" + "a:"
                + JSONUtils.toString(a));
        if (a == null || a.getDoctorId() == null || a.getOrganId() == null
                || a.getSourceNum() == null || a.getSourceNum() <= 0
                || a.getSourceType() == null || a.getWorkType() == null
                || a.getEndTime() == null || a.getStartTime() == null
                || a.getWorkDate() == null
                || a.getEndTime().before(a.getStartTime())
                || StringUtils.isEmpty(a.getAppointDepartCode())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "appointSource is required");
        }
        if (a.getStopFlag() == null) {
            a.setStopFlag(0);
        }
        if(StringUtils.isEmpty(a.getOrganSchedulingId())){
            a.setOrganSchedulingId(a.getDoctorId()
                    +DateConversion.getDateFormatter(a.getWorkDate(),"yyyyMMdd")
                    +a.getWorkType());
        }
        if (StringUtils.isEmpty(a.getAppointDepartName())) {
            AppointDepartDAO departDAO = DAOFactory
                    .getDAO(AppointDepartDAO.class);
            AppointDepart depart = departDAO.getByOrganIDAndAppointDepartCode(
                    a.getOrganId(), a.getAppointDepartCode());
            if (depart != null) {
                a.setAppointDepartName(depart.getAppointDepartName());
            }
        }
        a.setFromFlag(1);
        a.setUsedNum(0);
        a.setCreateDate(new Date());
        List<Integer> ids = new ArrayList<Integer>();
        int avg = a.getSourceNum();
        List<Object[]> os = DateConversion.getAverageTime(a.getStartTime(),
                a.getEndTime(), avg);
        Integer docId = a.getDoctorId();
        Integer orderNum = this.getMaxOrderNum(docId, a.getOrganId(), a.getWorkDate());
        //设置起始号源标志 如果有起始位置 则用起始位置，没有这使用原来的从0开始
        if (a.getStartNum() != null) {
            orderNum = a.getStartNum() - 1;//设置 起始号源
        }
        if (orderNum == null) {
            orderNum = 0;
        }
        /*if (orderNum == null) {
            orderNum = 0;
		}*/
        a.setSourceNum(1);
        for (Object[] o : os) {
            AppointSource ac = a;
            ac.setStartTime((Date) o[0]);
            ac.setEndTime((Date) o[1]);
            ac.setOrderNum(++orderNum);
            ids.add(save(ac).getAppointSourceId());
        }
        if (ids.size() <= 0) {
            return null;
        }
        DoctorDAO dDao = DAOFactory.getDAO(DoctorDAO.class);
        Doctor d = dDao.get(docId);
        d.setHaveAppoint(1);
        dDao.update(d);
        return ids;
    }

    /**
     * 查询最大OrderNum值
     *
     * @param doctorId 医生内码
     * @param organId  机构编码
     * @param workDate 工作日期
     * @return int 最大OrderNum值
     * @author luf
     */
    @RpcService
    @DAOMethod(sql = "select max(orderNum) from AppointSource where doctorId=:doctorId and organId=:organId and workDate=:workDate")
    public abstract Integer getMaxOrderNum(@DAOParam("doctorId") int doctorId,
                                           @DAOParam("organId") int organId,
                                           @DAOParam("workDate") Date workDate);

    /**
     * 排班生成号源
     *
     * @author luf
     */
    @RpcService
    public void fromScheduleToSource() {
        AppointScheduleDAO addao = DAOFactory.getDAO(AppointScheduleDAO.class);
        EmploymentDAO emdao = DAOFactory.getDAO(EmploymentDAO.class);
        List<AppointSchedule> ads = addao.findAllEffectiveSchedule(0);
        for (AppointSchedule ad : ads) {
            logger.info("排班生成号源<== fromScheduleToSource ==> AppointSchedule:"
                    + JSONUtils.toString(ad));
            int week = ad.getWeek();
            LocalDate dt = new LocalDate();
            Date now = dt.toDate();
            int max = ad.getMaxRegDays();
            Date dTime = dt.plusDays(max).toDate();
            long maxDateTime = dTime.getTime();
            if (ad.getLastGenDate() != null) {
                now = ad.getLastGenDate();
            }
            while (now.getTime() <= maxDateTime) {
                List<Date> dates = addao.findTimeSlotByThree(now, max, week);
                Date lastDate = DateConversion.getFormatDate(dates.get(1),
                        "yyyy-MM-dd");
                long lastDateTime = lastDate.getTime();
                if (lastDateTime <= maxDateTime) {
                    String startTimeString = DateConversion.getDateFormatter(
                            ad.getStartTime(), "HH:mm:ss");
                    String endTimeString = DateConversion.getDateFormatter(
                            ad.getEndTime(), "HH:mm:ss");
                    Date startTime = DateConversion.getDateByTimePoint(
                            lastDate, startTimeString);
                    Date endTime = DateConversion.getDateByTimePoint(lastDate,
                            endTimeString);
                    int doctorId = ad.getDoctorId();
                    int organId = ad.getOrganId();
                    int department = ad.getDepartId();
                    int sourceLevel = ad.getClinicType();
                    Double price = 0.0;
                    Employment e = emdao.getByDocAndOrAndDep(doctorId, organId,
                            department);
                    if (e != null) {
                        switch (sourceLevel) {
                            case 1:
                                price = e.getClinicPrice() == null ? 0.0 : e
                                        .getClinicPrice();
                                break;
                            case 2:
                                price = e.getProfClinicPrice() == null ? 0.0 : e
                                        .getProfClinicPrice();
                                break;
                            case 3:
                                price = e.getSpecClinicPrice() == null ? 0.0 : e
                                        .getSpecClinicPrice();
                            default:
                                break;
                        }
                    }
                    AppointSource a = new AppointSource();
                    a.setDoctorId(doctorId);
                    a.setOrganId(organId);
                    a.setSourceNum(ad.getSourceNum());
                    a.setSourceType(ad.getSourceType());
                    a.setWorkType(ad.getWorkType());
                    a.setStartTime(startTime);
                    a.setEndTime(endTime);
                    a.setWorkDate(lastDate);
                    a.setAppointDepartCode(ad.getAppointDepart());
                    a.setOrganSchedulingId(ad.getScheduleId().toString());
                    a.setCloudClinic(ad.getTelMedFlag());
                    a.setCloudClinicType(ad.getTelMedType());
                    a.setSourceLevel(sourceLevel);
                    a.setPrice(price);
                    a.setClinicAddr(ad.getWorkAddr());
                    a.setStartNum(ad.getStartNum());
                    this.addOneSource(a);
                    ad.setLastGenDate(lastDate);
                }
                now = lastDate;
            }
            addao.update(ad);
        }
    }

    /**
     * 查询医生日期号源服务--新增云门诊判断
     *
     * @param doctorId   --医生编号
     * @param sourceType --号源类别
     * @return List<AppointSource>
     * @author luf
     */
    @RpcService
    public List<AppointSource> queryTotalByDoctorDateAndCloud(
            final int doctorId, final int sourceType) {
        List<AppointSource> acs = new ArrayList<AppointSource>();
        List<Object[]> os;
        HibernateStatelessResultAction<List<Object[]>> action = new AbstractHibernateStatelessResultAction<List<Object[]>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                StringBuilder hql = new StringBuilder(
                        "select a,sum(sourceNum-usedNum) from AppointSource a where "
                                + "doctorId=:doctorId and workDate>NOW() and stopFlag=0 and "
                                + "((cloudClinic =1 and cloudClinicType <>2 ) or cloudClinic=0  or cloudClinic is null) ");
                if (sourceType != 2) {
                    hql.append("and sourceType=:sourceType ");
                }
                hql.append("group by organId,appointDepartCode,workDate,price,workType,sourceLevel,cloudClinic "
                        + "order by workDate");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("doctorId", doctorId);
                if (sourceType != 2) {
                    q.setParameter("sourceType", sourceType);
                }
                List<Object[]> os = q.list();
                setResult(os);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        os = action.getResult();
        if (os == null || os.size() <= 0) {
            return acs;
        }
        return appointSourceListForTotal(os);
    }

    /**
     * 号源列表转换
     *
     * @param appointSources 号源列表
     * @return List<AppointSource>
     * @author luf
     */
    public List<AppointSource> appointSourceListForTotal(List<Object[]> os) {
        List<AppointSource> list = new ArrayList<AppointSource>();
        for (Object[] o : os) {
            Long sum = (Long) o[1];
            AppointSource source = convertAppointSourceForTotal((AppointSource) o[0]);
            source.setRemainder(sum);
            list.add(source);
        }
        return list;
    }

    /**
     * 号源对象转换
     *
     * @param appointSource 号源信息
     * @return AppointSource
     * @author luf
     */
    public AppointSource convertAppointSourceForTotal(
            AppointSource appointSource) {
        AppointSource source = new AppointSource();
        Date workDate = appointSource.getWorkDate();
        String week = DateConversion.getWeekOfDate(workDate);
        source.setOrganId(appointSource.getOrganId());
        source.setAppointDepartCode(appointSource.getAppointDepartCode());
        source.setAppointDepartName(appointSource.getAppointDepartName());
        source.setDoctorId(appointSource.getDoctorId());
        source.setWorkDate(workDate);
        source.setPrice(appointSource.getPrice());
        source.setWorkType(appointSource.getWorkType());
        source.setSourceLevel(appointSource.getSourceLevel());
        source.setCloudClinic(appointSource.getCloudClinic());
        source.setWeek(week);
        return source;
    }

    /**
     * 服务名:查询医生分时段号源服务(syf支付宝用)
     *
     * @param doctorId
     * @param sourceType
     * @param workDate
     * @param workType
     * @param organID
     * @param appointDepartCode
     * @param price
     * @return
     * @throws DAOException
     * @author xyf
     */
    @RpcService
    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<AppointSource> queryDoctorSourceAlipay(final int doctorId,
                                                       final int sourceType, final Date workDate, final int workType,
                                                       final int organID, final String appointDepartCode,
                                                       final Double price, final String doType) throws DAOException {
        List<AppointSource> asList;
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String appointDepartCodeV = appointDepartCode;
                StringBuilder hql = new StringBuilder("");
                Query query = null;
                String appointDepartCodeStr = "";
                if (appointDepartCode.indexOf("A") != -1) {//区分院区
                    appointDepartCodeStr = "appointDepartCode like '%A%'";
                } else {
                    appointDepartCodeStr = "appointDepartCode not like '%A%'";
                }
                if (sourceType == 2) {
                    hql = new StringBuilder(
                            "from AppointSource where doctorId=:doctorId and "
                                    + "workDate=:workDate and workType=:workType and organID=:organID and appointDepartCode=:appointDepartCode and "
                                    + "sourceNum>usedNum and stopFlag=0 "
                                    + "and (cloudClinic<>1 or cloudClinic is null)"
                                    + " order by startTime asc");//
                    query = ss.createQuery(hql.toString());
                    query.setInteger("workType", workType);
                    query.setString("appointDepartCode", appointDepartCode);
                } else {
                    if (workType == 0) {
                        hql = new StringBuilder(
                                "from AppointSource where doctorId=:doctorId and sourceType=:sourceType and "
                                        + "workDate=:workDate and organID=:organID and " + appointDepartCodeStr + " and "
                                        + "sourceNum>usedNum and stopFlag=0 "
                                        + "and (cloudClinic<>1 or cloudClinic is null)"
                                        + " order by startTime asc");
                        query = ss.createQuery(hql.toString());
                        query.setInteger("sourceType", sourceType);
                    } else {
                        hql = new StringBuilder(
                                "from AppointSource where doctorId=:doctorId and sourceType=:sourceType and "
                                        + "workDate=:workDate and workType=:workType and organID=:organID and " + appointDepartCodeStr + " and "
                                        + "sourceNum>usedNum and stopFlag=0 "
                                        + "and (cloudClinic<>1 or cloudClinic is null)"
                                        + " order by startTime asc");
                        query = ss.createQuery(hql.toString());
                        query.setInteger("sourceType", sourceType);
                        query.setInteger("workType", workType);
                    }
                }
                query.setInteger("doctorId", doctorId);
                query.setTimestamp("workDate", workDate);
                query.setInteger("organID", organID);
                if (doType.equals("1")) {
                    query.setMaxResults(1);
                }
                List<AppointSource> temp = query.list();
                AppointDepartDAO AppointDepartDAO = DAOFactory
                        .getDAO(AppointDepartDAO.class);
                for (int i = 0; i < temp.size(); i++) {
                    temp.get(i).setAppointDepartId(
                            AppointDepartDAO
                                    .getIdByOrganIdAndAppointDepartCode(
                                            organID, temp.get(i)
                                                    .getAppointDepartCode()));
                }
                setResult(temp);

            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        asList = (List) action.getResult();
        return asList;
    }

    /**
     * 查询医生日期号源服务--邵逸夫支付宝服务
     *
     * @param doctorId   --医生编号
     * @param sourceType --号源类别
     * @return
     * @author xyf
     */
    @RpcService
    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<DoctorDateSource> totalByDoctorDateAndCloudClinicAlipay(final int doctorId, final int sourceType) throws DAOException {
        List<DoctorDateSource> List;
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                Query query = null;
                String hql = null;
                // 医生端预约默认取全部，现在医生app传2
                hql = "select new eh.entity.bus.DoctorDateSource(organId,appointDepartCode,appointDepartName,doctorId,workDate,sum(sourceNum-usedNum),price,workType,sourceLevel,cloudClinic,startTime) from AppointSource where doctorId=:doctorId and sourceType=:sourceType and startTime>NOW()  and stopFlag=0 and (cloudClinic=0 or cloudClinic is null ) group by organId,appointDepartCode,workDate,price,workType,sourceLevel,cloudClinic order by workDate";
                query = ss.createQuery(hql);
                query.setInteger("doctorId", doctorId);
                query.setInteger("sourceType", sourceType);
                List<DoctorDateSource> temp = query.list();
                setResult(temp);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List = (List) action.getResult();
        for (DoctorDateSource d : List) {
            String week = DateConversion.getWeekOfDate(d.getStartTime());
            d.setWeek(week);
        }
        return List;
    }

    /**
     * 查询医生日期剩余号源总数服务(syf支付宝用)
     *
     * @param doctorId          --医生编号
     * @param sourceType        --号源类别
     * @param organId           --机构号
     * @param appointDepartCode --科室编号
     * @return
     * @author xyf
     */
    @RpcService
    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<HashMap<String, Object>> totalByDoctorDateAndWorkDate(
            final int doctorId, final int sourceType, final Date workDate,
            final int organId, final String appointDepartCode, final int isWeek)
            throws DAOException {
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                Query query = null;
                Query queryTotal = null;
                String hql = null;
                String hqlTotal = null;
                String appointDepartCodeStr = "";
                if (appointDepartCode.indexOf("A") != -1) {//区分下院区
                    appointDepartCodeStr = "appointDepartCode like '%A%'";
                } else {
                    appointDepartCodeStr = "appointDepartCode not like '%A%'";
                }
                hql = "select new eh.entity.bus.DoctorDateSource(organId,appointDepartCode,appointDepartName,doctorId,workDate,sum(sourceNum-usedNum),price,workType,sourceLevel,cloudClinic) from AppointSource where doctorId=:doctorId and sourceType=:sourceType and workDate=:workDate  and organId=:organId and startTime>NOW() and " + appointDepartCodeStr + " and (cloudClinic<>1 or cloudClinic is null) and stopFlag=0  group by organId,appointDepartCode,workDate,price,workType,sourceLevel order by workType";
                hqlTotal = "select new eh.entity.bus.DoctorDateSource(organId,appointDepartCode,appointDepartName,doctorId,workDate,sum(sourceNum-usedNum),price,workType,sourceLevel,cloudClinic) from AppointSource where doctorId=:doctorId and sourceType=:sourceType and workDate=:workDate and organId=:organId and " + appointDepartCodeStr + " and (cloudClinic<>1 or cloudClinic is null) and stopFlag=0  group by organId,appointDepartCode,workDate,price,workType,sourceLevel order by workType";
                query = ss.createQuery(hql);
                query.setInteger("doctorId", doctorId);
                query.setInteger("sourceType", sourceType);
                query.setDate("workDate", workDate);
                query.setInteger("organId", organId);
                List<DoctorDateSource> temp = query.list();
                List<HashMap<String, Object>> source = new ArrayList<HashMap<String, Object>>();
                if (temp.size() > 0) {
                    for (DoctorDateSource dcs : temp) {
                        HashMap<String, Object> cell = new HashMap<String, Object>();
                        cell.put("organId", dcs.getOragnID());
                        cell.put("professionCode", dcs.getProfessionCode());
                        cell.put("professionName", dcs.getProfessionName());
                        cell.put("doctorId", dcs.getDoctorID());
                        cell.put("workDate", dcs.getWorkDate());
                        cell.put("sourceNum", dcs.getSourceNum());
                        cell.put("price", dcs.getPrice());
                        cell.put("workType", dcs.getWorkType());
                        cell.put("sourceLevel", dcs.getSourceLevel());
                        source.add(cell);
                    }
                } else if (temp.size() == 0 && isWeek == 0) {
                    queryTotal = ss.createQuery(hqlTotal);
                    queryTotal.setInteger("doctorId", doctorId);
                    queryTotal.setInteger("sourceType", sourceType);
                    queryTotal.setDate("workDate", workDate);
                    queryTotal.setInteger("organId", organId);
                    List<DoctorDateSource> tempTotal = queryTotal.list();
                    if (tempTotal.size() > 0) {
                        for (DoctorDateSource docSource : tempTotal) {
                            HashMap<String, Object> cell = new HashMap<String, Object>();
                            cell.put("organId", docSource.getOragnID());
                            cell.put("professionCode",
                                    docSource.getProfessionCode());
                            cell.put("professionName",
                                    docSource.getProfessionName());
                            cell.put("doctorId", docSource.getDoctorID());
                            cell.put("workDate", docSource.getWorkDate());
                            cell.put("sourceNum", docSource.getSourceNum());
                            cell.put("price", docSource.getPrice());
                            cell.put("workType", docSource.getWorkType());
                            cell.put("sourceLevel", docSource.getSourceLevel());
                            source.add(cell);
                        }
                    }
                }
                setResult(source);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List<HashMap<String, Object>>) action.getResult();
    }

    /**
     * 查询医生一周排班服务(syf支付宝用)
     *
     * @param doctorId          --医生编号
     * @param workDate          --排班日期
     * @param organId           --机构号
     * @param appointDepartCode --科室编号
     * @return
     * @author xyf
     */
    @RpcService
    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<HashMap<String, Object>> queryDoctorScheduleByWorkDate(
            final int doctorId, final Date workDate, final int organId, final String appointDepartCode)
            throws DAOException {
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                Query query = null;
                String hql = null;
                String appointDepartCodeStr = "";
                if (appointDepartCode.indexOf("A") != -1) {//用于邵逸夫区分院区，省中根据机构号区分
                    appointDepartCodeStr = "appointDepartCode like '%A%'";
                } else {
                    appointDepartCodeStr = "appointDepartCode not like '%A%'";
                }
                hql = "select new eh.entity.bus.DoctorDateSource(organId,appointDepartCode,appointDepartName,doctorId,workDate,sum(sourceNum-usedNum),price,workType,sourceLevel,cloudClinic) from AppointSource where doctorId=:doctorId and workDate>:startDate and workDate<:endDate and organId=:organId and startTime>NOW() and " + appointDepartCodeStr + " and sourceType=1  and stopFlag=0  group by organId,appointDepartCode,workDate,price,workType,sourceLevel order by workDate, workType ";
                query = ss.createQuery(hql);
                query.setInteger("doctorId", doctorId);
                query.setDate("startDate", workDate);
                query.setDate("endDate", DateConversion.getDateOfWeekNow(workDate));
                query.setInteger("organId", organId);
                List<DoctorDateSource> temp = query.list();
                List<HashMap<String, Object>> source = new ArrayList<HashMap<String, Object>>();
                if (temp.size() > 0) {
                    for (DoctorDateSource dcs : temp) {
                        HashMap<String, Object> cell = new HashMap<String, Object>();
                        cell.put("organId", dcs.getOragnID());
                        cell.put("professionCode", dcs.getProfessionCode());
                        cell.put("professionName", dcs.getProfessionName());
                        cell.put("doctorId", dcs.getDoctorID());
                        cell.put("workDate", dcs.getWorkDate());
                        cell.put("sourceNum", dcs.getSourceNum());
                        cell.put("price", dcs.getPrice());
                        cell.put("workType", dcs.getWorkType());
                        cell.put("sourceLevel", dcs.getSourceLevel());
                        source.add(cell);
                    }
                }
                setResult(source);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List<HashMap<String, Object>>) action.getResult();
    }

    /**
     * 统计医生一周剩余号源数(syf支付宝用)
     *
     * @param doctorId          --医生编号
     * @param organId           --机构号
     * @param appointDepartCode --科室编号
     * @return
     * @author xyf
     */
    @RpcService
    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<HashMap<String, Object>> totalDoctorScheduleByWorkDate(
            final int doctorId, final int organId, final String appointDepartCode)
            throws DAOException {
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                Query query = null;
                String hql = null;
                String appointDepartCodeStr = "";
                if (appointDepartCode.indexOf("A") != -1) {//用于邵逸夫区分院区，省中根据机构号区分
                    appointDepartCodeStr = "appointDepartCode like '%A%'";
                } else {
                    appointDepartCodeStr = "appointDepartCode not like '%A%'";
                }
                hql = "select sum(sourceNum-useNum) from AppointSource where doctorId=:doctorId and workDate>:startDate and workDate<:endDate and organId=:organId and startTime>NOW() and " + appointDepartCodeStr + " and sourceType=1  and stopFlag=0 ";
                query = ss.createQuery(hql);
                query.setInteger("doctorId", doctorId);
                Date startDate = new Date();
                startDate = DateConversion.getFormatDate(startDate, "yyyy-MM-dd");
                query.setDate("startDate", startDate);
                query.setDate("endDate", DateConversion.getDateOfWeekNow(startDate));
                query.setInteger("organId", organId);
                List<DoctorDateSource> temp = query.list();
                List<HashMap<String, Object>> source = new ArrayList<HashMap<String, Object>>();
                if (temp.size() > 0) {
                    for (DoctorDateSource dcs : temp) {
                        HashMap<String, Object> cell = new HashMap<String, Object>();
                        cell.put("organId", dcs.getOragnID());
                        cell.put("professionCode", dcs.getProfessionCode());
                        cell.put("professionName", dcs.getProfessionName());
                        cell.put("doctorId", dcs.getDoctorID());
                        cell.put("workDate", dcs.getWorkDate());
                        cell.put("sourceNum", dcs.getSourceNum());
                        cell.put("price", dcs.getPrice());
                        cell.put("workType", dcs.getWorkType());
                        cell.put("sourceLevel", dcs.getSourceLevel());
                        source.add(cell);
                    }
                }
                setResult(source);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List<HashMap<String, Object>>) action.getResult();
    }

    /**
     * 服务名:查询医生分时段号源服务(sz支付宝用)
     *
     * @param doctorId
     * @param sourceType
     * @param workDate
     * @param workType
     * @param organID
     * @param price
     * @return
     * @throws DAOException
     * @author xyf
     */
    @RpcService
    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<AppointSource> queryDoctorSourceAlipaySz(final int doctorId,
                                                         final int sourceType, final Date workDate, final int workType,
                                                         final int organID, final Double price, final String doType)
            throws DAOException {
        List<AppointSource> asList = new ArrayList<AppointSource>();
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder("");
                Query query = null;
                if (sourceType == 2) {
                    hql = new StringBuilder(
                            "from AppointSource where doctorId=:doctorId and "
                                    + "workDate=:workDate and workType=:workType and organID=:organID and "
                                    + "sourceNum>usedNum and stopFlag=0 "
                                    + "and (cloudClinic<>1 or cloudClinic is null)"
                                    + " order by startTime asc");//
                    query = ss.createQuery(hql.toString());
                    query.setInteger("workType", workType);
                } else {
                    if (workType == 0) {
                        hql = new StringBuilder(
                                "from AppointSource where doctorId=:doctorId and sourceType=:sourceType and "
                                        + "workDate=:workDate and organID=:organID and "
                                        + "sourceNum>usedNum and stopFlag=0 "
                                        + "and (cloudClinic<>1 or cloudClinic is null)"
                                        + " order by startTime asc");
                        query = ss.createQuery(hql.toString());
                        query.setInteger("sourceType", sourceType);
                    } else {
                        hql = new StringBuilder(
                                "from AppointSource where doctorId=:doctorId and sourceType=:sourceType and "
                                        + "workDate=:workDate and workType=:workType and organID=:organID   and "
                                        + "sourceNum>usedNum and stopFlag=0 "
                                        + "and (cloudClinic<>1 or cloudClinic is null)"
                                        + " order by startTime asc");
                        query = ss.createQuery(hql.toString());
                        query.setInteger("sourceType", sourceType);
                        query.setInteger("workType", workType);
                    }
                }
                query.setInteger("doctorId", doctorId);
                query.setTimestamp("workDate", workDate);
                query.setInteger("organID", organID);
                if (doType.equals("1")) {
                    query.setMaxResults(1);
                }
                List<AppointSource> temp = query.list();
                AppointDepartDAO AppointDepartDAO = DAOFactory
                        .getDAO(AppointDepartDAO.class);
                for (int i = 0; i < temp.size(); i++) {
                    temp.get(i).setAppointDepartId(
                            AppointDepartDAO
                                    .getIdByOrganIdAndAppointDepartCode(
                                            organID, temp.get(i)
                                                    .getAppointDepartCode()));
                }
                setResult(temp);

            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        asList = (List) action.getResult();
        return asList;
    }

    /**
     * 查询医生日期剩余号源总数服务(sz支付宝用)
     *
     * @param doctorId   --医生编号
     * @param sourceType --号源类别
     * @param organId    --机构号
     * @param isWeek     --0:当天 1:一周
     * @return
     * @author xyf
     */
    @RpcService
    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<HashMap<String, Object>> totalByDoctorDateAndWorkDateSz(
            final int doctorId, final int sourceType, final Date workDate,
            final int organId, final int isWeek) throws DAOException {
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                Query query = null;
                String hql = null;
                if (isWeek == 0) {// isWeek==0 获取一天剩余号源数 isWeek==1 获取一周剩余号源数
                    hql = "select new eh.entity.bus.DoctorDateSource(organId,appointDepartCode,appointDepartName,doctorId,workDate,sum(sourceNum-usedNum),price,workType,sourceLevel,cloudClinic) from AppointSource where doctorId=:doctorId and sourceType=:sourceType and workDate=:workDate and organId=:organId and startTime>NOW() and stopFlag=0  group by organId,appointDepartCode,workDate,price,workType,sourceLevel order by workType";
                } else {
                    hql = "select new eh.entity.bus.DoctorDateSource(organId,appointDepartCode,appointDepartName,doctorId,workDate,sum(sourceNum-usedNum),price,workType,sourceLevel,cloudClinic) from AppointSource where doctorId=:doctorId and sourceType=:sourceType and week(workDate)=week(:workDate) and organId=:organId  and usedNum=0 and stopFlag=0 and startTime>NOW() group by organId,appointDepartCode,workDate,price,workType,sourceLevel order by workType";
                }
                query = ss.createQuery(hql);
                query.setInteger("doctorId", doctorId);
                query.setInteger("sourceType", sourceType);
                query.setDate("workDate", workDate);
                query.setInteger("organId", organId);
                List<DoctorDateSource> temp = query.list();
                List<HashMap<String, Object>> source = new ArrayList<HashMap<String, Object>>();
                if (temp.size() > 0) {
                    for (DoctorDateSource dcs : temp) {
                        HashMap<String, Object> cell = new HashMap<String, Object>();
                        cell.put("organId", organId);
                        cell.put("professionCode", dcs.getProfessionCode());
                        cell.put("professionName", dcs.getProfessionName());
                        cell.put("doctorId", dcs.getDoctorID());
                        cell.put("workDate", dcs.getWorkDate());
                        cell.put("sourceNum", dcs.getSourceNum());
                        cell.put("price", dcs.getPrice());
                        cell.put("workType", dcs.getWorkType());
                        cell.put("sourceLevel", dcs.getSourceLevel());
                        source.add(cell);
                    }
                }
                setResult(source);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List<HashMap<String, Object>>) action.getResult();
    }

    /**
     * 服务名:查询医生本周是否有号源(邵逸夫支付宝用)
     *
     * @param doctorId
     * @param startDay
     * @param endDay
     * @param organId
     * @param appointDepartCode
     * @return
     * @throws DAOException
     * @author csy
     */
    @RpcService
    @SuppressWarnings({"rawtypes", "unchecked"})
    public boolean checkDoctorSourceForWeek(final int doctorId,
                                            final Date startDay, final Date endDay, final int organId,
                                            final String appointDepartCode) throws DAOException {
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {

            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "from AppointSource where doctorId=:doctorId and "
                                + " appointDepartCode=:appointDepartCode"
                                + " and organID=:organId"
                                + " and workDate>=:startDay and workDate<:endDay and startTime>NOW()"
                                + " and sourceNum>usedNum and stopFlag=0 and sourceType=1"
                                + " order by startTime asc");
                Query query = ss.createQuery(hql.toString());
                query.setInteger("doctorId", doctorId);
                query.setDate("startDay", startDay);
                query.setDate("endDay", endDay);
                query.setInteger("organId", organId);
                query.setString("appointDepartCode", appointDepartCode);
                List<AppointSource> temp = query.list();
                setResult(temp.size());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        int sourceNum = (int) action.getResult();
        if (sourceNum > 0) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * /** 根据错误码以及号源ID处理
     * <p>
     * 0为成功：将号源UsedNum置为1 1为停诊：将号源更新为停诊 2为重复：根据取号规则返回一个新号源
     * <p>
     * AngryKitty
     *
     * @return
     */
    @RpcService
    public HashMap<String, Object> doSourceByErrCodeAndSourceId(int errCode,
                                                                int appointSourceId) {
        HashMap<String, Object> result = new HashMap<String, Object>();
        AppointSource source = null;
        switch (errCode) {
            case 0:// 号源使用成功
                this.updateUseNumBySourceID(appointSourceId);// 将号源信息更新
                result = null;
                break;
            case 1:// 停诊
                AppointSource oldSource = getByAppointSourceId(appointSourceId);
                updateStopFlagForHisFailed(oldSource.getOrganId(),
                        oldSource.getOrganSchedulingId(), oldSource.getWorkType());
                break;
            case 2:// 号源重复
                this.updateUseNumBySourceID(appointSourceId);
                source = this.getNewAppointSourceByOldID(appointSourceId);
                if (source != null) {
                    result.put("restrictID", source.getOrganSchedulingId() + "");
                    result.put("predate", source.getStartTime() + "");
                    result.put("regno", source.getOrderNum() + "");
                    result.put("appointSourceId", source.getAppointSourceId() + "");
                } else {
                    result = null;
                }
                break;
            default:
                result = null;
                break;
        }
        return result;
    }

    /**
     * 根据appointRecordId 获取 AppointSource
     */
    @RpcService
    @DAOMethod
    public abstract AppointSource getByAppointSourceId(int appointSourceId);

    /**
     * 根据规则找到合适号源信息
     *
     * @param appointSourceId
     * @return
     */
    @RpcService
    public AppointSource getNewAppointSourceByOldID(int appointSourceId) {
        AppointSource source = null;
        AppointSource oldSource = getByAppointSourceId(appointSourceId);
        if (oldSource == null) {
            return null;
        }
        List<AppointSource> sourceList;
        Calendar cal = Calendar.getInstance();
        cal.setTime(oldSource.getStartTime());
        int hours = cal.get(Calendar.HOUR_OF_DAY);
        if (hours < 12) {
            sourceList = this.findAppointSourceByDocAndWorkDateANDFromFlagAM(
                    oldSource.getDoctorId(), oldSource.getWorkDate(),
                    oldSource.getStartTime(), 0);// 获取有效的号源
        } else {
            sourceList = this.findAppointSourceByDocAndWorkDateANDFromFlagPM(
                    oldSource.getDoctorId(), oldSource.getWorkDate(),
                    oldSource.getStartTime(), 0);// 获取有效的号源
        }
        if (sourceList != null && sourceList.size() > 0) {
            source = sourceList.get(0);
        }
        return source;
    }

    /**
     * 查询医生的可用号源信息(上午)(号源非平台自动生成的)
     *
     * @param doctorId
     * @param workDate
     * @param workType
     * @return
     * @author AngryKitty
     */
    @DAOMethod(sql = "FROM AppointSource WHERE doctorId=:doctorId AND workDate=:workDate AND startTime>=:startTime  AND sourceNum>usedNum AND stopFlag=0 AND HOUR(startTime)<12 AND fromFlag=:fromFlag AND stopFlag=0")
    public abstract List<AppointSource> findAppointSourceByDocAndWorkDateANDFromFlagAM(
            @DAOParam("doctorId") Integer doctorId,
            @DAOParam("workDate") Date workDate,
            @DAOParam("startTime") Date startTime,
            @DAOParam("fromFlag") Integer fromFlag);

    /**
     * 查询医生的可用号源信息(下午) (号源非平台自动生成的)
     *
     * @param doctorId
     * @param workDate
     * @param workType
     * @return
     * @author AngryKitty
     */
    @DAOMethod(sql = "FROM AppointSource WHERE doctorId=:doctorId AND workDate=:workDate AND startTime>=:startTime  AND sourceNum>usedNum AND stopFlag=0 AND  HOUR(startTime)>=12 AND fromFlag=:fromFlag AND stopFlag=0")
    public abstract List<AppointSource> findAppointSourceByDocAndWorkDateANDFromFlagPM(
            @DAOParam("doctorId") Integer doctorId,
            @DAOParam("workDate") Date workDate,
            @DAOParam("startTime") Date startTime,
            @DAOParam("fromFlag") Integer fromFlag);

    @Override
    public void setDefaultSortInfo(String defaultSortInfo) {
        super.setDefaultSortInfo(defaultSortInfo);
    }

    /**
     * 查询医生日期号源服务--新增云门诊判断(输出格式改变)
     *
     * @param doctorId   --医生编号
     * @param sourceType --号源类别
     * @return List<HashMap<String, Object>>
     * @throws ControllerException
     * @author luf
     */
    @RpcService
    @SuppressWarnings("unchecked")
    public List<HashMap<String, Object>> convertTotalForIOS(int doctorId,
                                                            int sourceType) throws ControllerException {
        DoctorDAO d = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doctor = d.get(doctorId);
        Integer organid = doctor.getOrgan();
        List<DoctorDateSource> dateSources = new ArrayList<>();
        this.hisByDoctorData(doctorId, sourceType);
        dateSources = this.totalByDoctorDateAndCloudClinic(doctorId, sourceType);


        List<HashMap<String, Object>> list = new ArrayList<HashMap<String, Object>>();

        for (DoctorDateSource dateSource : dateSources) {
            Map<String, Object> object = new HashMap<String, Object>();
            List<DoctorDateSource> sources = new ArrayList<DoctorDateSource>();
            Integer oragnID = dateSource.getOragnID();
            String professionCode = dateSource.getProfessionCode();
            int mach = -1;
            for (int i = 0; i < list.size(); i++) {
                Map<String, Object> hashMap = list.get(i);
                if (oragnID.equals(hashMap.get("oragnID"))
                        && professionCode.equals(hashMap.get("professionCode"))) {
                    mach = i;
                    break;
                }
            }
            if (mach >= 0) {
                sources = (List<DoctorDateSource>) list.get(mach).get(
                        "dateSource");
                sources.add(dateSource);
                (list.get(mach)).put("dateSource", sources);
            } else {
                String organName = DictionaryController.instance()
                        .get("eh.base.dictionary.Organ").getText(oragnID);
                String professionName = dateSource.getProfessionName();
                sources.add(dateSource);
                object.put("oragnID", oragnID);
                object.put("organName", organName);
                object.put("professionCode", professionCode);
                object.put("professionName", professionName);
                object.put("dateSource", sources);
                list.add((HashMap<String, Object>) object);
            }
        }
        return list;
    }

    /**
     * 供 可用接诊时段号源查询服务(输出格式改变) 调用
     *
     * @param appointSources 号源列表
     * @return List<HashMap<String, Object>>
     * @throws ControllerExceptionqueryDoctorSource
     * @author luf
     */
    @SuppressWarnings("unchecked")
    @RpcService
    public List<HashMap<String, Object>> convertQueryDoctorSourceCloud(
            List<AppointSource> appointSources) throws ControllerException {
        List<HashMap<String, Object>> list = new ArrayList<HashMap<String, Object>>();
        for (AppointSource appointSource : appointSources) {
            Map<String, Object> object = new HashMap<String, Object>();
            List<AppointSource> sources = new ArrayList<AppointSource>();
            String clinicAddr = appointSource.getClinicAddr();
            String appointDepartCode = appointSource.getAppointDepartCode();
            Integer doctorId = appointSource.getDoctorId();
            int mach = -1;
            for (int i = 0; i < list.size(); i++) {
                Map<String, Object> hashMap = list.get(i);
                if (doctorId.equals(hashMap.get("doctorId"))
                        && appointDepartCode.equals(hashMap
                        .get("appointDepartCode"))
                        && clinicAddr.equals(hashMap.get("clinicAddr"))) {
                    mach = i;
                    break;
                }
            }
            if (mach >= 0) {
                sources = (List<AppointSource>) list.get(mach).get(
                        "appointSource");
                sources.add(appointSource);
                (list.get(mach)).put("appointSource", sources);
            } else {
                String appointDepartName = appointSource.getAppointDepartName();
                Integer organId = appointSource.getOrganId();
                String organName = DictionaryController.instance()
                        .get("eh.base.dictionary.Organ").getText(organId);
                String doctorName = DictionaryController.instance()
                        .get("eh.base.dictionary.Doctor").getText(doctorId);
                sources.add(appointSource);
                object.put("organId", organId);
                object.put("organName", organName);
                object.put("doctorId", doctorId);
                object.put("doctorName", doctorName);
                object.put("appointDepartCode", appointDepartCode);
                object.put("appointDepartName", appointDepartName);
                object.put("clinicAddr", clinicAddr);
                object.put("appointSource", sources);
                list.add((HashMap<String, Object>) object);
            }
        }
        return list;
    }

    /**
     * 可用接诊时段号源查询服务(输出格式改变)
     *
     * @param inAddrArea  接诊区域代码
     * @param inOrganId   接诊机构内码
     * @param outDoctorId 出诊医生内码
     * @param outWorkDate 出诊工作日期
     * @param workType    值班类别
     * @return Map<String, Object>
     * @throws Exception
     * @author luf
     */
    @RpcService
    public Map<String, Object> queryDoctorSourceCloudConvertOut(
            final String inAddrArea, final Integer inOrganId,
            final Integer outDoctorId, final Date outWorkDate,
            final Integer workType) throws Exception {
        Map<String, Object> map = this.queryDoctorSourceCloud(inAddrArea,
                inOrganId, outDoctorId, outWorkDate, workType);
        List<AppointSourceListAndDoctor> empty = new ArrayList<AppointSourceListAndDoctor>();
        List<HashMap<String, Object>> empo = new ArrayList<HashMap<String, Object>>();
        if (map == null) {
            map = new HashMap<String, Object>();
            map.put("outSource", empo);
            map.put("inSource", empty);
            return map;
        }
        @SuppressWarnings("unchecked")
        Object datas1 = map.get("outSource");
        if (datas1 != null) {
            List<AppointSource> datas = (List<AppointSource>) datas1;
            List<HashMap<String, Object>> list = this.convertQueryDoctorSourceCloud(datas);
            map.remove("outSource");
            if (list != null && !list.isEmpty()) {
                map.put("outSource", list);
            } else {
                map.put("outSource", empo);
            }
        } else {
            map.remove("outSource");
            map.put("outSource", empo);
        }
        if (map.get("inSource") != null && ((List<AppointSourceListAndDoctor>) map.get("inSource")).isEmpty()) {
            map.remove("inSource");
            map.put("inSource", empty);
        }
        return map;
    }

    /**
     * 只用于生成测试库数据普通号源
     */
    public void createAppointSource(int doctorID) {
        AppointSource appointSource = new AppointSource();
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doctor = doctorDAO.getByDoctorId(doctorID);
        AppointDepartDAO appointDepartDAO = DAOFactory
                .getDAO(AppointDepartDAO.class);

        List<AppointDepart> appointDepartList = appointDepartDAO
                .findAllByOrganId(doctor.getOrgan());
        AppointDepart appointDepart = appointDepartList.get(0);
        appointSource.setOrganId(doctor.getOrgan());
        appointSource.setOrganSchedulingId("");
        appointSource
                .setAppointDepartCode(appointDepart.getAppointDepartCode());
        appointSource
                .setAppointDepartName(appointDepart.getAppointDepartName());
        appointSource.setClinicAddr("手动生成地址");
        appointSource.setDoctorId(doctorID);
        Date workdate = getDateAftXDays(new Date(), 7);// 7天后的号源
        appointSource.setWorkDate(workdate);
        appointSource.setWorkType(1);
        appointSource.setSourceType(1);
        appointSource.setSourceLevel(3);
        appointSource.setPrice(100.0d);
        appointSource.setOrganSchedulingId(new Date().getTime() + "");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        String workdateStr = df.format(workdate);
        appointSource.setOrganSourceId(workdateStr + "|"
                + appointSource.getOrganSchedulingId() + "|"
                + appointSource.getWorkType() + "|1");
        workdateStr += " 09:00:00";
        appointSource.setStartTime(getCurrentDate(workdateStr,
                "yyyy-MM-dd hh:mm:ss"));
        appointSource.setEndTime(getCurrentDate(workdateStr,
                "yyyy-MM-dd hh:mm:ss"));
        appointSource.setSourceNum(33);
        appointSource.setUsedNum(0);
        appointSource.setOrderNum(1);
        appointSource.setStopFlag(0);
        appointSource.setCreateDate(new Date());
        appointSource.setOriginalSourceId("手动生成");
        appointSource.setFromFlag(0);

        AppointSourceDAO appointSourceDAO = DAOFactory
                .getDAO(AppointSourceDAO.class);
        appointSourceDAO.save(appointSource);
        // 更新医生是否有号源标志
        doctorDAO.updateHaveAppointByDoctorId(doctorID, 1);

    }

    /**
     * 创建云门诊号源
     */
    public void createAppointSourceAndCloudClinic(int doctorID) {
        AppointSource appointSource = new AppointSource();
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doctor = doctorDAO.getByDoctorId(doctorID);
        AppointDepartDAO appointDepartDAO = DAOFactory
                .getDAO(AppointDepartDAO.class);

        List<AppointDepart> appointDepartList = appointDepartDAO
                .findAllByOrganId(doctor.getOrgan());
        AppointDepart appointDepart = appointDepartList.get(0);
        appointSource.setOrganId(doctor.getOrgan());
        appointSource.setOrganSchedulingId("");
        appointSource
                .setAppointDepartCode(appointDepart.getAppointDepartCode());
        appointSource
                .setAppointDepartName(appointDepart.getAppointDepartName());
        appointSource.setClinicAddr("手动生成地址");
        appointSource.setDoctorId(doctorID);
        Date workdate = getDateAftXDays(new Date(), 7);// 7天后的号源
        appointSource.setWorkDate(workdate);
        appointSource.setWorkType(1);
        appointSource.setSourceType(1);
        appointSource.setSourceLevel(3);
        appointSource.setPrice(100.0d);
        appointSource.setOrganSchedulingId(new Date().getTime() + "");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        String workdateStr = df.format(workdate);
        appointSource.setOrganSourceId(workdateStr + "|"
                + appointSource.getOrganSchedulingId() + "|"
                + appointSource.getWorkType() + "|1");
        workdateStr += " 09:00:00";
        appointSource.setStartTime(getCurrentDate(workdateStr,
                "yyyy-MM-dd hh:mm:ss"));
        appointSource.setEndTime(getCurrentDate(workdateStr,
                "yyyy-MM-dd hh:mm:ss"));
        appointSource.setSourceNum(33);
        appointSource.setUsedNum(0);
        appointSource.setOrderNum(1);
        appointSource.setStopFlag(0);
        appointSource.setCreateDate(new Date());
        appointSource.setOriginalSourceId("手动生成");
        appointSource.setFromFlag(0);
        appointSource.setCloudClinic(1);
        appointSource.setCloudClinicType(1);

        AppointSourceDAO appointSourceDAO = DAOFactory
                .getDAO(AppointSourceDAO.class);
        appointSourceDAO.save(appointSource);
        // 更新医生是否有号源标志
        doctorDAO.updateHaveAppointByDoctorId(doctorID, 1);

    }

    /**
     * @param
     * @return void
     * @throws
     * @Class eh.bus.dao.AppointSourceDAO.java
     * @Title: fromScheduleAndAllotToSource
     * @Description: TODO 定时执行，生成号源
     * @Date 2015-12-1下午3:01:52
     */
    @RpcService
    public void fromScheduleAndTempToSource() {
        AppointScheduleDAO addao = DAOFactory.getDAO(AppointScheduleDAO.class);
        EmploymentDAO emdao = DAOFactory.getDAO(EmploymentDAO.class);
        TempScheduleDAO tempDao = DAOFactory.getDAO(TempScheduleDAO.class);
        SourceAllotDAO allotDao = DAOFactory.getDAO(SourceAllotDAO.class);
        List<AppointSchedule> ads = addao.findAllEffectiveSchedule(0);
        for (AppointSchedule ad : ads) {
            int week = ad.getWeek();
            Date now = new Date();
            int max = ad.getMaxRegDays();
            long dTime = max * 1000L * 60 * 60 * 24;
            long nowTime = now.getTime();
            long maxDateTime = nowTime + dTime;
            if (ad.getLastGenDate() != null) {
                now = ad.getLastGenDate();
            }
            while (now.getTime() <= maxDateTime) {
                List<Date> dates = addao.findTimeSlotByThree(now, max, week);
                Date lastDate = DateConversion.getFormatDate(dates.get(1),
                        "yyyy-MM-dd");
                int doctorId = ad.getDoctorId();

                // 判断该日是否包含已经生效的临时排班
                List<TempSchedule> temps = tempDao
                        .findTempsByWorkDateAndDoctorID(lastDate, doctorId);
                if (temps != null && temps.size() > 0) {
                    logger.info("该日有已生效的临时排班数据：" + JSONUtils.toString(temps));
                    return;
                }
                long lastDateTime = lastDate.getTime();
                if (lastDateTime <= maxDateTime) {
                    DateFormat df = DateFormat.getTimeInstance();// 只显示出时分秒
                    String startTimeString = df.format(ad.getStartTime());
                    String endTimeString = df.format(ad.getEndTime());
                    Date startTime = DateConversion.getDateByTimePoint(
                            lastDate, startTimeString);
                    Date endTime = DateConversion.getDateByTimePoint(lastDate,
                            endTimeString);
                    int organId = ad.getOrganId();
                    int department = ad.getDepartId();
                    int sourceLevel = ad.getClinicType();
                    double price = 0.0;
                    Employment e = emdao.getByDocAndOrAndDep(doctorId, organId,
                            department);
                    switch (sourceLevel) {
                        case 1:
                            price = e.getClinicPrice();
                            break;
                        case 2:
                            price = e.getProfClinicPrice();
                            break;
                        case 3:
                            price = e.getSpecClinicPrice();
                            break;
                        default:
                            price=0.0;
                            break;
                    }
                    AppointSource a = new AppointSource();
                    a.setDoctorId(doctorId);
                    a.setOrganId(organId);
                    a.setSourceNum(ad.getSourceNum());
                    a.setSourceType(ad.getSourceType());
                    a.setWorkType(ad.getWorkType());
                    a.setStartTime(startTime);
                    a.setEndTime(endTime);
                    a.setWorkDate(lastDate);
                    a.setAppointDepartCode(ad.getAppointDepart());
                    a.setOrganSchedulingId(ad.getScheduleId().toString());
                    a.setCloudClinic(ad.getTelMedFlag());
                    a.setCloudClinicType(ad.getTelMedType());
                    a.setSourceLevel(sourceLevel);
                    a.setPrice(price);
                    a.setClinicAddr(ad.getWorkAddr());

                    // 生成号源
                    Integer total = Integer.valueOf(allotDao
                            .getSumByScheduleId(ad.getScheduleId()).toString());
                    if (total.equals(ad.getSourceNum())  && !total.equals(0) ) {
                        this.addOneSourceByAllot(a, ad.getGenMode());
                        ad.setLastGenDate(lastDate);
                    } else {
                        logger.error("该排班号源个数(" + ad.getSourceNum()
                                + ")不等于号源生成规则总数(" + total + "),【"
                                + ad.getScheduleId() + "】");
                    }

                }
                now = lastDate;
            }
            addao.update(ad);
        }
    }

    /**
     * @param @param  a
     * @param @return
     * @param @throws DAOException
     * @return List<Integer>
     * @throws
     * @Class eh.bus.dao.AppointSourceDAO.java
     * @Title: addOneSourceByAllot
     * @Description: TODO 根据号源生成规则插入号源表
     * @author AngryKitty
     * @Date 2015-12-1下午4:50:19
     */
    @RpcService
    public List<Integer> addOneSourceByAllot(AppointSource a, int genMode)
            throws DAOException {
        logger.info("新增号源信息 AppointSourceDAO==> addOneSourceByAllot <==="
                + "a:" + JSONUtils.toString(a));
        SourceAllotDAO allotDao = DAOFactory.getDAO(SourceAllotDAO.class);

        if (a == null || a.getDoctorId() == null || a.getOrganId() == null
                || a.getSourceNum() == null || a.getSourceNum() <= 0
                || a.getSourceType() == null || a.getWorkType() == null
                || a.getEndTime() == null || a.getStartTime() == null
                || a.getWorkDate() == null
                || a.getEndTime().before(a.getStartTime())
                || StringUtils.isEmpty(a.getAppointDepartCode())) {
            return null;
        }
        if (a.getStopFlag() == null) {
            a.setStopFlag(0);
        }
        if (StringUtils.isEmpty(a.getAppointDepartName())) {
            a.setAppointDepartName(DAOFactory
                    .getDAO(AppointDepartDAO.class)
                    .getByOrganIDAndAppointDepartCode(a.getOrganId(),
                            a.getAppointDepartCode()).getAppointDepartName());
        }
        a.setFromFlag(1);
        a.setUsedNum(0);
        a.setCreateDate(new Date());
        List<Integer> ids = new ArrayList<Integer>();
        int avg = a.getSourceNum();
        List<Object[]> os = DateConversion.getAverageTime(a.getStartTime(),
                a.getEndTime(), avg);
        Integer orderNum = this.getMaxOrderNum(a.getDoctorId(), a.getOrganId(),
                a.getWorkDate());
        if (orderNum == null) {
            orderNum = 0;
        }
        a.setSourceNum(1);

        // 获取该排班的号源生成规则
        List<SourceAllot> allots = allotDao.findByScheduleId(Integer.parseInt(a
                .getOrganSchedulingId()));

        if (allots == null) {
            logger.error("该排班无对应生成号源规则【" + a.getOrganSourceId() + "】");
            return null;
        }
        int sum = allots.size();
        int i = 0;
        for (Object[] o : os) {
            AppointSource ac = a;
            switch (genMode) {
                case 1:// 固定顺序模式
                    if (allots.get(i).getSourceNum() > 0) {
                        allots.get(i)
                                .setSourceNum(allots.get(i).getSourceNum() - 1);
                    } else {
                        i++;
                    }
                    ac.setSourceType(allots.get(i).getSourceType());
                    break;
                case 2:// 间隔顺序模式
                    while (allots.get(i).getSourceNum() <= 0) {
                        i++;
                        if (i == sum) {
                            i = 0;
                        }
                    }
                    allots.get(i).setSourceNum(allots.get(i).getSourceNum() - 1);
                    ac.setSourceType(allots.get(i).getSourceType());
                    i++;
                    if (i == sum) {
                        i = 0;
                    }
                    break;
                case 3:// 随机顺序模式'
                    i = (int) (Math.random() * sum);
                    allots.get(i).setSourceNum(allots.get(i).getSourceNum() - 1);
                    ac.setSourceType(allots.get(i).getSourceType());
                    if (allots.get(i).getSourceNum() == 0) {
                        allots.remove(i);
                        sum -= 1;
                    }
                    break;
                default:
                    throw new DAOException(" genMode is not allowed");
            }

            ac.setStartTime((Date) o[0]);
            ac.setEndTime((Date) o[1]);
            ac.setOrderNum(++orderNum);
            ids.add(save(ac).getAppointSourceId());
        }
        if (ids.size() <= 0) {
            return null;
        }
        return ids;
    }

    /**
     * @param @param  workDate 日期
     * @param @param  doctorId 医生编码
     * @param @return
     * @return List<AppointSource>
     * @throws
     * @Class eh.bus.dao.AppointSourceDAO.java
     * @Title: findByWorkDateAndDoctorId
     * @Description: TODO 查询医生某日的有效号源情况（平台生成的号源）
     * @author AngryKitty
     * @Date 2015-12-2下午2:16:50
     */
    @RpcService
    @DAOMethod(sql = " from AppointSource where workDate=:workDate and doctorId =:doctorId and stopFlag =0 and fromFlag = 1")
    public abstract List<AppointSource> findByWorkDateAndDoctorId(
            @DAOParam("workDate") Date workDate,
            @DAOParam("doctorId") Integer doctorId);

    /**
     * 放在AppointSourceDAO 类中
     *
     * @param
     * @return void
     * @Description: TODO 临时排班生成号源
     * @author Zhongzx
     * @Date 2015-12-3下午1:03:24
     */
    @RpcService
    public void fromTempScheduleToSource() {
        TempScheduleDAO dao = DAOFactory.getDAO(TempScheduleDAO.class);
        EmploymentDAO empdao = DAOFactory.getDAO(EmploymentDAO.class);
        // 找出未生效并且工作的临时排班
        List<TempSchedule> tlist = dao.findByFlagAndWorkFlag(false, 0);
        Date now = new Date();
        for (TempSchedule t : tlist) {
            List<AppointSource> alist = this.findByWorkDateAndDoctorId(
                    t.getWorkDate(), t.getDoctorId());
            // 当天有正常号源，报错
            if (alist != null && alist.size() > 0) {
                logger.error(t.getScheduleId() + "当天有正常号源");
                return;
            }
            // 当天之后的未生效排班
            if ((t.getWorkDate().getTime()) >= now.getTime()) {
                DateFormat df = DateFormat.getTimeInstance();// 只显示出时分秒
                String startTimeString = df.format(t.getStartTime());
                String endTimeString = df.format(t.getEndTime());
                Date startTime = DateConversion.getDateByTimePoint(
                        t.getWorkDate(), startTimeString);
                Date endTime = DateConversion.getDateByTimePoint(
                        t.getWorkDate(), endTimeString);
                int doctorId = t.getDoctorId();
                int organId = t.getOrganId();
                int department = t.getDepartId();
                int sourceLevel = t.getClinicType();
                double price = 0.0;
                Employment e = empdao.getByDocAndOrAndDep(doctorId, organId,
                        department);
                switch (sourceLevel) {
                    case 1:
                        price = e.getClinicPrice();
                        break;
                    case 2:
                        price = e.getProfClinicPrice();
                        break;
                    case 3:
                        price = e.getSpecClinicPrice();
                    default:
                        break;
                }
                AppointSource as = new AppointSource();
                as.setDoctorId(doctorId);
                as.setOrganId(organId);
                as.setSourceNum(t.getSourceNum());
                as.setSourceType(t.getSourceType());
                as.setWorkType(t.getWorkType());
                as.setStartTime(startTime);
                as.setEndTime(endTime);
                as.setWorkDate(t.getWorkDate());
                as.setAppointDepartCode(t.getAppointDepart());
                as.setOrganSchedulingId(t.getScheduleId().toString());
                as.setCloudClinic(t.getTelMedFlag());
                as.setCloudClinicType(t.getTelMedType());
                as.setSourceLevel(sourceLevel);
                as.setPrice(price);
                as.setClinicAddr(t.getWordAddr());
                as.setFromFlag(2);
                this.addOneSourceFromTemp(as);
            }
            t.setFlag(true);
            dao.update(t);
        }
    }

    /**
     * 放在AppointSourceDAO 类中
     *
     * @param @param  a 号源对象
     * @param @return
     * @param @throws DAOException
     * @return List<Integer> 号源序号
     * @Description: TODO 为了增加从临时排班生成的号源 把fromFlag 设为 2 表示来源是临时排班
     * @author Zhongzx
     * @Date 2015-12-3下午6:08:16
     */
    @RpcService
    public List<Integer> addOneSourceFromTemp(AppointSource a)
            throws DAOException {
        logger.info("新增号源信息 AppointSourceDAO==> addOneSource <===" + "a:"
                + JSONUtils.toString(a));
        if (a == null || a.getDoctorId() == null || a.getOrganId() == null
                || a.getSourceNum() == null || a.getSourceNum() <= 0
                || a.getSourceType() == null || a.getWorkType() == null
                || a.getEndTime() == null || a.getStartTime() == null
                || a.getWorkDate() == null
                || a.getEndTime().before(a.getStartTime())
                || StringUtils.isEmpty(a.getAppointDepartCode())) {
            return null;
        }
        if (a.getStopFlag() == null) {
            a.setStopFlag(0);
        }
        if (StringUtils.isEmpty(a.getAppointDepartName())) {
            a.setAppointDepartName(DAOFactory
                    .getDAO(AppointDepartDAO.class)
                    .getByOrganIDAndAppointDepartCode(a.getOrganId(),
                            a.getAppointDepartCode()).getAppointDepartName());
        }
        a.setFromFlag(2);
        a.setUsedNum(0);
        a.setCreateDate(new Date());
        List<Integer> ids = new ArrayList<Integer>();
        int avg = a.getSourceNum();
        List<Object[]> os = DateConversion.getAverageTime(a.getStartTime(),
                a.getEndTime(), avg);
        Integer orderNum = this.getMaxOrderNum(a.getDoctorId(), a.getOrganId(),
                a.getWorkDate());
        if (orderNum == null) {
            orderNum = 0;
        }
        a.setSourceNum(1);
        for (Object[] o : os) {
            AppointSource ac = new AppointSource();
            ac = a;
            ac.setStartTime((Date) o[0]);
            ac.setEndTime((Date) o[1]);
            ac.setOrderNum(++orderNum);
            ids.add(save(ac).getAppointSourceId());
        }
        if (ids.size() <= 0) {
            return null;
        }
        return ids;
    }

    /**
     * 供totalByDoctorDateForHealth调用
     * <p>
     * eh.bus.dao
     *
     * @param doctorId
     * @param sourceType
     * @return List<AppointSource>
     * @author luf 2016-2-1
     */
    // 2016-9-22 luf:由于实时挂号不上线将startTime>NOW()改为workDate>NOW()
    @DAOMethod(sql = "select SUM(a.sourceNum-a.usedNum),a from AppointSource a ,HisServiceConfig c " +
            "where c.organid=a.organId and  a.doctorId=:doctorId and a.sourceNum-a.usedNum>0" +
            " and a.sourceType=:sourceType and " +
            "( workDate>NOW() or ( endTime>NOW() and c.canAppoint is not null ) ) " +
            "and (a.cloudClinic<>1 or a.cloudClinic is null) and a.stopFlag=0 " +
            "group by a.appointDepartCode,a.workDate,a.price,a.workType,a.sourceLevel order by a.workDate")
    public abstract List<Object[]> findTotalByDcotorId(
            @DAOParam("doctorId") Integer doctorId,
            @DAOParam("sourceType") Integer sourceType);

    /**
     * 供totalByDoctorDateForHealthWithOrgan调用
     * <p>
     * eh.bus.dao
     *
     * @param doctorId
     * @param sourceType
     * @return List<AppointSource>
     * @author luf 2016-2-1
     */
    // 2016-9-22 luf:由于实时挂号不上线将startTime>NOW()改为workDate>NOW()
    @DAOMethod(sql = "select SUM(a.sourceNum-a.usedNum),a from AppointSource a ,HisServiceConfig c " +
            "where a.organId=:organId and c.organid=a.organId and  a.doctorId=:doctorId and a.sourceNum-a.usedNum>0" +
            " and a.sourceType=:sourceType and " +
            "( workDate>NOW() or ( endTime>NOW() and c.canAppoint is not null ) ) " +
            "and (a.cloudClinic<>1 or a.cloudClinic is null) and a.stopFlag=0 " +
            "group by a.appointDepartCode,a.workDate,a.price,a.workType,a.sourceLevel order by a.workDate")
    public abstract List<Object[]> findTotalByDcotorIdWithOrgan(
            @DAOParam("doctorId") Integer doctorId,
            @DAOParam("sourceType") Integer sourceType, @DAOParam("organId") Integer organId);


    /**
     * 根据机构-科室筛选医生实时号源
     * 供 effectiveSourceDoctorsForhealth 接口调用
     *
     * @param doctorId
     * @param sourceType
     * @param organId
     * @param appointDepartCode
     * @param date
     * @return List<Object[]>
     * @author houxr 2017-04-08
     */
    public List<Object[]> findHaveSourceByDoctorAndAppointDepartCode(final Integer doctorId, final Integer sourceType,
                                                                     final Integer organId, final String appointDepartCode,
                                                                     final Date date) {
        logger.info("机构-科室查询医生实时号源param:doctorId["+doctorId+"]," +
                "sourceType["+sourceType+"],organId["+organId+"],appointDepartCode["+appointDepartCode+"],date["+date+"]");
        HibernateStatelessResultAction<List<Object[]>> action = new AbstractHibernateStatelessResultAction<List<Object[]>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuffer hql = new StringBuffer("select SUM(a.sourceNum-a.usedNum),a from AppointSource a ,HisServiceConfig c " +
                        "where c.organid=a.organId AND a.organId=:organId AND a.doctorId=:doctorId AND a.sourceNum-a.usedNum>0" +
                        " AND a.sourceType=:sourceType AND a.appointDepartCode =:appointDepartCode ");
                if (!ObjectUtils.isEmpty(date)) {
                    hql.append(" AND a.workDate=:workDate AND a.endTime>=:endTime ");
                } else {
                    hql.append(" AND ( a.workDate>=:currentTime or ( a.endTime>=:currentTime AND c.canAppoint is not null ) ) ");
                }
                hql.append(" AND (a.cloudClinic<>1 or a.cloudClinic is null) AND a.stopFlag=0 ");
                hql.append(" group By a.appointDepartCode,a.workDate,a.price,a.workType,a.sourceLevel order by a.workDate");
                Query q = ss.createQuery(hql.toString());
                if (!ObjectUtils.isEmpty(date)) {
                    HisServiceConfigDAO hscDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
                    Boolean can = hscDao.isCanAppoint(organId);
                    q.setParameter("workDate", DateConversion.getFormatDate(date, "yyyy-MM-dd"));
                    if (can) {
                        Date date = new Date();
                        q.setParameter("endTime", DateConversion.getFormatDate(date, "yyyy-MM-dd"));
                    } else {
                        q.setParameter("endTime", DateConversion.getFormatDate(DateConversion.getDaysAgo(-1), "yyyy-MM-dd"));
                    }
                }
                q.setParameter("doctorId", doctorId);
                q.setParameter("organId", organId);
                q.setParameter("sourceType", sourceType);
                q.setParameter("appointDepartCode", appointDepartCode);
                q.setParameter("currentTime", new Date());
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<Object[]> doctors = action.getResult();
        return doctors;
    }

    /**
     * 根据机构-科室筛选医生实时号源
     * 供 effectiveSourceDoctorsForhealth 接口调用
     * 仅将findHaveSourceByDoctorAndAppointDepartCode入参改为list
     *
     * @param doctorId
     * @param sourceType
     * @param organId
     * @param appointDepartCode
     * @param date
     * @return List<Object[]>
     * @author houxr 2017-04-08
     */
    public List<Object[]> findHaveSourcesByDoctorAndAppointDepartCodes(final Integer doctorId, final Integer sourceType,
                                                                     final Integer organId, final List<String> appointDepartCode,
                                                                     final Date date) {
        logger.info("机构-科室查询医生实时号源param:doctorId["+doctorId+"]," +
                "sourceType["+sourceType+"],organId["+organId+"],appointDepartCode["+JSONUtils.toString(appointDepartCode)+"],date["+date+"]");
        HisServiceConfigDAO hscDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
        final Boolean can = hscDao.isCanAppoint(organId);
// 去除和hisServiceConfig联表查询
        HibernateStatelessResultAction<List<Object[]>> action = new AbstractHibernateStatelessResultAction<List<Object[]>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuffer hql = new StringBuffer("select SUM(a.sourceNum-a.usedNum),a from AppointSource a  " +
                        "where  a.organId=:organId AND a.doctorId=:doctorId AND a.sourceNum-a.usedNum>0" +
                        " AND a.sourceType=:sourceType AND a.appointDepartCode in(:appointDepartCode) ");
                if (!ObjectUtils.isEmpty(date)) {
                    hql.append(" AND a.workDate=:workDate AND a.endTime>=:endTime ");
                } else {

                    if(can){
                        hql.append(" AND  a.endTime>=:currentTime ");
                    }else
                        hql.append(" AND  a.workDate>=:currentTime ");
                }
                hql.append(" AND (a.cloudClinic<>1 or a.cloudClinic is null) AND a.stopFlag=0 ");
                hql.append(" group By a.appointDepartCode,a.workDate,a.price,a.workType,a.sourceLevel order by a.workDate");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("currentTime", new Date());
                if (!ObjectUtils.isEmpty(date)) {

                    q.setParameter("workDate", DateConversion.getFormatDate(date, "yyyy-MM-dd"));
                    if (can) {
                        Date date = new Date();
                        q.setParameter("endTime", DateConversion.getFormatDate(date, "yyyy-MM-dd"));
                    } else {
                        q.setParameter("endTime", DateConversion.getFormatDate(DateConversion.getDaysAgo(-1), "yyyy-MM-dd"));
                    }
                }
                q.setParameter("doctorId", doctorId);
                q.setParameter("organId", organId);
                q.setParameter("sourceType", sourceType);
                q.setParameterList("appointDepartCode", appointDepartCode);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<Object[]> doctors = action.getResult();
        return doctors;
    }


    /**
     * 供totalByDoctorDateForHealthWithOrgan调用
     * <p>
     * eh.bus.dao
     *
     * @param doctorId
     * @param sourceType
     * @return List<AppointSource>
     * @author luf 2016-2-1
     */
    // 2016-9-22 luf:由于实时挂号不上线将startTime>NOW()改为workDate>NOW()
    public List<Object[]> findTotalByDcotorIdForHealth(final Integer doctorId, final Integer sourceType) {
        HibernateStatelessResultAction<List<Object[]>> action = new AbstractHibernateStatelessResultAction<List<Object[]>>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                // 2017-1-18 luf：增加个性化需求
                OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
                List<Integer> organs = organDAO.findOrgansByUnitForHealth();

                StringBuilder hql = new StringBuilder("select SUM(a.sourceNum-a.usedNum),a from AppointSource a ,HisServiceConfig c " +
                        "where c.organid=a.organId and  a.doctorId=:doctorId and a.sourceNum-a.usedNum>0" +
                        " AND a.endTime>=:currentTime "+
                        " and a.sourceType=:sourceType and " +
                        "( workDate>:currentTime or ( endTime>=:currentTime and c.canAppoint is not null ) ) " + //预约号  or 当天号
                        "and (a.cloudClinic<>1 or a.cloudClinic is null) and a.stopFlag=0 ");
                if (organs != null && organs.size() > 0) {
                    hql.append(" and (");
                    for (Integer i : organs) {
                        hql.append("a.organId=").append(i).append(" or ");
                    }
                    hql = new StringBuilder(hql.substring(0, hql.length() - 3)).append(")");
                }
                hql.append(" group by a.appointDepartCode,a.workDate,a.price,a.workType,a.sourceLevel order by a.workDate,a.startTime");

                Query q = statelessSession.createQuery(hql.toString());
                q.setParameter("doctorId", doctorId);
                q.setParameter("sourceType", sourceType);
                q.setParameter("currentTime", new Date());
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 供totalByDoctorDateForHealthWithOrganAndDate调用
    * @Author:gey
    * @Description:当已经完成日期筛选
    * @Params:
    * @Date:20:10 2017-03-22
    */
    public List<Object[]> findTotalByDcotorIdForHealth(final Integer doctorId, final Integer sourceType ,final Date date) {
        HibernateStatelessResultAction<List<Object[]>> action = new AbstractHibernateStatelessResultAction<List<Object[]>>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                // 2017-1-18 luf：增加个性化需求
                OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
                List<Integer> organs = organDAO.findOrgansByUnitForHealth();

                StringBuilder hql = new StringBuilder("select SUM(a.sourceNum-a.usedNum),a from AppointSource a ,HisServiceConfig c " +
                        "where c.organid=a.organId and  a.doctorId=:doctorId and a.sourceNum-a.usedNum>0" +
                        " AND a.endTime>=:currentTime "+
                        " and a.sourceType=:sourceType and " +
                        "( workDate>=:currentTime  or ( endTime>=:currentTime  and c.canAppoint is not null ) ) " +
                        "and (a.cloudClinic<>1 or a.cloudClinic is null) and a.stopFlag=0 ");
                if (organs != null && organs.size() > 0) {
                    hql.append(" and (");
                    for (Integer i : organs) {
                        hql.append("a.organId=").append(i).append(" or ");
                    }
                    hql = new StringBuilder(hql.substring(0, hql.length() - 3)).append(")");
                }
                if(date!=null){
                    hql.append("and a.workDate=:workDate");
                }
                hql.append(" group by a.appointDepartCode,a.workDate,a.price,a.workType,a.sourceLevel order by a.workDate,startTime");
                Query q = statelessSession.createQuery(hql.toString());
                q.setParameter("doctorId", doctorId);
                q.setParameter("sourceType", sourceType);
                if(date!=null){
                    q.setParameter("workDate",date);
                }
                q.setParameter("currentTime", new Date());
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }


    /**
     * 供totalByDoctorDateForHealthWithOrganAndDate调用
    * @Author:gey
    * @Description:
    * @Params:
    * @Date:20:14 2017-03-22
    */
    public List<Object[]> findTotalByDcotorIdWithOrganForHealth(
            final Integer doctorId, final Integer sourceType, final Integer organId,final Date date ,final Integer depart) {
        HibernateStatelessResultAction<List<Object[]>> action = new AbstractHibernateStatelessResultAction<List<Object[]>>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                // 2017-1-18 luf：增加个性化需求
                OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
                List<Integer> organs = organDAO.findOrgansByUnitForHealth();
                AppointDepartDAO appointDepartDAO = DAOFactory.getDAO(AppointDepartDAO.class);
                List<String> appointDepartCode = new ArrayList<String>();
                if (depart != null && depart > 0 && organId != null && organId > 0) {
                    appointDepartCode = appointDepartDAO.findListByOrganIDAndDepartID(organId, depart);
                }
                StringBuilder hql = new StringBuilder("select SUM(a.sourceNum-a.usedNum),a from AppointSource a ,HisServiceConfig c " +
                        "where a.organId=:organId and c.organid=a.organId and  a.doctorId=:doctorId and a.sourceNum-a.usedNum>0" +
                        " and a.sourceType=:sourceType " +
                        " AND a.endTime>=:currentTime "+
                        "and ( workDate>=:currentTime  or ( endTime>=:currentTime  and c.canAppoint is not null ) ) " +
                        "and (a.cloudClinic<>1 or a.cloudClinic is null) and a.stopFlag=0 ");
                /*StringBuilder hql = new StringBuilder("select SUM(a.sourceNum-a.usedNum),a from AppointSource a ,HisServiceConfig c " +
                        "where a.organId=:organId and c.organid=a.organId and  a.doctorId=:doctorId and a.sourceNum-a.usedNum>0" +
                        " and a.sourceType=:sourceType and " +
                        "and (a.cloudClinic<>1 or a.cloudClinic is null) and a.stopFlag=0 ");*/
                if (organs != null && organs.size() > 0) {
                    hql.append(" and (");
                    for (Integer i : organs) {
                        hql.append("a.organId=").append(i).append(" or ");
                    }
                    hql = new StringBuilder(hql.substring(0, hql.length() - 3)).append(")");
                }
                if(date!=null){
                    hql.append("and a.workDate=:workDate");
                }
                if (depart != null && depart > 0 && organId != null && organId > 0) {
                    hql.append(" and a.appointDepartCode in(:appointDepartCode)");
                }
                hql.append(" group by a.appointDepartCode,a.workDate,a.price,a.workType,a.sourceLevel order by a.workDate,a.startTime");
                Query q = statelessSession.createQuery(hql.toString());
                q.setParameter("doctorId", doctorId);
                q.setParameter("sourceType", sourceType);
                q.setParameter("organId", organId);
                q.setParameter("currentTime", new Date());
                if(date!=null){
                    q.setParameter("workDate",DateConversion.getFormatDate(date, "yyyy-MM-dd"));
                }
                if (depart != null && depart > 0 && organId != null && organId > 0) {
                    q.setParameterList("appointDepartCode", appointDepartCode);
                }
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 供totalByDoctorDateForHealthWithOrgan调用
     * <p>
     * eh.bus.dao
     *
     * @param doctorId
     * @param sourceType
     * @return List<AppointSource>
     * @author luf 2016-2-1
     */
    // 2016-9-22 luf:由于实时挂号不上线将startTime>NOW()改为workDate>NOW()
    public List<Object[]> findTotalByDcotorIdWithOrganForHealth(
            final Integer doctorId, final Integer sourceType, final Integer organId , final Integer depart) {
        HibernateStatelessResultAction<List<Object[]>> action = new AbstractHibernateStatelessResultAction<List<Object[]>>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                // 2017-1-18 luf：增加个性化需求
                OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
                List<Integer> organs = organDAO.findOrgansByUnitForHealth();
                AppointDepartDAO appointDepartDAO = DAOFactory.getDAO(AppointDepartDAO.class);
                List<String> appointDepartCode = new ArrayList<String>();
                if (depart != null && depart > 0 && organId != null && organId > 0) {
                    appointDepartCode = appointDepartDAO.findListByOrganIDAndDepartID(organId, depart);
                }
                StringBuilder hql = new StringBuilder("select SUM(a.sourceNum-a.usedNum),a from AppointSource a ,HisServiceConfig c " +
                        "where a.organId=:organId and c.organid=a.organId and  a.doctorId=:doctorId and a.sourceNum-a.usedNum>0" +
                        " and a.sourceType=:sourceType  " +
                        " AND a.endTime>=:currentTime "+
                        "and ( workDate>=:currentTime  or ( endTime>=:currentTime  and c.canAppoint is not null ) ) " +
                        "and (a.cloudClinic<>1 or a.cloudClinic is null) and a.stopFlag=0 ");
                if (organs != null && organs.size() > 0) {
                    hql.append(" and (");
                    for (Integer i : organs) {
                        hql.append("a.organId=").append(i).append(" or ");
                    }
                    hql = new StringBuilder(hql.substring(0, hql.length() - 3)).append(")");
                }
                if (depart != null && depart > 0 && organId != null && organId > 0) {
                    hql.append(" and a.appointDepartCode in(:appointDepartCode)");
                }
                hql.append(" group by a.appointDepartCode,a.workDate,a.price,a.workType,a.sourceLevel order by a.workDate,a.startTime");
                Query q = statelessSession.createQuery(hql.toString());
                q.setParameter("doctorId", doctorId);
                q.setParameter("sourceType", sourceType);
                q.setParameter("organId", organId);
                q.setParameter("currentTime", new Date());
                if (depart != null && depart > 0 && organId != null && organId > 0) {
                    q.setParameterList("appointDepartCode", appointDepartCode);
                }
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 查询医生日期号源服务
     * <p>
     * eh.bus.dao
     * <p>
     * 由totalByDoctorDateForHealthWithOrgan替换
     *
     * @param doctorId   医生内码
     * @param sourceType 号源类别
     * @return List<Map<String, Object>>
     * @throws ControllerException
     * @author luf 2016-1-29
     */
    @SuppressWarnings("unchecked")
    @RpcService
    public List<Map<String, Object>> totalByDoctorDateForHealth(int doctorId,
                                                                int sourceType) throws ControllerException {
        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();


        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doctor = doctorDAO.get(doctorId);
        Integer organId = doctor.getOrgan();
        HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
        boolean f = hisServiceConfigDao.isServiceEnable(organId, ServiceType.SOURCEREAL);
        logger.info(organId + "sourceReal:"+f);
        if (f) {
            EmploymentDAO dao = DAOFactory.getDAO(EmploymentDAO.class);
            Employment employ = dao.getPrimaryEmpByDoctorId(doctorId);
            OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
            Organ o = organDAO.getByOrganId(organId);
            String jobNum = employ.getJobNumber();
            HisDoctorParam doctorParam = new HisDoctorParam();
            doctorParam.setJobNum(jobNum);
            doctorParam.setDoctorId(doctorId);
            doctorParam.setOrganizeCode(o.getOrganizeCode());
            doctorParam.setOrganID(o.getOrganId());
            //异步获取实时号源
            new RpcAsynchronousUtil(doctorParam, organId).obtainNowSource();

            /*HisDoctorParam doctorParam = new HisDoctorParam();
            doctorParam.setJobNum(jobNum);
            doctorParam.setDoctorId(doctorId);
            HisServiceConfig config = hisServiceConfigDao.getByOrganId(organId);*/
            /*String organName = DictionaryController.instance()
                    .get("eh.base.dictionary.Organ").getText(organId);*/
            //String hisServiceId = config.getAppDomainId() + ".appointGetService";
            //Object obj = RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId, "getScheulingForHealth", doctorParam);
            //RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId, "getScheulingForHealth", doctorParam);


           /* List<DoctorDateSource> sources = (List<DoctorDateSource>) obj;
            for (DoctorDateSource data : sources) {
                Map<String, Object> dataMap = new HashMap<String, Object>();
                dataMap.put("appointDepartCode", data.getProfessionCode());
                dataMap.put("organId", data.getOragnID());
                dataMap.put("appointDepartName", data.getProfessionName());
                dataMap.put("organName", organName);
                dataMap.put("appointSources", data.getAppointSourceList());

                results.add(dataMap);
            }
            return results;*/
        }

        List<Object[]> oss = this.findTotalByDcotorId(doctorId, sourceType);
        if (oss == null) {
            return results;
        }
        for (Object[] os : oss) {
            Map<String, Object> map = new HashMap<String, Object>();
            Long remainder = (Long) os[0];
            AppointSource as = (AppointSource) os[1];
            as.setWeek(DateConversion.getWeekOfDate(as.getWorkDate()));
            as.setRemainder(remainder.longValue());

            String appointDepartCode = as.getAppointDepartCode();
            Integer asOrganId = as.getOrganId();
            boolean isOrNot = false;
            for (Map<String, Object> m : results) {
                String adc = (String) m.get("appointDepartCode");
                Integer oid = (Integer) m.get("organId");
                if (adc.equals(appointDepartCode) && oid.equals(asOrganId)) {
                    ((List<AppointSource>) m.get("appointSources")).add(as);
                    isOrNot = true;
                    break;
                }
            }
            if (!isOrNot) {
                List<AppointSource> ass = new ArrayList<AppointSource>();
                ass.add(as);
                map.put("appointDepartCode", appointDepartCode);
                map.put("organId", asOrganId);
                map.put("appointDepartName", as.getAppointDepartName());
                map.put("organName",
                        DictionaryController.instance()
                                .get("eh.base.dictionary.Organ")
                                .getText(asOrganId));
                map.put("appointSources", ass);
                results.add(map);
            }
        }
        return results;
    }

    /**
     * 废弃不可用
     */
    @RpcService
    public void updateDocHaveAppoint() {

        final Integer[] organArray = {1, 1000017, 1000024, 1000007, 1000097};// 有号源的机构

        @SuppressWarnings("rawtypes")
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);

                List<Doctor> doctorList;
                for (Integer o : organArray) {
                    doctorList = doctorDAO.findByOrganAndHaveAppoint(o, 1);
                    for (Doctor d : doctorList) {
                        Query query = null;
                        String hql = null;
                        hql = "select new eh.entity.bus.DoctorDateSource(organId,appointDepartCode,appointDepartName,doctorId,workDate,sum(sourceNum-usedNum)," +
                                "price,workType,sourceLevel,cloudClinic,cloudClinicType) from AppointSource where doctorId=:doctorId  and " +
                                "workDate>=:currentTime and stopFlag=0 and ((cloudClinic =1 and cloudClinicType <>2 ) or cloudClinic=0  or cloudClinic is null)" +
                                " group by organId,appointDepartCode,workDate,price,workType,sourceLevel,cloudClinic order by workDate";
                        query = ss.createQuery(hql);
                        query.setInteger("doctorId", d.getDoctorId());
                        query.setParameter("currentTime", new Date());
                        @SuppressWarnings("unchecked")
                        List<DoctorDateSource> temp = query.list();
                        if (null == temp || temp.size() == 0) {
                            doctorDAO.updateHaveAppointByDoctorId(d.getDoctorId().intValue(), 0);
                        }
                    }
                }
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
    }

    /**
     * 查询号源医生列表接口(添加范围)
     * <p>
     * eh.bus.dao
     *
     * @param organId    机构内码
     * @param department 科室代码
     * @param name       医生姓名
     * @param range      范围- 0只查无号源医生，1只查有号源医生，-1查询全部医生
     * @param startDate  号源分页开始时间
     * @param start      分页起始位置
     * @return List<Doctor>
     * @author luf 2016-3-4
     */
    @RpcService
    public List<Doctor> findDocAndSourcesWithRange(int organId,
                                                   Integer department, String name, int range, Date startDate,
                                                   int start) {
        if (startDate == null) {
//			logger.error("查询号源医生列表接口(添加范围)findDocAndSourcesWithRange==>startDate is required!");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "startDate is required!");
        }
        startDate = DateConversion.getFormatDate(startDate, "yyyy-MM-dd");
        Date endDate = DateConversion.getDateOfWeekNow(startDate);
        List<Doctor> ds = this.findDocsWithRange(organId, department, name,
                range, startDate, endDate, start);
        List<Doctor> target = new ArrayList<Doctor>();
        for (Doctor d : ds) {
            List<AppointSource> as = findSourcesByFive(organId,
                    d.getDoctorId(), startDate, endDate);
            d.setAppointSources(as);
            target.add(d);
        }
        return target;
    }

    /**
     * 查询号源医生列表接口(添加范围)(所有号源来源)
     * <p>
     * eh.bus.dao
     *
     * @param organId    机构内码
     * @param department 科室代码
     * @param name       医生姓名
     * @param range      范围- 0只查无号源医生，1只查有号源医生，-1查询全部医生
     * @param startDate  号源分页开始时间
     * @param start      分页起始位置
     * @return List<Doctor>
     * @author luf 2016-3-4
     */
    @RpcService
    public List<Doctor> findAllDocAndSourcesWithRange(int organId,
                                                      Integer department, String name, int range, Date startDate,
                                                      int start) {
        if (startDate == null) {
//			logger.error("查询号源医生列表接口(添加范围)findDocAndSourcesWithRange==>startDate is required!");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "startDate is required!");
        }
        startDate = DateConversion.getFormatDate(startDate, "yyyy-MM-dd");
        Date endDate = DateConversion.getDateOfWeekNow(startDate);
        List<Doctor> ds = this.findAllDocsWithRange(organId, department, name,
                range, startDate, endDate, start);
        List<Doctor> target = new ArrayList<Doctor>();
        for (Doctor d : ds) {
            List<AppointSource> as = findAllSourcesByFive(organId,
                    d.getDoctorId(), startDate, endDate);
            d.setAppointSources(as);
            target.add(d);
        }
        return target;
    }

    /**
     * 查询号源医生列表接口(添加范围)(所有号源来源)
     *
     * 运营平台（权限改造）
     */
    @RpcService
    public List<Doctor> findAllDocAndSourcesWithRangeForOp(int organId,
                                                      Integer department, String name, int range, Date startDate,
                                                      int start) {
        Set<Integer> o = new HashSet<Integer>();
        o.add(organId);
        if (!SecurityService.isAuthoritiedOrgan(o)) {
            return null;
        }
        return this.findAllDocAndSourcesWithRange(organId, department, name, range, startDate, start);
    }

    /**
     * 分页查询号源服务(包含所有来源的号源)
     *
     * 运营平台（权限改造）
     *
     */
    @RpcService
    public List<AppointSource> findAllSourcesByFiveForOp(final int organId,
                                                    final int doctorId, final Date startDate, final Date endDate) {
        Set<Integer> o = new HashSet<Integer>();
        o.add(organId);
        if (!SecurityService.isAuthoritiedOrgan(o)) {
            return null;
        }
        EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
        List<Employment> ems = employmentDAO.findByDoctorId(doctorId);
        if(ems==null||ems.isEmpty()){
            throw new DAOException("职业点信息缺失");
        }
        o = new HashSet<Integer>();
        for(Employment e:ems){
            o.add(e.getOrganId());
        }
        if(!SecurityService.isAuthoritiedOrgan(o)){
            return null;
        }
        return this.findAllSourcesByFive(organId, doctorId, startDate, endDate);
    }
    /**
     * 供 查询号源医生列表接口(添加范围) 调用
     * <p>
     * eh.bus.dao
     *
     * @param organId    机构内码
     * @param department 科室代码
     * @param name       医生姓名
     * @param range      范围- 0只查无号源医生，1只查有号源医生，-1查询全部医生
     * @param start      分页起始位置
     * @return List<Doctor>
     * @author luf 2016-3-4
     */
    public List<Doctor> findDocsWithRange(final int organId,
                                          final Integer department, final String name, final int range,
                                          final Date startDate, final Date endDate, final int start) {
        logger.info("===== AppointSourceDAO 查询号源医生列表接口(添加范围) ===== findDocsWithRange >>>"
                + "organId="
                + organId
                + ";department="
                + department
                + ";name="
                + name + ";range=" + range + ";start=" + start);
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                StringBuilder hql = new StringBuilder(
                        "SELECT distinct d FROM Employment e,Doctor d WHERE e.organId=:organId AND e.doctorId=d.doctorId and d.status=1");
                if (department != null) {
                    hql.append(" AND e.department=:department");
                }
                if (!StringUtils.isEmpty(name)) {
                    hql.append(" and d.name like :name");
                }
                switch (range) {
                    case 0:
                        hql.append(" and (select count(*) from AppointSource s where s.doctorId=d.doctorId and s.organId=:organId"
                                + " and workDate>=:startDate and workDate<=:endDate and fromFlag=1)<=0");
                        break;
                    case 1:
                        hql.append(" and (select count(*) from AppointSource s where s.doctorId=d.doctorId and organId=:organId"
                                + " and workDate>=:startDate and workDate<=:endDate and fromFlag=1)>0");
                        break;
                    default:
                        break;
                }
                Query q = ss.createQuery(hql.toString());
                q.setParameter("organId", organId);
                if (department != null) {
                    q.setParameter("department", department);
                }
                if (!StringUtils.isEmpty(name)) {
                    q.setParameter("name", "%" + name + "%");
                }
                if (range == 0 || range == 1) {
                    q.setParameter("startDate", startDate);
                    q.setParameter("endDate", endDate);
                }
                q.setFirstResult(start);
                q.setMaxResults(10);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 供 查询号源医生列表接口(添加范围) 调用(所有号源来源)
     * <p>
     * eh.bus.dao
     *
     * @param organId    机构内码
     * @param department 科室代码
     * @param name       医生姓名
     * @param range      范围- 0只查无号源医生，1只查有号源医生，-1查询全部医生
     * @param start      分页起始位置
     * @return List<Doctor>
     * @author luf 2016-3-4
     */
    public List<Doctor> findAllDocsWithRange(final int organId,
                                             final Integer department, final String name, final int range,
                                             final Date startDate, final Date endDate, final int start) {
        logger.info("===== AppointSourceDAO 查询号源医生列表接口(添加范围) ===== findDocsWithRange >>>"
                + "organId="
                + organId
                + ";department="
                + department
                + ";name="
                + name + ";range=" + range + ";start=" + start);
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                StringBuilder hql = new StringBuilder(
                        "SELECT distinct d FROM Employment e,Doctor d WHERE e.organId=:organId AND e.doctorId=d.doctorId and d.status=1");
                if (department != null) {
                    hql.append(" AND e.department=:department");
                }
                if (!StringUtils.isEmpty(name)) {
                    hql.append(" and d.name like :name");
                }
                switch (range) {
                    case 0:
                        hql.append(" and (select count(*) from AppointSource s where s.doctorId=d.doctorId and s.organId=:organId"
                                + " and workDate>=:startDate and workDate<=:endDate )<=0");
                        break;
                    case 1:
                        hql.append(" and (select count(*) from AppointSource s where s.doctorId=d.doctorId and organId=:organId"
                                + " and workDate>=:startDate and workDate<=:endDate )>0");
                        break;
                    default:
                        break;
                }
                Query q = ss.createQuery(hql.toString());
                q.setParameter("organId", organId);
                if (department != null) {
                    q.setParameter("department", department);
                }
                if (!StringUtils.isEmpty(name)) {
                    q.setParameter("name", "%" + name + "%");
                }
                if (range == 0 || range == 1) {
                    q.setParameter("startDate", startDate);
                    q.setParameter("endDate", endDate);
                }
                q.setFirstResult(start);
                q.setMaxResults(10);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }


    /**
     * 上传停诊通知（广福）
     */
    @RpcService
    public String updateStopFlag(int organId, String organSchedulingID, int workType) {
        try {
            this.updateStopFlagForSchedulingOpenOrStop(organId, organSchedulingID, 1, workType);
            return "";
        } catch (Exception e) {
            logger.error("exception:", e);
            return e.getMessage();
        }
    }

    /**
     * 上传停诊删除通知(广福)
     */
    @RpcService
    public String updateCancleStopFlag(int organId, String organSchedulingID, int workType) {
        try {
            this.updateStopFlagForSchedulingOpenOrStop(organId, organSchedulingID, 0, workType);
            return "";
        } catch (Exception e) {
            logger.error("exception:", e);
            return e.getMessage();
        }
    }

    /**
     * 查询指定机构一周内的排班，不包括当天
     *
     * @param organId
     * @param startTime
     * @param endTime
     * @return
     */
    @RpcService
    public List<Map<String, Object>> getScheduling(final int organId, final String startTime, final String endTime) throws DAOException {

//        final int week = DateConversion.getWeekOfDateInt(new Date());

        HibernateStatelessResultAction<List<AppointSchedule>> action = new AbstractHibernateStatelessResultAction<List<AppointSchedule>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                String hql = "from AppointSchedule where  organId=" + organId +
                        "  order by week,workType";
                Query q = ss.createQuery(hql.toString());
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<AppointSchedule> result = action.getResult();
        AppointDepartDAO adDao = DAOFactory.getDAO(AppointDepartDAO.class);
        EmploymentDAO edDao = DAOFactory.getDAO(EmploymentDAO.class);
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        for (AppointSchedule as : result) {
            String organ = as.getOrganId().toString();
            String schedulingID = as.getScheduleId().toString();
            String deptCode = as.getAppointDepart();
            AppointDepart ad = adDao.getByOrganIDAndAppointDepartCode(as.getOrganId(), deptCode);
            String jobNumber = edDao.getJobNumberByDoctorIdAndOrganIdAndDepartment(as.getDoctorId(), as.getOrganId(), as.getDepartId());
            String deptname = ad == null ? "" : ad.getAppointDepartName();
            String doctorName = "";
            try {
                doctorName = DictionaryController.instance().get("eh.base.dictionary.Doctor").getText(as.getDoctorId());
            } catch (ControllerException e) {
                logger.error(e);
            }
            Map<String, Object> m = new HashMap<String, Object>();
            m.put("OrganID", organ);
            m.put("SchedulingID", schedulingID);
            m.put("AppointDepartCode", deptCode);
            m.put("AppointDepartName", deptname);
            m.put("DoctorID", jobNumber);
            m.put("DoctorName", doctorName);
            m.put("WeekID", as.getWeek());

            Date workDate = DateConversion.getDateByWeekday(as.getWeek());
            m.put("WorkDate", DateConversion.getDateFormatter(workDate, "yyyy-MM-dd"));

            m.put("WorkType", as.getWorkType());
            m.put("StartTime", DateConversion.getDateFormatter(as.getStartTime(), "HH:mm"));
            m.put("EndTime", DateConversion.getDateFormatter(as.getEndTime(), "HH:mm"));
            m.put("StopType", as.getUseFlag() == 0 ? "0" : "1");
            m.put("SourceType", as.getSourceType());
            m.put("SourceLevel", as.getClinicType());
            m.put("Price", "");
            m.put("SourceNum", as.getSourceNum());
            m.put("RemainNum", as.getSourceNum());
            m.put("BranchId", organ);
            list.add(m);
        }
        return list;
    }


    public List<DoctorDateSource> getHisSchdulingByDoctor(HisDoctorParam doc) {
        final int doctorId = doc.getDoctorId();
//		IHisServiceInterface appointService = AppContextHolder.getBean(hisServiceId, IHisServiceInterface.class);
        List<DoctorDateSource> dbSource = totalByDoctorDateAndCloudClinic(doctorId, 2);
        List<DoctorDateSource> cloudSources = new ArrayList<>();
        for (DoctorDateSource dds : dbSource) {
            Integer clinic = dds.getCloudClinic();
            if (clinic != null && clinic.intValue() == 1) {
                cloudSources.add(dds);
            }
        }
        HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
        HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(doc.getOrganID());
        String hisServiceId = cfg.getAppDomainId() + ".appointGetService";// 调用服务id
        //List<DoctorDateSource> schList = (List<DoctorDateSource>) RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId, "getDoctorScheduling", doc);

        if(DBParamLoaderUtil.getOrganSwich(doc.getOrganID())){
        	IAppointHisService appointService = AppContextHolder.getBean("his.iAppointHisService", IAppointHisService.class);
            HisDoctorParamTO hisDoctorParamTO = new HisDoctorParamTO();
            BeanUtils.copy(doc,hisDoctorParamTO);
            appointService.getDoctorScheduling(hisDoctorParamTO);
        }else
        	RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId, "getDoctorScheduling", doc);


        //schList.addAll(cloudSources);
        //List<DoctorDateSource> sortList = schList;
        //sort(cloudSources);

//		List<DoctorDateSource>  schList = appointService.getDoctorScheduling(doc);

        return cloudSources;
    }

    /**
     * 查询医生日期号源服务--云门诊号源
     *
     * @param doctorId   --医生编号
     * @param sourceType --号源类别
     * @return
     * @author luf 2016-08-25
     */
    @RpcService
    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<DoctorDateSource> totalByDoctorDateCloud(final int doctorId, final int sourceType) throws DAOException {
        List<DoctorDateSource> list = new ArrayList<DoctorDateSource>();

        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doctor = doctorDAO.get(doctorId);
        EmploymentDAO dao = DAOFactory.getDAO(EmploymentDAO.class);
        Employment employ = dao.getPrimaryEmpByDoctorId(doctorId);
        String jobNum = employ.getJobNumber();
        HisDoctorParam doctorParam = new HisDoctorParam();
        doctorParam.setJobNum(jobNum);
        doctorParam.setDoctorId(doctorId);

        HibernateStatelessResultAction<List<DoctorDateSource>> action = new AbstractHibernateStatelessResultAction<List<DoctorDateSource>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                Query query = null;
                String hql = null;

                if (sourceType == 2) {// 医生端预约默认取全部，现在医生app传2
                    hql = "select new eh.entity.bus.DoctorDateSource(organId,appointDepartCode,appointDepartName,doctorId,workDate,sum(sourceNum-usedNum),price,workType,sourceLevel,cloudClinic,startTime) from AppointSource where doctorId=:doctorId  and workDate>DATE(NOW()) and stopFlag=0 and cloudClinic =1 and cloudClinicType <>2 group by organId,appointDepartCode,workDate,price,workType,sourceLevel,cloudClinic order by workDate";
                    query = ss.createQuery(hql);
                    query.setInteger("doctorId", doctorId);
                    // query.setInteger("sourceType", sourceType);
                } else {
                    hql = "select new eh.entity.bus.DoctorDateSource(organId,appointDepartCode,appointDepartName,doctorId,workDate,sum(sourceNum-usedNum),price,workType,sourceLevel,cloudClinic,startTime) from AppointSource where doctorId=:doctorId and sourceType=:sourceType and workDate>DATE(NOW())  and stopFlag=0 and cloudClinic = 1 and cloudClinicType <>2 group by organId,appointDepartCode,workDate,price,workType,sourceLevel,cloudClinic order by workDate";
                    query = ss.createQuery(hql);
                    query.setInteger("doctorId", doctorId);
                    query.setInteger("sourceType", sourceType);
                }

                List<DoctorDateSource> temp = query.list();
                //zhangsl 2017-06-02 11:00:00 app3.8.5云门诊当天号源可约机构配置
                final List<Integer> todayOrgans=DAOFactory.getDAO(HisServiceConfigDAO.class).findByCanAppointToday();
                if (sourceType == 2&&todayOrgans!=null&&!todayOrgans.isEmpty()) {
                    hql = "select new eh.entity.bus.DoctorDateSource(organId,appointDepartCode,appointDepartName,doctorId,workDate,sum(sourceNum-usedNum),price,workType,sourceLevel,cloudClinic,startTime) " +
                            "from AppointSource where doctorId=:doctorId  and stopFlag=0 and cloudClinic =1 and cloudClinicType <>2 and WorkDate=DATE(NOW()) and sourceNum>=usedNum " +
                            //2016-11-15 luf:为了显示当天号源，去掉已约一条的限制
                            //"and usedNum >=1 ";
                            "and organId in(:todayOrgans)" +
                            "group by organId,appointDepartCode,workDate,workType,sourceLevel,price,cloudClinic " +//,appointSourceId
                            "order by workDate,workType,usedNum";
                    query = ss.createQuery(hql);
                    query.setInteger("doctorId", doctorId);
                    query.setParameterList("todayOrgans", todayOrgans);
                    //当天预约的号源
                    List<DoctorDateSource> todayClinicSource = query.list();
                    todayClinicSource.addAll(temp);
                    setResult(todayClinicSource);
                }else{
                    setResult(temp);
                }
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        list = action.getResult();

        // 存放约满的号源
        List<DoctorDateSource> dds = new ArrayList<DoctorDateSource>();
        for (DoctorDateSource d : list) {
            String week = DateConversion.getWeekOfDate(d.getStartTime());
            d.setWeek(week);
            if (d.getSourceNum() <= 0) {
                dds.add(d);
            }
        }
        list.removeAll(dds);
        list.addAll(dds);
        return list;
    }

    /**
     *云门诊模块就诊号源按照执业点分组显示
     * @param doctorId  --医生编号
     * @param sourceType --号源类别
     * @return
     */
    @RpcService
    public List<HashMap<String,Object>> newTotalByDoctorDateCloud(final int doctorId, final int sourceType) throws ControllerException {
        List<DoctorDateSource> dateSources = new ArrayList<>();
        dateSources = totalByDoctorDateCloud(doctorId,sourceType);
        List<HashMap<String, Object>> list = new ArrayList<HashMap<String, Object>>();
        for (DoctorDateSource dateSource : dateSources){
            Map<String, Object> object = new HashMap<String, Object>();
            List<DoctorDateSource> sources = new ArrayList<DoctorDateSource>();
            Integer oragnID = dateSource.getOragnID();
            String professionCode = dateSource.getProfessionCode();
            int mach = -1;
            for (int i = 0; i < list.size(); i++) {
                Map<String, Object> hashMap = list.get(i);
                if (oragnID.equals(hashMap.get("oragnID"))
                        && professionCode.equals(hashMap.get("professionCode"))) {
                    mach = i;
                    break;
                }
            }
            if (mach >= 0) {
                sources = (List<DoctorDateSource>) list.get(mach).get("dateSource");
                sources.add(dateSource);
                (list.get(mach)).put("dateSource", sources);
            } else {
                String organName = DictionaryController.instance().get("eh.base.dictionary.Organ").getText(oragnID);
                String professionName = dateSource.getProfessionName();
                sources.add(dateSource);
                object.put("oragnID", oragnID);
                object.put("organName", organName);
                object.put("professionCode", professionCode);
                object.put("professionName", professionName);
                object.put("dateSource", sources);
                list.add((HashMap<String, Object>) object);
            }
        }
        return list;
    }

    /**
     * 可用接诊时段号源查询服务-去除tab
     *
     * @param inAddrArea  接诊方机构属地区域
     * @param inOrganId   接诊方机构内码
     * @param outDoctorId 就诊方医生内码
     * @param outWorkDate 就诊方号源日期
     * @param workType    号源日期类型
     * @param doctorId    当前登录医生内码
     * @return
     * @author LF 2016-08-28
     */
    @RpcService
    public List<HashMap<String, Object>> queryCloudWithoutTab(String inAddrArea,
                                                              Integer inOrganId, Integer outDoctorId,
                                                              Date outWorkDate, Integer workType, int doctorId) {
        List<HashMap<String, Object>> results = new ArrayList<HashMap<String, Object>>();
        //出诊方号源
        List<AppointSource> datas = findAppointSource(outDoctorId,
                outWorkDate, workType);
        //过滤当天当前时间一小时前的号源
        Date time = DateConversion.getDateAftHour(new Date(), 1);
        List<AppointSource> datasRes = new ArrayList<AppointSource>();
        for (AppointSource s : datas) {
            int cloudclinic = s.getCloudClinic();
            if (cloudclinic == 1 && !s.getStartTime().before(time)) {
                datasRes.add(s);
            }
        }
        if (datasRes == null || datasRes.isEmpty()) {
            return results;
        }
        //出诊方starttime
        List<Date> dates = new ArrayList<Date>();
        for (AppointSource data : datasRes) {
            dates.add(data.getStartTime());
        }
        //匹配的接诊方号源及医生信息-出参排序=其次排序：按时间降序；再次排序：按首字母降序
        List<Object[]> appointSourceAndDoctor = new ArrayList<Object[]>();
        if (inOrganId != null) {
            appointSourceAndDoctor = findAppointSourceCloudWithoutTab(inOrganId,
                    outDoctorId, dates);
        } else {
            appointSourceAndDoctor = findAppointSourceCloudAreaWithoutTab(inAddrArea,
                    outDoctorId, dates);
        }

        List<HashMap<String, Object>> myself = new ArrayList<HashMap<String, Object>>();
        List<HashMap<String, Object>> inSources = new ArrayList<HashMap<String, Object>>();
        List<HashMap<String, Object>> outSources = new ArrayList<HashMap<String, Object>>();
        List<AppointSource> datasResHaveIn = new ArrayList<AppointSource>();
        for (Object[] os : appointSourceAndDoctor) {
            AppointSource as = (AppointSource) os[0];
            Date start = as.getStartTime();
            Doctor d = (Doctor) os[1];
            Integer docId = d.getDoctorId();
            HashMap<String, Object> map = new HashMap<String, Object>();
            map.put("inSource", as);
            map.put("doctor", d);
            boolean isMyself = false;
            for (AppointSource data : datasRes) {
                Date startT = data.getStartTime();
                if (start.equals(startT)) {
                    map.put("outSource", data);
                    datasResHaveIn.add(data);
                    break;
                }
            }
            if (docId.equals(doctorId)) {
                isMyself = true;
                map.put("isMyself", isMyself);
                myself.add(map);
            } else {
                map.put("isMyself", isMyself);
                inSources.add(map);
            }
        }
        datasRes.removeAll(datasResHaveIn);
        for (AppointSource data : datasRes) {
            HashMap<String, Object> map = new HashMap<String, Object>();
            map.put("outSource", data);
            outSources.add(map);
        }

        results.addAll(myself);
        results.addAll(inSources);
        results.addAll(outSources);

        return results;
    }

    /**
     * 供 可用接诊时段号源查询服务-去除tab 调用
     *
     * @param inAddrArea
     * @param dates
     * @return
     * @author LF
     */
    @DAOMethod(sql = "select a,d from AppointSource a,Organ o,Doctor d where a.doctorId=d.doctorId and o.addrArea=:inAddrArea AND a.organId=o.organId AND cloudClinic>0 AND (cloudClinicType=0 OR cloudClinicType=2) AND sourceNum>usedNum AND a.doctorId <> :doctorId AND startTime in :startTime and a.stopFlag=0 order by a.startTime,d.name")
    public abstract List<Object[]> findAppointSourceCloudAreaWithoutTab(
            @DAOParam("inAddrArea") String inAddrArea,
            @DAOParam("doctorId") Integer doctorId,
            @DAOParam("startTime") List<Date> dates);

    /**
     * 供 可用接诊时段号源查询服务-去除tab 调用
     *
     * @param doctorId
     * @param organId
     * @param dates
     * @return
     * @author LF
     */
    @DAOMethod(sql = "select s,d FROM AppointSource s,Doctor d WHERE s.doctorId=d.doctorId and s.organId=:organId AND s.cloudClinic>0 AND (s.cloudClinicType=0 OR s.cloudClinicType=2) AND s.sourceNum>s.usedNum AND s.doctorId <> :doctorId AND s.startTime in :startTime and s.stopFlag=0 order by s.startTime,d.name")
    public abstract List<Object[]> findAppointSourceCloudWithoutTab(
            @DAOParam("organId") Integer organId,
            @DAOParam("doctorId") Integer doctorId,
            @DAOParam("startTime") List<Date> dates);

    /**
     * 判断医生是否有号源-供PC端调用
     *
     * @param doctorId
     * @param sourceType
     * @return
     */
    @RpcService
    public HashMap<String, Object> hasSourceOrNot(int doctorId, int sourceType) {
        List<DoctorDateSource> dss = this.totalByDoctorDateAndCloudClinic(doctorId, sourceType);
        Boolean hasSource = false;
        if (dss != null && !dss.isEmpty()) {
            hasSource = true;
        }
        ConsultSetDAO setDAO = DAOFactory.getDAO(ConsultSetDAO.class);
        ConsultSet cs = setDAO.getById(doctorId);
        Boolean isOpenTrans = false;
        if (cs != null && cs.getTransferStatus() != null && cs.getTransferStatus().equals(1)) {
            isOpenTrans = true;
        }
        HashMap<String, Object> result = new HashMap<String, Object>();
        result.put("hasSource", hasSource);
        result.put("isOpenTrans", isOpenTrans);
        return result;
    }

    @RpcService
    public void updateStopFlagForSchedulingOpenOrStopAndSendSMS(int organId, String organSchedulingId, int stopFlag, int workType) {
        //workType = 0 更新一天的号源
        if (0 == workType) {
            updateStopFlagForSchedulingOpenOrStopAllDay(organId, organSchedulingId, stopFlag);
        } else {
            updateStopFlagForSchedulingOpenOrStop(organId, organSchedulingId, stopFlag, workType);
        }
        //获取该排班停诊的号源
        AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
        List<AppointSource> sourceList = querySource(organId, organSchedulingId, workType, 1);
        if (sourceList != null && sourceList.size() > 0) {
            List<Integer> sources = new ArrayList<Integer>();
            for(AppointSource s : sourceList){
                sources.add(s.getAppointSourceId());
            }
            List<AppointRecord> ars = appointRecordDAO.findByAppointSourceIdHasAppoint(sources);
            if (CollectionUtils.isEmpty(ars)) {
                return ;
            }
            for(AppointRecord  ar : ars){
                Integer status = ar.getAppointStatus();
                if (status != null && status.intValue() == 0) {
                    appointRecordDAO.doCancelAppoint(ar.getAppointRecordId(), "system", "医院", "医生停诊");
                }
                //待支付状态
                if (status != null && status.intValue() == 4) {
                    appointRecordDAO.doCancelAppoint(ar.getAppointRecordId(), "system", "医院", "医生停诊");
                }
                //已支付，走取消结算逻辑
                if (status != null && status.intValue() == 5) {
                    appointRecordDAO.cancel(ar.getAppointRecordId(), "system", "医院", "医生停诊");
                }
            }
        }
    }

    public List<AppointSource> querySource(final int organId, final String organSchedulingId, final int workType, final int stopFlag) throws DAOException {
        //final List<AppointSource> asList = new ArrayList<AppointSource>();
        HibernateStatelessResultAction<List<AppointSource>> action = new AbstractHibernateStatelessResultAction() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder("from AppointSource where  organSchedulingId=:organSchedulingId ");
                if(workType!=0){
                    hql.append(" and workType=:workType ") ;
                }
                hql.append( "and organID=:organID and startTime>=:currentTime and stopFlag=1");//
                Query query = ss.createQuery(hql.toString());
                query.setString("organSchedulingId", organSchedulingId);
                if(workType!=0){
                    query.setInteger("workType", workType);
                }
                query.setInteger("organID", organId);
                query.setParameter("currentTime", new Date());
                setResult(query.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);

        return action.getResult();
    }

    /**
     * 将该机构所有his来源普通号源停诊
     *
     * @param organId 机构主键
     */
    @DAOMethod(sql = " update AppointSource set stopFlag =1 where fromFlag=0 AND cloudClinic=0 AND stopFlag=0 AND workDate>CURDATE() AND organId=:organId")
    public abstract void updateHisSourceByOrganId(@DAOParam("organId") Integer organId);

    @DAOMethod(sql = " select appointSourceId from AppointSource where fromFlag=1 AND cloudClinic=0 AND stopFlag=0 AND workDate>CURDATE() AND organId=:organId")
    public abstract List<Integer> findSourceIdByOrganId(@DAOParam("organId") Integer organId);


    /**
     * 查询医生日期号源服务-添加机构入参
     * <p>
     * eh.bus.dao
     * <p>
     * 根据totalByDoctorDateForHealth修改
     *
     * @param doctorId   医生内码
     * @param sourceType 号源类别
     * @return List<Map<String, Object>>
     * @throws ControllerException
     * @author luf 2017-1-11
     */
    @SuppressWarnings("unchecked")
    @RpcService
    public List<Map<String, Object>> totalByDoctorDateForHealthWithOrgan(int doctorId,
                                                                         int sourceType, Integer organ, Integer depart) throws ControllerException {
        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();

        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doctor = doctorDAO.get(doctorId);
        Integer organId = doctor.getOrgan();
        if (organ != null && organ > 0) {
            organId = organ;
        }
        Organ organH = DAOFactory.getDAO(OrganDAO.class).getByOrganId(organId);
        HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
        boolean f = hisServiceConfigDao.isServiceEnable(organId, ServiceType.SOURCEREAL);

        if (f) {
            EmploymentDAO dao = DAOFactory.getDAO(EmploymentDAO.class);
            Employment employ = dao.getPrimaryEmpByDoctorId(doctorId);
            String jobNum = employ.getJobNumber();
            HisDoctorParam doctorParam = new HisDoctorParam();
            doctorParam.setJobNum(jobNum);
            doctorParam.setDoctorId(doctorId);
            doctorParam.setOrganizeCode(organH.getOrganizeCode());
            doctorParam.setOrganID(organH.getOrganId());

            //异步获取实时号源
            new RpcAsynchronousUtil(doctorParam, organId).obtainNowSource();
        }

        List<Object[]> oss = new ArrayList<Object[]>();
        if (organ == null || organ <= 0) {
            oss = this.findTotalByDcotorIdForHealth(doctorId, sourceType);
        } else {
            oss = this.findTotalByDcotorIdWithOrganForHealth(doctorId, sourceType, organ, depart);
        }
        if (oss == null) {
            return results;
        }
        sortByWorkDateAndWotkType(oss);
        for (Object[] os : oss) {
            Map<String, Object> map = new HashMap<String, Object>();
            Long remainder = (Long) os[0];
            AppointSource as = (AppointSource) os[1];
            as.setWeek(DateConversion.getWeekOfDate(as.getWorkDate()));
            as.setRemainder(remainder.longValue());

            String appointDepartCode = as.getAppointDepartCode();
            Integer asOrganId = as.getOrganId();

            Integer departId = null;
            AppointDepartDAO appointDepartDAO = DAOFactory.getDAO(AppointDepartDAO.class);
            //广西妇保医院特殊处理 需要添加行政科室Department的departId给微信端
//            if (OrganConstant.ORGAN_GXFB_XZ == asOrganId.intValue() || OrganConstant.ORGAN_GXFB_XY == asOrganId.intValue()) {
                departId = appointDepartDAO.getByOrganIDAndAppointDepartCode(asOrganId, appointDepartCode).getDepartId();
//            }

            boolean isOrNot = false;
            for (Map<String, Object> m : results) {
                String adc = (String) m.get("appointDepartCode");
                Integer oid = (Integer) m.get("organId");
                if (adc.equals(appointDepartCode) && oid.equals(asOrganId)) {
                    ((List<AppointSource>) m.get("appointSources")).add(as);
                    isOrNot = true;
                    break;
                }
            }
            if (!isOrNot) {
                List<AppointSource> ass = new ArrayList<AppointSource>();
                ass.add(as);
                map.put("appointDepartCode", appointDepartCode);
                map.put("organId", asOrganId);
                map.put("appointDepartName", as.getAppointDepartName());
                map.put("organName",
                        DictionaryController.instance()
                                .get("eh.base.dictionary.Organ")
                                .getText(asOrganId));
                map.put("appointSources", ass);
                map.put("departId", departId);
                results.add(map);
            }
        }
        return results;
    }


    private static void sortByWorkDateAndWotkType(List<Object[]> oss) {
        Ordering<Object> objectOrdering =  Ordering.from(new Comparator<Object>() {
            @Override
            public int compare(Object o1, Object o2) {
                Object[] oo1 = (Object[]) o1;
                Object[] oo2 = (Object[]) o2;
                AppointSource appointSource1 = (AppointSource) oo1[1];
                AppointSource appointSource2 = (AppointSource) oo2[1];
                String stringWorkType_1 = String.valueOf(appointSource1.getWorkType());
                String stringWorkType_2 = String.valueOf(appointSource2.getWorkType());
                return stringWorkType_1.compareTo(stringWorkType_2);
            }
        });

        Ordering<Object> objectOrdering2 =  Ordering.from(new Comparator<Object>() {
            @Override
            public int compare(Object o1, Object o2) {
                Object[] oo1 = (Object[]) o1;
                Object[] oo2 = (Object[]) o2;
                AppointSource appointSource1 = (AppointSource) oo1[1];
                AppointSource appointSource2 = (AppointSource) oo2[1];
                return appointSource1.getWorkDate().compareTo(appointSource2.getWorkDate());
            }
        });

        //Collections.sort(oss, ComparatorUtils.chainedComparator(objectOrdering2,objectOrdering));
        Collections.sort(oss,objectOrdering);
        Collections.sort(oss,objectOrdering2);
    }
    /**
     * 当选择就诊日期后返回该医生当日号源,当时间为null时，返回全部
     *
     * @param doctorId   医生内码
     * @param sourceType 号源类别
     * @param organ      机构ID
     * @param date       workdate的值
     * @return List<Map<String, Object>>
     * @throws ControllerException
     * @Author:gey
     * @Description:查询医生日期号源服务-
     * @Date:15:26 2017-03-22
    */
    @SuppressWarnings("unchecked")
    @RpcService
    public List<Map<String, Object>> totalByDoctorDateForHealthWithOrganAndDate(int doctorId, int sourceType, Integer organ,Date date, Integer depart) throws ControllerException {
        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();

        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doctor = doctorDAO.get(doctorId);
        Integer organId = doctor.getOrgan();
        if (organ != null && organ > 0) {
            organId = organ;
        }
        Organ organH = DAOFactory.getDAO(OrganDAO.class).getByOrganId(organId);
        HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
        boolean f = hisServiceConfigDao.isServiceEnable(organId, ServiceType.SOURCEREAL);

        if (f) {
            EmploymentDAO dao = DAOFactory.getDAO(EmploymentDAO.class);
            Employment employ = dao.getPrimaryEmpByDoctorId(doctorId);
            String jobNum = employ.getJobNumber();
            HisDoctorParam doctorParam = new HisDoctorParam();
            doctorParam.setJobNum(jobNum);
            doctorParam.setDoctorId(doctorId);
            doctorParam.setOrganizeCode(organH.getOrganizeCode());
            doctorParam.setOrganID(organH.getOrganId());

            //异步获取实时号源
            new RpcAsynchronousUtil(doctorParam, organId).obtainNowSource();
        }

            List<Object[]> oss = new ArrayList<Object[]>();
            if (organ == null || organ <= 0) {
                oss = this.findTotalByDcotorIdForHealth(doctorId, sourceType,date);
            } else {
                oss = this.findTotalByDcotorIdWithOrganForHealth(doctorId, sourceType, organ,date, depart);
            }
            if (oss == null) {
                return results;
            }
        sortByWorkDateAndWotkType(oss);
            for (Object[] os : oss) {
                Map<String, Object> map = new HashMap<String, Object>();
                Long remainder = (Long) os[0];
                AppointSource as = (AppointSource) os[1];
                as.setWeek(DateConversion.getWeekOfDate(as.getWorkDate()));
                as.setRemainder(remainder.longValue());

                String appointDepartCode = as.getAppointDepartCode();
                Integer asOrganId = as.getOrganId();

                Integer departId = null;
                AppointDepartDAO appointDepartDAO = DAOFactory.getDAO(AppointDepartDAO.class);
                //广西妇保医院特殊处理 需要添加行政科室Department的departId给微信端
//                if (OrganConstant.ORGAN_GXFB_XZ == asOrganId.intValue() || OrganConstant.ORGAN_GXFB_XY == asOrganId.intValue()) {
                    departId = appointDepartDAO.getByOrganIDAndAppointDepartCode(asOrganId, appointDepartCode).getDepartId();
//                }

                boolean isOrNot = false;
                for (Map<String, Object> m : results) {
                    String adc = (String) m.get("appointDepartCode");
                    Integer oid = (Integer) m.get("organId");
                    if (adc.equals(appointDepartCode) && oid.equals(asOrganId)) {
                        ((List<AppointSource>) m.get("appointSources")).add(as);
                        isOrNot = true;
                        break;
                    }
                }
                if (!isOrNot) {
                    List<AppointSource> ass = new ArrayList<AppointSource>();
                    ass.add(as);
                    map.put("appointDepartCode", appointDepartCode);
                    map.put("organId", asOrganId);
                    map.put("appointDepartName", as.getAppointDepartName());
                    map.put("organName",
                            DictionaryController.instance()
                                    .get("eh.base.dictionary.Organ")
                                    .getText(asOrganId));
                    map.put("appointSources", ass);
                    map.put("departId", departId);
                    results.add(map);
                }
            }
        return results;
    }



    /**
     * 查询医生的号源信息( 供 可用接诊时段号源查询服务 调用)
     *
     * @param doctorId
     * @param workDate
     * @param workType
     * @return
     * @author LF
     */
    @DAOMethod(sql = "FROM AppointSource WHERE appointDepartCode=:professionCode AND doctorId=:doctorId AND workDate=:workDate AND workType=:workType AND sourceNum>usedNum AND stopFlag=0 AND cloudClinic>0 AND cloudClinicType<>2")
    public abstract List<AppointSource> findAppointSourceWithProfessionCode(
            @DAOParam("doctorId") Integer doctorId,
            @DAOParam("workDate") Date workDate,
            @DAOParam("workType") Integer workType,
            @DAOParam("professionCode") String professionCode);


    @Override
    @RpcService
    public AppointSource save(AppointSource o) throws DAOException {
        logger.info("save appointSource:"+o.getUsedNum()+"|organSourceId:"+o.getOrganSourceId());
        o = commonDataValidate(o);
        return super.save(o);
    }

    @Override
    @RpcService
    public AppointSource update(AppointSource o) throws DAOException {
        o = commonDataValidate(o);
        return super.update(o);
    }

    /**
     * 基础数据校验设置默认值
     */
    private AppointSource commonDataValidate(AppointSource o) {
        if (o.getUsedNum() == null) {
            o.setUsedNum(0);
        }
        if (o.getPrice() == null){
            o.setPrice(0d);
        }
        return o;
    }

    /**
     * 查询医生日期号源服务--剔除云门诊
     *
     * @param doctorId   --医生编号
     * @param sourceType --号源类别
     * @return
     */
    public List<DoctorDateSource> searchDoctorDateSourcesWithoutCloud(final int doctorId, final int sourceType) throws DAOException {
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doctor = doctorDAO.get(doctorId);
        final Integer organId = doctor.getOrgan();
        EmploymentDAO dao = DAOFactory.getDAO(EmploymentDAO.class);
        Employment employ = dao.getPrimaryEmpByDoctorId(doctorId);
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        Organ o = organDAO.getByOrganId(organId);
        String jobNum = employ.getJobNumber();
        HisDoctorParam doctorParam = new HisDoctorParam();
        doctorParam.setJobNum(jobNum);
        doctorParam.setDoctorId(doctorId);
        doctorParam.setOrganizeCode(o.getOrganizeCode());
        doctorParam.setOrganID(o.getOrganId());

        HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
        boolean f = hisServiceConfigDao.isServiceEnable(organId, ServiceType.SOURCEREAL);
        if (f) {
            new RpcAsynchronousUtil(doctorParam, organId).obtainNowSource();
        }

        HibernateStatelessResultAction<List<DoctorDateSource>> action = new AbstractHibernateStatelessResultAction<List<DoctorDateSource>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder("select new eh.entity.bus.DoctorDateSource(organId,appointDepartCode,appointDepartName,doctorId,workDate," +
                        "sum(sourceNum-usedNum),price,workType,sourceLevel,cloudClinic,startTime) from AppointSource where doctorId=:doctorId and " +
                        "workDate>=:currentTime and stopFlag=0 and (cloudClinic=0 or cloudClinic is null)");

                if (sourceType != 2) {
                    hql.append(" and sourceType=:sourceType");
                }

                hql.append(" group by organId,appointDepartCode,workDate,price,workType,sourceLevel,cloudClinic order by workDate");
                Query query = ss.createQuery(hql.toString());
                query.setInteger("doctorId", doctorId);
                query.setParameter("currentTime", new Date());

                if (sourceType != 2) {
                    query.setInteger("sourceType", sourceType);
                }

                List<DoctorDateSource> temp = query.list();
                setResult(temp);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<DoctorDateSource> list = action.getResult();


        for (DoctorDateSource d : list) {
            String week = DateConversion.getWeekOfDate(d.getStartTime());
            d.setWeek(week);
        }
        return list;
    }

    /**
     *  查询医生的普通号源
     *  */

    public List<Integer>  findStopSource(final Integer doctorId, final Integer organId, final Integer workType, final  String organSchedulingId,final Date workDate ){
        AbstractHibernateStatelessResultAction<List<Integer>> action = new AbstractHibernateStatelessResultAction<List<Integer>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder("select appointSourceId FROM AppointSource " +
                        "WHERE  organId=:organId  AND stopFlag=0 " +
                        "AND (cloudClinic=0 or cloudClinic is null) and endTime>=now() ");
                if(doctorId!=null){
                    hql.append("AND doctorId=:doctorId ");
                }
                if(!StringUtils.isEmpty(organSchedulingId)){
                    hql.append("AND organSchedulingId=:organSchedulingId ");
                }
                if(workDate!=null){
                    hql.append("AND workDate=:workDate ");
                }
                if(workType!=null&&workType.intValue()!=0){
                    hql.append("AND workType=:workType ");
                }
                Query query = ss.createQuery(hql.toString());
                if(doctorId!=null) {
                    query.setParameter("doctorId", doctorId);
                }
                query.setParameter("organId", organId);
                if(workDate!=null){
                    query.setParameter("workDate", workDate);
                }
                if((workType!=null&&workType.intValue()!=0)){
                    query.setParameter("workType", workType);
                }
                if(!StringUtils.isEmpty(organSchedulingId)){
                    query.setParameter("organSchedulingId", organSchedulingId);
                }
                List<Integer> temp = query.list();
                setResult(temp);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }
    /**
     *  更新医生的普通号源
     *  */
    @DAOMethod(sql = "update AppointSource set stopFlag=1 WHERE doctorId=:doctorId AND organId=:organId AND workDate>=:workDate AND sourceNum>usedNum AND stopFlag=0 AND (cloudClinic=0 or cloudClinic is null) ")
    public abstract void updateAppointSourceByDoctorID(
            @DAOParam("doctorId") Integer doctorId,
            @DAOParam("organId")  Integer organId,
            @DAOParam("workDate") Date workDate,
            @DAOParam("workType")  Integer workType);

    /**
     *  更新医生的普通号源 并发送预约取消短信 供前置机调用
     *  后期改成异步的应该
     *  */
    @RpcService
    public PushResponseModel updateStopFlagForDoctorAndSendSMS(HisAppointSchedule schedule) {
        logger.info("排班停诊His参数："+JSONUtils.toString(schedule));
        PushResponseModel re = validateParam(schedule);
        try {
            if(!re.isSuccess()){
                return re;
            }
            Integer organId = schedule.getOrganID();
            Integer doctorID = schedule.getDoctorID();
            Integer workType = schedule.getWorkType();
            String schedulingID = schedule.getSchedulingID();
            Date workDate = schedule.getWorkDate();

            //获取该排班停诊的号源
            AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
            //查询需要更新的号源
            List<Integer> sourceList = findStopSource(doctorID, organId,workType,schedulingID,workDate);
            //更新号源
//        updateAppointSourceByDoctorID(doctorID, organId,workDate,workType);
            //更新预约记录
            if (sourceList != null && sourceList.size() > 0) {
                updateStopFlagByIds(sourceList);
                List<AppointRecord> ars = appointRecordDAO.findByAppointSourceIdHasAppoint(sourceList);
                if (CollectionUtils.isEmpty(ars)) {
                    return re.setSuccess("200");
                }
                for(AppointRecord  ar : ars){
                    Integer status = ar.getAppointStatus();
                    if (status != null && status.intValue() == 0) {
                        appointRecordDAO.doCancelAppoint(ar.getAppointRecordId(), "system", "医院", "医生停诊");
                    }
                    //待支付
                    if (status != null && status.intValue() == 4) {
                        appointRecordDAO.doCancelAppoint(ar.getAppointRecordId(), "system", "医院", "医生停诊");
                    }
                    //已支付，走取消结算逻辑
                    if (status != null && status.intValue() == 5) {
                        appointRecordDAO.cancel(ar.getAppointRecordId(), "system", "医院", "医生停诊");
                    }
                }

            }
            return re.setSuccess("200");
        }catch (Exception e){
            logger.error(e);
            return re.setError("-1",e.getMessage());
        }
    }



    private PushResponseModel validateParam(HisAppointSchedule schedule) {
        PushResponseModel r = new PushResponseModel();
        if(0==schedule.getOrganID()){
            return r.setError("-1","organID is required");
        }
        if(schedule.getDoctorID()==null
           &&schedule.getWorkDate()==null
           &&schedule.getSchedulingID()==null){
            return r.setError("-1","organID only !!!");
        }
        return r.setSuccess("200");
    }


    /**
     * 删除机构没有被预约过的号源
     *
     * @param organId   机构ID
     * @Author:Andywang
     * @Date:15:26 2017-06-19
     */
    public Integer deleteUnUsedSourceByOrgan(final Integer organId){
        if (organId == null || organId <=0)
        {
            logger.info("OrganID为空");
            return 0;
        }
        OrganDAO od = DAOFactory.getDAO(OrganDAO.class);
        Organ o = od.getByOrganId(organId);
        if (o == null || StringUtils.isEmpty(o.getShortName()))
        {
            logger.info("OrganID: " + organId + " 的机构不存在！");
            return 0;
        }
        String backedTableName = DBUtil.backupTableByClass(AppointSource.class, " organId = " + organId);
        final HibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder("delete from  AppointSource where organId=:organId and usedNum=0");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("organId", organId);
                Integer count = q.executeUpdate();
                setResult(count);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        Integer affectedCount =   action.getResult();
        logger.info("Backuped Table: " + backedTableName + " Method: deleteUnUsedSourceByOrgan, OrganId:  " + organId +  " Rows: " +affectedCount);
        return affectedCount;
    }

    /**
     * 恢复使用状态
     *
     * @param organId   机构ID
     *  @param organSchedulingId   排班ID
     * @Author:Andywang
     * @Date:15:26 2017-06-19
     */
    public Integer recoverSourceByOrganAndSchedule(final Integer organId, final String organSchedulingId){
        if (organId == null || organId <=0)
        {
            logger.info("OrganID为空");
            return 0;
        }
        OrganDAO od = DAOFactory.getDAO(OrganDAO.class);
        Organ o = od.getByOrganId(organId);
        if (o == null || StringUtils.isEmpty(o.getShortName()))
        {
            logger.info("OrganID: " + organId + " 的机构不存在！");
            return 0;
        }
        String backedTableName = DBUtil.backupTableByClass(AppointSource.class, " organId = " + organId);
        final HibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder("update  AppointSource set usedNum=0,stopFlag=0 where organId=:organId ");
                if (!StringUtils.isEmpty(organSchedulingId))
                {
                    hql.append(" and organSchedulingId=:organSchedulingId");
                }
                Query q = ss.createQuery(hql.toString());
                q.setParameter("organId", organId);
                if (!StringUtils.isEmpty(organSchedulingId))
                {
                    q.setParameter("organSchedulingId",organSchedulingId);
                }
                Integer count = q.executeUpdate();
                setResult(count);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        Integer affectedCount =   action.getResult();
        logger.info("Backuped Table: " + backedTableName + " Method: recoverSourceByOrganAndSchedule, OrganId:  " + organId + " organSchedulingId: " + organSchedulingId + " Rows: " +affectedCount);
        return affectedCount;

    }


    @DAOMethod(sql = "FROM AppointSource WHERE organId=:organId AND doctorId=:doctorId AND workDate=:workDate AND workType=:workType AND organSchedulingId=:organSchedulingId AND appointDepartCode=:appointDepartCode AND sourceNum>usedNum AND endTime>now() AND stopFlag=0 order by  orderNum ")
    public abstract List<AppointSource> findAppointSourceNext(
            @DAOParam("organId") Integer organId,
            @DAOParam("doctorId") Integer doctorId,
            @DAOParam("workDate") Date workDate,
            @DAOParam("workType") Integer workType,
            @DAOParam("organSchedulingId") String organSchedulingId,
            @DAOParam("appointDepartCode") String appointDepartCode
    );

}
