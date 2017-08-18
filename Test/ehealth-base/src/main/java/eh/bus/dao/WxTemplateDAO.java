package eh.bus.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.entity.bus.vo.WxTemplate;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

public abstract class WxTemplateDAO extends HibernateSupportDelegateDAO<WxTemplate> {
    private static final Log logger = LogFactory.getLog(WxTemplateDAO.class);


    public WxTemplateDAO() {
        super();
        this.setKeyField("id");
        this.setEntityName(WxTemplate.class.getName());
    }

    /**
     * 根据给定appId、templateKey查询对应的模板id
     * @param templateKey
     * @param appId
     * @return
     */
    @RpcService
    public String getTemplateIdByTemplateKeyAndAppId(String templateKey, String appId){
        WxTemplate template = getTemplateByTemplateKeyAndAppId(templateKey,appId);
        if(template==null){
            return null;
        }
        return template.getTemplateId();
    }


    /**
     * 根据给定appId、templateKey查询对应的模板
     * @param templateKey
     * @param appId
     * @return
     */
    @DAOMethod(sql = "FROM WxTemplate WHERE templateKey=:templateKey AND appId=:appId")
    public abstract WxTemplate getTemplateByTemplateKeyAndAppId(
            @DAOParam("templateKey") String templateKey,
            @DAOParam("appId") String appId);


    /**
     * 根据给定appId、templateKey查询对应的模板
     * @param templateKey
     * @param appId
     * @return
     */
    public  WxTemplate getTemplateByAppIdAndTemplateKey(String appId, String templateKey)
    {
        if (StringUtils.isEmpty(appId)) {
            new DAOException(DAOException.VALUE_NEEDED, "appId is required!");
        }
        if (StringUtils.isEmpty(templateKey)) {
            new DAOException(DAOException.VALUE_NEEDED, "templateKey is required!");
        }
        return this.getTemplateByTemplateKeyAndAppId(templateKey,appId);
    }


    /**
     * 根据appId查询对应的模板列表
     * @param appId
     * @return
     */
    @DAOMethod(sql = "FROM WxTemplate WHERE appId=:appId")
    public abstract List<WxTemplate> findTemplateListByAppId(
            @DAOParam("appId") String appId);

    /**
     * 根据给定templateKey查询对应的模板列表
     * @param templateKey
     * @return
     */
    @DAOMethod(sql = "FROM WxTemplate WHERE templateKey=:templateKey ")
    public abstract List<WxTemplate> findTemplateListByTemplateKey(
            @DAOParam("templateKey") String templateKey);

    @DAOMethod(sql = "delete from WxTemplate where id=:id")
    public abstract void removeTemplateById(@DAOParam("id") Integer id);

    public void removeTemplatesByIdList(List<Integer> lists)
    {
        if (lists != null)
        {
            Iterator ite = lists.iterator();
            while (ite.hasNext())
            {
                this.removeTemplateById(Integer.parseInt(ite.next().toString()));
            }
        }
    }

    public WxTemplate addOrUpdate(WxTemplate template)
    {
        if (template == null)
        {
            return null;
        }
        if (template.getId() != null && template.getId()>0)
        {
            return this.update(template);
        }
        else
        {
            return this.save(template);
        }
    }

    public List<WxTemplate>  addOrUpdateBatch(List<WxTemplate> templates)
    {
        if (templates == null)
        {
            return null;
        }
        List<WxTemplate> returnTemplates = new ArrayList<WxTemplate>();
        Iterator ite = templates.iterator();
        while (ite.hasNext())
        {
            WxTemplate template = (WxTemplate)ite.next();
            WxTemplate dbTemplate = this.getTemplateByTemplateKeyAndAppId(template.getTemplateKey(),template.getAppId());
            if (template.getId() != null && template.getId()>0)
            {
                if (dbTemplate != null)// db object exist
                {
                    if (dbTemplate.getId().intValue() != template.getId().intValue()) //conflict, need to throw exception
                    {
                        throw new DAOException(DAOException.VALIDATE_FALIED, "ID conflic, ID from Database not equal to ID from Frontend.");
                    }
                    returnTemplates.add(this.update(template));
                }
                else//not exist, need to validation before save
                {
                    template.setId(null);
                    returnTemplates.add(this.save(template));
                }
            }
            else
            {
                if (dbTemplate != null)
                {
                    template.setId(dbTemplate.getId());
                    returnTemplates.add(this.update(template));
                }
                else
                {
                    returnTemplates.add(this.save(template));
                }
            }
        }
        return returnTemplates;
    }

    private String validateTemplate(WxTemplate template)
    {
        StringBuilder err = new StringBuilder();
        if(template == null)
        {
            err.append("WxTemplate Object is required!");
        }else {
            if (StringUtils.isEmpty(template.getName())) {
                err.append(template.getName() + " name is required!");
            }

            if (StringUtils.isEmpty(template.getAppId())) {
                err.append(template.getName() + " appid is required!");
            }

            if (StringUtils.isEmpty(template.getTemplateId())) {
                err.append(template.getName() + " templateID is required!");
            }

            if (StringUtils.isEmpty(template.getTemplateKey())) {
                err.append(template.getName() + " templateKey is required!");
            }

            if (StringUtils.isEmpty(template.getOfficialAccountsName())) {
                err.append(template.getName() + " OfficialAccountName is required!");
            }
        }
        return err.toString();
    }

    public WxTemplate save(WxTemplate template) {
        String error = this.validateTemplate(template);
        if (!StringUtils.isEmpty(error))
        {
            throw new DAOException(DAOException.VALUE_NEEDED, error);
        }
        template.setCreateTime(new Date());
        if (template.getTemplateId() == null )
        {
            logger.error("template.getTemplateId is null "+JSONUtils.toString(template));
        }
        super.save(template);
        if (template.getId() > 0)
        {
            return template;
        }
        return null;
    }

    public WxTemplate update(WxTemplate template) {
        if (template.getId() == null || (template.getId() != null && template.getId()<=0))
        {
            throw new DAOException(DAOException.VALUE_NEEDED, "Invalid WxTemplate Object to update!");
        }
        String error = this.validateTemplate(template);
        if (!StringUtils.isEmpty(error))
        {
            throw new DAOException(DAOException.VALUE_NEEDED, error);
        }
        WxTemplate target = super.get(template.getId());
        BeanUtils.map(template, target);
        super.update(target);
        return target;
    }

}
