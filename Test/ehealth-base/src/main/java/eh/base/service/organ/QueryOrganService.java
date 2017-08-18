package eh.base.service.organ;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Maps;
import ctd.account.UserRoleToken;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.annotation.RpcService;
import ctd.util.context.Context;
import ctd.util.context.ContextUtils;
import eh.base.constant.BussTypeConstant;
import eh.base.constant.DoctorTabConstant;
import eh.base.constant.ErrorCode;
import eh.base.dao.HisServiceConfigDAO;
import eh.base.dao.OrganConfigDAO;
import eh.base.dao.OrganDAO;
import eh.base.dao.UnitOpauthorizeDAO;
import eh.bus.constant.SearchConstant;
import eh.bus.dao.SearchContentDAO;
import eh.bus.service.common.CurrentUserInfo;
import eh.entity.base.*;
import eh.entity.bus.SearchContent;
import eh.entity.mpi.HealthCard;
import eh.entity.mpi.PatientType;
import eh.mpi.dao.HealthCardDAO;
import eh.mpi.dao.PatientTypeDAO;
import eh.utils.LocalStringUtil;
import eh.utils.MapValueUtil;
import eh.utils.ValidateUtil;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Created by luf on 2016/6/30.
 */

public class QueryOrganService {
    private static final Logger log = LoggerFactory.getLogger(QueryOrganService.class);

    /**
     * 供前端调取医院列表
     *
     * @param addr
     * @param flag 向上查询标志-0往上查1查本区域
     * @param type 0-按照原来模式不变 1-过滤未出报告的机构 2-过滤出未能支付的机构 3-过滤不能查询缴费信息的医院
     * @return
     * @author LF
     */
    @RpcService
    public List<Organ> findByAddrAreaLikeUp(String addr, int flag, int type) {
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        if (type == 1) {//需要过滤未出报告的医院
            List<Organ> organs = organDAO.findByAddrAreaAndRepostLike(addr);
            if (flag == 1) {
                return organs;
            }
            if (null != organs && !organs.isEmpty()) {
                return organs;
            }
            //若要往上查，且入参级（杭州市）没有数据，则查询省级机构
            organs = organDAO.findByAddrAreaAndRepostLike(addr.substring(0, 2));
            return organs;
        }

        if (type == 3) { //过滤不能查询缴费信息的医院
            if (flag != 1) {
                addr = addr.substring(0, 2);
            }
            return organDAO.findByAddrAreaAndPaymentLike(addr);
        }

        if (2 == type) {//过滤出不支持支付的机构
            List<Organ> organs = organDAO.findByAddrAreaAndPayLike(addr);
            if (flag == 1) {
                return organs;
            }
            if (null != organs && !organs.isEmpty()) {
                return organs;
            }
            //若要往上查，且入参级（杭州市）没有数据，则查询省级机构
            organs = organDAO.findByAddrAreaAndPayLike(addr.substring(0, 2));
            return organs;
        } else {
            List<Organ> organs = organDAO.findByAddrAreaLike(addr);
            if (flag == 1) {
                return organs;
            }
            if (null != organs && !organs.isEmpty()) {
                return organs;
            }
            //若要往上查，且入参级（杭州市）没有数据，则查询省级机构
            organs = organDAO.findByAddrAreaLike(addr.substring(0, 2));
            return organs;
        }
    }

