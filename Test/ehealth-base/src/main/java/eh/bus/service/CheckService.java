package eh.bus.service;

import ctd.persistence.DAOFactory;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.service.BusActionLogService;
import eh.bus.dao.*;
import eh.entity.bus.CheckAppointItem;
import eh.entity.bus.CheckRequest;
import eh.entity.bus.CheckSchedule;
import eh.entity.bus.CheckSource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 医技检查相关服务 houxr 2016-07-26
 */
public class CheckService {
    private static final Log logger = LogFactory.getLog(CheckService.class);

    /**
     * 根据排班id查找对应医技检查预约号源CheckRequest
     *
     * @param organSchedulingId
     * @return
     */
    public List<CheckRequest> findCheckRequestByOrganIdAndSchedulingId(Integer organId, String organSchedulingId) {
        CheckSourceDAO checkSourceDAO = DAOFactory.getDAO(CheckSourceDAO.class);
        CheckRequestDAO checkRequestDAO = DAOFactory.getDAO(CheckRequestDAO.class);
        CheckAppointItemDAO checkAppointItemDAO = DAOFactory.getDAO(CheckAppointItemDAO.class);
        List<CheckRequest> checkRequestList = new ArrayList<CheckRequest>();
        List<CheckSource> sourceList = checkSourceDAO.findCheckSourceByOrganIdAndCheckScheduleId(organId, organSchedulingId);
        for (CheckSource cs : sourceList) {
            CheckAppointItem checkAppointItem = checkAppointItemDAO.get(cs.getCheckAppointId());
            checkRequestList = checkRequestDAO.findCheckRequestByCheckAppointIdAndChkSourceId(checkAppointItem.getOrganId(), cs.getCheckAppointId(), cs.getChkSourceId());
        }
        return checkRequestList;
    }


    /**
     * 医技检查停班同时停诊服务 点击停班，同时停诊后，停诊今天之后的号源；当天号源不停诊
     *
     * @param ids             排班id
     * @param useFlag         停班状态:1停班 0正常
     * @param stopOrNotSource 是否同时停诊 true停班停诊:1停诊 0正常
     * @return
     * @author houxr
     * @date 2016-07-27 15:15:30
     * @author houxr
     */
    @RpcService
    public Integer stopOrNotScheduleAndSource(List<Integer> ids, int useFlag, Boolean stopOrNotSource) {
        logger.info("医技检查停班同时停诊服务=>stopOrNotScheduleAndSource:"
                + "ids:" + JSONUtils.toString(ids) + ";useFlag:" + useFlag + ";stopFlag:" + stopOrNotSource);
        int count = 0;
        for (Integer id : ids) {
            if (id == null) {
                continue;
            }
            CheckScheduleDAO checkScheduleDAO = DAOFactory.getDAO(CheckScheduleDAO.class);
            CheckSchedule cs = checkScheduleDAO.get(id);//取得一个排班对象
            if (cs == null) {
                continue;
            }
            cs.setUseFlag(useFlag);//设置排班状态为:1停班 0正常
            checkScheduleDAO.update(cs);

            //检查排班停班同时停诊服务操作日志
            CheckAppointItem checkAppointItem = DAOFactory.getDAO(CheckAppointItemDAO.class).get(cs.getCheckAppointId());
            BusActionLogService.recordBusinessLog("检查排班", cs.getCheckScheduleId().toString(), "CheckSchedule",
                    checkScheduleDAO.getLogMessageByCheckSchedule(cs) +  " 检查[" + checkAppointItem.getCheckAppointName() + "]的星期" + (cs.getWeek() == 7 ? "天" : cs.getWeek())
                            + "排班" + (useFlag == 1 ? "停班" + (stopOrNotSource ? ",同时停诊" : "") : "取消停班"));

            //是否号源要做停诊：停诊号源 根据排班的id 查找到对应班次的号源记录 做停诊标记 当天号源不停诊
            if (stopOrNotSource) {
                CheckSourceDAO checkSourceDAO = DAOFactory.getDAO(CheckSourceDAO.class);
                List<CheckSource> checkSourceList = checkSourceDAO.findCheckSourceByOrganIdAndCheckScheduleId(cs.getOrganId(),
                        String.valueOf(cs.getCheckScheduleId()));
                for (CheckSource checkSource : checkSourceList) {
                    checkSource.setStopFlag(1);//1停诊 0正常
                    checkSource = checkSourceDAO.update(checkSource);
                    //已经预约号源记录标记设置:已取消 若存在被约号,短信通知患者停诊取消
                    checkRequestSourceStopOrNot(checkSource);
                }
            }
            count++;
        }
        return count;
    }


