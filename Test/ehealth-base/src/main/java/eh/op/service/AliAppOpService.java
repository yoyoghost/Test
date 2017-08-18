package eh.op.service;

import ctd.mvc.alilife.AliLifeAppManager;
import ctd.mvc.alilife.entity.OauthAliApp;
import ctd.mvc.alilife.entity.OauthAliAppDAO;
import ctd.mvc.alilife.entity.OauthAliAppProps;
import ctd.mvc.alilife.entity.OauthAliAppPropsDAO;
import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.util.BeanUtils;
import ctd.util.annotation.RpcService;
import eh.base.service.BusActionLogService;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

/**
 * @author jianghc
 * @create 2017-04-19 13:53
 **/
public class AliAppOpService {
    private OauthAliAppDAO oauthAliAppDAO;
    private OauthAliAppPropsDAO oauthAliAppPropsDAO;

    public AliAppOpService() {
        oauthAliAppDAO = DAOFactory.getDAO(OauthAliAppDAO.class);
        oauthAliAppPropsDAO = DAOFactory.getDAO(OauthAliAppPropsDAO.class);
    }

    @RpcService
    public QueryResult<OauthAliApp> queryAliAppListWithPage(OauthAliApp oauthAliApp, int start, int limit) {
        return oauthAliAppDAO.findAliAppListWithPage(oauthAliApp, start, limit);
    }

    @RpcService
    public OauthAliApp getById(String id) {
        if (StringUtils.isEmpty(id)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "id is require");
        }
        return oauthAliAppDAO.get(id);
    }

    @RpcService
    public OauthAliApp saveOneAliApp(OauthAliApp aliApp) {
        if (aliApp == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "aliApp is require");
        }
        if (StringUtils.isEmpty(aliApp.getId())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "aliApp.id is require");
        }
        String id = aliApp.getId().trim();
        if (oauthAliAppDAO.get(id) != null) {
            throw new DAOException("aliApp.id is exist");
        }
        if (StringUtils.isEmpty(aliApp.getAlipayPublicKey())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "aliApp.alipayPublicKey is require");
        }
        if (StringUtils.isEmpty(aliApp.getHomeFile())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "aliApp.homeFile is require");
        }
        if (StringUtils.isEmpty(aliApp.getPrivateKey())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "aliApp.privateKey is require");
        }
        if (StringUtils.isEmpty(aliApp.getPublicKey())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "aliApp.publicKey is require");
        }
        Map<String, String> map = aliApp.getProperties();
        oauthAliAppDAO.save(aliApp);
        for (Map.Entry<String, String> entry : map.entrySet()) {
            OauthAliAppProps aliAppProps = new OauthAliAppProps();
            aliAppProps.setAppId(id);
            aliAppProps.setPropName(entry.getKey());
            aliAppProps.setPropValue(entry.getValue());
            oauthAliAppPropsDAO.save(aliAppProps);
        }
        AliLifeAppManager.instance().reload(id);
        BusActionLogService.recordBusinessLog("支付宝生活号管理", id, "OauthAliApp",
                "添加生活号：【" + id + "】");
        return oauthAliAppDAO.get(id);
    }

    @RpcService
    public OauthAliApp updateOneAliApp(OauthAliApp aliApp) {
        if (aliApp == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "aliApp is require");
        }
        if (StringUtils.isEmpty(aliApp.getId())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "aliApp.id is require");
        }
        String id = aliApp.getId().trim();
        OauthAliApp oldAliApp = oauthAliAppDAO.get(id);
        if (oldAliApp == null) {
            throw new DAOException("aliApp.id is not exist");
        }
        if (StringUtils.isEmpty(aliApp.getAlipayPublicKey())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "aliApp.alipayPublicKey is require");
        }
        if (StringUtils.isEmpty(aliApp.getHomeFile())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "aliApp.homeFile is require");
        }
        if (StringUtils.isEmpty(aliApp.getPrivateKey())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "aliApp.privateKey is require");
        }
        if (StringUtils.isEmpty(aliApp.getPublicKey())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "aliApp.publicKey is require");
        }
        Map<String, String> map = aliApp.getProperties();
        BeanUtils.map(aliApp, oldAliApp);
        oauthAliAppDAO.update(oldAliApp);
        oauthAliAppPropsDAO.removeByAppid(id);
        for (Map.Entry<String, String> entry : map.entrySet()) {
            OauthAliAppProps aliAppProps = new OauthAliAppProps();
            aliAppProps.setAppId(id);
            aliAppProps.setPropName(entry.getKey());
            aliAppProps.setPropValue(entry.getValue());
            oauthAliAppPropsDAO.save(aliAppProps);
        }
        AliLifeAppManager.instance().reload(id);
        BusActionLogService.recordBusinessLog("支付宝生活号管理", id, "OauthAliApp",
                "更新生活号：【" + id + "】");
        return oauthAliAppDAO.get(id);
    }


    @RpcService
    public void deleteOneAliApp(String id) {
        if (StringUtils.isEmpty(id)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "aliApp.id is require");
        }
        OauthAliApp oldAliApp = oauthAliAppDAO.get(id);
        if (oldAliApp == null) {
            throw new DAOException("aliApp.id is not exist");
        }
        oauthAliAppDAO.remove(id);
        oauthAliAppPropsDAO.removeByAppid(id);
        BusActionLogService.recordBusinessLog("支付宝生活号管理", id, "OauthAliApp",
                "删除生活号：【" + id + "】");
        AliLifeAppManager.instance().reload(id);
    }


}
