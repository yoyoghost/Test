package eh.base.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.base.ScratchableBox;

import java.util.List;

/**
 * @author jianghc
 * @create 2016-11-17 9:44
 **/
public abstract class ScratchableBoxDAO extends HibernateSupportDelegateDAO<ScratchableBox> {

    public ScratchableBoxDAO(){
        super();
        this.setEntityName(ScratchableBox.class.getName());
        this.setKeyField("moduleId");
    }

    @DAOMethod
    public abstract ScratchableBox getByModuleId(Integer moduleId);

    @DAOMethod(sql = " from ScratchableBox where configType=:configType order by moduleId")
    public abstract List<ScratchableBox> findAllBox(@DAOParam("configType")String configType);



}
