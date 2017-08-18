package eh.op.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.base.service.BusActionLogService;
import eh.bus.dao.WxTemplateDAO;
import eh.entity.base.WechatModuleMessage;
import eh.entity.bus.vo.WxTemplate;
import eh.op.dao.WechatModuleMessageDAO;
import org.apache.log4j.Logger;

import java.util.List;

/**
 * Created by andywang on 2016/12/6.
 */
public class WechatModuleMessageOpService {
    private static final Logger logger = Logger.getLogger(WechatModuleMessageOpService.class);

    @RpcService
    public List<WechatModuleMessage> findAllModuleMessages() {
        WechatModuleMessageDAO messageDAO = DAOFactory.getDAO(WechatModuleMessageDAO.class);
        return messageDAO.findAllModuleMessages();
    }

    @RpcService
    public void removeTemplatesByIdList(List<Integer> lists)
    {
        if (lists != null)
        {
            WxTemplateDAO dao = DAOFactory.getDAO(WxTemplateDAO.class);
            if(!lists.isEmpty())
            {
                try
                {
                    Integer id = lists.get(0);
                    WxTemplate temp1 = dao.get(0);
                    if (temp1 != null)
                    {
                        String accountName= temp1.getOfficialAccountsName();
                        BusActionLogService.recordBusinessLog("微信公众号管理",null, null, accountName + " 批量删除模板消息");
                    }
                }
                catch (Exception e)
                {
                    throw new DAOException(e.getMessage());
                }
            }
            dao.removeTemplatesByIdList(lists);
        }
    }

    @RpcService
    public void removeTemplateById(Integer id)
    {
        if (id != null)
        {

            WxTemplateDAO dao = DAOFactory.getDAO(WxTemplateDAO.class);
            WxTemplate temp = dao.get(id);
            if (temp != null)
            {
                BusActionLogService.recordBusinessLog("微信公众号管理",id.toString(), "WxTemplate", temp.getOfficialAccountsName() + " 删除模板消息 " + temp.getName());
                dao.removeTemplateById(id);
            }
        }
    }

    @RpcService
    public WxTemplate addOrUpdate(WxTemplate template)
    {
        if (template == null )
        {
            return null;
        }
        WxTemplateDAO dao = DAOFactory.getDAO(WxTemplateDAO.class);
        BusActionLogService.recordBusinessLog("微信公众号管理",template.getId().toString(), "WxTemplate", template.getOfficialAccountsName() + " 更新模板消息 " + template.getName());
        return dao.addOrUpdate(template);
    }

    @RpcService
    public List<WxTemplate> addOrUpdateBatch(List<WxTemplate> templates)
    {
        if (templates == null )
        {
            return null;
        }
        WxTemplateDAO dao = DAOFactory.getDAO(WxTemplateDAO.class);
        if(!templates.isEmpty())
        {
            try
            {
                WxTemplate temp1 = templates.get(0);
                String accountName= temp1.getOfficialAccountsName();
                BusActionLogService.recordBusinessLog("微信公众号管理",null, null, accountName + " 批量更新模板消息");
            }
            catch (Exception e)
            {
                throw new DAOException(e.getMessage());
            }
        }
        return dao.addOrUpdateBatch(templates);
    }

    @RpcService
    public  List<WxTemplate> findTemplateListByAppId(String appId)
    {
        if (appId == null )
        {
            return null;
        }
        WxTemplateDAO dao = DAOFactory.getDAO(WxTemplateDAO.class);
        return dao.findTemplateListByAppId(appId);
    }

    @RpcService
    public WxTemplate getTemplateByAppIdAndTemplateKey(String appId,String templateKey)
    {
        WxTemplateDAO dao = DAOFactory.getDAO(WxTemplateDAO.class);
        return dao.getTemplateByAppIdAndTemplateKey(appId,templateKey);
    }
}
