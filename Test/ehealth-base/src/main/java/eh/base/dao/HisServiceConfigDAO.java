package eh.base.dao;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.ngari.common.mode.HisResponseTO;
import com.ngari.his.patient.mode.PatientQueryRequestTO;
import com.ngari.his.patient.service.IPatientHisService;
import ctd.net.broadcast.Observer;
import ctd.net.broadcast.support.BroadcastInstance;
import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.event.support.AbstractDAOEventLisenter;
import ctd.persistence.event.support.CreateDAOEvent;
import ctd.persistence.event.support.UpdateDAOEvent;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.spring.AppDomainContext;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.CdrType;
import eh.base.constant.ErrorCode;
import eh.base.constant.ServiceType;
import eh.bus.dao.AppointRecordDAO;
import eh.bus.service.HisRemindService;
import eh.bus.service.common.CurrentUserInfo;
import eh.entity.base.HisServiceConfig;
import eh.entity.bus.AppointRecord;
import eh.entity.bus.RpcServiceInfo;
import eh.entity.his.PatientQueryRequest;
import eh.entity.his.hisCommonModule.HisResponse;
import eh.entity.mpi.HealthCard;
import eh.entity.mpi.Patient;
import eh.mpi.dao.HealthCardDAO;
import eh.mpi.dao.PatientDAO;
import eh.remote.IPatientService;
import eh.util.DBParamLoaderUtil;
import eh.util.RpcServiceInfoStore;
import eh.util.RpcServiceInfoUtil;
import eh.utils.DateConversion;
import eh.utils.ValidateUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public abstract class HisServiceConfigDAO extends HibernateSupportDelegateDAO<HisServiceConfig> {
    public static final org.slf4j.Logger logger = LoggerFactory.getLogger(HisServiceConfigDAO.class);
    private LoadingCache<Integer, HisServiceConfig> store = CacheBuilder.newBuilder().build(new CacheLoader<Integer, HisServiceConfig>() {
        @Override
        public HisServiceConfig load(Integer integer) throws Exception {
            return getByOrganIdDB(integer);
        }
    });

    public HisServiceConfigDAO() {
        super();
        this.setEntityName(HisServiceConfig.class.getName());
        this.setKeyField("id");
        BroadcastInstance.getSubscriber().attach(HisServiceConfigDAO.class.getSimpleName(), new Observer<Integer>() {
            @Override
            public void onMessage(Integer message) {
                store.invalidate(message);
            }
        });
        this.addEventListener(new AbstractDAOEventLisenter() {
            @Override
            public void onUpdate(UpdateDAOEvent e) {
                HisServiceConfig config = (HisServiceConfig) e.getTarget();
                BroadcastInstance.getPublisher().publish(HisServiceConfigDAO.class.getSimpleName(), config.getOrganid());
            }

            @Override
            public void onCreate(CreateDAOEvent e) {
                HisServiceConfig config = (HisServiceConfig) e.getTarget();
                BroadcastInstance.getPublisher().publish(HisServiceConfigDAO.class.getSimpleName(), config.getOrganid());
            }
        });
    }

    /**
     * 刷新hisconfig缓存方法：
     * 将缓存中对应数据清空，则再次访问，会重新从数据库中加载
     * @param organId
     */
    @RpcService
    public void reload(Integer organId){
        BroadcastInstance.getPublisher().publish(HisServiceConfigDAO.class.getSimpleName(), organId);
    }

    @DAOMethod(sql = "from HisServiceConfig where organId=:organId")
    public abstract HisServiceConfig getByOrganIdDB(@DAOParam("organId")int organId);

    @RpcService
    public HisServiceConfig getByOrganId(int id){
        try {
            return store.get(id);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public HisServiceConfig get(Object id) throws DAOException {
        return super.get(id);
    }

    /**
     * 该机构 指定服务是否可用
     *
     * @param organ 机构id
     * @param type  服务类型
     * @return
     */
    @RpcService
    public boolean isServiceEnable(int organ, ServiceType type) {
        HisServiceConfig config = getByOrganId(organ);
        if (config == null)
            return false;
        if (type.equals(ServiceType.APPOINT)) {
            if (config.getAppointenable() != null && config.getAppointenable() == 1) {
                return true;
            }
        } else if (type.equals(ServiceType.TRANSFER)) {
            if (config.getTransferenable() != null && config.getTransferenable() == 1) {
                return true;
            }
        } else if (type.equals(ServiceType.MEDFILING)) {
            if (config.getMedfilingenable() != null && config.getMedfilingenable() == 1) {
                return true;
            }
        } else if (type.equals(ServiceType.PATIENTGET)) {
            if (config.getPatientgetenable() != null && config.getPatientgetenable() == 1) {
                return true;
            }
        } else if (type.equals(ServiceType.TOHIS)) {
            if (config.getTohis() != null && config.getTohis() == 1) {
                return true;
            }
        } else if (type.equals(ServiceType.CANAPPOINTTODAY)) {
            if (config.getCanAppointToday() != null && config.getCanAppointToday() == 1) {
                return true;
            }
        } else if (type.equals(ServiceType.SOURCEREAL)) {
            if (config.getSourcereal() != null && config.getSourcereal() == 1) {
                return true;
            }
        } else if (type.equals(ServiceType.CANREPORT)) {
            if (config.getCanReport() != null) {
                return true;
            }
        } else if (type.equals(ServiceType.HISSTATUS)) {
            if (config.getHisStatus() != null && config.getHisStatus() == 1) {
                return true;
            }
        } else if (type.equals(ServiceType.INHOSPTOHIS)) {
            if (config.getInhosptohis() != null && config.getInhosptohis() == 1) {
                return true;
            }
        } else if (type.equals(ServiceType.CALLNUM)) {
            if (config.getCallNum() != null && config.getCallNum() == 1) {
                return true;
            }
        } else if (type.equals(ServiceType.CANSIGN)) {
            if (config.getCanSign() != null && config.getCanSign()) {
                return true;
            }
        } else if (type.equals(ServiceType.EXISTORGANID)) {
            if (config.getExistOrgan() != null && config.getExistOrgan() == 1) {
                return true;
            }
        } else if (type.equals(ServiceType.organSMS)) {
            if (config.getOrganSMS() != null && config.getOrganSMS() == 1) {
                return true;
            }
        } else if (type.equals(ServiceType.CHECKTHIS)) {
            if (config.getCheckHis() != null && config.getCheckHis() == 1) {
                return true;
            }
        } else if (type.equals(ServiceType.cancelPay)) {
            if (config.getCancelPay() != null && config.getCancelPay() == 1) {
                return true;
            }
        } else {
            return false;
        }

        return false;
    }

    /**
     * @param organ 机构名称
     * @param type  服务名称
     * @return boolean
     * @function sms中调用，转诊是否调用his
     * @author zhangjr
     * @date 2015-12-1
     */
    @RpcService
    public boolean isToHisEnable(int organ, String type) {
        if (type.equals("TOHIS")) {
            return isServiceEnable(organ, ServiceType.TOHIS);
        }
        return false;
    }

    /**
     * 判断该机构是否支持影像服务
     *
     * @param organ
     * @return
     */
    @RpcService
    public boolean isSupportImage(int organ) {
        HisServiceConfig config = getByOrganId(organ);
        if (config == null)
            return false;
        if (config.getSupportImage() != null && config.getSupportImage() == 1) {
            return true;
        }
        return false;
    }

    /**
     * HIS是否启用标志
     *
     * @param organ
     * @return
     */
    @RpcService
    public boolean isHisEnable(int organ) {
        return isServiceEnable(organ, ServiceType.HISSTATUS);
    }

    /**
     * 机构His状态维护
     *
     * @param hisServiceConfig
     * @return
     * @date 2016-05-30 15:20:31
     */
    public HisServiceConfig updateHisServiceConfigForOp(HisServiceConfig hisServiceConfig) {
        if (hisServiceConfig == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "hisServiceConfig is null");
        }

        Integer organId = hisServiceConfig.getOrganid();
        Integer newStatus = hisServiceConfig.getHisStatus();
        Boolean sendMsgFlag = false;

        if (organId == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "organId of hisServiceConfig is null");
        }
        if (newStatus == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "hisStatus of hisServiceConfig is null");
        }

        HisServiceConfig hisConfig = this.getByOrganId(organId);
        if (hisConfig == null) {
            throw new DAOException("can not find the record by organId:" + hisServiceConfig.getOrganid());
        }

        //数据库中保存为0[正在维护],即将更新成1[维护完成],
        if ((hisConfig.getHisStatus() == null || hisConfig.getHisStatus() == 0)
                && newStatus == 1) {
            sendMsgFlag = true;
        }

        BeanUtils.map(hisServiceConfig, hisConfig);
        update(hisConfig);

        //发送消息给操作过的医生
        if (sendMsgFlag) {
            new HisRemindService().pushMsgForHisBeNormal(organId);
        }
        return hisConfig;
    }

    /**
     * 机构His状态维护
     *
     * @param hisServiceConfig
     * @return
     * @date 2017-01-16 15:20:31  andywang
     */
    public HisServiceConfig addOrUpdateHisServiceConfig(HisServiceConfig hisServiceConfig) {
        if (hisServiceConfig == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "hisServiceConfig is null");
        }
        if (hisServiceConfig.getOrganid() == null || StringUtils.isEmpty(hisServiceConfig.getOrganname())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "请选择机构！");
        }
        if (hisServiceConfig.getAppDomainId() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "AppDomainID不能为空！");
        }
        Integer organId = hisServiceConfig.getOrganid();
        Boolean sendMsgFlag = false;
        HisServiceConfig hisConfig = this.getByOrganId(organId);
        if (hisConfig == null) {//没有记录就添加新的记录
            try {
                if(StringUtils.isEmpty(hisServiceConfig.getCanAppoint()))
                {
                    hisServiceConfig.setCanAppoint(null);
                }
                if(StringUtils.isEmpty(hisServiceConfig.getCanReport()))
                {
                    hisServiceConfig.setCanReport(null);
                }
                if(StringUtils.isEmpty(hisServiceConfig.getCanPay()))
                {
                    hisServiceConfig.setCanPay(null);
                }
                hisConfig = save(hisServiceConfig);
                BusActionLogDAO ldao = DAOFactory.getDAO(BusActionLogDAO.class);
                ldao.recordLog("His服务配置", "", "HisServiceConfig", hisServiceConfig.getOrganname() + "添加HisServiceConfig[AppDomainID:" + hisServiceConfig.getAppDomainId() + "]");
            } catch (Exception e) {
                logger.error(e.getMessage());
                throw new DAOException(ErrorCode.SERVICE_ERROR, "添加config报错！");
            }
        } else {
            Integer newStatus = hisServiceConfig.getHisStatus();
            //数据库中保存为0[正在维护],即将更新成1[维护完成],
            if ((hisConfig.getHisStatus() == null || hisConfig.getHisStatus() == 0)
                    && newStatus == 1) {
                sendMsgFlag = true;
            }
            BeanUtils.map(hisServiceConfig, hisConfig);
            if(StringUtils.isEmpty(hisServiceConfig.getCanAppoint()))
            {
                hisConfig.setCanAppoint(null);
            }
            if(StringUtils.isEmpty(hisServiceConfig.getCanReport()))
            {
                hisConfig.setCanReport(null);
            }
            if(StringUtils.isEmpty(hisServiceConfig.getCanPay()))
            {
                hisConfig.setCanPay(null);
            }
            try {
                update(hisConfig);
                BusActionLogDAO ldao = DAOFactory.getDAO(BusActionLogDAO.class);
                ldao.recordLog("His服务配置", "", "HisServiceConfig", hisServiceConfig.getOrganname() + "修改HisServiceConfig[AppDomainID:" + hisServiceConfig.getAppDomainId() + "]");
            } catch (Exception e) {
                logger.error(e.getMessage());
                throw new DAOException(ErrorCode.SERVICE_ERROR, "更新config报错！");
            }
        }
        //发送消息给操作过的医生
        if (sendMsgFlag) {
            new HisRemindService().pushMsgForHisBeNormal(organId);
        }
        return hisConfig;
    }

    @DAOMethod(sql = "select confirmTimeLimit from HisServiceConfig where organid=:organid")
    public abstract Integer getConfirmTimeLimitByOrganid(@DAOParam("organid") int organId);


    @DAOMethod(sql = "select organid from HisServiceConfig where callNum=1")
    public abstract List<Integer> findByCallNum();

    @DAOMethod(sql = "select organid from HisServiceConfig where canAppoint is not null")
    public abstract List<Integer> findByCanAppoint();

    @DAOMethod(sql = "select organid from HisServiceConfig where canAppointToday=1")
    public abstract List<Integer> findByCanAppointToday();

    /**
     * 查询支持支付且强制支付的机构
     * [定时器，sms使用]
     * @return
     */
    @RpcService
    @DAOMethod(sql = "select h from HisServiceConfig h,OrganConfig c where h.organid=c.organId and h.payTime>0 and  (c.payAhead=-1 or c.payAhead>=0)",limit = 0)
    public abstract List<HisServiceConfig> findHisConfigByCanPayAndPayCancelTime();

    /**
     * 查询支持支付且强制支付的机构
     * [sms使用]
     * @return
     */
    @RpcService
    @DAOMethod(sql = "select h.organid from HisServiceConfig h,OrganConfig c where h.organid=c.organId and h.payTime>0 and  (c.payAhead=-1 or c.payAhead>=0) and  h.organid=:organId")
    public abstract List<Integer> findOrganIdsByCanPayAndPayCancelTime(@DAOParam("organId") Integer organId);

    @DAOMethod(sql = "select organid from HisServiceConfig where canAppoint is not null or canAppointToday=1")
    public abstract List<Integer> findTodayOrgans();

    /**
     * 获取就诊时间限制
     *
     * @param organId
     * @return Integer
     */
    @RpcService
    public Integer getConfirmTimeLimit(int organId) {
        Integer timeLimit = this.getConfirmTimeLimitByOrganid(organId);
        if (null == timeLimit || timeLimit <= 0) {
            return null;
        }
        return timeLimit;
    }

    /**
     * 判断接诊时间是否超过限制
     *
     * @param organId      机构内码
     * @param transferType 转诊类型
     * @param confirmTime  接诊时间
     * @return boolean
     */
    @RpcService
    public boolean isOverConfirmTimeLimit(int organId, int transferType, Date confirmTime) {
        Integer timeLimit = this.getConfirmTimeLimit(organId);
        if (transferType != 1) {
            return true;
        }
        if (null == timeLimit) {
            return true;
        }
        Date lastTime = DateConversion.getDateAftXDays(new Date(), timeLimit);
        confirmTime = DateConversion.getFormatDate(confirmTime, "yyyy-MM-dd");
        lastTime = DateConversion.getFormatDate(lastTime, "yyyy-MM-dd");
        if (confirmTime.after(lastTime)) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "就诊时间需在" + timeLimit + "天内");
        }
        return true;
    }

    /**
     * 获取是否支持实时挂号和建档
     *
     * @param organId
     * @param mpiId
     * @return
     * @date 2016-9-9 luf
     */
    @RpcService
    public Map<String, Object> canAppointAndCanFile(Integer organId, String mpiId) {

        //disableAppoint(organId);

        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        Patient patient = patientDAO.get(mpiId);
        if (patient == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "pateint is required!");
        }
        Map<String, Object> result = new HashMap<String, Object>();

        Map<String, Object> vMap = isNeedVCode(organId, mpiId, "");
        result.put("vMap", vMap);

        boolean hasOrNot = isPatientExistByOrgan(organId,mpiId);
        HisServiceConfig serviceConfig = this.getByOrganId(organId);
        if (serviceConfig == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该机构未配置！");
        }
        String canAppoint = serviceConfig.getCanAppoint();
        Integer canFile = serviceConfig.getCanFile();
        if (StringUtils.isEmpty(canAppoint)) {
            canAppoint = "0";
        }
        if (canFile == null) {
            canFile = 0;
        }
        result.put("canAppoint", canAppoint);
        result.put("canFile", canFile);
        result.put("hasOrNot", hasOrNot);

        return result;
    }

    /**
     * 上海第六人民医院不支持挂号
     *
     * @param organId
     */
    public void disableAppoint(Integer organId) {
        Map<String, String> wxAppProperties = CurrentUserInfo.getCurrentWxProperties();
        logger.info("wxAppProperties[{}]", wxAppProperties);
        //上海第六人民医院不能挂号
        String type = "0";//0 全国 1 当前管理单元，包括当前管理单元以下的，搜索按%	2 只限当前管理单元 搜索按=
        if (wxAppProperties != null && ValidateUtil.notBlankString(wxAppProperties.get("manageUnitId"))) {
            type = !org.springframework.util.StringUtils.isEmpty(wxAppProperties.get("type")) ? (String) wxAppProperties.get("type") : "0";
        }
        logger.info("当前请求来源类型=" + type);
        if (organId != null && isShangHai6(organId) && type.equalsIgnoreCase("0")) {
            logger.info("当前机构不支持平台挂号:1000899");
            throw new DAOException(DAOException.ACCESS_DENIED, "暂未开通，敬请期待");
        }
    }

    /**
     * p判断上海6院
     * @param organId
     * @return
     */
    public static boolean isShangHai6(Integer organId) {
        return organId.intValue() == 1000899 || organId.intValue() == 1000087;
    }

    public Map<String, Object> isNeedVCode(Integer organid, String mpiid, String cardId) {
        logger.info("isNeedVCode "+organid+","+mpiid+","+cardId);
        Map<String, Object> res = new HashMap<>();

        PatientDAO patientDao = DAOFactory.getDAO(PatientDAO.class);
        Patient patient = patientDao.getByMpiId(mpiid);
        res.put("patientName", patient.getPatientName());
        res.put("mpiId", mpiid);
        res.put("organId", organid);
        HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
        HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(organid);
        String needVcode = cfg.getNeedValidateCode();
        boolean isNeedVcode = "1".equals(needVcode);
        if (isNeedVcode) {
            //先从数据库查询该病人有没有医院的病历号或卡证
            boolean isExistBase = getPatientFromBase(organid, mpiid);
//            boolean isExistBase = false;
            //在云平台没有
            if (!isExistBase) {
                sendVcode(organid, mpiid, res, patient);
            } else {
                //有的话直接预约
                //todo 初次预约也需要
                if (firstTimeAppoint(mpiid, organid)) {
                    sendVcode(organid, mpiid, res, patient);
                } else {
                    res.put("hasCard", true);
                    res.put("isNeedVcode", false);
                }
            }
            if (!firstTimeAppoint(mpiid, organid)) {
                res.put("isNeedVcode", false);
            }
        } else {
            res.put("isNeedVcode", false);
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

    private void sendVcode(Integer organid, String mpiid, Map<String, Object> res, Patient patient) {
        res.put("isNeedVcode", true);
        boolean hasOrNot = true;
        String organMobile = "";
        HisResponse<PatientQueryRequest> dd = getPatientFromHis(organid, patient);
        logger.info("getPatientFromHis result= "+ JSONUtils.toString(dd));
        hasOrNot = dd == null ? false : dd.isSuccess();
        res.put("hasOrNot", hasOrNot);
        if (dd.getData() != null) {
            organMobile = dd.getData().getMobile();
            res.put("hasCard", true);
            res.put("hasOrNot", true);
            res.put("hasOrNot", true);
            res.put("cardId", dd.getData().getCardID());
            res.put("organId", organid);
            res.put("mpiId", mpiid);
        } else {
            res.put("hasCard", false);
        }
        //如果医院中的  手机号码为空
        if (StringUtils.isEmpty(organMobile)) {
            String msg = "您在医院的记录中没有手机号，无法接收验证短信，请前往医院登记手机号";
            res.put("isNeedVcode", true);
            res.put("msg", msg);
            res.put("mobile", "");
        } else {
            //发送验证码
            ValidateCodeDAO vcDao = DAOFactory.getDAO(ValidateCodeDAO.class);
            String vCode = vcDao.sendValidateCodeToPatient(organMobile);
            res.put("mobile", organMobile);
            res.put("vCode", vCode);
        }
    }

    /**
     * 根据机构获取是否支持实时挂号
     *
     * @param organId
     * @return
     */
    public Boolean isCanAppoint(int organId) {
        HisServiceConfig config = this.getByOrganId(organId);
        if (config == null || config.getCanAppoint() == null) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * 查询这个机构是否开通了这些电子病历接口
     * <item key="0" text="门诊病历"/>
     * <item key="1" text="检验报告"/>
     * <item key="2" text="检查报告"/>
     * <item key="3" text="处方"/>
     * <item key="4" text="治疗记录"/>
     * <item key="5" text="住院病历"/>
     * <item key="6" text="医嘱"/>
     * <item key="7" text="医学影像"/>
     * <item key="8" text="病患部位"/>
     * <item key="9" text="其他"/>
     */
    //TODO
    @RpcService
    public Map<CdrType, Boolean> getFunctionByOrgan(Integer organId) {
        Map<CdrType, Boolean> functionMap = new HashMap<CdrType, Boolean>();
        Boolean canReport = isServiceEnable(organId, ServiceType.CANREPORT);
        if (canReport) {
            functionMap.put(CdrType.labReport, canReport);
            functionMap.put(CdrType.checkReport, canReport);
        }


        return functionMap;
    }

    @RpcService
    public boolean isNeedSeal(Integer organID){
        HisServiceConfig config = this.getByOrganId(organID);
//        return  config!=null&&config.getIsNeedSeal()!=null&&config.getIsNeedSeal().intValue()==1;
        if(config!=null&&config.getIsNeedSeal()!=null){
            return config.getIsNeedSeal().intValue()==1;
        }
        return false;
    }

    /***
     * 查询患者是否在医院有建档
     * */
    public boolean isPatientExistByOrgan(Integer organId, String mpiId){
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        Patient patient = patientDAO.get(mpiId);
        if (patient == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "patient not fund");
        }
        boolean isBaseExist = getPatientFromBase(organId, mpiId);
//        boolean isBaseExist=false;
        if(isBaseExist){
            logger.info(StringUtils.join(mpiId,"在平台存在"));
            return true;
        }
        logger.info(StringUtils.join(mpiId,"在平台不存在"));
        HisResponse dd = getPatientFromHis(organId, patient);
        return dd.isSuccess();
    }

    /**
     * 从数据库查询该病人有没有医院的病历号或卡证
     * */
    private boolean getPatientFromBase(Integer organId, String mpiId){
        HealthCardDAO healthCardDAO = DAOFactory.getDAO(HealthCardDAO.class);
        List<HealthCard> card = healthCardDAO.findByMpiIdAndCardOrgan(mpiId, organId);//病历号也是医院卡证的一种
        return !card.isEmpty();
    }

    public HisResponse<PatientQueryRequest> getPatientFromHis(Integer organId,Patient patient) {
        HisResponse dd = new HisResponse();
        PatientQueryRequest req = getPatientParam(patient);
        req.setOrgan(organId);

        HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
        HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(organId);
        if (cfg == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "该机构未配置！");
        }
        String hisServiceId = cfg.getAppDomainId() + ".patientService";//调用服务id
//        RpcServiceInfoStore rpcServiceInfoStore = AppDomainContext.getBean("eh.rpcServiceInfoStore", RpcServiceInfoStore.class);
//        RpcServiceInfo info = rpcServiceInfoStore.getInfo(hisServiceId);
//        if (info == null) {//没有配置接口的默认认为使用身份证，默认存在
//            dd.setMsgCode("200");
//            return dd;
//        }

        //Object res = RpcServiceInfoUtil.getClientService(IPatientService.class, hisServiceId, "queryPatient", req);
        Object res = null;
        if(DBParamLoaderUtil.getOrganSwich(organId)){
            IPatientHisService iPatientHisService = AppDomainContext.getBean("his.iPatientHisService", IPatientHisService.class);
            PatientQueryRequestTO reqTO= new PatientQueryRequestTO();
            BeanUtils.copy(req,reqTO);
            logger.info("===reqTO:"+JSONUtils.toString(reqTO));
            HisResponseTO<PatientQueryRequestTO> responseTO = iPatientHisService.queryPatient(reqTO);
            logger.info("===responseTO:"+JSONUtils.toString(responseTO));
            HisResponse<PatientQueryRequest> resP = new HisResponse<PatientQueryRequest>();
            if(null!=responseTO){
            	BeanUtils.copy(responseTO, resP);
            	PatientQueryRequest temp = new PatientQueryRequest();
            	BeanUtils.copy(responseTO.getData(),temp);
            	temp.setHisCardList(responseTO.getData().getHisCardList());
            	resP.setData(temp);
            }
            return resP;
        }else{
        	res = RpcServiceInfoUtil.getClientService(IPatientService.class, hisServiceId, "queryPatient", req);
        }

        if(res!=null){
            dd = (HisResponse)res;
//            if(dd.isSuccess()){
//                Object resData = dd.getData();
//                if(resData!=null){
//                    try {
////                        HealthCardDAO cardDao = DAOFactory.getDAO(HealthCardDAO.class);
//                        PatientQueryRequest hisPatient = (PatientQueryRequest)resData;
//                        String organPatientID = hisPatient.getCardID();//医院病人标识
////                        String cardType = hisPatient.getCardType();
////                        String type = cardType==null||cardType.isEmpty()?"1":hisPatient.getCardType();//标识类型  1 医院卡证  2 医保卡   （一般默认医院卡证）
////                        cardDao.saveOrganCard(organId,organPatientID,patient.getMpiId(),type);
//                        return dd;
//                    }catch (Exception e){
//                        e.printStackTrace();
//                        dd.setMsgCode("-1");
//                        return dd;
//                    }
//                }
//            }
            return dd;
        }
        dd.setMsgCode("-1");
        return dd;
    }

    private PatientQueryRequest getPatientParam(Patient patient){
        PatientQueryRequest req = new PatientQueryRequest();
        req.setMpi(patient.getMpiId());
        req.setPatientName(patient.getPatientName());
        req.setCertID(patient.getRawIdcard());
        req.setMobile(patient.getMobile());
        req.setCredentialsType("01");//01身份证
        req.setGuardianFlag(patient.getGuardianFlag());
        req.setGuardianName(patient.getGuardianName());
        req.setBirthday(patient.getBirthday());
        req.setPatientSex(patient.getPatientSex());
        return req;
    }
    
}