    /**
     * 医技检查号源出/停诊服务 对已经预约的号源 短信通知
     *
     * @param ids      需修改的号源序号列表
     * @param stopFlag 停诊标志
     * @return int 修改成功的条数
     * @author houxr
     */
    @RpcService
    public int stopOrNotCheckSource(List<Integer> ids, int stopFlag) {
        logger.info("医技检查号源出/停诊服务stopOrNotCheckSources=>ids:" + JSONUtils.toString(ids)
                + ";stopFlag:" + stopFlag);
        //预约记录取消记录业务日志
        String logMessage = null;
        CheckSourceDAO sourceDAO = DAOFactory.getDAO(CheckSourceDAO.class);
        int count = 0;
        for (Integer id : ids) {
            if (id == null) {
                continue;
            }
            CheckSource cs = sourceDAO.get(id);
            if (cs == null) {
                continue;
            }
            if(logMessage == null)
            {
                logMessage = sourceDAO.getLogMessageByCheckSource(cs);
            }
            cs.setStopFlag(stopFlag);
            cs = sourceDAO.update(cs);
            //预约检查记录做停诊标记并且发送短信通知预约医生和患者
            if(stopFlag==1) {//执行的是停诊动作
                checkRequestSourceStopOrNot(cs);
            }
            count++;
        }
        BusActionLogService.recordBusinessLog("检查号源", JSONUtils.toString(ids), "CheckSource",
                logMessage + " 号源" + JSONUtils.toString(ids) + (stopFlag == 1 ? "停诊" : "恢复出诊"));
        return count;
    }

    /**
     * 预约检查记录做停诊标记并且发送短信通知预约医生和患者
     *
     * @param cs 检查号源
     */
    public void checkRequestSourceStopOrNot(final CheckSource cs) {
        CheckRequestDAO checkRecordDAO = DAOFactory.getDAO(CheckRequestDAO.class);
        CheckAppointItemDAO checkAppointItemDAO = DAOFactory.getDAO(CheckAppointItemDAO.class);
        CheckAppointItem checkAppointItem = checkAppointItemDAO.get(cs.getCheckAppointId());
        if (cs.getChkSourceId() != null && cs.getCheckAppointId() != null && null != checkAppointItem) {
            //查找检查号源预约记录
            List<CheckRequest> checkRequests =
                    checkRecordDAO.findDjcCheckRequestByCheckAppointIdAndChkSourceId(checkAppointItem.getOrganId(), cs.getCheckAppointId(), cs.getChkSourceId());
            if (null != checkRequests && !checkRequests.isEmpty()) {
                for (CheckRequest checkRequest : checkRequests) {
                    boolean flag = checkRecordDAO.cancelCheckNoMsg(checkRequest.getCheckRequestId(), "预约检查号源停诊");
                    if (flag) {//短信通知申请医生和患者
                        checkRecordDAO.sendSmsForCheckSourceStopToDocAndPat(checkRequest.getCheckRequestId());
                    }
                }
            }
        }
    }


    /**
     * 运营平台 取消检查预约
     *
     * @param checkRequestId
     * @param cancelReason
     */
    @RpcService
    public void cancelCheck(final int checkRequestId, final String cancelReason) {
        BusActionLogService.recordBusinessLog("业务单取消",String.valueOf(checkRequestId),"CheckRequest",
                "医技检查单["+checkRequestId+"]被取消");
        CheckRequestDAO checkRecordDAO = DAOFactory.getDAO(CheckRequestDAO.class);
        boolean res = checkRecordDAO.cancelCheckNoMsg(checkRequestId, cancelReason);
        if (res) {
            String SmsType = "CancelCheckToPat";
            String BusType = "CancelCheckToPat";
            CheckReqMsg.sendMsg(checkRequestId, SmsType, BusType);
//            CheckReqMsg.sendMsg(checkRequestId, "cancelCheckToPat");
        }
    }


}
