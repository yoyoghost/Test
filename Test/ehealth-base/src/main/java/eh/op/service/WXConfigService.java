package eh.op.service;

import ctd.controller.exception.ControllerException;
import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.BeanUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.service.BusActionLogService;
import eh.base.service.QRInfoService;
import eh.entity.wx.WXConfig;
import eh.entity.wx.WxAppProps;
import eh.op.dao.WXConfigsDAO;
import eh.op.dao.WxAppPropsDAO;
import eh.remote.IWXMenuServiceInterface;
import eh.remote.IWXPMServiceInterface;
import eh.util.ControllerUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;

/**
 * Created by houxr on 2016/9/11.config
 */
public class WXConfigService {
    private static final Log logger = LogFactory.getLog(SourceOpService.class);

    /**
     * 新建wxConfig
     *
     * @param wxConfig
     * @return
     * @author houxr
     */
    @RpcService
    public WXConfig addOneWXConfig(WXConfig wxConfig) {
        List<WxAppProps> propses = wxConfig.getProps();
        if (propses != null && propses.size() > 0) {
            HashMap<String, Integer> map = new HashMap<String, Integer>();
            for (WxAppProps props : propses) {
                if (map.get(props.getPropName()) == null || map.get(props.getPropName()) != 1) {
                    map.put(props.getPropName(), 1);
                } else {
                    throw new DAOException(" props is exist");
                }
            }
        }
        WXConfigsDAO wxConfigsDAO = DAOFactory.getDAO(WXConfigsDAO.class);
        WXConfig target = wxConfigsDAO.addOneWXConfig(wxConfig);
        if (propses != null && propses.size() > 0) {
            WxAppPropsDAO wxAppPropsDAO = DAOFactory.getDAO(WxAppPropsDAO.class);
            List<WxAppProps> reProps = new ArrayList<WxAppProps>();
            for (WxAppProps prop : propses) {
                prop.setConfigId(target.getId());
                reProps.add(wxAppPropsDAO.save(prop));
            }
            target.setProps(reProps);
        }

        try {//刷新缓存
            ControllerUtil.reloadWXAppByAppId(target.getAppID());
        } catch (ControllerException e) {
            logger.error(e);
        }

        BusActionLogService.recordBusinessLog("微信公众号管理", target.getId().toString(), "WXConfig",
                "新增微信公众号:" + target.getWxName() + ",appId:" + target.getAppID());
        return target;
    }

    /**
     * 查询所有微信账号
     * <p>
     * status（0为纳里，1为机构）
     */
    @RpcService
    public List<WXConfig> findAllWXConfig(Integer status) {
        WXConfigsDAO wxConfigsDAO = DAOFactory.getDAO(WXConfigsDAO.class);
        if (status == null) {
            return wxConfigsDAO.findAllConfig();
        }
        return wxConfigsDAO.findByStatus(status);
    }

    @RpcService
    public QueryResult<WXConfig> queryWxConfigByStatusAndName(Integer status, String name, int start, int limit) {
        WXConfigsDAO wxConfigsDAO = DAOFactory.getDAO(WXConfigsDAO.class);
        try {
            return wxConfigsDAO.queryWXConfigByStatusAndName(name, status, start, limit);
        }catch (Exception e){
            throw new DAOException("查询失败");
        }

    }


    /**
     * 查询所有 wxConfig
     *
     * @return
     */
    @RpcService
    public List<WXConfig> findConfigsByWxType(Integer wxType) {
        WXConfigsDAO wxConfigsDAO = DAOFactory.getDAO(WXConfigsDAO.class);
        if (wxType == null) {
            return wxConfigsDAO.findAllConfig();
        }
        return wxConfigsDAO.findByWxType(wxType);
    }

