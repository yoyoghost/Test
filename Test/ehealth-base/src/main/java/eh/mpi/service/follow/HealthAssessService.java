package eh.mpi.service.follow;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.dao.DoctorDAO;
import eh.entity.mpi.Aticle;
import eh.entity.msg.SmsInfo;
import eh.push.SmsPushService;
import eh.remote.IAssessService;
import eh.util.HttpHelper;
import eh.utils.DateConversion;
import eh.utils.MapValueUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author renzh
 * @date 2016-12-15 下午 13:55
 */
public class HealthAssessService {

    private final static Logger logger = LoggerFactory.getLogger(HealthAssessService.class);

    private String url;
    private String serviceId;
    private Map methodMap;
    private String baseUrl;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public void setMethodMap(Map methodMap) {
        this.methodMap = methodMap;
    }

    /**
     * 查随访表单列表（老的接口准备废弃）
     * @return
     */
    @RpcService
    public List<Object> getAssessListByType(String appId,Integer assessType,Integer start,Integer limit){
        if(null == assessType){
            throw new DAOException(DAOException.VALUE_NEEDED, "assessType is needed");
        }
        Map map = queryAssessList(appId, String.valueOf(assessType), 0, 0, start, limit);
        List<Object> list = (List) map.get("body");
        return list;
    }

    public Map<String, Object> visitHsService(Map<String, Object> paramMap, String methodName){
        Map<String, String> headers = new HashMap();
        headers.put("X-Service-Id", serviceId);
        headers.put("X-Service-Method", methodName);
        Map<String, Object> resMap = null;
        try {
            resMap = JSONUtils.parse(HttpHelper.sendPostRequest(getBaseUrl(), headers, JSONUtils.toString(Lists.newArrayList(paramMap))), Map.class);
        } catch (Exception e) {
            logger.error("visit method=[{}], error=[{}]", methodName, e.getMessage());
        }
        if(null == resMap){
            resMap = new HashMap<>();
        }
        return resMap;
    }

    /**
     * 查随访表单列表(默认查全部)
     * @return
     */
    @RpcService
    public Map<String, Object> getAssessList(){
        Map paramMap = new ImmutableMap.Builder().
                put("appId", 2).
                put("hospitalId", 0).
                put("depId", 0).
                put("assessType", 3).
                put("start", 0).
                put("limit", 99).build();
        return visitHsService(paramMap, "getAssessmentList");
    }

    /**
     * 根据医生Id获取评估历史记录列表
     * @param appId
     * @param doctorId
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    public Map<String, Object> getHistoryListByDoctorId(String appId,String doctorId,String mpiId,Integer assessType,Integer start,Integer limit){
        if(null == doctorId){
            throw new DAOException(DAOException.VALUE_NEEDED, "doctorId is needed");
        }
        if(null == assessType){
            throw new DAOException(DAOException.VALUE_NEEDED, "assessType is needed");
        }
        return queryHistoryAssessList(appId, Integer.valueOf(doctorId), mpiId, String.valueOf(assessType), "", null, null, start, limit);
    }

    /**
     * 通过assessHisId拉取评估结论
     * @param assessHisId
     * @return
     */
    @RpcService
    public Map<String, Object> getAssessConclusionInfoBySssessHisId(String assessHisId){
        Map paramMap = new ImmutableMap.Builder().put("assessHisId", assessHisId).build();
        Map resMap = visitHsService(paramMap, "getAssessConclusionInfo");
        Integer code = MapValueUtil.getInteger(resMap, "code");
        if(null == code || 200 != code){
            throw new DAOException(ErrorCode.SERVICE_ERROR, MapValueUtil.getString(resMap, "msg"));
        }
        return resMap;
    }

    /**
     * 通过assessId 评估信息
     * @param assessId
     * @return
     */
    @RpcService
    public Map<String, Object> getAssessConclusionInfoByAssessId(String assessId){
        Map paramMap = new ImmutableMap.Builder().put("assessId", assessId).build();
        Map resMap = visitHsService(paramMap, "getAssessListInfoByAssessId");
        Integer code = MapValueUtil.getInteger(resMap, "code");
        if(null == code || 200 != code){
            throw new DAOException(ErrorCode.SERVICE_ERROR, MapValueUtil.getString(resMap, "msg"));
        }
        return resMap;
    }

