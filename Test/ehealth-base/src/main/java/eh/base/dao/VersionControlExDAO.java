package eh.base.dao;

import ctd.account.UserRoleToken;
import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.constant.VersionControlConstant;
import eh.bus.asyndobuss.AsyncCommonDaoManager;
import eh.bus.asyndobuss.bean.DaoEvent;
import eh.bus.service.common.CurrentUserInfo;
import eh.entity.base.VersionControl;
import eh.entity.base.VersionControlBean;
import eh.entity.base.VersionControlServerLog;
import eh.entity.bus.msg.SimpleWxAccount;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.sql.Timestamp;

/**
 * company: ngarihealth
 * author: 0184/yu_yun
 * date:2016/4/15.
 */
public abstract class VersionControlExDAO extends HibernateSupportDelegateDAO<VersionControl> {

    private static final Log logger = LogFactory.getLog(VersionControlExDAO.class);

    public VersionControlExDAO(){
        super();
        this.setEntityName(VersionControl.class.getName());
        this.setKeyField("id");
    }

    /**
     * 服务窗专用，如 微信公众号，支付宝服务窗
     * @param prgType 程序类型
     * @param clientType 终端类型
     * @param channelType 渠道类型
     * @return VersionControlBean
     */
    @RpcService
    public VersionControlBean checkVersionForServer(@DAOParam("prgType") final Integer prgType,
                                                    @DAOParam("clientType")final Integer clientType,
                                                    @DAOParam("channelType")final Integer channelType){
        UserRoleToken userRoleToken = UserRoleToken.getCurrent();
        if(null != userRoleToken){
            if(VersionControlConstant.CLIENTTYPE_WEIXIN == clientType) {
                SimpleWxAccount simpleWxAccount = CurrentUserInfo.getSimpleWxAccount();
                if(null != simpleWxAccount){
                    String appId = simpleWxAccount.getAppId();
                    String openId = simpleWxAccount.getOpenId();
                    if(StringUtils.isNotEmpty(appId) && StringUtils.isNotEmpty(openId)){
                        VersionControlBean versionBean = checkVersion(prgType,clientType,channelType);
                        if(null == versionBean){
//                            logger.error("VersionControlExDAO.checkVersionForServer [prgType:"+prgType+",clientType:"+clientType+",channelType:"+channelType+"]");
                            throw new DAOException(ErrorCode.SERVICE_ERROR,"没有该版本信息 "+openId);
                        }
                        VersionControlServerLogDao versionControlServerLogDao = DAOFactory.getDAO(VersionControlServerLogDao.class);
                        if(null != versionControlServerLogDao){
                            VersionControlServerLog log = versionControlServerLogDao.getByAppIdAndOpenId(appId,openId);
                            //如果用户版本的更新日期比版本更新的日期大，则不需要提醒
                            Timestamp currentVersionUpdateDate = Timestamp.valueOf(versionBean.getUpdateDate());
                            if(null != log && null != log.getUpdateDate() &&
                                    log.getUpdateDate().getTime() >= currentVersionUpdateDate.getTime()){
                                return null;
                            }

                            //以下为需要提示更新内容
                            //更新记录表
                            AsyncCommonDaoManager daoManager = AsyncCommonDaoManager.getInstance();
                            if(null != log) {
                                DaoEvent e = new DaoEvent();
                                e.setDao(versionControlServerLogDao);
                                e.setMethodName("updateVersionInfo");
                                e.addArgsOrdered(log.getId()).addArgsOrdered(versionBean.getVersion()).addArgsOrdered(currentVersionUpdateDate);
                                daoManager.fireEvent(e);
                            }else{
                                log = new VersionControlServerLog();
                                log.setAppId(appId);
                                log.setOpenId(openId);
                                log.setVersion(versionBean.getVersion());
                                log.setServerType(VersionControlConstant.SERVERTYPE_WEIXIN);
                                log.setUpdateDate(currentVersionUpdateDate);
                                log.setExpand("");
                                DaoEvent e = new DaoEvent();
                                e.setDao(versionControlServerLogDao);
                                e.setMethodName("save");
                                e.addArgsOrdered(log);
                                daoManager.fireEvent(e);
                            }
                            return versionBean;
                        }

                    }else{
//                        logger.error("VersionControlExDAO.checkVersionForServer [appId:"+appId+",openId:"+openId+"]");
                        throw new DAOException(ErrorCode.SERVICE_ERROR,"用户信息不全 "+openId);
                    }
                }else{
                    logger.error("VersionControlExDAO.checkVersionForServer oAuthWeixinMP is null");
                }
            }

            return null;
        }else{
//            logger.error("VersionControlExDAO.checkVersionForServer userRoleToken is null");
            throw new DAOException(ErrorCode.SERVICE_ERROR,"无法获取当前用户，请联系纳里客服");
        }
    }

    /**
     *
     * @param prgType 程序类型
     * @param clientType 终端类型
     * @param channelType 渠道类型
     * @return VersionControlBean
     */
    @RpcService
    public VersionControlBean checkVersion(@DAOParam("prgType") final Integer prgType,
                                           @DAOParam("clientType")final Integer clientType,
                                           @DAOParam("channelType")final Integer channelType){
        if(null != prgType && prgType > 0
                && null != clientType && clientType > 0
                && null != channelType && channelType > 0){
            HibernateStatelessResultAction<VersionControlBean> action = new AbstractHibernateStatelessResultAction<VersionControlBean>() {
                public void execute(StatelessSession ss) throws Exception {
                    VersionControlBean bean;
                    String hql = new String("select new eh.entity.base.VersionControlBean(version.prgAddress, version.updateContent, version.updateDate, version.version," +
                            "strategy.strategyType, strategy.strategyInterval, version.md5) " +
                            "from VersionControl version,VersionControlStrategy strategy where version.prgType = :prgType " +
                            "and version.clientType = :clientType and version.channelType = :channelType and version.versionType = 1 " +
                            "and version.updateStrategy = strategy.id ");
                    Query q = ss.createQuery(hql);
                    q.setParameter("prgType", prgType);
                    q.setParameter("clientType", clientType);
                    q.setParameter("channelType", channelType);

                    bean = (VersionControlBean)q.uniqueResult();
                    setResult(bean);
                }
            };
            HibernateSessionTemplate.instance().executeReadOnly(action);
            return action.getResult();
        }else{
//            logger.error("VersionControlExDAO.checkVersion params: prgType="+prgType+"&clientType="+clientType+"&channelType="+channelType);
            throw new DAOException(ErrorCode.SERVICE_ERROR,"系统版本有误，请联系纳里客服");
        }
    }
}