    /**
     * [运营平台] 机构微信相关设置查询
     *
     * @param organName
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    public QueryResult<WXConfig> queryWXConfigByStartAndLimit(final String organName, final int start, final int limit) {
        WXConfigsDAO wxConfigsDAO = DAOFactory.getDAO(WXConfigsDAO.class);
        return wxConfigsDAO.queryWXConfigByStartAndLimit(organName, start, limit);
    }

    /**
     * [运营平台] 机构微信相关设置查询 返回 QueryResult<Map>
     *
     * @param organName
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    public QueryResult<Map> queryMapByStartAndLimit(final String organName, final int start, final int limit) {
        WXConfigsDAO wxConfigsDAO = DAOFactory.getDAO(WXConfigsDAO.class);
        return wxConfigsDAO.queryMapByStartAndLimit(organName, start, limit);
    }

    /**
     * 根据appId获取对应公众号素材库中永久图文素材消息
     *
     * @param appId
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    public Map queryWXMediasByStartAndLimit(String appId, int start, int limit) {
        IWXPMServiceInterface wxPMService = AppContextHolder.getBean("eh.wxPushMessService", IWXPMServiceInterface.class);
        Map resultMap = wxPMService.findListMediasByStartAndLimit(appId, start, limit);
        return resultMap;
    }


    /**
     * 根据mediaId获取微信素材库中的图文消息
     *
     * @param appId
     * @param mediaId
     * @return
     */
    @RpcService
    public Map findMediaByMediaId(String appId, String mediaId) {
        IWXPMServiceInterface wxPMService = AppContextHolder.getBean("eh.wxPushMessService", IWXPMServiceInterface.class);
        Map resultMap = wxPMService.findMediaByMediaId(appId, mediaId);
        return resultMap;
    }

    /**
     * 根据appId获取对应公众号的当前菜单
     *
     * @param appId
     * @return
     */
    @RpcService
    public Map<String, Object> getWxMenus(String appId) {
        IWXMenuServiceInterface wxMenuService = AppContextHolder.getBean("eh.wxMenuService", IWXMenuServiceInterface.class);
        Map<String, Object> resultMap = wxMenuService.getWxMenus(appId);
        return resultMap;
    }

    /**
     * 根据appId获取对应环境的微信菜单[主页] url
     *
     * @param appId
     * @return
     */
    @RpcService
    public String getWxHomePageUrl(String appId) {
        IWXMenuServiceInterface wxMenuService = AppContextHolder.getBean("eh.wxMenuService", IWXMenuServiceInterface.class);
        String url = wxMenuService.getWxHomePageUrl(appId);
        logger.info("HomePageUrl:" + url);
        return url;
    }


