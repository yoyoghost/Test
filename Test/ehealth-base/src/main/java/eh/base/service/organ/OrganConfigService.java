package eh.base.service.organ;

import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.schema.annotation.ItemProperty;
import ctd.util.BeanUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.dao.*;
import eh.base.service.BusActionLogService;
import eh.bus.constant.CloudConstant;
import eh.bus.dao.ConsultSetDAO;
import eh.entity.base.*;
import eh.entity.mpi.PatientUserTypeEnum;
import eh.entity.wx.WXConfig;
import eh.op.auth.service.SecurityService;
import eh.op.dao.WXConfigsDAO;
import eh.utils.DateConversion;
import org.apache.log4j.Logger;
import org.springframework.util.NumberUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.*;

public class OrganConfigService {
    private static final Logger log = Logger.getLogger(OrganConfigService.class);

    private static <T extends Number> T parseNumber(String value, Class<T> targetClass, T defaultValue) {
        try {
            return value == null ? defaultValue : NumberUtils.parseNumber(value, targetClass);
        } catch (NumberFormatException e) {
            throw new DAOException(DAOException.VALIDATE_FALIED, "值不能解析为数字");
        }
    }

    /**
     * 设置机构Config，没有则创建记录，有则更新
     *
     * @param organConfig
     */
    @RpcService
    public void updateOrganConfig(OrganConfig organConfig) {
        if (null == organConfig) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organConfig is null");
        }
        Integer organId = organConfig.getOrganId();
        if (null == organId) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organId is null");
        }

        OrganDAO organDao = DAOFactory.getDAO(OrganDAO.class);
        if (!organDao.exist(organId)) {
            throw new DAOException(DAOException.ENTITIY_NOT_FOUND, "organ[" + organId + "] is not exist");
        }
        OrganConfigDAO configDao = DAOFactory.getDAO(OrganConfigDAO.class);
        OrganConfig target = configDao.get(organId);

        if (null == target) {
            if (null == organConfig.getAccountFlag()) {
                organConfig.setAccountFlag(0);
            }
            configDao.save(organConfig);
        } else {//考虑设置签约机构价格和医生签约价格:医生签约价格必须大于等于机构签约价格
            BeanUtils.map(organConfig, target);
            configDao.update(target);
        }
    }

    /**
     * 设置机构Config，没有则创建记录，有则更新
     *
     * @param organId
     * @param key
     * @param value
     */
    @RpcService
    public void setOrganConfig(Integer organId, String key, String value) {
        OrganDAO dao = DAOFactory.getDAO(OrganDAO.class);
        Organ organ = dao.getByOrganId(organId);
        if (organ == null) {
            throw new DAOException(DAOException.ENTITIY_NOT_FOUND, "机构不存在");
        }
        Number numberValue;
        switch (key) {
            case "HisServiceConfig.hisStatus":
                setHisServiceConfig(organId, "hisStatus", Boolean.valueOf(value));
                logChangeConfig(HisServiceConfig.class, organ, "HIS是否可用", Boolean.valueOf(value));
                break;
            case "OrganConfig.accountFlag":
                setConfig(organId, "accountFlag", Boolean.valueOf(value));
                logChangeConfig(OrganConfig.class, organ, "支持积分奖励政策", Boolean.valueOf(value));
                break;
            case "Organ.takeMedicineFlag":
                updateOrgan(organId, "takeMedicineFlag", Boolean.valueOf(value));
                logChangeConfig(Organ.class, organ, "支持到院取药", Boolean.valueOf(value));
                break;
            case "OrganConfig.canSign":
                setOrganCanSign(organId, "canSign", Boolean.valueOf(value));
                logChangeConfig(OrganConfig.class, organ, "允许开展签约业务", Boolean.valueOf(value));
                break;
            case "OrganConfig.signPrice":
                if (ObjectUtils.isEmpty(value)) {
                    throw new DAOException(ErrorCode.SERVICE_ERROR, "机构签约价格不能为空");
                }
                numberValue = parseNumber(value, BigDecimal.class, BigDecimal.ZERO);
                setConfig(organId, "signPrice", numberValue);
                logChangeConfig(OrganConfig.class, organ, "医生默认签约价格", numberValue);
                break;
            case "OrganConfig.signSubsidyPrice":
                if (ObjectUtils.isEmpty(value)) {
                    throw new DAOException(ErrorCode.SERVICE_ERROR, "签约政府补贴价格不能为空");
                }
                numberValue = parseNumber(value, BigDecimal.class, BigDecimal.ZERO);
                setConfig(organId, "signSubsidyPrice", numberValue);
                logChangeConfig(OrganConfig.class, organ, "医生默认签约政府补贴价格", numberValue);
                break;
            case "OrganConfig.diagnosisCenterFlag":
                if (ObjectUtils.isEmpty(value)) {
                    throw new DAOException(ErrorCode.SERVICE_ERROR, "远程诊断中心标志不能为空");
                }
                setConfig(organId, "diagnosisCenterFlag", Boolean.valueOf(value));
                logChangeConfig(OrganConfig.class, organ, "是否诊断中心", Boolean.valueOf(value));
                break;
            case "OrganConfig.payAhead":
                if (ObjectUtils.isEmpty(value)) {
                    throw new DAOException(ErrorCode.SERVICE_ERROR, "预约缴费支付时间不能为空");
                }
                int v = 0;
                try {
                    v = Integer.parseInt(value);
                } catch (Exception e) {
                    throw new DAOException(ErrorCode.SERVICE_ERROR, "预约缴费支付时间不合法");
                }
                if (v < -2) {
                    throw new DAOException(ErrorCode.SERVICE_ERROR, "预约缴费支付时间不合法");
                }
                setConfig(organId, "payAhead", v);
                String msg = "不限制";
                if (v == -2) {
                    msg = "不支持预约缴费支付功能";
                } else if (v == -1) {
                    msg = "不限制";
                } else if (v == 0) {
                    msg = "就诊当天";
                } else {
                    msg = "提前" + v + "天";
                }
                logChangeConfig(OrganConfig.class, organ, "预约缴费支付时间", msg);
                break;
            case "OrganConfig.sourceFlag":
                if (ObjectUtils.isEmpty(value)) {
                    throw new DAOException(ErrorCode.SERVICE_ERROR, "号源来源不能为空");
                }
                numberValue = parseNumber(value, Integer.class, Integer.valueOf(0));
                setConfig(organId, "sourceFlag", numberValue);
                /**
                 * 2017-3-30
                 * 与王国君确认，更改号源来源时候不去停诊现有平台号源 （随运营平台V2.9.0发布）
                 */
                //AppointOpService appointOpService = AppContextHolder.getBean("appointOpService", AppointOpService.class);
                //appointOpService.stopAllSourceByOrganId(organId);
                logChangeConfig(OrganConfig.class, organ, "号源来源", (numberValue.intValue() == 0) ? "纳里平台管理" : "医院HIS导入");
                break;
            case "OrganConfig.wxConfigId":
                if (ObjectUtils.isEmpty(value)) {
                    throw new DAOException(ErrorCode.SERVICE_ERROR, "微信公众号不能为空");
                }
                numberValue = parseNumber(value, Integer.class, Integer.valueOf(0));

                WXConfigsDAO wxConfigsDAO = DAOFactory.getDAO(WXConfigsDAO.class);
                WXConfig wxConfig = wxConfigsDAO.getById(numberValue.intValue());
                if (wxConfig == null) {
                    numberValue = Integer.valueOf(0);
                }
                setOrganConfigWxConfigId(organId, "wxConfigId", numberValue.intValue());
                String wxName = "默认纳里平台微信公众号";
                if (wxConfig != null) {
                    wxName = wxConfig.getWxName();
                }
                logChangeConfig(OrganConfig.class, organ, "机构关联微信公众号", wxName);
                break;
            case "HisServiceConfig.signtohis":
                setOrganSigntohis(organId, "signtohis", Boolean.valueOf(value));
                logChangeConfig(HisServiceConfig.class, organ, "签约功能是否对接HIS", Boolean.valueOf(value));
                break;
            case "OrganConfig.priceForRecipeRegister":
                numberValue = parseNumber(value, BigDecimal.class, BigDecimal.ZERO);
                setConfig(organId, "priceForRecipeRegister", numberValue);
                logChangeConfig(OrganConfig.class, organ, "处方挂号费", numberValue);
                break;
            case "OrganConfig.childrenRegistration":
                setConfig(organId, "childrenRegistration", Boolean.valueOf(value));
                logChangeConfig(OrganConfig.class, organ, "是否允许儿童预约挂号", Boolean.valueOf(value));
                break;
            case "OrganConfig.preContract":
                setConfig(organId, "preContract", Boolean.valueOf(value));
                logChangeConfig(OrganConfig.class, organ, "是否允许预签约", Boolean.valueOf(value));
                break;
            case "OrganConfig.signingAhead":
                numberValue = parseNumber(value, Integer.class, Integer.valueOf(0));
                setConfig(organId, "signingAhead", numberValue);
                logChangeConfig(OrganConfig.class, organ, "续签时间", numberValue + "个月");
                break;
            case "OrganConfig.doctorOrderType":
                numberValue = parseNumber(value, Integer.class, Integer.valueOf(0));
                setConfig(organId, "doctorOrderType", numberValue);
                try {
                    value = DictionaryController.instance().get("eh.base.dictionary.DoctorOrderType").getText(value);
                } catch (ControllerException e) {
                    value = numberValue.toString();
                }
                logChangeConfig(OrganConfig.class, organ, "科室医生排序规则", value);
                break;
            case "OrganConfig.appointCloudPayOnLine":
                setConfig(organId, "appointCloudPayOnLine", value == null ? Boolean.FALSE : Boolean.valueOf(value));
                logChangeConfig(OrganConfig.class, organ, "云门诊仅线上支付", value == null ? false : Boolean.valueOf(value));
                break;
            case "OrganConfig.meetCloudPayPlan":
                numberValue = parseNumber(value, Integer.class, Integer.valueOf(0));
                setConfig(organId, "meetCloudPayPlan", numberValue);
                logChangeConfig(OrganConfig.class, organ, "云会诊支付方案：方案", numberValue);
                break;
            default:
                if (key.startsWith("OrganConfig.")) {
                    updateOrganConfigItem(organId,key,value,organ);
                } else {
                    throw new DAOException("key is not allowed");
                }
                break;

        }
    }


    protected void updateOrganConfigItem(Integer organId,String key,String value,Organ organ){
        OrganConfigDAO configDao = DAOFactory.getDAO(OrganConfigDAO.class);
        OrganConfig organConfig = configDao.getByOrganId(organId);
        if (organConfig == null) {
            throw new DAOException("OrganConfig 缺失");
        }
        try {
            String itemName = key.substring(12, key.length());
            BeanUtils.setProperty(organConfig, itemName, value);
        } catch (Exception e) {
            throw new DAOException("OrganConfig 赋值失败：" + e.getMessage());
        }
        configDao.update(organConfig);
        String str= key;
        Object obj = value;
        try {
            Field field =OrganConfig.class.getDeclaredField("recipeCreamPrice");
            ItemProperty itemProperty = field.getAnnotation(ItemProperty.class);
            if(itemProperty!=null){
                str = itemProperty.alias();
                if(StringUtils.isEmpty(str)){
                    str=key;
                }
            }
            if(field.getType().getName().equals(Boolean.class.getName())){
                obj = Boolean.valueOf(value);
            }

        } catch (NoSuchFieldException e) {
           log.error("获取注解失败[OrganConfig."+key+"]:"+e.getMessage());
        }
        logChangeConfig(OrganConfig.class, organ, str, obj);
    }


    protected void logChangeConfig(Class<?> bizClass, Organ organ, String optionName, Object value) {
        if (value instanceof Boolean) {
            value = ((boolean) value) ? "打开" : "关闭";
        } else {
            value = "改为: " + value;
        }
        BusActionLogService.recordBusinessLog("修改机构设置", organ.getOrganId().toString(), bizClass.getName(),
                String.format("[%2$s](%1$s)的'%3$s'选项被%4$s", organ.getOrganId(), organ.getName(), optionName, value));
    }

    //设置医技检查被通知医生
    @RpcService
    public void setNotifyPartyForCheck(Integer organId, List<Integer> docs) {
        if (organId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organId is require");
        }
        OrganDAO odao = DAOFactory.getDAO(OrganDAO.class);
        Organ organ = odao.get(organId);
        if (organ == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "找不到这个机构[ID:" + organId + "]");
        }
        OrganConfigDAO organConfigDAO = DAOFactory.getDAO(OrganConfigDAO.class);
        OrganConfig config = organConfigDAO.getByOrganId(organId);
        if (config == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organId is not exist");
        }
        String oldNotifyParty = config.getNotifyPartyForCheck() == null ? "" : config.getNotifyPartyForCheck();
        String strNotifyParty = null;
        StringBuilder sbNotifyParty = new StringBuilder("");
        if (docs != null && docs.size() > 0) {
            for (Integer docId : docs) {
                sbNotifyParty.append(docId).append(",");
            }
            strNotifyParty = sbNotifyParty.substring(0, sbNotifyParty.length() - 1);
        }
        config.setNotifyPartyForCheck(strNotifyParty);
        organConfigDAO.update(config);
        BusActionLogService.recordBusinessLog("修改机构设置", organId.toString(), "OrganConfig",
                String.format("[%2$s](%1$s)的'医技检查被通知医生'从 %3$s 改为 %4$s", organ.getOrganId(), organ.getName(), oldNotifyParty, strNotifyParty));
    }


    protected void setHisServiceConfig(Integer organId, String key, boolean value) {
        HisServiceConfigDAO dao = DAOFactory.getDAO(HisServiceConfigDAO.class);
        HisServiceConfig config = new HisServiceConfig();
        config.setOrganid(organId);
        if ("hisStatus".equals(key)) {
            config.setHisStatus(value ? 1 : 0);
        }
        dao.updateHisServiceConfigForOp(config);
    }

    protected void setConfig(Integer organId, String key, Object value) {
        OrganConfig config = new OrganConfig();
        config.setOrganId(organId);
        switch (key) {
            case "accountFlag":
                config.setAccountFlag((Boolean) value ? 1 : 0);
                break;
            case "signPrice":
                config.setSignPrice((BigDecimal) value);
                break;
            case "signSubsidyPrice":
                config.setSignSubsidyPrice((BigDecimal) value);
                break;
            case "diagnosisCenterFlag":
                config.setDiagnosisCenterFlag((Boolean) value);
                break;
            case "payAhead":
                config.setPayAhead((Integer) value);
                break;
            case "sourceFlag":
                config.setSourceFlag((Integer) value);
                break;
            case "priceForRecipeRegister":
                config.setPriceForRecipeRegister((BigDecimal) value);
                break;
            case "childrenRegistration":
                config.setChildrenRegistration((Boolean) value);
                break;
            case "preContract":
                config.setPreContract((Boolean) value);
                break;
            case "signingAhead":
                config.setSigningAhead((Integer) value);
                break;
            case "doctorOrderType":
                config.setDoctorOrderType((Integer) value);
                break;
            case "appointCloudPayOnLine":
                config.setAppointCloudPayOnLine((Boolean) value);
                break;
            case "meetCloudPayPlan":
                config.setMeetCloudPayPlan((Integer) value);
                break;
        }
        updateOrganConfig(config);
    }

    protected void updateOrgan(Integer organId, String key, boolean value) {
        OrganDAO dao = DAOFactory.getDAO(OrganDAO.class);
        Organ organ = dao.getByOrganId(organId);
        if ("takeMedicineFlag".equals(key)) {
            organ.setTakeMedicineFlag(value ? 1 : 0);
        }
        dao.update(organ);
    }

    //修改机构是否支持签约功能
    protected void setOrganCanSign(Integer organId, String key, boolean value) {
        OrganConfigDAO organConfigDAO = DAOFactory.getDAO(OrganConfigDAO.class);
        ConsultSetDAO consultSetDAO = DAOFactory.getDAO(ConsultSetDAO.class);
        OrganConfig config = organConfigDAO.getByOrganId(organId);
        if ("canSign".equals(key)) {
            config.setCanSign(value ? Boolean.TRUE : Boolean.FALSE);
        }
        organConfigDAO.update(config);
        if (!value) {//签约功能打开的时候不执行医生可签约，关闭后执行更新医生可签约
            consultSetDAO.updateCanSignByDoctorId(value, organId);
        }
    }

    //修改机构是否有自己的微信公众号
    protected void setOrganConfigWxConfigId(Integer organId, String key, Integer value) {
        OrganConfigDAO organConfigDAO = DAOFactory.getDAO(OrganConfigDAO.class);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        OrganConfig organConfig = organConfigDAO.getByOrganId(organId);
        if ("wxConfigId".equals(key)) {
            organConfig.setWxConfigId(ObjectUtils.nullSafeEquals(0, value) ? null : value);
        }
        organConfigDAO.update(organConfig);
        //更新机构对于的医生二维码信息
        List<Doctor> doctorList = doctorDAO.findAllDoctorByOrgan(organConfig.getOrganId());
        //更新二维码字段为null
        for (Doctor doctor : doctorList) {
            doctor.setQrCode(null);
            doctor.setQrUrl(null);
            doctorDAO.update(doctor);
        }
    }


    //修改机构签约是否对接his:0否 1是
    protected void setOrganSigntohis(Integer organId, String key, Boolean value) {
        HisServiceConfigDAO dao = DAOFactory.getDAO(HisServiceConfigDAO.class);
        HisServiceConfig config = dao.getByOrganId(organId);
        if ("signtohis".equals(key)) {
            config.setSigntohis(value ? 1 : 0);
        }
        dao.update(config);
    }

    /**
     * 得到机构Config
     *
     * @param organId
     * @return
     */
    @RpcService
    public Map getOrganConfig(Integer organId) {
        if (null == organId) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organId is null");
        }
        OrganConfigDAO configDao = DAOFactory.getDAO(OrganConfigDAO.class);
        OrganConfig config = configDao.get(organId);
        if (config == null) {
            return null;
        }
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Map map = new HashMap();
        BeanUtils.map(config, map);
        map.put("notifyParty", doctorDAO.findByInDocIds(config.getNotifyPartyForCheck()));
        HisServiceConfigDAO hisServiceConfigDAO = DAOFactory.getDAO(HisServiceConfigDAO.class);
        List<Integer> forcedPayOrganList = hisServiceConfigDAO.findOrganIdsByCanPayAndPayCancelTime(organId);
        if(forcedPayOrganList.size() > 0){  //是否为强制支付的机构 true 是 false 否
            map.put("isForcedPay",true);
        } else {
            map.put("isForcedPay",false);
        }
        return map;
    }


    /**
     * 运营平台（权限改造）
     * @param organId
     * @return
     */
    @RpcService
    public Map getOrganConfigForOp(Integer organId) {
        Set<Integer> o = new HashSet<Integer>();
        o.add(organId);
        if(!SecurityService.isAuthoritiedOrgan(o)){
            return null;
        }
        return  this.getOrganConfig(organId);
    }


    /**
     * 获取所有远程诊断中心
     *
     * @return
     */
    @RpcService
    public List<Organ> findAllDiagnosisCenter() {
        OrganConfigDAO configDao = DAOFactory.getDAO(OrganConfigDAO.class);
        return configDao.findAllDiagnosisCenter();
    }

    /**
     * 获取机构
     *
     * @param organId
     * @return
     */
    @RpcService
    public OrganConfig getConfig(Integer organId) {
        if (null == organId) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organId is null");
        }
        OrganConfigDAO configDao = DAOFactory.getDAO(OrganConfigDAO.class);
        return configDao.get(organId);
    }

    /**
     * 判断该机构是否支持影像服务
     *
     * @param organ
     * @return
     */
    @RpcService
    public boolean isCanImage(int organ) {
        OrganConfig config = getConfig(organ);
        if (config == null)
            return false;
        if (config.getCanImage() != null && config.getCanImage() == 1) {
            return true;
        }
        return false;
    }

    /**
     * 判断机构是否隐藏所有处方药
     * <p>
     * 通过 OrganDrugList 是否存在负数 organId 的记录来判断。
     *
     * @param organId
     * @return 1 - 隐藏了处方药品；0 - 没有隐藏；-1 - 没有药品
     */
    @RpcService
    public int getOrganPrescriptionInvisible(int organId) {
        OrganDrugListDAO organDrugListDAO = DAOFactory.getDAO(OrganDrugListDAO.class);
        long count = organDrugListDAO.getCountByOrganId(0 - organId);
        if (count > 0) {
            return 1;
        }
        count = organDrugListDAO.getCountByOrganId(organId);
        if (count > 0) {
            return 0;
        }
        return -1;
    }

    /**
     * 设置机构是否隐藏所有处方药
     * <p>
     * 通过将 OrganDrugList 中的 organId 修改为其负数来隐藏。
     *
     * @param organId
     * @param
     * @return
     */
    @RpcService
    public int setOrganPrescriptionInvisible(int organId, boolean invisible) {
        OrganDrugListDAO organDrugListDAO = DAOFactory.getDAO(OrganDrugListDAO.class);
        OrganDAO dao = DAOFactory.getDAO(OrganDAO.class);
        Organ organ = dao.getByOrganId(organId);
        if (organ == null) {
            throw new DAOException(DAOException.ENTITIY_NOT_FOUND, "Organ[" + organId + "] is not exists");
        }
        if (invisible) { // hide
            organDrugListDAO.updateOrganIdByOrganId(0 - organId, organId);
        } else { // show
            organDrugListDAO.updateOrganIdByOrganId(organId, 0 - organId);
        }

        logChangeConfig(OrganDrugList.class, organ, "隐藏所有处方药品", invisible);
        return getOrganPrescriptionInvisible(organId);
    }


    @RpcService
    public void test() {

        try {

            ItemProperty itemProperty = OrganConfig.class.getDeclaredField("recipeCreamPrice").getAnnotation(ItemProperty.class);
            System.out.println("===============>"+itemProperty.alias());

        }catch (Exception e){

        }




    }

    @RpcService
    public boolean canShowPayButton(int organId,Date workDate) {
        Date now = DateConversion.getFormatDate(new Date(),DateConversion.YYYY_MM_DD);
        if (workDate.before(now)) {
            return false;
        }
        OrganConfigDAO organConfigDAO = DAOFactory.getDAO(OrganConfigDAO.class);
        Integer appointCloudPayDay = organConfigDAO.getAppointCloudPayDayByOrganId(organId);
        if (appointCloudPayDay == null || appointCloudPayDay.equals(CloudConstant.PAYAHEAD_UNLIMITED)) {
            return true;
        }
        Date lastShowDay = DateConversion.getDateAftXDays(now, appointCloudPayDay);
        return !(workDate.before(now) || workDate.after(lastShowDay));
    }


    @RpcService
    public List<Map<String, Object>> getSupportPatientUserTypes(Integer organId) {
        OrganConfigDAO configDao = DAOFactory.getDAO(OrganConfigDAO.class);
        OrganConfig config = configDao.get(organId);

        boolean withoutIdCardChild;
        //如果支持无就诊卡儿童就诊，返回全部类型
        if (config != null && config.getChildWithoutIdSupport() != null && config.getChildWithoutIdSupport().intValue() == 1) {
            withoutIdCardChild = true;
        } else {
            withoutIdCardChild = false;
        }
        return PatientUserTypeEnum.toMapList(withoutIdCardChild);
    }

}

