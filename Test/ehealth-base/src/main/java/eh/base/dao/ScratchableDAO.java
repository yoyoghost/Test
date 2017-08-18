package eh.base.dao;


import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import eh.entity.base.Scratchable;
import eh.entity.base.ScratchableBox;
import org.apache.axis.utils.StringUtils;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.ArrayList;
import java.util.List;

public abstract class ScratchableDAO extends HibernateSupportDelegateDAO<Scratchable> {
    public ScratchableDAO() {
        super();
        this.setEntityName(Scratchable.class.getName());
        this.setKeyField("latexId");
    }


    /**
     * 微信九宫格
     * @param configId
     * @param tempId
     * @return
     */
    public List<Scratchable> findModelsByConfigId(String configId, Integer tempId) {
        if (tempId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, " tempId  is required");

        }
        final StringBuilder sbHql = new StringBuilder("  from Scratchable a where configType='wechat' ");
        sbHql.append(" and a.tempId =").append(tempId);
        if (configId != null) {
            sbHql.append(" and a.configId=").append(configId);
        } else {
            sbHql.append(" and a.level = 0");
        }
        sbHql.append(" order by a.orderNum asc ");
        final HibernateStatelessResultAction<List<Scratchable>> action = new AbstractHibernateStatelessResultAction<List<Scratchable>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                Query query = ss.createQuery(sbHql.toString());
                query.setFirstResult(0); // 设置第一条记录开始的位置
                query.setMaxResults(999); // 设置返回的纪录总条数
                List<Scratchable> qList = query.list();
                List<Scratchable> list = new ArrayList<Scratchable>();
                ScratchableBoxDAO scratchableBoxDAO = DAOFactory.getDAO(ScratchableBoxDAO.class);
                for (Scratchable scratchable : qList) {
                    ScratchableBox scratchableBox = scratchableBoxDAO.getByModuleId(scratchable.getModulId());
                    if (scratchableBox!=null){
                        if(scratchable.getBoxIcon()==null||StringUtils.isEmpty(scratchable.getBoxIcon().trim())){
                            scratchable.setBoxIcon(scratchableBox.getBoxIcon());
                        }
                        if(scratchable.getBoxTxt()==null|| StringUtils.isEmpty(scratchable.getBoxTxt().trim())){
                            scratchable.setBoxTxt(scratchableBox.getBoxTxt());
                        }
                        if(scratchable.getBoxDesc()==null|| StringUtils.isEmpty(scratchable.getBoxDesc().trim())){
                            scratchable.setBoxDesc(scratchableBox.getBoxDesc());
                        }
                        if(scratchable.getLinkType()==null){
                            scratchable.setLinkType(scratchableBox.getLinkType());
                        }
                        if(scratchable.getBoxLink()==null|| StringUtils.isEmpty(scratchable.getBoxLink().trim())){
                            scratchable.setBoxLink(scratchableBox.getBoxLink());
                        }
                    }
                    if (!scratchable.getUsed()){
                        String[] strs = scratchable.getBoxIcon().split("\\.");
                        if (strs.length==2) {
                            scratchable.setBoxIcon(strs[0] + "_unopen." + strs[1]);
                        }
                    }

                    list.add(scratchable);

                }
               setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    @DAOMethod(sql = " from Scratchable where configType=:configType and configId=:configId and tempId=:tempId order by orderNum asc")
    public abstract List<Scratchable> findByConfigIdAndTempId(@DAOParam("configType")String configType,@DAOParam("configId") String configId,@DAOParam("tempId") Integer tempId);

    @DAOMethod(sql = " from Scratchable where configId is null and configType=:configType and tempId=:tempId order by orderNum asc")
    public abstract List<Scratchable> findDefaultByTempId(@DAOParam("configType")String configType,@DAOParam("tempId") Integer tempId);

    @DAOMethod(sql = " delete from Scratchable where configId=:configId and tempId=:tempId and configType=:configType")
    public abstract void deleteByConfigIdAndTempId(@DAOParam("configId") String configId,@DAOParam("tempId") Integer tempId,@DAOParam("configType")String configType);

    @DAOMethod(sql = " delete from Scratchable where configId is null and tempId=:tempId and configType=:configType")
    public abstract void deleteDefaultByTempId(@DAOParam("tempId") Integer tempId,@DAOParam("configType")String configType);

}


