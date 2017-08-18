package eh.base.service;

import ctd.account.UserRoleToken;
import ctd.net.broadcast.MQHelper;
import ctd.net.broadcast.Observer;
import ctd.net.broadcast.Subscriber;
import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.base.dao.BusActionLogDAO;
import eh.base.dao.LogonLogDAO;
import eh.bus.service.consult.OnsConfig;
import eh.entity.base.BusActionLog;
import eh.remote.IOpDoctorAndOrganService;
import eh.remote.IWxUserInfoServiceInterface;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;

/**
 * Created by houxr on 2016/7/15.
 */
public class BusActionLogService {
    private static final Log logger = LogFactory.getLog(BusActionLogService.class);
    public static final Integer LOGON_LOG = 1;
    public static final Integer BUS_LOG = 2;
    public static final Integer WX_SUBSCRIBE = 3;
    public static final Integer CHANGE_STATUS = 4;

    /**
     * 业务日志查询
     *
     * @param bizClass      业务类型
     * @param startDt       操作起始时间
     * @param endDt         操作结束时间
     * @param keyword       关键字：操作用户 or 姓名
     * @param actionContent 操作内容
     * @param start         起始页
     * @param limit         条数
     * @return
     * @author houxr
     */
    @RpcService
    public QueryResult<BusActionLog> queryBusActionLogByStartAndLimit(final String bizClass,
                                                                      final String startDt,
                                                                      final String endDt,
                                                                      final String keyword,
                                                                      final String actionContent,
                                                                      final int start, final int limit) {
        BusActionLogDAO busActionLogDAO = DAOFactory.getDAO(BusActionLogDAO.class);
        return busActionLogDAO.queryBusActionLogByStartAndLimit(bizClass, startDt, endDt, keyword, actionContent, start, limit);
    }


    /**
     * 业务操作日志记录
     *
     * @param actionType 业务类型
     * @param bizId      业务id
     * @param bizClass   操作业务对象
     * @param content    记录日志内容
     */
    @RpcService
    public static void recordBusinessLog(String actionType, String bizId, String bizClass, String content) {
        BusActionLogDAO busActionLogDAO = DAOFactory.getDAO(BusActionLogDAO.class);
        try {
            busActionLogDAO.recordLog(actionType, bizId, bizClass, content);
        } catch (Exception e) {
            logger.error("日志记录失败：" + content + ",失败原因：" + e.getMessage());
        }

    }

    /**
     * 获取所有 日志类型
     *
     * @return
     */
    @RpcService
    public List<String> findActionTypeFromBusActionLog() {
        BusActionLogDAO busActionLogDAO = DAOFactory.getDAO(BusActionLogDAO.class);
        List<String> actionTypes = busActionLogDAO.findActionTypeFromBusActionLog();
        return actionTypes;
    }

    /**
     * 记录导出数据日志
     */
    @RpcService
    public void recordExportLog(String busType, String msg) {
        BusActionLogDAO busActionLogDAO = DAOFactory.getDAO(BusActionLogDAO.class);
        busActionLogDAO.recordLog("业务数据导出", null, null, "导出一份" + busType + "的业务数据,查询条件【" + msg + "】");
    }

    @PostConstruct
    public void recordLogByOns() {
        //OnsConfig onsConfig = (OnsConfig) AppContextHolder.getBean("onsConfig");
        if(!OnsConfig.onsSwitch){
            return;
        }
        Subscriber subscriber = MQHelper.getMqSubscriber();
        subscriber.attach(OnsConfig.logTopic, new Observer<Map>() {
            @Override
            public void onMessage(Map map) {
                if (map == null) {
                    logger.error("ONS 日志记录失败：map为null");
                    return;
                }
                Integer type = (Integer) map.get("type");
                if (type == null) {
                    logger.error("ONS 日志记录失败：map.type为null");
                    return;
                }
                switch (type) {
                    case 1:
                        try {
                            LogonLogDAO logonLogDAO = DAOFactory.getDAO(LogonLogDAO.class);
                            logonLogDAO.saveLongLogByUserRoleTokenAndDate((UserRoleToken) map.get("urt"));
                        } catch (Exception e) {
                            logger.error("logonLog error:" + e.toString());
                        }
                        break;
                    case 3:
                        try {
                            IWxUserInfoServiceInterface wxUserInfoService = AppContextHolder.getBean("ehop.wxUserInfoService",IWxUserInfoServiceInterface.class);
                            if ("subscribe".equals(map.get("event"))){//关注
                                wxUserInfoService.subscribeUserInfo((String)map.get("openId"),(String)map.get("appId"));
                            }
                            if ("unsubscribe".equals(map.get("event"))){//取消关注
                                wxUserInfoService.unSubscribeUserInfo((String)map.get("openId"));
                            }
                        }catch (Exception e) {
                            logger.error("weixin error:" + e.toString());
                        }
                        break;
                    case 4:
                        try {
                            String event = (String) map.get("event");
                            if ("changedocstatus".equals(event)){
                                IOpDoctorAndOrganService opDoctorAndOrganService = AppContextHolder.getBean("ehop.localDoctorBusinessStatisticsService",IOpDoctorAndOrganService.class);
                                Integer doctorId = (Integer) map.get("doctorId");
                                Integer status = (Integer) map.get("status");
                                opDoctorAndOrganService.updateStatusByDoctorId(status,doctorId);
                            }
                            if ("changeorganstatus".equals(event)){
                                IOpDoctorAndOrganService opDoctorAndOrganService = AppContextHolder.getBean("ehop.localOrganTotalBusinessStatisticsService",IOpDoctorAndOrganService.class);
                                Integer organId = (Integer) map.get("organId");
                                Integer status = (Integer) map.get("status");
                                opDoctorAndOrganService.updateStatusByOrganId(status,organId);
                            }
                        }catch (Exception e) {
                            logger.error("update error:" + e.toString());
                        }
                        break;
                    default:
                        logger.error("ONS 日志记录失败：map.type=" + type + "不支持");
                }
            }
        });
    }


}
