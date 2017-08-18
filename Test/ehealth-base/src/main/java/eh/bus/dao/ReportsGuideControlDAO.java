package eh.bus.dao;

import ctd.account.UserRoleToken;
import ctd.account.user.UserRoleTokenEntity;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcService;
import eh.bus.service.common.CurrentUserInfo;
import eh.entity.bus.RepGuideControl;
import eh.entity.bus.msg.SimpleWxAccount;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;


/**
 * Created by zhangz on 2016/6/17.
 */
public abstract class ReportsGuideControlDAO extends HibernateSupportDelegateDAO<RepGuideControl> {
    private static final Log logger = LogFactory.getLog(ReportsGuideControlDAO.class);

    public ReportsGuideControlDAO(){
        super();
        this.setEntityName(RepGuideControl.class.getName());
        this.setKeyField("controlId");
    }
    @RpcService
    @DAOMethod()
    public abstract RepGuideControl getByOpenId(String openId);

    /**
     * 报告详情引导查询服务
     * @return true表示需要弹窗;false表示不需要弹窗
     */
    @RpcService
    public boolean checkReportGuideControl(){
        UserRoleTokenEntity ure = (UserRoleTokenEntity) UserRoleToken.getCurrent();
        if(null != ure) {
            logger.info("checkReportGuideControl comming ");
            SimpleWxAccount simpleWxAccount = CurrentUserInfo.getSimpleWxAccount();
                if (null != simpleWxAccount) {
                    String appId = simpleWxAccount.getAppId();
                    String openId = simpleWxAccount.getOpenId();
                    logger.info("appId:"+appId+"  openId:"+openId);
                    if (StringUtils.isNotEmpty(appId) && StringUtils.isNotEmpty(openId)) {
                        RepGuideControl rep = getByOpenId(openId);
                        if (rep == null) {//找不到记录，返回true，需要弹出
                            //保存信息，下次进入不弹出
                            logger.info("true");
                            Calendar ca = Calendar.getInstance();
                            ca.setTime(new Date()); //得到当前日期
                            Date resultDate = ca.getTime(); // 结果
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                            RepGuideControl newRep = new RepGuideControl();
                            newRep.setOpenId(openId);
                            newRep.setFuncType("1");//暂时用1表示取单引导层
                            newRep.setFirstTime(sdf.format(resultDate));
                            addRepGuideControl(newRep);
                            return true;
                        } else {
                            logger.info("false");
                            return false;
                        }
                    }
                }else{
                    logger.error("oAuthWeixinMP = null!");
                }
        }
        return false;
    }

    @RpcService
    public void addRepGuideControl(RepGuideControl rep) {
        if (rep !=null){
            this.save(rep);
        }
    }

}
