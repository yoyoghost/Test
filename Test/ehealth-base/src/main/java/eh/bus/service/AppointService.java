package eh.bus.service;

import com.ngari.common.mode.HisResponseTO;
import com.ngari.his.appoint.mode.AppointCancelRequestTO;
import com.ngari.his.appoint.mode.QueryHisAppointRecordParamTO;
import com.ngari.his.appoint.mode.QueryHisAppointRecordResponseTO;
import com.ngari.his.appoint.service.IAppointHisService;
import com.ngari.his.patient.mode.PatientQueryRequestTO;
import com.ngari.his.patient.service.IPatientHisService;
import ctd.account.UserRoleToken;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.spring.AppDomainContext;
import ctd.util.AppContextHolder;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import ctd.util.context.Context;
import eh.base.constant.ErrorCode;
import eh.base.dao.*;
import eh.base.service.BusActionLogService;
import eh.base.service.organ.OrganConfigService;
import eh.base.user.UserSevice;
import eh.bus.constant.CloudConstant;
import eh.bus.dao.*;
import eh.entity.base.Doctor;
import eh.entity.base.Employment;
import eh.entity.base.HisServiceConfig;
import eh.entity.base.Organ;
import eh.entity.bus.*;
import eh.entity.his.AppointCancelRequest;
import eh.entity.his.PatientQueryRequest;
import eh.entity.his.hisCommonModule.HisResponse;
import eh.entity.mpi.HealthCard;
import eh.entity.mpi.Patient;
import eh.mpi.dao.HealthCardDAO;
import eh.mpi.dao.PatientDAO;
import eh.remote.IHisServiceInterface;
import eh.remote.IPatientService;
import eh.task.executor.SaveChangedAppointRecordExecutor;
import eh.util.DBParamLoaderUtil;
import eh.util.RpcServiceInfoStore;
import eh.util.RpcServiceInfoUtil;
import eh.util.SameUserMatching;
import eh.utils.DateConversion;
import eh.utils.LocalStringUtil;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.util.CollectionUtil;
import org.hibernate.StatelessSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 只有小鱼端调用此服务中的接口，且所有platform均传值xiaoyu
 */
public class AppointService {
    private static final Logger logger = LoggerFactory.getLogger(AppointService.class);

    @RpcService
    public void updateAppointSource(List<HisAppointRecord> list) {
        logger.info("receive his request total size:" + list.size());
        SaveChangedAppointRecordExecutor executor = new SaveChangedAppointRecordExecutor(list);
        executor.execute();
        /*for(HisAppointRecord source:list){
            try{
			updateSource(source);
			}catch(Exception e)
			{
				logger.error("号源更新异常："+e.getMessage()+"/r/n号源信息："+JSONUtils.toString(source));
			}
		}*/
    }


