package eh.op.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.entity.wx.WXConfig;
import eh.entity.wx.WeChatDailyUsers;
import eh.op.dao.WXConfigsDAO;
import eh.op.dao.WeChatDailyUsersDAO;
import eh.remote.IWXServiceInterface;
import eh.utils.DateConversion;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;

/**
 * @author jianghc
 * @create 2017-04-07 09:31
 **/
public class WeChatDailyUsersService {
    private static final Log logger = LogFactory.getLog(WeChatDailyUsersService.class);


    private WeChatDailyUsersDAO weChatDailyUsersDAO;
    private WXConfigsDAO wxConfigsDAO;
    private IWXServiceInterface wxService;

    public WeChatDailyUsersService() {
        weChatDailyUsersDAO = DAOFactory.getDAO(WeChatDailyUsersDAO.class);
        wxConfigsDAO = DAOFactory.getDAO(WXConfigsDAO.class);
        wxService = AppContextHolder.getBean("eh.wxService", IWXServiceInterface.class);

    }

    /**
     * 定时统计微信公众号用户情况
     *
     * @param bDate
     * @param eDate
     */
    @RpcService
    public void saveWeChatDailyUsers(Date bDate, Date eDate) {
        if (bDate == null || eDate == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "date is require");
        }
        Date now = new Date();
        if (!bDate.before(now) || !eDate.before(now) || bDate.after(eDate)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "date is error");
        }
        List<WXConfig> configs = wxConfigsDAO.findAllConfig();
        if (configs == null || configs.size() <= 0) {
            return;
        }
        for (WXConfig wxConfig : configs) {
            logger.info("====>开始统计公众号日用户情况：" + wxConfig.getWxName());
            this.createWeChatDailyDetail(1, wxConfig, bDate, eDate);
            this.createWeChatDailyDetail(2, wxConfig, bDate, eDate);
        }
    }




    private void createWeChatDailyDetail(int type, WXConfig wxConfig, Date bDate, Date eDate) {
        HashMap<String, Object> detail = null;
        try {
            detail = wxService.getDailyUserByAppId(type, bDate, eDate, wxConfig.getAppID());
        } catch (Exception e) {
            logger.error("===>从微信获取用户情况失败:" + e.getMessage());
            return;
        }
        if (detail.get("errcode") != null) {
            logger.error("===>从微信获取用户情况失败:" + detail.get("errmsg"));
            return;
        }
        logger.info("===>微信每日用户情况(" + (type == 1 ? "明细" : "合计") + "):" + JSONUtils.toString(detail));
        List<Map> detailList = null;
        Set<Map.Entry<String, Object>> set = detail.entrySet();
        if (set == null || set.size() <= 0) {
            return;
        }
        for (Map.Entry<String, Object> entry : set) {
            detailList = (List<Map>) entry.getValue();
            break;
        }
        if (detailList == null || detailList.size() <= 0) {
            return;
        }
        for (Map map : detailList) {
            String strRefDate = (String) map.get("ref_date");
            Date refDate = DateConversion.getCurrentDate(strRefDate, DateConversion.YYYY_MM_DD);
            Integer newUser = (Integer) map.get("new_user");
            Integer cancelUser = (Integer) map.get("cancel_user");
            Integer userSource = (Integer) map.get("user_source");
            Integer cumulateUser = (Integer) map.get("cumulate_user");
            String strUserSource = userSource == null ? "0" : userSource + "";
            if (type == 2) {
                strUserSource = "T";
            }
            WeChatDailyUsers weChatDailyUsers = weChatDailyUsersDAO
                    .getByWxConfigIdAndRefDateAndUserSource(wxConfig.getId(), refDate, strUserSource);
            if (weChatDailyUsers == null) {//新增
                weChatDailyUsers = new WeChatDailyUsers(wxConfig.getId()
                        , refDate, strUserSource
                        , newUser == null ? Integer.valueOf(0) : newUser
                        , cancelUser == null ? Integer.valueOf(0) : cancelUser
                        , cumulateUser == null ? Integer.valueOf(0) : cumulateUser);
                weChatDailyUsersDAO.save(weChatDailyUsers);
            } else {//更新
                weChatDailyUsers.setNewUser(newUser == null ? Integer.valueOf(0) : newUser);
                weChatDailyUsers.setCancelUser(cancelUser == null ? Integer.valueOf(0) : cancelUser);
                weChatDailyUsers.setCumulateUser(cumulateUser == null ? Integer.valueOf(0) : cumulateUser);
                weChatDailyUsersDAO.update(weChatDailyUsers);
            }
        }
        logger.info("===>微信每日用户统计完成");
    }

    @RpcService
    public WeChatDailyUsers getByWxConfigIdAndRefDateAndUserSource(Integer wxConfig, Date refDate, String userSource) {
        return weChatDailyUsersDAO.getByWxConfigIdAndRefDateAndUserSource(wxConfig, refDate, userSource);
    }




}