    /**
     * zhongzx
     * 判断当前微信号的管理单元是区域还是机构
     * 如果是机构 判断是否可以取单 是否可以支付
     *
     * @return
     */
    @RpcService
    public Map<String, Object> organOrNotByManageUnit() {
        Map<String, String> wxAppProperties = CurrentUserInfo.getCurrentWxProperties();
        OrganDAO dao = DAOFactory.getDAO(OrganDAO.class);
        if (null == wxAppProperties) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "wxApp is null or is not wxApp");
        }
        String type = wxAppProperties.get("type");
        String manageUnit = wxAppProperties.get("manageUnitId");
        /*String type = "2";
        String manageUnit = "eh330001";*/
        Map<String, Object> map = new HashMap<>();
        //flag 0-区域 1-机构
        List<Integer> organIdList = dao.findEffOrgansUnitLike(manageUnit + "%");
        if(null != organIdList && organIdList.size() > 0 ){
            if( 1 == organIdList.size()){
                map.put("flag", "1");
            }else{
                map.put("flag", "0");
            }
        }else{
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该管理单元没有对应的机构");
        }
        //如果是机构 还要判断改机构是否支持取单或者支付
        //canPay 支付
        //canReport 取单 0 不需绑定（身份证查询） 1 就诊卡 2 就诊卡、医保卡 -1不可以取单
        if ("1".equals(map.get("flag"))) {
            Integer organId = organIdList.get(0);
            String canReport = dao.getConsultByOrgan(organId);
            String canPay = dao.getPayResByOrgan(organId);
            map.put("canReport", canReport);
            map.put("canPay", canPay);
            map.put("organId", organId);
        }
        return map;
    }

    /**
     * zhongzx
     * 根据患者和医院支持的类型进行是否进行卡绑定 以及绑定哪种类型的卡
     *
     * @param type    1-取单 2-支付
     * @param organId 医院Id
     * @param mpiId   患者编号
     * @return flag -1不支持支付或者取单 0-不需绑定 1-需要绑定就诊卡 2-需要绑定就诊卡或者医保卡 3-已绑定就诊卡 4-已绑定就诊卡或者医保卡
     */
    @RpcService
    public Map<String, Object> cardBinding(Integer type, Integer organId, String mpiId) {
        OrganDAO dao = DAOFactory.getDAO(OrganDAO.class);
        HealthCardDAO hdao = DAOFactory.getDAO(HealthCardDAO.class);
        PatientTypeDAO pdao = DAOFactory.getDAO(PatientTypeDAO.class);
        Organ organ = dao.get(organId);
        String flag = "";
        String supportType = "";
        Map<String, Object> map = new HashMap<>();
        if (1 == type) {
            supportType = dao.getConsultByOrgan(organId);
        } else {
            supportType = dao.getPayResByOrgan(organId);
        }
        //supportType -1-不支持支付或者取单
        if ("-1".equals(supportType)) {
            flag = "-1";
        }
        //supportType 0-不需要绑定
        else if ("0".equals(supportType)) {
            flag = "0";
        } else {
            //医院就诊卡列表
            List<HealthCard> cList = hdao.findByMpiIdAndCardTypeAndCardOrgan(mpiId, "1", organId);
            boolean b1;
            //判断是否有医院就诊卡
            if (null == cList || cList.size() == 0) {
                b1 = false;
            } else {
                b1 = true;
            }
            //supportType 1-需要绑定就诊卡
            if ("1".equals(supportType)) {
                if (b1) {
                    flag = "3";
                    //返回最近用过的就诊卡
                    map.put("card", cList.get(cList.size() - 1));
                } else {
                    flag = "1";
                }
            } else {
                boolean b2 = false;
                //医保卡列表
                List<HealthCard> cardList = hdao.findByMpiIdAndCardType(mpiId, "2");
                List<HealthCard> hList = new ArrayList<>();
                //判断医保是否包括此医院
                if (null != cardList) {
                    for (HealthCard c : cardList) {
                        PatientType pType = pdao.get(c.getCardOrgan().toString());
                        if (organ.getAddrArea().startsWith(pType.getAddrArea())) {
                            b2 = true;
                            hList.add(c);
                            break;
                        }
                    }
                }
                //supportType 2-需要判断是否绑定就诊卡或者医保卡
                if (b1 || b2) {
                    flag = "4";
                    //如果有医保卡 用医保卡 没有医保卡 用最近的就诊卡
                    if (0 != hList.size()) {
                        map.put("card", hList.get(0));
                    } else {
                        map.put("card", cList.get(cList.size() - 1));
                    }
                } else {
                    flag = "2";
                }
            }
        }
        map.put("flag", flag);
        return map;
    }

    /**
     * zsl
     * 根据地区查询医院列表并按医院等级分组（微信健康端）
     *
     * @param addr 区域代码
     * @return
     */
    @RpcService
    public List<Map<String, Object>> findByAddrAreaLikeGroupByGrade(String addr) throws ControllerException {
        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        List<Organ> organs;
        organs = organDAO.findValidOrganForHealth(addr);
        for (Organ o : organs) {
            String grade = o.getGrade() == null ? "99" : o.getGrade();
            String gradeText = DictionaryController.instance().get("eh.base.dictionary.Grade").getText(grade);
            boolean contain = false;
            for (int i = 0; i < results.size(); i++) {
                String reGrade = (String) results.get(i).get("grade");
                if (reGrade.equals(gradeText)) {
                    Organ organ = new Organ();
                    organ.setName(o.getName());
                    organ.setOrganId(o.getOrganId());
                    ((List<Organ>) results.get(i).get("organs")).add(organ);
                    contain = true;
                    break;
                }
            }
            if (!contain) {
                List<Organ> os = new ArrayList<Organ>();
                Organ organ = new Organ();
                Map<String, Object> result = new HashMap<String, Object>();
                organ.setName(o.getName());
                organ.setOrganId(o.getOrganId());
                os.add(organ);
                result.put("organs", os);
                result.put("grade", gradeText);
                results.add(result);
            }
        }
        return results;
    }

    /**
     * 预约挂号首页医院列表----分页
     * @param searchType 查询类型：0非个性化（剔除无医生并向上查找） 1非个性化（剔除无医生） 2按地区查询个性化 3查询个性化
     * @param addr
     * @param start
     * @param limit
     * @author zhangsl
     * @Date 2016-12-19 15:38:21
     */
    @RpcService
    public List<Organ> findByAddrAreaLikeInPage(Integer searchType,String addr, int start,int limit) {
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        List<Organ> organs= organDAO.findByAddrAreaLikeInPage(searchType,addr, start, limit);
        if(searchType==0){
            List<Organ> tempOrgans= organDAO.findByAddrAreaLikeInPage(searchType,addr, 0, 1);
            if (null != tempOrgans && !tempOrgans.isEmpty()) {
                return organs;
            }else if(addr.length()>2) {
                //若要往上查，且入参级（杭州市）没有数据，则查询省级机构
                organs = organDAO.findByAddrAreaLikeInPage(searchType, addr.substring(0, 2), start, limit);
            }
        }

        return organs;
    }

    /**
     * 根据地区查询医院列表并按医院等级分组（微信端 寻医问药）
     * 从药品 点击进入列表 还要根据药品筛选
     * @author zhongzx
     * @param addr    地区
     * @param queryParam  扩展参数 drugId 药品平台参数
     * @return
     * @throws Exception
     */
    @RpcService
    public List<Map<String, Object>> findByAddrAreaLikeGroupByGradeExt(String addr, Map<String, Object> queryParam) throws Exception{

        Integer drugId = MapValueUtil.getInteger(queryParam, "drugId");
        Integer mark = MapValueUtil.getInteger(queryParam, "mark");
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        List<Organ> organList;
        //如果mark == 2 在线续方 查询逻辑不同
        if(mark != null && 2 == mark){
            organList = organDAO.queryOrganCanRecipe(addr, drugId);
        }else{
            organList = organDAO.findValidOrganForHealth(addr);
        }
        List<Map<String, Object>> resultInfo = new ArrayList<>();
        if(organList != null && organList.size() > 0){
            for (Organ o : organList) {
                String grade = o.getGrade() == null ? "99" : o.getGrade();
                String gradeText = DictionaryController.instance().get("eh.base.dictionary.Grade").getText(grade);
                boolean contain = false;
                for (int i = 0; i < resultInfo.size(); i++) {
                    String reGrade = (String) resultInfo.get(i).get("grade");
                    if (reGrade.equals(gradeText)) {
                        Organ organ = new Organ();
                        organ.setName(o.getName());
                        organ.setOrganId(o.getOrganId());
                        ((List<Organ>) resultInfo.get(i).get("organs")).add(organ);
                        contain = true;
                        break;
                    }
                }
                if (!contain) {
                    List<Organ> os = new ArrayList<Organ>();
                    Organ organ = new Organ();
                    Map<String, Object> result = new HashMap<String, Object>();
                    organ.setName(o.getName());
                    organ.setOrganId(o.getOrganId());
                    os.add(organ);
                    result.put("organs", os);
                    result.put("grade", gradeText);
                    resultInfo.add(result);
                }
            }
        }
        return resultInfo;
    }

    /**
     * 取单功能判断是否为该医院指定跳转页面（第三方提供） such as pacs+
     * @param mpiId
     * @param organId
     * @return
     */
    @RpcService
    public Map<String, String> getCustomerUrl(String mpiId, Integer organId){
        log.info("getCustomerUrl start in with params: mpiId[{}], organId[{}]", mpiId, organId);
        Map<String, String> resultMap = Maps.newHashMap();
        if(ValidateUtil.blankString(mpiId) || ValidateUtil.nullOrZeroInteger(organId)){
            log.error("getCustomerUrl necessary parameter is null, please check!");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "参数缺失");
        }
        try {
            OrganConfig organConfig = DAOFactory.getDAO(OrganConfigDAO.class).getByOrganId(organId);
            if(organConfig==null || ValidateUtil.blankString(organConfig.getPacsSkipUrl())){
                // 该机构未配置pacs+跳转地址
                return resultMap;
            }
            String pacsSkipUrl = organConfig.getPacsSkipUrl();
//            Patient patient = DAOFactory.getDAO(PatientDAO.class).getByMpiId(mpiId);
//            HisResponse<PatientQueryRequest> hisResponse = DAOFactory.getDAO(HisServiceConfigDAO.class).getPatientFromHis(organId, patient);
//            log.info("getCustomerUrl hisServiceConfigDAO.getPatientFromHis dao[{}]", JSONObject.toJSONString(hisResponse));
//            PatientQueryRequest patientQueryRequest = hisResponse.getData();
//            String cardId = patientQueryRequest.getCardID();
//            if(ValidateUtil.blankString(cardId)){
//                log.error("getCustomerUrl not fetch cardId info!");
//                throw new DAOException(ErrorCode.SERVICE_ERROR, "未获取到就诊卡信息");
//            }
//            String skipUrl = LocalStringUtil.format(pacsSkipUrl, cardId);
            String skipUrl = LocalStringUtil.format(pacsSkipUrl, "");
            //"http://platform.pacs-plus.com/embedded/all/425026521/{}"
            resultMap.put("skipUrl", skipUrl);
            return resultMap;
        }catch (Exception e){
            log.error("getCustomerUrl error, params: mpiId[{}], stackTrace[{}], errorMessage[{}], stackTrace[{}]", mpiId, organId, e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }
    }

    /**
     * 按条件查询会诊中心机构列表（剔除无会诊中心）
     * zhangsl 2017-05-31 17:21:14
     * @param shortName
     * @param addr
     * @return
     */
    public List<Organ> queryOrganForMeetCenter(String shortName, String addr){
        List<Organ> result=new ArrayList<>();
        UserRoleToken ur = (UserRoleToken) ContextUtils
                .get(Context.USER_ROLE_TOKEN);
        Doctor doctor = (Doctor) ur.getProperty("doctor");
        Employment employment = (Employment) ur.getProperty("employment");
        List<Integer> organs = this.findUnitOpauthorizeOrganIds(BussTypeConstant.MEETCLINIC);
        if(organs==null||organs.isEmpty()||doctor==null||employment.getOrganId() == null) {
            return result;
        }
        int doctorId = doctor.getDoctorId();
        int requestOrganId=employment.getOrganId();

        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        List<Organ> organList = organDAO.queryOrganForMeetCenter(shortName, addr, organs, doctorId);

        result=this.sortOrganForUnitOpauthorize(organList,requestOrganId,BussTypeConstant.MEETCLINIC);
        return result;
    }

    /**
     * 获取当前医生能看到的机构id
     * zhangsl 2017-05-31 15:59:25
     * @param bussType
     * @return
     */
    public List<Integer> findUnitOpauthorizeOrganIds(Integer bussType){
        UserRoleToken ur = (UserRoleToken) ContextUtils
                .get(Context.USER_ROLE_TOKEN);
        Doctor doctor = (Doctor) ur.getProperty("doctor");
        UnitOpauthorizeDAO uoDao = DAOFactory.getDAO(UnitOpauthorizeDAO.class);
        List<Integer> oList = uoDao.findByBussId(bussType);
        if (oList == null) {
            oList = new ArrayList<>();
        }
        if (doctor == null || doctor.getOrgan() == null) {
            return null;
        }
        int myOrganId = doctor.getOrgan();
        oList.add(myOrganId);
        return oList;
    }

    /**
     * 按条件查询预约云门诊机构列表（剔除无号源）
     * zhangsl 2017-06-02 10:19:45
     * @param shortName
     * @param addr
     * @return
     */
    public List<Organ> queryOrganForCloud(String shortName, String addr) {
        List<Organ> result = new ArrayList<>();
        UserRoleToken ur = (UserRoleToken) ContextUtils
                .get(Context.USER_ROLE_TOKEN);
        Doctor doctor = (Doctor) ur.getProperty("doctor");
        Employment employment = (Employment) ur.getProperty("employment");
        List<Integer> organs = this.findUnitOpauthorizeOrganIds(BussTypeConstant.APPOINTMENT);
        if (organs == null || organs.isEmpty() || doctor == null || employment == null || employment.getOrganId() == null) {
            return result;
        }
        int doctorId = doctor.getDoctorId();
        int requestOrganId = employment.getOrganId();
        //获取当天云门诊可约机构列表
        HisServiceConfigDAO hisServiceConfigDAO = DAOFactory.getDAO(HisServiceConfigDAO.class);
        List<Integer> todayOrgans = hisServiceConfigDAO.findByCanAppointToday();

        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        List<Organ> organList = organDAO.queryOrganForCloud(shortName, addr, organs, doctorId, todayOrgans);

        result = this.sortOrganForUnitOpauthorize(organList, requestOrganId, BussTypeConstant.APPOINTMENT);
        return result;
    }

    /**
     * 追加机构授权排序
     * @param organs
     * @param requestOrganId
     * @param bus
     * @return
     * @author zhangsl 2017-06-02 13:37:52
     */
    public List<Organ> sortOrganForUnitOpauthorize(List<Organ> organs,int requestOrganId, int bus){
        if(organs==null||organs.isEmpty()){
            return organs;
        }
        List<Organ> result=new ArrayList<Organ>();
        String bussType = "0";
        switch (bus) {
            case BussTypeConstant.TRANSFER:
            case BussTypeConstant.APPOINTMENT:
                bussType = "1";
                break;
            case BussTypeConstant.MEETCLINIC:
                bussType = "2";
                break;
            default:
                break;
        }
        if(!"0".equals(bussType)) {
            UnitOpauthorizeDAO unitOpauthorizeDAO = DAOFactory.getDAO(UnitOpauthorizeDAO.class);
            for (Organ o : organs) {
                List<UnitOpauthorize> opauthorizes=unitOpauthorizeDAO.findByOrgAndAccAndBus(o.getOrganId(), requestOrganId, bussType);
                if(opauthorizes==null||opauthorizes.isEmpty()){//判断organs是否经过授权机构筛选，否则无需排序
                    return organs;
                }
                o.setOrderNum(opauthorizes.get(0).getOrderNum()==null?0:opauthorizes.get(0).getOrderNum());
            }
        }
        Collections.sort(organs, new Comparator<Organ>() {
            @Override
            public int compare(Organ o1, Organ o2) {
                return o2.getOrderNum().compareTo(o1.getOrderNum());//按orderNum降序
            }
        });
        return organs;
    }

    /**
     * 按筛选入口类型查询机构列表
     * zhangsl 2017-05-31 19:04:56
     * @param shortName
     * @param addr
     * @param deType 1-会诊中心，2云门诊预约
     * @return
     */
    @RpcService
    public List<Organ> queryOrganByDetailType(String shortName,String addr,int deType){
        List<Organ> result = new ArrayList<Organ>();
        switch (deType) {
            case 1://会诊中心
                result = this.queryOrganForMeetCenter(shortName, addr);
                break;
            case 2://云门诊预约
                result = this.queryOrganForCloud(shortName, addr);
                break;
            default:
                break;

        }
        return result;
    }
}
