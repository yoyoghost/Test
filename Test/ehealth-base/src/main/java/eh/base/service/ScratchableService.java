package eh.base.service;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import ctd.account.Client;
import ctd.account.UserRoleToken;
import ctd.account.thirdparty.ThirdParty;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.impl.thirdparty.ThirdPartyDao;
import ctd.util.AppContextHolder;
import ctd.util.BeanUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.constant.ScratchableConstant;
import eh.base.constant.SystemConstant;
import eh.base.dao.ScratchableDAO;
import eh.bus.service.common.ClientPlatformEnum;
import eh.bus.service.common.CurrentUserInfo;
import eh.entity.base.Scratchable;
import eh.entity.bus.msg.SimpleThird;
import eh.entity.bus.msg.SimpleWxAccount;
import eh.entity.mpi.Patient;
import eh.entity.wx.WXConfig;
import eh.op.dao.WXConfigsDAO;
import eh.util.ControllerUtil;
import eh.utils.ValidateUtil;
import org.apache.axis.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;

import java.util.*;


public class ScratchableService {
    private static final Logger log = LoggerFactory.getLogger(ScratchableService.class);
 /*   private ScratchableDAO scrDao;

    public ScratchableService() {
        scrDao = DAOFactory.getDAO(ScratchableDAO.class);
    }*/