    /**
     * 直接返回入参的方法 没删除是因为老的app在用这个
     * @param map
     * @return
     */
    @RpcService
    public String getDefaultAssessForPatient(Map<String, Object> map){

        String url = MapValueUtil.getString(map, "url");
        if(StringUtils.isEmpty(url)){
            throw new DAOException(DAOException.VALUE_NEEDED, "url is needed");
        }
        return url;
    }

    /**
     * 根据患者 和表单Id 获取具有填写默认值的表单地址
     * zhongzx
     * @param map
     * @return
     */
    @RpcService
    public String getDefaultAssessParameters(Map<String, Object> map){
        validateParamMap(map);
        String url = MapValueUtil.getString(map, "url");
        String mpiId = MapValueUtil.getString(map, "mpiId");
        if(StringUtils.isEmpty(mpiId)){
            return url;
        }
        if(url.contains("resource")){
            return url;
        }

        /**
         * 获取健康评估项目的服务
         */
        IAssessService assessService = AppContextHolder.getBean("assess.assessService", IAssessService.class);
        if (null == assessService) {
            logger.error("assess.assessService is null, not register");
            return url;
        }
        Map<String, Object> resMap;
        try {
            resMap = assessService.getDefaultAssessUrl(map);
        }catch (Exception e){
            logger.error("getDefaultAssessUrl fail, error=[{}]", e.getMessage());
            return url;
        }
        /**
         * 这里没有进行一系列的判空操作 是因为如果调用成功 肯定有返回值
         */
        return MapValueUtil.getString(resMap, "defaultAssessUrl");
    }

    /**
     * 校验参数
     * @param map
     */
    private void validateParamMap(Map<String, Object> map){
        String url = MapValueUtil.getString(map, "url");
        String assessId = MapValueUtil.getString(map, "assessId");
        Integer doctorId = MapValueUtil.getInteger(map, "doctorId");
        if(StringUtils.isEmpty(url)){
            throw new DAOException(DAOException.VALUE_NEEDED, "url is needed");
        }
        if(StringUtils.isEmpty(assessId)){
            throw new DAOException(DAOException.VALUE_NEEDED, "assessId is needed");
        }
        if(null == doctorId){
            throw new DAOException(DAOException.VALUE_NEEDED, "doctorId is needed");
        }
    }

    /**
     * 资讯id查询资讯详情
     * @param aid
     * @return
     */
    @RpcService
    public String getInformationById(Integer aid){
        try {
            Map map = HttpHelper.httpsGet("http://www.ngarihealth.com/api.php/App/getArticle?aid="+aid);
            if(null == map){
                throw new DAOException(ErrorCode.SERVICE_ERROR, "map is null");
            }
            Aticle aticle = new Aticle();
            Map shareUrlMap = (Map) map.get("shareurl");
            Map bodyMap = (Map) map.get("body");
            if(null ==  shareUrlMap){
                throw new DAOException(ErrorCode.SERVICE_ERROR, "shareUrlMap is null");
            }
            if(null == bodyMap){
                throw new DAOException(ErrorCode.SERVICE_ERROR, "bodyMap is null");
            }
            aticle.setWxUrl((MapValueUtil.getString(shareUrlMap, "share_weixin")));
            aticle.setMsgUrl((MapValueUtil.getString(shareUrlMap, "share_sms")));
            aticle.setInfoId((MapValueUtil.getString(bodyMap, "id")));
            aticle.setImgUrl((MapValueUtil.getString(bodyMap, "thumb")));
            aticle.setGolink((MapValueUtil.getString(bodyMap, "golink")));
            aticle.setTitle((MapValueUtil.getString(bodyMap, "title")));
            aticle.setOtherUrl((MapValueUtil.getString(shareUrlMap, "share_other")));
            aticle.setIsvideo((MapValueUtil.getString(bodyMap, "isvideo")));
            aticle.setDesc((MapValueUtil.getString(bodyMap, "describe")));
            return JSONUtils.toString(aticle);
        } catch (Exception e) {
            logger.error("service[{}],method[{}] catch IOException[{}]","HealthAssessService","getInformationById",e.getMessage());
        }
        return null;
    }

    /**
     * 根据文章Id  获取文章标题
     * @param aid
     * @return
     */
    public String getArticleTitle(Integer aid){
        try {
            Map map = HttpHelper.httpsGet("http://www.ngarihealth.com/api.php/App/getArticle?aid="+aid);
            return ((Map)map.get("body")).get("title").toString();
        } catch (Exception e) {
            logger.error("service[{}],method[{}] catch Exception[{}]:","HealthAssessService","getArticleTitle", e.getMessage());
            return "";
        }
    }