    /**
     * 根据appId创建微信公众号的菜单
     *
     * @param appId
     * @param jsonMenu
     */
    @RpcService
    public Map<String, Object> createWXMenu(String appId, String jsonMenu) {
        WXConfigsDAO wxConfigsDAO = DAOFactory.getDAO(WXConfigsDAO.class);
        WXConfig wxConfig = wxConfigsDAO.getByAppID(appId);
        if (wxConfig == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "appId对应的公众号不存在");
        }
        BusActionLogService.recordBusinessLog("微信公众号管理", appId, "WXConfig",
                "微信公众号:" + wxConfig.getWxName() + ",appId:" + appId + "修改菜单为:" + jsonMenu);
        IWXMenuServiceInterface wxMenuService = AppContextHolder.getBean("eh.wxMenuService", IWXMenuServiceInterface.class);
        Map<String, Object> resultMap = wxMenuService.createWXMenu(appId, jsonMenu);
        return resultMap;
    }


    /**
     * 更新一个config
     *
     * @param wxConfig
     * @return
     */

    @RpcService
    public WXConfig updateOneWXConfig(WXConfig wxConfig) {
        if (wxConfig.getId() == null) {
            throw new DAOException("wxConfig is not exist!");
        }
        if (StringUtils.isEmpty(wxConfig.getWxKey())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "wxKey is required!");
        }
        WxAppPropsDAO wxAppPropsDAO = DAOFactory.getDAO(WxAppPropsDAO.class);
        List<WxAppProps> propses = wxConfig.getProps();
        HashMap<String, WxAppProps> map = new HashMap<String, WxAppProps>();
        if (propses != null && propses.size() > 0) {
            for (WxAppProps props : propses) {
                if (map.get(props.getPropName()) == null) {
                    map.put(props.getPropName(), props);
                } else {
                    throw new DAOException(" props is exist");
                }
            }
        } else {
            propses = new ArrayList<WxAppProps>();
            wxAppPropsDAO.deleteByConfigId(wxConfig.getId());
        }
        WXConfigsDAO wxConfigsDAO = DAOFactory.getDAO(WXConfigsDAO.class);
        WXConfig target = wxConfigsDAO.getConfigAndPropsById(wxConfig.getId());
        List<WxAppProps> oldProps = target.getProps();
        if (oldProps != null && oldProps.size() > 0) {
            for (WxAppProps oldp : oldProps) {
                WxAppProps newP = map.get(oldp.getPropName());
                if (newP == null) {
                    wxAppPropsDAO.deleteById(oldp.getId());
                } else {
                    BeanUtils.map(newP, oldp);
                    map.put(oldp.getPropName(), oldp);
                }
            }
        }
        Iterator iter = map.entrySet().iterator();
        propses.clear();
        while (iter.hasNext()) {
            Map.Entry entry = (Map.Entry) iter.next();
            WxAppProps appProps = (WxAppProps) entry.getValue();
            if (appProps.getId() == null) {
                appProps.setConfigId(wxConfig.getId());
                appProps = wxAppPropsDAO.save(appProps);
            } else {
                appProps = wxAppPropsDAO.update(appProps);
            }
            propses.add(appProps);
        }
        if(!wxConfig.getAppID().trim().equals(target.getAppID().trim())){
            //当appid更新的时候所有原先生成的二维码失效
            QRInfoService qrInfoService = AppContextHolder.getBean("eh.qrInfoService",QRInfoService.class);
            qrInfoService.invalidQRInfosByWxConfigId(target.getId());
        }
      //  BeanUtils.map(wxConfig, target);
        target = wxConfigsDAO.update(wxConfig);
        target.setProps(propses);
        try {//刷新缓存
            ControllerUtil.reloadWXAppByAppId(target.getAppID());
        } catch (ControllerException e) {
            logger.error(e);
        }
        BusActionLogService.recordBusinessLog("微信公众号管理", target.getId().toString(), "WXConfig",
                "修改微信公众号:" + target.getWxName());
        return target;
    }


    @RpcService
    public WXConfig getById(Integer id) {
        if (id == null) {
            new DAOException(DAOException.VALUE_NEEDED, "id is required!");
        }
        WXConfigsDAO wxConfigsDAO = DAOFactory.getDAO(WXConfigsDAO.class);
        return wxConfigsDAO.getConfigAndPropsById(id);
    }

    @RpcService
    public String getManageUnitByConfigId(Integer id){
        WXConfig config = this.getById(id);
        if (config != null){
            return config.getManageUnit();
        }
        return null;
    }

    @RpcService
    public List<Map> findAllWXConfigs() {
        WXConfigsDAO wxConfigsDAO = DAOFactory.getDAO(WXConfigsDAO.class);
        List<WXConfig> list = wxConfigsDAO.findAllConfig();
        List<Map> maps = new ArrayList<Map>();
        if(list==null){
            return maps;
        }
        for(WXConfig config :list){
            Map<String,Object> map = new HashMap<String, Object>();
            BeanUtils.map(config,map);
            maps.add(map);
        }
        return maps;
    }
    /**
     * 根据appId获取WXConfig
     * @param appId
     * @return
     */
    @RpcService
    public Map getByAppId(String appId){
        Map<String,Object> map = new HashMap<String, Object>();
        WXConfigsDAO wxConfigsDAO = DAOFactory.getDAO(WXConfigsDAO.class);
        WXConfig wXConfig = wxConfigsDAO.getByAppID(appId);
        if (wXConfig == null){
            return null;
        }
        BeanUtils.map(wXConfig,map);
        return map;
    }

}