    /**
     * 获取九宫格模板
     *
     * @return
     */
    @RpcService
    public List<Scratchable> findModleByAppID() {
        ScratchableDAO scrDao =  DAOFactory.getDAO(ScratchableDAO.class);
        String appId = null;
        Integer tempId = 0;
        try {

            Client client = CurrentUserInfo.getCurrentClient();
            SimpleWxAccount wx = CurrentUserInfo.getSimpleWxAccount();
            Map<String, String> wxAppProperties = CurrentUserInfo.getCurrentWxProperties();
            if (client == null || null == wxAppProperties || null == wx) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "wxApp is null or is not wxApp");
            }
            ClientPlatformEnum clientPlatformEnum = ClientPlatformEnum.fromKey(client.getOs());
            List<Scratchable> list = Lists.newArrayList();
            log.info("获取九宫格：clientPlatformEnum:"+clientPlatformEnum);
            if (ClientPlatformEnum.WEIXIN.equals(clientPlatformEnum)) {
                String tempIdStr = wxAppProperties.get("tempId");
                tempId = Integer.parseInt(tempIdStr);
                appId = wx.getAppId();
                if (tempId == null) {
                    throw new DAOException(DAOException.VALUE_NEEDED, "tempId is required");
                }
                if (appId == null || StringUtils.isEmpty(appId.trim())) {
                    throw new DAOException(DAOException.VALUE_NEEDED, "appId is required");
                }
                WXConfigsDAO wxConfigsDAO = DAOFactory.getDAO(WXConfigsDAO.class);
                log.info("获取九宫格：AppId:"+appId);
                WXConfig wxConfig = wxConfigsDAO.getByAppID(appId);
                if (wxConfig == null) {//找不到微信公众号配置，取默认配置
                    wxConfig = new WXConfig();
                }
                log.info("获取九宫格：WXConfig.id:"+wxConfig.getId());
                list = scrDao.findModelsByConfigId(wxConfig.getId()==null?null:wxConfig.getId().toString(), tempId);
                if (list == null || list.isEmpty()) {
                    list = scrDao.findModelsByConfigId(null, tempId);
                }
            } else if (ClientPlatformEnum.ALILIFE.equals(clientPlatformEnum)) {
                list = findByConfigIdAndTempId(clientPlatformEnum.getOpPlatKey(), wx.getAppId(), tempId);
            } else if (ClientPlatformEnum.WEB.equals(clientPlatformEnum)) {
                list = findByConfigIdAndTempId(clientPlatformEnum.getOpPlatKey(), wx.getAppId(), tempId);
            } else if (ClientPlatformEnum.WX_WEB.equals(clientPlatformEnum)) {
                SimpleThird third = (SimpleThird) wx;
                list = findByConfigIdAndTempId(clientPlatformEnum.getOpPlatKey(), third.getAppkey(), tempId);
            } else {
                log.warn("findModleByAppID not support client plat, clientPlat[{}]", clientPlatformEnum);
            }
            return list;
        } catch (Exception e) {
            log.error("findModleByAppID error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }
    }

    /**
     * 根据ConfigID和模板ID获取九宫格
     *
     * @param configId
     * @param tempId
     * @return
     */
    @RpcService
    public List<Scratchable> findByConfigIdAndTempId(String configType, String configId, Integer tempId) {
        ScratchableDAO scrDao =  DAOFactory.getDAO(ScratchableDAO.class);
        if (StringUtils.isEmpty(configType)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "configType is require");
        }
        if (tempId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "tempId is require");
        }
        if (StringUtils.isEmpty(configId)) {
            return scrDao.findDefaultByTempId(configType, tempId);
        }
        return scrDao.findByConfigIdAndTempId(configType, configId, tempId);
    }

    /**
     * 微信 wechat
     * 保存九宫格配置
     *
     * @param configId
     * @param tempId
     * @param list
     * @return
     */
    @RpcService
    public List<Scratchable> saveWeChatModules(String configId, Integer tempId, List<Scratchable> list) {
        ScratchableDAO scrDao =  DAOFactory.getDAO(ScratchableDAO.class);
        if (tempId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "tempId is required");
        }
        if (list == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "list is required");
        }
        WXConfigsDAO wxConfigsDAO = DAOFactory.getDAO(WXConfigsDAO.class);
        WXConfig wxConfig = null;
        String WxName = "通用";
        if (!StringUtils.isEmpty(configId)) {
            wxConfig = wxConfigsDAO.get(Integer.valueOf(configId));
            if (wxConfig == null) {
                throw new DAOException("wxConfig is not exist");
            }
            WxName = wxConfig.getWxName();
        }

        List<Scratchable> scratchables = findByConfigIdAndTempId("wechat", configId, tempId);
        if (scratchables != null && scratchables.size() > 0) {
            throw new DAOException(DAOException.VALUE_NEEDED, " scratchables is exist");
        }
        List<Scratchable> reList = new ArrayList<Scratchable>();
        UserRoleToken urt = UserRoleToken.getCurrent();
        if (urt == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "UserRoleToken is required");
        }
        Integer userId = urt.getId();
        String userName = urt.getUserName();
        Date date = new Date();
        for (Scratchable scratchable : list) {
            scratchable.setConfigType("wechat");
            scratchable.setConfigId(StringUtils.isEmpty(configId)? null : configId);
            scratchable.setTempId(tempId);
            scratchable.setCreater(userId);
            scratchable.setCreaterName(userName);
            scratchable.setCreatDate(date);
            scratchable.setUpdater(userId);
            scratchable.setUpdateName(userName);
            scratchable.setLastModify(date);
            reList.add(scrDao.save(scratchable));
        }
        if (wxConfig != null) {//更新模板并刷新缓存
            reloadWxTemp(wxConfig.getAppID(), Integer.parseInt(configId), tempId);
        }
        BusActionLogService.recordBusinessLog("微信九宫格配置", configId + "--" + tempId, "Scratchable",
                "给[" + WxName + "]添加[" + tempId + "]九宫格配置");
        return reList;
    }

    @RpcService
    public List<Scratchable> updateWeChatModules(String configId, Integer tempId,
                                                 List<Scratchable> list, List<Integer> dels) {
        ScratchableDAO scrDao =  DAOFactory.getDAO(ScratchableDAO.class);
        if (tempId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "tempId is required");
        }
        if (list == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "list is require");
        }
        WXConfigsDAO wxConfigsDAO = DAOFactory.getDAO(WXConfigsDAO.class);
        WXConfig wxConfig = null;
        String WxName = "通用";
        if (!StringUtils.isEmpty(configId)) {
            wxConfig = wxConfigsDAO.get(Integer.valueOf(configId));
            if (wxConfig == null) {
                throw new DAOException("wxConfig is not exist");
            }
            WxName = wxConfig.getWxName();
        }
        if (dels != null) {
            for (Integer latexId : dels) {
                scrDao.remove(latexId);
            }
        }
        List<Scratchable> reList = new ArrayList<Scratchable>();
        UserRoleToken urt = UserRoleToken.getCurrent();
        if (urt == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "UserRoleToken is required");
        }
        Integer userId = urt.getId();
        String userName = urt.getUserName();
        Date date = new Date();
        for (Scratchable scratchable : list) {
            scratchable.setUpdater(userId);
            scratchable.setUpdateName(userName);
            scratchable.setLastModify(date);
            if (scratchable.getLatexId() == null) {//保存
                scratchable.setCreater(userId);
                scratchable.setCreaterName(userName);
                scratchable.setCreatDate(date);
                scratchable.setConfigId(StringUtils.isEmpty(configId)? null : configId);
                scratchable.setConfigType("wechat");
                reList.add(scrDao.save(scratchable));
            } else {//更新
                Scratchable old = scrDao.get(scratchable.getLatexId());
                BeanUtils.map(scratchable, old);
                reList.add(scrDao.update(old));
            }
        }
        if (wxConfig != null) {//更新模板并刷新缓存
            reloadWxTemp(wxConfig.getAppID(), Integer.parseInt(configId), tempId);
        }
        BusActionLogService.recordBusinessLog("微信九宫格配置", configId + "--" + tempId, "Scratchable",
                "给[" + WxName + "]更新[" + tempId + "]九宫格配置");
        return reList;
    }

    @RpcService
    public void reloadWxTemp(String appId, Integer configId, Integer tempId) {
        if (appId == null || StringUtils.isEmpty(appId.trim())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "appId is required");
        }
        if (configId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "configId is required");
        }
        if (tempId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "tempId is required");
        }
        WXConfigsDAO wxConfigsDAO = DAOFactory.getDAO(WXConfigsDAO.class);
        wxConfigsDAO.updateTempIdByConfigID(tempId, configId);
        try {//刷新缓存
            ControllerUtil.reloadWXAppByAppId(appId);
        } catch (ControllerException e) {
            log.error("reloadWxTemp() error: "+e);
        }
    }


    @RpcService
    public void deleteWeChatModules(Integer configId, Integer tempId) {
        ScratchableDAO scrDao =  DAOFactory.getDAO(ScratchableDAO.class);
        if (tempId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "tempId is required");
        }
        String WxName = "通用";
        if (configId != null && configId > 0) {
            WXConfigsDAO wxConfigsDAO = DAOFactory.getDAO(WXConfigsDAO.class);
            WXConfig wxConfig = wxConfigsDAO.get(configId);
            if (wxConfig == null) {
                throw new DAOException("wxConfig is not exist");
            }
            WxName = wxConfig.getWxName();
        }
        if (configId == null) {
            scrDao.deleteDefaultByTempId(tempId, "wechat");
        } else {
            scrDao.deleteByConfigIdAndTempId(configId.toString(), tempId, "wechat");
        }
        BusActionLogService.recordBusinessLog("微信九宫格配置", configId + "--" + tempId, "Scratchable",
                "给[" + WxName + "]删除[" + tempId + "]九宫格配置");
    }

    @RpcService
    public List<Scratchable> findScratchableByPlatform(String configType, String configId, Integer tempId) {
        ScratchableDAO scrDao =  DAOFactory.getDAO(ScratchableDAO.class);
        if (StringUtils.isEmpty(configType)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "configType is require");
        }
        if (tempId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "tempId is require");
        }
        if (StringUtils.isEmpty(configId)) {
            return scrDao.findDefaultByTempId(configType, tempId);
        } else {
            List<Scratchable> list = scrDao.findByConfigIdAndTempId(configType, configId, tempId);
            if (list == null || list.isEmpty()) {//若无配置个性化首页，则返回默认的首页配置
                list = scrDao.findDefaultByTempId(configType, tempId);
            }
            return list;
        }
    }

    /**
     * 获取科室的列表
     * @return
     */
    @RpcService
    public List<Scratchable> findHotDepartment() {
        Client client = CurrentUserInfo.getCurrentClient();
        SimpleWxAccount simple = CurrentUserInfo.getSimpleWxAccount();
        String appkey = simple.getAppId();

        ScratchableService service = AppContextHolder.getBean("eh.scratchableService", ScratchableService.class);
        String configType = ScratchableConstant.SCRATCHABLE_CONFIGTYPE_PROFESSION;

        WXConfigsDAO wxConfigsDAO = DAOFactory.getDAO(WXConfigsDAO.class);
        WXConfig wxConfig = wxConfigsDAO.getByAppID(appkey);
        if (ObjectUtils.isEmpty(wxConfig)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "wxconfig is null");
        }
        Integer configId = wxConfig.getId();
        Integer tempId = 0; //默认的模板为0
        List<Scratchable> dbScratchable = service.findScratchableByPlatform(configType, configId.toString(), tempId);
        return dbScratchable;

    }

    /**
     * 第三方接入医生首页获取个性化数据
     *
     * @return
     */
    @RpcService
    public List<Scratchable> findDocIndexModle() {
        Client client = CurrentUserInfo.getCurrentClient();
        SimpleWxAccount simple=CurrentUserInfo.getSimpleWxAccount();
        String appkey=simple.getAppId();

        ClientPlatformEnum clientPlatformEnum = ClientPlatformEnum.fromKey(client.getOs());
        if (ClientPlatformEnum.WX_WEB.equals(clientPlatformEnum)) {
            SimpleThird third = (SimpleThird) simple;
            appkey=third.getAppkey();
        }


        ScratchableService service = AppContextHolder.getBean("eh.scratchableService", ScratchableService.class);
        String configType= ScratchableConstant.SCRATCHABLE_CONFIGTYPE_DOCTORINDEX;
        Integer tempId=0;//默认的模板为0

        List<Scratchable> returnList=new ArrayList<Scratchable>();
        List<Scratchable> dbScratchable=service.findScratchableByPlatform(configType,appkey,tempId);
        for (Scratchable s:dbScratchable) {
            Scratchable sratch=new Scratchable();
            sratch.setBoxTxt(s.getBoxTxt());
            sratch.setBoxDesc(s.getBoxDesc());
            sratch.setUsed(s.getUsed());
            sratch.setLinkType(s.getLinkType());
            sratch.setBoxLink(s.getBoxLink());
            returnList.add(sratch);
        }
        return returnList;
    }

    public List<Scratchable> findTeamDocIndexModle() {
        List<Scratchable> returnList=new ArrayList<Scratchable>();

        Scratchable sratch=new Scratchable();
        sratch.setBoxTxt("图文咨询");
        sratch.setBoxDesc("医生通过图文，语音与您交流");
        sratch.setUsed(true);
        sratch.setLinkType("3");
        sratch.setBoxLink("onlineConsult");
        returnList.add(sratch);

        Scratchable patientTransfer=new Scratchable();
        patientTransfer.setBoxTxt("特需预约");
        patientTransfer.setBoxDesc("申请额外加号");
        patientTransfer.setUsed(true);
        patientTransfer.setLinkType("3");
        patientTransfer.setBoxLink("patientTransfer");
        returnList.add(patientTransfer);


        return returnList;
    }

    @RpcService
    public List<Scratchable> savePlatformModules(String configType, String configId,
                                                 Integer tempId, List<Scratchable> list) {
        ScratchableDAO scrDao =  DAOFactory.getDAO(ScratchableDAO.class);
        if (StringUtils.isEmpty(configType)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "configType is require");
        }
        String configTypeText = "";
        try {
            configTypeText = DictionaryController.instance()
                    .get("eh.base.dictionary.PlatformType").getText(configType);
        } catch (ControllerException e) {
            throw new DAOException("configType is not exist");
        }
        if (tempId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "tempId is require");
        }
        if (list == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "list is require");
        }
        /*if (StringUtils.isEmpty(configId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "configId is require");
        }*/
        List<Scratchable> scratchables = findByConfigIdAndTempId(configType, configId, tempId);
        if (scratchables != null && scratchables.size() > 0) {
            throw new DAOException(DAOException.VALUE_NEEDED, " scratchables is exist");
        }
        List<Scratchable> reList = new ArrayList<Scratchable>();
        UserRoleToken urt = UserRoleToken.getCurrent();
        if (urt == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "UserRoleToken is required");
        }
        Integer userId = urt.getId();
        String userName = urt.getUserName();
        Date date = new Date();
        for (Scratchable scratchable : list) {
            scratchable.setConfigType(configType);
            scratchable.setConfigId(StringUtils.isEmpty(configId)? null : configId);
            scratchable.setTempId(tempId);
            scratchable.setCreater(userId);
            scratchable.setCreaterName(userName);
            scratchable.setCreatDate(date);
            scratchable.setUpdater(userId);
            scratchable.setUpdateName(userName);
            scratchable.setLastModify(date);
            reList.add(scrDao.save(scratchable));
        }
        BusActionLogService.recordBusinessLog("九宫格配置", configType + "--" + configId + "--" + tempId, "Scratchable",
                "给平台类型为：" + configTypeText + "，APPKey：[" + configId + "]添加[" + tempId + "]九宫格配置");
        return reList;
    }


    @RpcService
    public List<Scratchable> updatePlatformModules(String configType, String configId,
                                                   Integer tempId, List<Scratchable> list,
                                                   List<Integer> dels) {
        ScratchableDAO scrDao =  DAOFactory.getDAO(ScratchableDAO.class);
        if (StringUtils.isEmpty(configType)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "configType is require");
        }
        String configTypeText = "";
        try {
            configTypeText = DictionaryController.instance()
                    .get("eh.base.dictionary.PlatformType").getText(configType);
        } catch (ControllerException e) {
            throw new DAOException("configType is not exist");
        }
        if (tempId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "tempId is required");
        }
        if (list == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "list is require");
        }
        if (dels != null) {
            for (Integer latexId : dels) {
                scrDao.remove(latexId);
            }
        }
        List<Scratchable> reList = new ArrayList<Scratchable>();
        UserRoleToken urt = UserRoleToken.getCurrent();
        if (urt == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "UserRoleToken is required");
        }
        Integer userId = urt.getId();
        String userName = urt.getUserName();
        Date date = new Date();
        for (Scratchable scratchable : list) {
            scratchable.setUpdater(userId);
            scratchable.setUpdateName(userName);
            scratchable.setLastModify(date);
            if (scratchable.getLatexId() == null) {//保存
                scratchable.setCreater(userId);
                scratchable.setCreaterName(userName);
                scratchable.setCreatDate(date);
                scratchable.setConfigId(StringUtils.isEmpty(configId)?null:configId);
                scratchable.setConfigType(configType);
                reList.add(scrDao.save(scratchable));
            } else {//更新
                Scratchable old = scrDao.get(scratchable.getLatexId());
                BeanUtils.map(scratchable, old);
                reList.add(scrDao.update(old));
            }
        }
        BusActionLogService.recordBusinessLog("九宫格配置", configType + "--" + configId + "--" + tempId, "Scratchable",
                "给平台类型为：" + configTypeText + "，APPKey：[" + configId + "]更新[" + tempId + "]九宫格配置");
        return reList;
    }

    @RpcService
    public void deletePlatformModules(String configType, String configId, Integer tempId) {
        ScratchableDAO scrDao =  DAOFactory.getDAO(ScratchableDAO.class);
        if (StringUtils.isEmpty(configType)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "configType is require");
        }
        String configTypeText = "";
        try {
            configTypeText = DictionaryController.instance()
                    .get("eh.base.dictionary.PlatformType").getText(configType);
        } catch (ControllerException e) {
            throw new DAOException("configType is not exist");
        }
        if (tempId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "tempId is required");
        }

        if (StringUtils.isEmpty(configId)) {
            scrDao.deleteDefaultByTempId(tempId, configType);
        } else {
            scrDao.deleteByConfigIdAndTempId(configId, tempId, configType);
        }
        BusActionLogService.recordBusinessLog("九宫格配置", configId + "--" + tempId, "Scratchable",
                "给平台类型为：" + configTypeText + "，APPKey：[" + configId + "]删除[" + tempId + "]九宫格配置");
    }

    @RpcService
    public List<Scratchable> findModleForHealthCard() {
        Integer tempId = 0;
        String platformType = "healthcard"; // TODO warning，此处有坑！目前只支持微信，其他端个性化需要填坑
        try {
            Client client = CurrentUserInfo.getCurrentClient();
            SimpleWxAccount wx = CurrentUserInfo.getSimpleWxAccount();
            Map<String, String> properties = CurrentUserInfo.getCurrentWxProperties();
            if (client == null || null == wx || properties==null) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "wxApp or client or properties is null");
            }
            String organId = properties.get("organId");
            ClientPlatformEnum clientPlatformEnum = ClientPlatformEnum.fromKey(client.getOs());
            List<Scratchable> list = Lists.newArrayList();
            if (ClientPlatformEnum.ALILIFE.equals(clientPlatformEnum)){
                list = findScratchableByPlatform(platformType, wx.getAppId(), tempId);
            }else if(ClientPlatformEnum.WEIXIN.equals(clientPlatformEnum)){
                WXConfig wxConfig = DAOFactory.getDAO(WXConfigsDAO.class).getByAppID(wx.getAppId());
                list = findScratchableByPlatform(platformType, String.valueOf(wxConfig.getId()), tempId);
            }else if(ClientPlatformEnum.WEB.equals(clientPlatformEnum)) {
                list = findScratchableByPlatform(platformType, wx.getAppId(), tempId);
            } else if (ClientPlatformEnum.WX_WEB.equals(clientPlatformEnum)) {
                SimpleThird third = (SimpleThird) wx;
                list = findScratchableByPlatform(platformType, third.getAppkey(), tempId);
            } else {
                log.warn("findModleForHealthCard not support client plat, clientPlat[{}]", clientPlatformEnum);
            }
            return packCustomerUrl(list, organId);
        } catch (Exception e) {
            log.error("findModleForHealthCard error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }
    }

    private List<Scratchable> packCustomerUrl(List<Scratchable> list, String organId) {
        if(ValidateUtil.blankList(list)){
            return list;
        }
        for(Scratchable sc : list){
            if(ValidateUtil.notBlankString(sc.getBoxDesc()) && sc.getBoxDesc().contains("http")){
                sc.setCustomerUrl(packCustomerParams(sc.getBoxDesc(), organId));
            }
        }
        return list;
    }

    private String packCustomerParams(String boxDesc, String organId) {
        try {
            Map<String, String> params = Maps.newTreeMap();
            ThirdParty thirdParty = DAOFactory.getDAO(ThirdPartyDao.class).get(SystemConstant.NGARI_APP_KEY_FOR_THIRDPLAT);
            UserRoleToken urt = UserRoleToken.getCurrent();
            Patient patient = (Patient) urt.getProperty("patient");
            params.put("appkey", SystemConstant.NGARI_APP_KEY_FOR_THIRDPLAT);
            params.put("tid", String.valueOf(urt.getId()));
            params.put("mobile", patient.getMobile());
            params.put("idcard", patient.getIdcard());
            params.put("patientName", patient.getPatientName());
            params.put("signature", thirdParty.signature(packForSignature(params)));
            params.put("organId", organId);
            return processTemplate(boxDesc, params, "{", "}");
        }catch (Exception e){
            log.error("packCustomerParams error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw e;
        }
    }

    private String packForSignature(Map<String, String> params) {
        StringBuilder s = new StringBuilder();
        for (String k : params.keySet()) {
            String v = params.get(k);
            s.append("&").append(k).append("=").append(org.apache.commons.lang3.StringUtils.isEmpty(v) ? "" : v);
        }
        return s.substring(1);
    }

    private String processTemplate(String template, Map<String, ?> paramMap, String preSeparatorPart, String sufSeparatorPart) {
        if (template == null || template.length() == 0 || paramMap == null || paramMap.size() == 0) {
            return template;
        }
        Iterator<? extends Map.Entry<String, ?>> it = paramMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ?> en = it.next();
            String value = en.getValue() == null ? "" : en.getValue().toString();
            template = template.replaceAll(escapeRegexChar(preSeparatorPart + en.getKey() + sufSeparatorPart), value);
        }
        return template;
    }

    /**
     * 内部方法，将给定字符串中包含的正则字符转义
     *
     * @param input
     * @return
     */
    private static String escapeRegexChar(String input) {
        String regexChars = "*.?+$^[](){}|\\/";
        StringBuffer sb = new StringBuffer();
        for (char c : input.toCharArray()) {
            if (regexChars.indexOf(c) > -1) {
                sb.append("\\");
                sb.append(c);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

}