    /**
     * 接收公卫系统表单写入结果
     * zhongzx
     * @param paramMap
     */
    @RpcService
    public void receiveHealthSystemResult(Map<String, Object> paramMap){
        String code = MapValueUtil.getString(paramMap, "code");
        String data = MapValueUtil.getString(paramMap, "data");
        logger.info("receiveHealthSystemResult return code=[{}]", code);
        if(StringUtils.isEmpty(code)){
            code = "";
            logger.info("code is null or empty");
        }
        if(StringUtils.isEmpty(data)){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "data is empty");
        }
        Map<String, Object> dataMap = JSONUtils.parse(data, Map.class);
        String doctorId = MapValueUtil.getString(dataMap, "docId");
        if(StringUtils.isEmpty(doctorId)){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "doctorId is empty");
        }
        Integer docId = Integer.valueOf(doctorId);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Integer organId = doctorDAO.getOrganByDoctorId(docId);
        //失败通知医生
        if(!"200".equals(code)) {
            SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
            SmsInfo smsInfo = new SmsInfo();
            smsInfo.setExtendValue(data);
            smsInfo.setBusType("HealthSystemResult");
            smsInfo.setSmsType("HealthSystemResult");
            smsInfo.setCreateTime(new Date());
            smsInfo.setClientId(null);
            smsInfo.setBusId(0);
            smsInfo.setOrganId(organId);
            smsPushService.pushMsgData2OnsExtendValue(smsInfo);
        }
    }

/**--------------------------------------------------app 3.8.8的表单接口-------------------------------------------------------**/
    /**
     * 给医生查询 全部 或者 相同机构相同科室的所有类型的
     */
    public Map<String, Object> queryAssessList(String appId, String assessType, Integer organId, Integer deptId, Integer start, Integer limit){
        Map paramMap = new ImmutableMap.Builder().
                put("appId", appId).
                put("hospitalId", organId).
                put("depId", deptId).
                put("assessType",assessType).
                put("start", start).
                put("limit", limit).build();
        return visitHsService(paramMap, "getAssessmentList");
    }

    /**
     *
     * @param doctorId
     * @param mpiId
     * @param assessType
     * @param limit
     */
    public Map<String, Object> queryHistoryAssessList(String appId, Integer doctorId, String mpiId, String assessType, String fillType, Date timeBegin,
                                       Date timeEnd, Integer start, Integer limit){
        Map paramMap = new ImmutableMap.Builder().
                put("appId", appId).
                put("doctId", doctorId).
                put("userId", mpiId).
                put("assessType", assessType).
                put("timeBegin", null == timeBegin?"":DateConversion.getDateFormatter(timeBegin, "yyyy-MM-dd")).
                put("timeEnd", null == timeEnd?"":DateConversion.getDateFormatter(timeEnd, "yyyy-MM-dd")).
                put("fillType", fillType).
                put("start", start).
                put("limit", limit).build();
        return visitHsService(paramMap, "getAssessHistoryList");
    }

    /**
     * 获取表单统计数量
     * @param appId
     * @param doctorId
     * @param mpiId
     * @param assessType
     * @param fillType
     * @return
     */
    @RpcService
    public Map<String, Object> queryAssessCountInfo(String appId, Integer doctorId, String mpiId, String assessType, String fillType){
        Map paramMap = new ImmutableMap.Builder().
                put("appId", appId).
                put("doctId", doctorId).
                put("userId", mpiId).
                put("assessType", assessType).
                put("fillType", fillType).build();
        return (Map) visitHsService(paramMap, "getAssessCountInfo").get("body");
    }

    @RpcService
    public List<Object> findAssessList(String appId, String assessType, Integer organId, Integer deptId, Integer start, Integer limit){
        return (List) queryAssessList(appId, assessType, organId, deptId, start, limit).get("body");
    }

    @RpcService
    public List<Object> findHistoryAssessList(String appId, Integer doctorId, String mpiId, String assessType, String fillType, Date date, Integer start, Integer limit ){
        Date timeBegin = null;
        Date timeEnd = null;
        if(null != date){
            List<Date> list = DateConversion.getStartAndEndDateByMonth(date);
            timeBegin = list.get(0);
            timeEnd = list.get(1);
        }
        Map<String, Object> bodyMap = (Map) queryHistoryAssessList(appId, doctorId, mpiId, assessType, fillType, timeBegin, timeEnd, start, limit).get("body");
        if(null == bodyMap){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "历史列表返回为空");
        }
        return (List) bodyMap.get("historyInfoList");
    }
}