    @RpcService
    public Boolean updateSource(final HisAppointRecord appointSource) {
        logger.info("receive his request:" + JSONUtils.toString(appointSource));
        AbstractHibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
            @Override
            public void execute(StatelessSession arg0) throws Exception {
                int organId = appointSource.getOrganId();
                String type = appointSource.getType();//1 释放的号源 2已用的号源
                String organAppointId = appointSource.getOrganAppointId();
                String organSchedulingId = appointSource.getOrganSchedulingId();
                String organSourceId = appointSource.getOrganSourceId();

                AppointSourceDAO dao = DAOFactory.getDAO(AppointSourceDAO.class);
                AppointRecordDAO recordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
                HisAppointRecordDAO arDAO = DAOFactory.getDAO(HisAppointRecordDAO.class);
                AppointSource old = dao.getAppointSourceNew(organId, organSchedulingId, organSourceId);
                if (old == null) {
                    logger.info("平台上未找到对应的号源信息:organSourceId=[{}]", organSourceId);
                    if ("1".equals(type)) {
                        AppointRecord record = recordDAO.getByOrganAppointIdAndOrganId(organAppointId, organId);
                        if (record == null) {
                            setResult(false);
                            logger.info("平台上未找到对应的号源预约信息:organAppointId=[{}]", organAppointId);
                            return;
                        }
                        old = dao.get(record.getAppointSourceId());
                        appointSource.setWorkType(old.getWorkType());
                        appointSource.setWorkDate(old.getStartTime());
                        appointSource.setOrganSchedulingId(old.getOrganSchedulingId());
                        appointSource.setOrganSourceId(old.getOrganSourceId());
                    }else{
                        setResult(false);
                        return;
                    }
                }
                appointSource.setWorkDate(old.getStartTime());

                //记录更新日志
                arDAO.saveOrUpdate(appointSource);
                logger.info("需更新的号源sourceID:" + appointSource.getOrganSourceId() + "类型：" + appointSource.getType());

                //int usedNum=old.getUsedNum();
                int number = appointSource.getNumber();//已用或释放号源数
                if (type.trim().equals("1")) {//预约取消
                    if (old.getUsedNum() > 0 && old.getUsedNum() >= number) {
                        int usedNumNew = old.getUsedNum() - number;//已用数减1
                        dao.updateUsedNumAfterCancel(usedNumNew, old.getAppointSourceId());
                    } else {
                        dao.updateUsedNumAfterCancel(0, old.getAppointSourceId());
                    }
                    //如果是取消操作，需要将平台上已预约的记录也取消
                    AppointRecordDAO appointDao = DAOFactory.getDAO(AppointRecordDAO.class);
                    AppointRecord ar = appointDao.getAppointedRecord(appointSource);
                    if (ar != null) {
                        logger.info("平台存在该预约记录，需要取消" + ar.getAppointRecordId());
                        appointDao.doCancelAppoint(ar.getAppointRecordId(), "system", "system", "已通过其他渠道取消该预约");
                    }

                }
                if (type.trim().equals("2")) {//预约

                    int ordernum = old.getOrderNum();
                    //已用号源大于号源数，即已无有效号源，则直接返回false
                    if ((old.getUsedNum() + number) > old.getSourceNum()) {
                        setResult(false);
                    }
                    //已用号源小于号源数，且剩余号源数大于1，则需插入预约记录、更新号源数、顺序数、更新医生表
                    else if ((old.getUsedNum() + number) < old.getSourceNum()) {
                        //更新号源表【已用号源数】
                        int UesdNum = old.getUsedNum();
                        UesdNum += 1;
                        ordernum += 1;
                        dao.updateUsedNum(UesdNum, ordernum, old.getAppointSourceId());
                        //更新医生表【是否有预约号源标志（1有号源 0无号源）】
                        List<DoctorDateSource> doctorAllSource = dao.totalByDoctorDate(old.getDoctorId(), old.getSourceType());
                            /*long doctorSumSource=0;
                            for(int i=0;i<doctorAllSource.size();i++){
								doctorSumSource=doctorSumSource+doctorAllSource.get(i).getSourceNum();
							}
							if(doctorSumSource>0&&old.getDoctorId()==0){
								doctorDAO.updateHaveAppointByDoctorId(old.getDoctorId(), 1);
							}else{
								if(old.getDoctorId()==1){
								logger.info("更新成无号源:doctorId:"+old.getDoctorId());
								doctorDAO.updateHaveAppointByDoctorId(old.getDoctorId(), 0);
								}
							}*/
                    }
                    //剩余号源数为1，即最后一个号源，则无需更新号源顺序号
                    else {
                        int UesdNum = old.getUsedNum();
                        UesdNum += 1;
                        dao.updateUsedNum(UesdNum, ordernum, old.getAppointSourceId());


                    }
                }
                //更新医生表【是否有预约号源标志（1有号源 0无号源）】，查询医生日期号源 会更新是否有号源标志
                List<DoctorDateSource> doctorAllSource = dao.totalByDoctorDate(old.getDoctorId(), old.getSourceType());
                    /*long doctorSumSource=0;
                    for(int i=0;i<doctorAllSource.size();i++){
						doctorSumSource=doctorSumSource+doctorAllSource.get(i).getSourceNum();
					}
					if(doctorSumSource>0){
						doctorDAO.updateHaveAppointByDoctorId(old.getDoctorId(), 1);
					}else{
						logger.info("更新成无号源:doctorId:"+old.getDoctorId());
						doctorDAO.updateHaveAppointByDoctorId(old.getDoctorId(), 0);
					}*/
            }

        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    /**
     * 根据排班id查找对应AppointRecord
     *
     * @param organSchedulingId
     * @return
     */
    @RpcService
    public List<AppointRecord> findAppointRecordByOrganSchedulingId(String organSchedulingId) {
        AppointRecordDAO recordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
        List<AppointRecord> appointRecordList = recordDAO.findAppointRecordByOrganSchedulingId(organSchedulingId);
        return appointRecordList;
    }

    /**
     * 停班同时停诊服务 点击停班，同时停诊后，停诊今天之后的号源；当天号源不停诊
     *
     * @param ids     排班id
     * @param useFlag 停班状态
     * @param isFlag  是否同时停诊
     * @return
     * @author houxr
     * @date 2016-04-21 15:15:30
     */
    @RpcService
    public Integer updateScheduleAndSourceStopOrNot(List<Integer> ids, int useFlag, Boolean isFlag) {
        logger.info("停班同时停诊服务->updateScheduleAndSourceStopOrNot:"
                + "ids:" + JSONUtils.toString(ids) + ";useFlag:" + useFlag + ";isFlag:" + isFlag);
        int count = 0;
        for (Integer id : ids) {
            if (id == null) {
                continue;
            }
            AppointScheduleDAO scheduleDAO = DAOFactory.getDAO(AppointScheduleDAO.class);
            AppointSchedule ad = scheduleDAO.get(id);//取得一个排班对象
            if (ad == null) {
                continue;
            }
            ad.setUseFlag(useFlag);//设置排班状态为停班
            scheduleDAO.update(ad);

            //增加停班同时停诊服务操作日志
            Doctor doctor = DAOFactory.getDAO(DoctorDAO.class).get(ad.getDoctorId());
            Organ organ = DAOFactory.getDAO(OrganDAO.class).get(ad.getOrganId());
            BusActionLogService.recordBusinessLog("医生排班", ad.getScheduleId().toString(), "AppointService",
                    "[" + organ.getShortName() + "]的医生[" + doctor.getName() + "]星期" + (ad.getWeek() == 7 ? "天" : ad.getWeek())
                            + "排班" + (useFlag == 1 ? "停班" + (isFlag ? ",同时停诊" : "") : "取消停班"));

            //排班预约记录标记设置:已取消 若存在被约号,短信通知患者停诊取消
            AppointRecordDAO recordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
            if (!StringUtils.isEmpty(String.valueOf(ad.getScheduleId()))) {
                //获取该排班所有预约记录（当前日期之后的）
                List<AppointRecord> appointRecords =
                        recordDAO.findAppointRecordByOrganSchedulingId(String.valueOf(ad.getScheduleId()));//修改排班预约状态
                for (AppointRecord appointRecord : appointRecords) {
                    appointRecord.setAppointStatus(2);//已取消
                    appointRecord.setCancelResean("医生停诊");
                    recordDAO.update(appointRecord);

                    //预约号源 医生停诊 短信通知
                    sendSmsForSourceStopToPatientOrDoctorByTelClinicFlag(appointRecord.getAppointRecordId());
                }
                //停诊号源 根据排班的id 查找到对应班次的号源记录 做停诊标记 当天号源不停诊
                if (isFlag) {
                    AppointSourceDAO sourceDAO = DAOFactory.getDAO(AppointSourceDAO.class);
                    List<AppointSource> sourceList = sourceDAO.findByOrganIdAndOrganSchedulingId(ad.getOrganId(), String.valueOf(ad.getScheduleId()));
                    for (AppointSource appointSource : sourceList) {
                        appointSource.setStopFlag(1);//0正常 1停诊
                        sourceDAO.update(appointSource);
                    }
                }
            }
            count++;
        }
        return count;
    }


    /**
     * 号源停诊 已经预约的号源 短信通知
     *
     * @param ids      需修改的号源序号列表
     * @param stopFlag 停诊标志
     * @return int 修改成功的条数
     * @author houxr
     */
    @RpcService
    public int updateSourcesStopOrNotNew(List<Integer> ids, int stopFlag) {
        logger.info("出/停诊服务 AppointSourceDAO====> updateSourcesStopOrNot <==="
                + "ids:" + JSONUtils.toString(ids) + ";stopFlag:" + stopFlag);

        //增加停班同时停诊服务操作日志
        AppointSourceDAO sourceDAO = DAOFactory.getDAO(AppointSourceDAO.class);
        AppointRecordDAO recordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
        int count = 0;
        String doctorMessage = null;
        for (Integer id : ids) {
            if (id == null) {
                continue;
            }
            AppointSource ac = new AppointSource();
            ac = sourceDAO.get(id);
            if (ac == null) {
                continue;
            }
            ac.setStopFlag(stopFlag);
            sourceDAO.update(ac);
            if (doctorMessage == null) {
                doctorMessage = sourceDAO.getDoctorInfoBySource(ac);
            }
            if (ac.getAppointSourceId() != null && stopFlag==1) {
                List<AppointRecord> appointRecords =
                        recordDAO.findSuccessAppointRecordByAppointSourceId(ac.getAppointSourceId());//修改号源预约状态
                for (AppointRecord appointRecord : appointRecords) {
                    sendSmsForSourceStopToPatientOrDoctorByTelClinicFlag(appointRecord.getAppointRecordId());
                }
            }
            count++;
        }
        BusActionLogService.recordBusinessLog("医生号源", JSONUtils.toString(ids), "AppointSource",
                doctorMessage + "号源" + JSONUtils.toString(ids) + (stopFlag == 1 ? "停诊" : "恢复出诊"));
        return count;
    }

    /**
     * 预约号源停诊短信通知患者或申请医生 云门诊 or 非云门诊
     *
     * @param appointRecordId
     * @return
     */
    @RpcService
    public void sendSmsForSourceStopToPatientOrDoctorByTelClinicFlag(Integer appointRecordId) {
        AppointRecordDAO dao = DAOFactory.getDAO(AppointRecordDAO.class);
        AppointRecord appointRecord = dao.getByAppointRecordId(appointRecordId);
        UserRoleToken urt = UserRoleToken.getCurrent();
        dao.cancelAppointWithFlag(appointRecordId,urt.getUserId(),urt.getUserName(),"医生号源停诊",1);
        String telClinicFlag = String.valueOf(appointRecord.getTelClinicFlag());
        String appointUser = appointRecord.getAppointUser();
        //是否云门诊
        if (StringUtils.equals("1", telClinicFlag) || StringUtils.equals("2", telClinicFlag)) {
            if (!appointUser.isEmpty() && appointUser.length() >= 16) {
                dao.sendSmsForSourceStopToAppointUser(appointRecord.getAppointRecordId(),appointRecord.getOrganId());//短信申请者
            } else {
                if(appointRecord.getPayFlag()!=null&&appointRecord.getPayFlag()==1) {
                    dao.sendSmsForOrderPayCloundClinicSourceStop(appointRecord.getAppointRecordId());//带退款信息短信
                }else {
                    dao.sendSmsForSourceStopToApplyDoctorAndPatient(appointRecord.getAppointRecordId(),appointRecord.getOrganId());//短信通知申请医生和患者
                }
            }
        } else {
            if (!appointUser.isEmpty() && appointUser.length() >= 16) {
                dao.sendSmsForSourceStopToAppointUser(appointRecord.getAppointRecordId(),appointRecord.getOrganId());//短信申请者
            } else {
                dao.sendSmsForSourceStopToApplyDoctorAndPatient(appointRecord.getAppointRecordId(),appointRecord.getOrganId());//短信通知申请医生和患者
            }
        }
    }

    /**
     * 预约/特需预约申请检验
     *
     * @param mpiId    患者
     * @param reMpiId  申请人
     * @param doctorId 目标医生
     * @param flag     标志-0预约1特需预约
     * @return Boolean
     */
    @RpcService
    public Boolean canPatientRequestAppoint(String mpiId, String reMpiId, int doctorId, int flag) {
        if (StringUtils.isEmpty(mpiId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "mpiId is required!");
        }
        if (StringUtils.isEmpty(reMpiId) || reMpiId.length() != 32) {
            throw new DAOException(DAOException.VALUE_NEEDED, "reMpiId is required!");
        }
        Date requestDate = new Date();
        AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
//          不应该是用 appointDate判断，应该使用workDate判断重复预约
//        //当天挂号预约成功且支付成功后同一个患者预约同一个医生不能重复提交
//        List<AppointRecord> todayAppointPayed = appointRecordDAO.findHasAppointTodayAndPayed(mpiId, reMpiId, doctorId, requestDate);
//        if (todayAppointPayed != null && !todayAppointPayed.isEmpty() && flag == 0) {
//            throw new DAOException(ErrorCode.SERVICE_ERROR, "抱歉，您不能重复提交.");
//        }
//
//        //当天挂号预约成功且未支付同一个患者预约同一个医生不能重复提交
//        List<AppointRecord> todayAppoint = appointRecordDAO.findHasAppointToday(mpiId, reMpiId, doctorId, requestDate);
//        if (todayAppoint != null && !todayAppoint.isEmpty() && flag == 0) {
//            throw new DAOException(ErrorCode.SERVICE_ERROR, "请5分钟后再次提交.");
//        }

//        董大说去掉
//        List<AppointRecord> ars = appointRecordDAO.findHasAppointByFour(mpiId, reMpiId, doctorId, requestDate);
//        if (ars != null && !ars.isEmpty() && flag == 0) {
//            throw new DAOException(ErrorCode.SERVICE_ERROR, "抱歉，您不能重复提交.");
//        }
        TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
        List<Transfer> transfers = transferDAO.findHasPatientTransfer(mpiId, reMpiId, doctorId, requestDate);
        if (transfers != null && !transfers.isEmpty() && flag == 1) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "抱歉，您不能重复提交.");
        }
        Map<String, Boolean> matchMap = SameUserMatching.patientsAndDoctor(mpiId, reMpiId, doctorId);
        if (null != matchMap && null != matchMap.get("teams") && null != matchMap.get("patSameWithDoc")) {
            Boolean teams = matchMap.get("teams");
            Boolean patSameWithDoc = matchMap.get("patSameWithDoc");
            if (!teams && patSameWithDoc) {
                // 2016-6-18 luf:区分1秒弹框和确定，将 ErrorCode.SERVICE_ERROR 改成 608
//                logger.error("患者与目标医生不能为同一个人");
                throw new DAOException(608, "患者与目标医生不能为同一个人");
            }
        }
        return true;
    }

    /**
     * 根据主键查询单条预约记录(返回预约记录对象+病人对象+目标医生对象+申请医生对象+对方医生对象)
     *
     * @param telClinicId  云诊室号
     * @param clinicObject 1接诊方2出诊方
     * @param platform     视频流平台 值见CloudClinicSetConstant.java
     * @return
     */
    @RpcService
    public AppointRecordAndPatientAndDoctor getPlatformFullAppointRecordByTelClinicId(
            String telClinicId, int clinicObject, String platform) {
        AppointRecordDAO appointDao = DAOFactory.getDAO(AppointRecordDAO.class);
        AppointRecord record = appointDao.getByTelClinicIdAndClinicObject(telClinicId, clinicObject);
        if (record == null || record.getTelClinicFlag() == null || (record.getTelClinicFlag() != null && record.getTelClinicFlag() != 1)) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该排队信息不是远程门诊排队信息，无法查看预约详情单");
        }

        return getPlatformFullAppointRecordById(record.getAppointRecordId(), platform);
    }

    /**
     * 根据主键查询单条预约记录(返回预约记录对象+病人对象+目标医生对象+申请医生对象+对方医生对象)
     *
     * @param appointRecordId
     * @param platform        视频流平台 值见CloudClinicSetConstant.java
     * @return
     * @author LF
     */
    @RpcService
    public AppointRecordAndPatientAndDoctor getPlatformFullAppointRecordById(
            int appointRecordId, String platform) {
        DoctorDAO dao = DAOFactory.getDAO(DoctorDAO.class);
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        AppointRecordDAO appointDao = DAOFactory.getDAO(AppointRecordDAO.class);
        CloudClinicSetService service = AppContextHolder.getBean("cloudClinicSetService", CloudClinicSetService.class);

        AppointRecordAndPatientAndDoctor result = appointDao.getAppointRecordAndPatientAndDoctorById(appointRecordId);
        AppointRecord aRecord = result.getAppointrecord();

        String reqUser = aRecord.getAppointUser();
        // 预约单详情, r如果是患者申请， 则requestDoc为null 申请信息从患者中取值
//		if (!StringUtils.isEmpty(reqUser) && reqUser.length() != 32) {
//			Doctor reqDoctor = dao.getByDoctorId(Integer.parseInt(reqUser));
//			result.setRequestDoctor(reqDoctor);
//		}
        // 2016-6-8 luf:申请人信息考虑患者申请，添加reqUser.length() == 32和result.setRequestPatient(appointUser);等
        if (!StringUtils.isEmpty(reqUser)) {
            if (reqUser.length() == 32) {
                Patient appointUser = patientDAO.getPatientPartInfo(reqUser);
                if (appointUser != null) {
                    appointUser.setMpiId(reqUser);
                    result.setRequestPatient(appointUser);
                }
            } else {
                Doctor appointUser = dao.getDoctorPartInfo(Integer
                        .parseInt(reqUser));
                if (appointUser != null) {
                    result.setRequestDoctor(appointUser);
                }
            }
        }

        Integer oppDoc = aRecord.getOppdoctor();
        if (oppDoc != null && oppDoc > 0) {
            Doctor oppDoctor = dao.getByDoctorId(oppDoc);
            result.setOppDoctor(oppDoctor);

            CloudClinicSet oppDocSet = service.getDoctorSetByPlatform(oppDoc, platform);
            result.setOppDocSet(oppDocSet);
        }
        return result;
    }


    /**
     * 根据主键查询单条预约记录(返回预约记录对象+病人对象+目标医生对象+申请医生对象+对方医生对象)
     *
     * @param telClinicId  云诊室号
     * @param doctorId     当前医生Id
     * @param clinicObject 1接诊方2出诊方
     * @param platform     视频流平台 值见CloudClinicSetConstant.java
     * @return
     * @date 2016-7-20 luf:拷贝原 getFullAppointRecordById 添加排队信息出现按
     */
    @RpcService
    public AppointRecordAndPatientAndDoctor getPlatformFullAppointRecordByTelClinicIdWithQueue(
            String telClinicId, int doctorId, int clinicObject, String platform) {
        AppointRecordDAO appointDao = DAOFactory.getDAO(AppointRecordDAO.class);
        AppointRecord record = appointDao.getByTelClinicIdAndClinicObject(telClinicId, clinicObject);
        if (record == null || record.getTelClinicFlag() == null || (record.getTelClinicFlag() != null && record.getTelClinicFlag() != 1)) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该排队信息不是远程门诊排队信息，无法查看预约详情单");
        }

        return getPlatformFullAppointRecordByIdWithQueue(record.getAppointRecordId(), doctorId, platform);
    }

    /**
     * 根据主键查询单条预约记录(返回预约记录对象+病人对象+目标医生对象+申请医生对象+对方医生对象)
     *
     * @param appointRecordId
     * @param platform        视频流平台 值见CloudClinicSetConstant.java
     * @return
     * @author LF
     * @date 2016-7-20 luf:拷贝原 getFullAppointRecordById 添加排队信息出现按
     */
    @RpcService
    public AppointRecordAndPatientAndDoctor getPlatformFullAppointRecordByIdWithQueue(
            int appointRecordId, int doctorId, String platform) {
        DoctorDAO dao = DAOFactory.getDAO(DoctorDAO.class);
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        AppointRecordDAO appointDao = DAOFactory.getDAO(AppointRecordDAO.class);
        CloudClinicSetService service = AppContextHolder.getBean("cloudClinicSetService", CloudClinicSetService.class);

        AppointRecordAndPatientAndDoctor result = appointDao.getAppointRecordAndPatientAndDoctorById(appointRecordId);
        AppointRecord aRecord = result.getAppointrecord();

        String reqUser = aRecord.getAppointUser();
        // 预约单详情, r如果是患者申请， 则requestDoc为null 申请信息从患者中取值
//		if (!StringUtils.isEmpty(reqUser) && reqUser.length() != 32) {
//			Doctor reqDoctor = dao.getByDoctorId(Integer.parseInt(reqUser));
//			result.setRequestDoctor(reqDoctor);
//		}
        // 2016-6-8 luf:申请人信息考虑患者申请，添加reqUser.length() == 32和result.setRequestPatient(appointUser);等
        Boolean isRequest = null;
        if (!StringUtils.isEmpty(reqUser)) {
            if (reqUser.length() == 32) {
                Patient appointUser = patientDAO.getPatientPartInfo(reqUser);
                if (appointUser != null) {
                    appointUser.setMpiId(reqUser);
                    result.setRequestPatient(appointUser);
                }
            } else {
                Doctor appointUser = dao.getDoctorPartInfo(Integer
                        .parseInt(reqUser));
                if (appointUser != null) {
                    result.setRequestDoctor(appointUser);
                    if (reqUser.equals(String.valueOf(doctorId))) {
                        isRequest = true;
                    } else {
                        isRequest = false;
                    }
                }
            }
        }

        Integer oppDoc = aRecord.getOppdoctor();
        if (oppDoc != null && oppDoc > 0) {
            Doctor oppDoctor = dao.getByDoctorId(oppDoc);
            result.setOppDoctor(oppDoctor);
            UserSevice userSevice = new UserSevice();
            result.setOppUrtId(userSevice.getDoctorUrtIdByDoctorId(oppDoc));

            CloudClinicSet oppDocSet = service.getDoctorSetByPlatform(oppDoc, platform);
            result.setOppDocSet(oppDocSet);
            if (null != aRecord.getClinicObject() && 0 < aRecord.getClinicObject()) {
                Integer clinicObject = aRecord.getClinicObject();
                Integer reqDocId = aRecord.getDoctorId();
                int tarDocId = aRecord.getDoctorId();
                if (clinicObject.equals(1)) {
                    tarDocId = aRecord.getOppdoctor();
                } else {
                    reqDocId = aRecord.getOppdoctor();
                }
                CloudClinicQueueDAO queueDAO = DAOFactory.getDAO(CloudClinicQueueDAO.class);
                List<CloudClinicQueue> queues = queueDAO.findAllQueueByTarget(tarDocId);
                int orderNum = -1;
                boolean isQueue = false;
//                Boolean isRecord = false;
                Integer queueId = null;
                String telClinicId = aRecord.getTelClinicId();
//                for (CloudClinicQueue queue : queues) {
//                    if (telClinicId.equals(queue.getTelClinicId())) {
//                        isRecord = true;
//                    }
//                }
//                if (isRecord) {
                for (CloudClinicQueue queue : queues) {
//                        Integer request = queue.getRequestDoctor();
                    orderNum++;
//                        if (request.equals(reqDocId)) {
//                            isQueue = true;
//                            break;
//                        }
                    if (telClinicId.equals(queue.getTelClinicId())) {
                        isQueue = true;
                        queueId = queue.getId();
                        break;
                    }
                }
                if (!isQueue) {
                    orderNum++;
                }
//                }
                result.setIsRequest(isRequest);
                result.setOrderNum(orderNum);
                result.setIsQueue(isQueue);
                result.setQueueId(queueId);
                String oppsum = appointDao.getSummary(telClinicId, oppDoc);
                aRecord.setOppSummary(oppsum);
                result.setAppointrecord(aRecord);
            }
        }
        Integer telClinicFlag = aRecord.getTelClinicFlag();
        if (telClinicFlag!=null && telClinicFlag.equals(CloudConstant.FLAG_APPOINTCLOUD)) {
            Integer clinicObject = aRecord.getClinicObject();
            Integer organId = aRecord.getOrganId();
            if (clinicObject!=null && clinicObject.equals(CloudConstant.RECORD_RECEIVE)) {
                organId = aRecord.getOppOrgan();
            }
            if (clinicObject!=null) {
                OrganConfigService organConfigService = AppDomainContext.getBean("eh.organConfigService", OrganConfigService.class);
                result.setCanShowPayButton(organConfigService.canShowPayButton(organId, aRecord.getWorkDate()));
            }
        }
        return result;
    }


    /**
     * 获取今日待就诊远程门诊服务(小鱼在线)
     *
     * @param doctorId 医生id
     * @param platform 视频流平台 值见CloudClinicSetConstant.java
     * @return
     */
    @RpcService
    public HashMap<String, Object> findTodayUnDoRecordListByPlatform(Integer doctorId, String platform) {
        HashMap<String, Object> returnMap = new HashMap<String, Object>();

        AppointRecordDAO recordDao = DAOFactory.getDAO(AppointRecordDAO.class);

        Date now = Context.instance().get("date.today", Date.class);

        //作为出诊方
        List<AppointRecord> outlist = recordDao.findTodayOutAppointList(doctorId, now, platform);
        List<HashMap<String, Object>> outReturnList = getInfo(outlist, platform);
        returnMap.put("out", outReturnList);

        //作为接诊方
        List<AppointRecord> inlist = recordDao.findTodayInAppointList(doctorId, now);
        List<HashMap<String, Object>> inReturnList = getInfo(inlist, platform);

        Collections.sort(inReturnList, new Comparator<HashMap<String, Object>>() {
            public int compare(HashMap<String, Object> e1, HashMap<String, Object> e2) {
//				至于降序升序，可以这样比较：
//				假如A的值大于B，你返回1。这样调用Collections.sort()方法就是升序
//				假如A的值大于B，你返回-1。这样调用Collections.sort()方法就是降序

                AppointRecord appoint1 = (AppointRecord) e1.get("appointRecord") == null ? new AppointRecord() : (AppointRecord) e1.get("appointRecord");
                AppointRecord appoint2 = (AppointRecord) e2.get("appointRecord") == null ? new AppointRecord() : (AppointRecord) e2.get("appointRecord");

                Integer ClinicStatus1 = appoint1.getClinicStatus() == null ? 0 : appoint1.getClinicStatus();
                Integer ClinicStatus2 = appoint2.getClinicStatus() == null ? 0 : appoint2.getClinicStatus();
                Boolean b1 = ClinicStatus1 == 2 ? true : false;
                Boolean b2 = ClinicStatus2 == 2 ? true : false;
                int a = b1.compareTo(b2);
                if (a != 0) {
                    return a > 0 ? 1 : -1;
                }

                CloudClinicSet set1 = (CloudClinicSet) e1.get("clinicSet") == null ? new CloudClinicSet() : (CloudClinicSet) e1.get("clinicSet");
                CloudClinicSet set2 = (CloudClinicSet) e2.get("clinicSet") == null ? new CloudClinicSet() : (CloudClinicSet) e2.get("clinicSet");
                Integer onLineStatus1 = set1.getOnLineStatus() == null ? 0 : set1.getOnLineStatus();
                Integer onLineStatus2 = set2.getOnLineStatus() == null ? 0 : set2.getOnLineStatus();
                a = onLineStatus1 - onLineStatus2;
                if (a != 0) {
                    return a > 0 ? -1 : 1;
                }

                Boolean isQueue1 = (Boolean) e1.get("isQueue") == null ? false : (Boolean) e1.get("isQueue");
                Boolean isQueue2 = (Boolean) e2.get("isQueue") == null ? false : (Boolean) e2.get("isQueue");
                a = isQueue1.compareTo(isQueue2);
                if (a != 0) {
                    return a > 0 ? -1 : 1;
                }

                Integer orderNum1 = (Integer) e1.get("orderNum") == null ? -1 : (Integer) e1.get("orderNum");
                Integer orderNum2 = (Integer) e2.get("orderNum") == null ? -1 : (Integer) e2.get("orderNum");
                Integer num1 = !b1 && isQueue1 ? orderNum1 : 0;
                Integer num2 = !b2 && isQueue2 ? orderNum2 : 0;
                a = num1 - num2;
                if (a != 0) {
                    return a > 0 ? 1 : -1;
                }

                Date date1 = appoint1.getStartTime();
                Date date2 = appoint2.getStartTime();
                a = date1.compareTo(date2);
                if (a != 0) {
                    return a > 0 ? 1 : -1;
                }

                return 1;
            }
        });

        returnMap.put("in", inReturnList);
        return returnMap;
    }

    private List<HashMap<String, Object>> getInfo(List<AppointRecord> list, String platform) {
        PatientDAO patientDao = DAOFactory.getDAO(PatientDAO.class);

        List<HashMap<String, Object>> returnList = new ArrayList<>();
        if (list != null && list.size() > 0) {
            for (AppointRecord a : list) {
                HashMap<String, Object> map = new HashMap<String, Object>();

                //根据前端组装预约信息
                AppointRecord appoint = new AppointRecord();
                appoint.setAppointRecordId(a.getAppointRecordId());
                appoint.setWorkDate(a.getWorkDate());
                appoint.setStartTime(a.getStartTime());
                appoint.setEndTime(a.getEndTime());
                appoint.setClinicObject(a.getClinicObject());
                appoint.setClinicStatus(a.getClinicStatus());
                appoint.setTelClinicFlag(a.getTelClinicFlag());
                appoint.setTelClinicId(a.getTelClinicId());
                appoint.setOppdoctor(a.getOppdoctor());
                appoint.setOppOrgan(a.getOppOrgan());
                map.put("appointRecord", appoint);

                Integer oppdocId = a.getOppdoctor();
                CloudClinicSetService service = AppContextHolder.getBean("cloudClinicSetService", CloudClinicSetService.class);
                CloudClinicSet set = service.getDoctorSetByPlatform(oppdocId, platform);
                map.put("clinicSet", set);

                Patient p = patientDao.getPatientPartInfoV2(a.getMpiid());
                map.put("patient", p);

                if (null != a.getClinicObject() && 0 < a.getClinicObject()) {
                    Integer clinicObject = a.getClinicObject();
                    Integer reqDocId = a.getDoctorId();
                    int tarDocId = a.getDoctorId();
                    if (clinicObject.equals(1)) {
                        tarDocId = a.getOppdoctor();
                    } else {
                        reqDocId = a.getOppdoctor();
                    }
                    CloudClinicQueueDAO queueDAO = DAOFactory.getDAO(CloudClinicQueueDAO.class);
                    List<CloudClinicQueue> queues = queueDAO.findAllQueueByTarget(tarDocId);
                    int orderNum = -1;
                    boolean isQueue = false;
                    Integer queueId = null;
                    String telClinicId = a.getTelClinicId();

                    for (CloudClinicQueue queue : queues) {
                        orderNum++;
                        if (telClinicId.equals(queue.getTelClinicId())) {
                            isQueue = true;
                            queueId = queue.getId();
                            break;
                        }
                    }
                    if (!isQueue) {
                        orderNum++;
                    }
                    map.put("orderNum", orderNum);
                    map.put("isQueue", isQueue);
                    map.put("queueId", queueId == null ? 0 : queueId);
                }
                returnList.add(map);
            }
        }

        return returnList;
    }

    /**
     * 校验线下病人验证码
     * @param flag 前段验证码效验通过次数
     *
     * */
    @RpcService
    public Map<String,Object> validatePatientOfflineBeforeAppoint(AppointRecord  appointRecord, int flag){
        //上海6元不支持挂号
        final HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
        hisServiceConfigDao.disableAppoint(appointRecord.getOrganId());

        Map<String,Object> res = new HashMap<>();
        Integer organid = appointRecord.getOrganId();
        String mpiid = appointRecord.getMpiid();
        String cardId = appointRecord.getCardId();
        res = isNeedVCode(organid,mpiid,cardId,flag);
        return res;
    }

    public Map<String,Object> isNeedVCode(Integer organid,String mpiid, String cardId,int flag){
        Map<String,Object> res = new HashMap<>();

        PatientDAO  patientDao = DAOFactory.getDAO(PatientDAO.class);
        Patient patient = patientDao.getByMpiId(mpiid);
        res.put("patientName", patient.getPatientName());
        res.put("mpiId", mpiid);
        res.put("organId", organid);
        HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
        HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(organid);
        String needVcode = cfg.getNeedValidateCode();
        boolean isNeedVcode = "1".equals(needVcode);
        if(isNeedVcode){
            Integer canfile = cfg.getCanFile();
            //先从数据库查询该病人有没有医院的病历号或卡证
            HealthCardDAO healthCardDAO = DAOFactory.getDAO(HealthCardDAO.class);
            List<HealthCard> card = healthCardDAO.findByMpiIdAndCardOrgan(mpiid, organid);//病历号也是医院卡证的一种

            Map<String, Object> patientMap = hisServiceConfigDao.canAppointAndCanFile(organid,mpiid);
            res.put("patient",patientMap);
            //在云平台没有
            if(card==null||card.size()==0){
                sendVcode(organid, mpiid, cardId, res, patient, cfg, patientMap);

                //补丁代码，当医院的需要验证码为true时候，再查询预约记录，如果有记录，设置isNeedVcode为false
                if (res != null && res.get("isNeedVcode") != null && ((Boolean) res.get("isNeedVcode")) == true) {
                    if (!firstTimeAppoint(mpiid, organid)) {
                        res.put("isNeedVcode", false);
                    }
                }

            }else{
                //有的话直接预约
                //todo 初次预约也需要
                if (firstTimeAppoint(mpiid, organid)) {
                    logger.info("mpiId="+mpiid+" organId= "+ organid + "是第一次预约,有卡");
                    sendVcode(organid, mpiid, cardId, res, patient, cfg, patientMap);
                } else {
                    res.put("hasCard",true);
                    res.put("isNeedVcode",false);
                }

                logger.info("返回的res=="+JSONUtils.toString(res));
            }
            if (flag > 0) {
                res.put("isNeedVcode",false);
            }
        }else{
            res.put("isNeedVcode",false);
        }
        return res;
    }

    private boolean firstTimeAppoint(String mpiid, Integer organid) {
        boolean firstTimeAppoint = false;
        AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
        List<AppointRecord> appointRecords = appointRecordDAO.findbyOrganIdAndMpiid(organid,mpiid);
        if (appointRecords.isEmpty()){
            firstTimeAppoint = true;
        }
        return firstTimeAppoint;
    }

    private void sendVcode(Integer organid, String mpiid, String cardId, Map<String, Object> res, Patient patient, HisServiceConfig cfg, Map<String, Object> patientMap) {
        res.put("isNeedVcode",true);
        String hisServiceId = cfg.getAppDomainId() + ".patientService";//调用服务id
        RpcServiceInfoStore rpcServiceInfoStore = AppDomainContext.getBean("eh.rpcServiceInfoStore", RpcServiceInfoStore.class);
//        RpcServiceInfo info = rpcServiceInfoStore.getInfo(hisServiceId);
        boolean hasOrNot = true;
        String organMobile = "";
//        if(info==null){
//            hasOrNot = true;
//        }else {
            PatientQueryRequest req = new PatientQueryRequest();
            req.setMpi(mpiid);
            req.setPatientName(patient.getPatientName());
            req.setCertID(patient.getIdcard());
            req.setMobile(patient.getMobile());
            req.setCredentialsType("01");
            req.setGuardianFlag(patient.getGuardianFlag());
            req.setGuardianName(patient.getGuardianName());
            req.setCardID(cardId);
            req.setBirthday(patient.getBirthday());
            req.setPatientSex(patient.getPatientSex());
            HisResponse<PatientQueryRequest> dd = null;
            if(DBParamLoaderUtil.getOrganSwich(req.getOrgan())){
                IPatientHisService iPatientHisService = AppDomainContext.getBean("his.iPatientHisService", IPatientHisService.class);
                HisResponseTO<PatientQueryRequestTO> responseTO = new HisResponseTO();
                PatientQueryRequestTO reqTO= new PatientQueryRequestTO();
                BeanUtils.copy(req,reqTO);
                responseTO = iPatientHisService.queryPatient(reqTO);
                dd =new HisResponse<PatientQueryRequest>();
                BeanUtils.copy(responseTO, dd);
                if(null!=responseTO.getData()){
                    PatientQueryRequest patientQueryRequestFlag=new PatientQueryRequest();
                    BeanUtils.copy(responseTO.getData(), patientQueryRequestFlag);
                    dd.setData(patientQueryRequestFlag);
                }
            }else{
                dd = (HisResponse) RpcServiceInfoUtil.getClientService(IPatientService.class, hisServiceId, "queryPatient", req);
            }
            hasOrNot = dd==null?false:dd.isSuccess();
            res.put("hasOrNot",hasOrNot);
            patientMap.put("hasOrNot",hasOrNot);//更新patientMap
            if (dd != null && dd.getData() != null) {
                organMobile = dd.getData().getMobile();
                cardId = dd.getData().getCardID();
                res.put("hasCard",true);
                res.put("hasOrNot",true);
                res.put("hasOrNot",true);
                res.put("cardId",cardId);
                res.put("organId",organid);
                res.put("mpiId",mpiid);
                sendMobileVcode(res, organMobile);
            }else{
                res.put("hasCard",false);
            }
//        }


        logger.info(JSONUtils.toString(res));
    }

    private void sendMobileVcode(Map<String, Object> res, String organMobile) {
        //如果医院中的  手机号码为空
        if(StringUtils.isEmpty(organMobile)){
            String msg = "您在医院的记录中没有手机号，无法接收验证短信，请前往医院登记手机号";
            res.put("isNeedVcode",true);
            res.put("msg",msg);
            res.put("mobile","");
        }else {
            //发送验证码
            ValidateCodeDAO vcDao = DAOFactory.getDAO(ValidateCodeDAO.class);
            String vCode = vcDao.sendValidateCodeToPatient(organMobile);
            res.put("mobile",organMobile);
            res.put("vCode",vCode);
        }
    }
    @RpcService
    public QueryHisAppointRecordResponse queryHisAppointRecordDetail(QueryHisAppointRecordParam param){
        validateParamDetail(param);
        String paramStr = JSONUtils.toString(param);
        logger.info("查询his预约详情记录param：{}",paramStr);

        HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
        HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(param.getOrganID());
        String hisServiceId = cfg.getAppDomainId()+".appointRecordService";
//        if(DBParamLoaderUtil.getOrganSwich(param.getOrganID())){
//            IPatientHisService iPatientHisService = AppDomainContext.getBean("his.iPatientHisService", IPatientHisService.class);
//            HisResponseTO<PatientQueryRequestTO> responseTO = new HisResponseTO();
//            PatientQueryRequestTO reqTO= new PatientQueryRequestTO();
//            BeanUtils.copy(req,reqTO);
//            responseTO = iPatientHisService.queryPatient(reqTO);
//            BeanUtils.copy(responseTO, dd);
//        }else{
        Object object = RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId, "queryHisAppointRecordDetail", param);
        if(object!=null) {
            HisResponse<QueryHisAppointRecordResponse> dd = (HisResponse<QueryHisAppointRecordResponse>) object;
            if(dd.isSuccess()){
                return dd.getData();
            }
        }
        return null;

    }

    private void validateParamDetail(QueryHisAppointRecordParam param) {
        Integer organId = param.getOrganID();
        if(organId==null||organId==0){
            throw new DAOException(DAOException.VALUE_NEEDED, "OrganID is required!");
        }
        if(StringUtils.isNotEmpty(param.getAppointID())){
            throw new DAOException(DAOException.VALUE_NEEDED, "AppointID is required!");
        }
    }

    @RpcService
    public List<QueryHisAppointRecordResponse> queryHisAppointRecord(QueryHisAppointRecordParam param){

        validateParam(param);
        String paramStr = JSONUtils.toString(param);
        logger.info("查询his预约记录param：{}",paramStr);

        HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
        HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(param.getOrganID());
        String hisServiceId = cfg.getAppDomainId()+".appointRecordService";
        HisResponse dd = new HisResponse();
        if(DBParamLoaderUtil.getOrganSwich(param.getOrganID())){
            IAppointHisService appointService = AppContextHolder.getBean("his.iAppointHisService", IAppointHisService.class);
            HisResponse<List<QueryHisAppointRecordResponseTO>> responseTO = new HisResponse<>();
            QueryHisAppointRecordParamTO reqTO= new QueryHisAppointRecordParamTO();
            BeanUtils.copy(param,reqTO);
            responseTO = appointService.queryHisAppointRecords(reqTO);
            BeanUtils.copy(responseTO, dd);
        }else{
            Object object = RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId, "queryHisAppointRecords", param);
            if(object!=null){
                dd = (HisResponse<List<QueryHisAppointRecordResponse>>)object;
                if(dd.isSuccess()){
                    Patient patient = DAOFactory.getDAO(PatientDAO.class).getByMpiId(param.getMpiID());
                    List<QueryHisAppointRecordResponse> res = (List<QueryHisAppointRecordResponse>) dd.getData();
                    for(QueryHisAppointRecordResponse ar : res){
                        ar.setPatientSex(patient.getPatientSex());
                        ar.setPatientName(patient.getPatientName());
                        ar.setAge(DateConversion.getAge(patient.getBirthday()));
                        ar.setPatientType(patient.getPatientType());
                        String dateStr = DateConversion.convertRequestDateForBuss(ar.getOperateDate());
                        ar.setDateDiff(dateStr);
                        if(StringUtils.isNoneBlank(ar.getDoctorID())){
                            List<Employment> emp = DAOFactory.getDAO(EmploymentDAO.class).findByJobNumberAndOrganId(ar.getDoctorID().trim(), Integer.parseInt(ar.getOrganID()));
                            if(emp!=null && emp.size()>=1){
                                Doctor doctor = DAOFactory.getDAO(DoctorDAO.class).getByDoctorId(emp.get(0).getDoctorId());
                                ar.setDoctor(doctor);
                            }
                        }

                    }
                    Collections.sort(res, new Comparator<QueryHisAppointRecordResponse>() {
                        @Override
                        public int compare(QueryHisAppointRecordResponse o1, QueryHisAppointRecordResponse o2) {
                            return o2.getOperateDate().compareTo(o1.getOperateDate());
                        }
                    });
                    return res;
                }
            }
        }
        return null;
    }

    private void  validateParam(QueryHisAppointRecordParam param) {
        Integer organId = param.getOrganID();
        if(organId==null||organId==0){
            throw new DAOException(DAOException.VALUE_NEEDED, "OrganID is required!");
        }
        if(StringUtils.isEmpty(param.getMpiID())){
            throw new DAOException(DAOException.VALUE_NEEDED, "mpiID is required!");

        }
        if(param.getStartTime()==null){
            param.setStartTime(DateConversion.getDateAftXDays(new Date(),-14));
            param.setEndTime(DateConversion.getDateAftXDays(new Date(),14));
        }
    }

    @RpcService
    public boolean cancelHisAppointRecord(QueryHisAppointRecordResponse param){
        String organAppointID = param.getAppointID();
        String organId = param.getOrganID() ;
        AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
        AppointRecord ngariRecord = appointRecordDAO.getByOrganAppointIdAndOrganId(organAppointID, Integer.parseInt(organId));
        if(ngariRecord!=null){
            return appointRecordDAO.cancel(ngariRecord.getAppointRecordId(),ngariRecord.getAppointUser(),ngariRecord.getAppointName(),param.getCancelReason());
        }
        HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
        HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(Integer.parseInt(organId));
        String hisServiceId = cfg.getAppDomainId() + ".appointmentService";// 调用服务id
        AppointCancelRequest req = getCancelReq(param);
        logger.info("send hisRecord Cancel request to his:" + JSONUtils.toString(req));
        boolean s = DBParamLoaderUtil.getOrganSwich(Integer.parseInt(organId));
        boolean flag =false;
        if(s){
            IAppointHisService appointService = AppContextHolder.getBean("his.iAppointHisService", IAppointHisService.class);
            AppointCancelRequestTO request = new AppointCancelRequestTO();
            logger.info("send cancel request to his,req=" +req.toString());
            BeanUtils.copy(req,request);
            logger.info("send cancel request to his,request=" +request);
            HisResponseTO<Object> res = appointService.cancelAppoint(request);
            logger.info("send cancel request to his,res=" +res);
            flag = res.isSuccess();
            logger.info(param.getAppointID()+"取消"+ (flag?"成功":"失败:"+res.getMsg()));
        }else{
            flag = (boolean) RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId, "cancelAppoint", req);
        }
        return flag;
    }
    private AppointCancelRequest getCancelReq(QueryHisAppointRecordResponse param){
        AppointCancelRequest req = new AppointCancelRequest();
        req.setTelClinicFlag(0);
        req.setPatientId(param.getPatID());
        req.setAppointId(param.getAppointID());
        req.setCancelReason(param.getCancelReason());
        req.setOrganId(param.getOrganID());
        req.setPatientName(param.getPatientName());
        req.setCardNum(param.getCardID());
        req.setCardID(param.getCardID());
        req.setCardType(param.getCardType());
        String certID = param.getCertID();
        if(StringUtils.isNotEmpty(certID)){
            String c = LocalStringUtil.getSubstringByDiff(certID, "-");
            req.setCardNum(c);
        }
        return  req;
    }
}
